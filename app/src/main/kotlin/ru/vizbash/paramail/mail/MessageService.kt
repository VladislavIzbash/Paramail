package ru.vizbash.paramail.mail

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.room.withTransaction
import com.sun.mail.imap.IMAPFolder
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import ru.vizbash.paramail.storage.MailDatabase
import ru.vizbash.paramail.storage.account.FolderEntity
import ru.vizbash.paramail.storage.account.MailAccount
import ru.vizbash.paramail.storage.message.Attachment
import ru.vizbash.paramail.storage.message.Message
import ru.vizbash.paramail.storage.message.MessageBody
import java.io.File
import java.lang.Integer.max
import javax.mail.*
import javax.mail.Message.RecipientType
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMultipart
import javax.mail.internet.MimeUtility
import kotlin.time.DurationUnit
import kotlin.time.toDuration

private const val TAG = "MessageService"
private const val DOWNLOAD_BLOCK_SIZE = 16000
private const val ATTACHMENTS_DIR = "attachments"
private const val FETCH_COUNT = 30

private val FETCH_PROFILE = FetchProfile().apply {
    add(FetchProfile.Item.ENVELOPE)
    add(FetchProfile.Item.FLAGS)
    add("Newsgroups")
}

enum class FetchState { FETCHING_NEW, FETCHING_OLD, DONE, ERROR }

class MessageService(
    private val account: MailAccount,
    private val db: MailDatabase,
    private val mailService: MailService,
    private val context: Context,
    private val coroutineScope: CoroutineScope,
    val folderEntity: FolderEntity,
) {
    companion object {
        private var fetchJob: Job? = null // Одна корутина на все папки
    }

    private val _fetchState = MutableStateFlow(FetchState.DONE)
    val fetchState = _fetchState.asStateFlow()

    private suspend inline fun <R> useFolder(crossinline block: suspend (IMAPFolder) -> R): R {
        return withContext(Dispatchers.IO) {
            mailService.connectImap(account.props, account.imap).use { store ->
                val folder = store.defaultFolder.getFolder(folderEntity.name).apply {
                    open(Folder.READ_ONLY)
                }

                Log.d(TAG, "${account.imap.creds!!.login}: opened folder ${folderEntity.name}")
                val ret = folder.use {
                    block(it as IMAPFolder)
                }
                Log.d(TAG, "${account.imap.creds.login}: closed folder ${folderEntity.name}")

                ret
            }
        }
    }

    val pagingSource get() = db.messageDao().getAllPaged(account.id, folderEntity.id)

//    val pagingSource: PagingSource<Int, Message>
//        get() {
//            val source = db.messageDao().getAllPaged(account.id, folderEntity.id)
//
//            return object : PagingSource<Int, Message>() {
//                init {
//                    source.registerInvalidatedCallback { invalidate() }
//                }
//
//                override val jumpingSupported get() = source.jumpingSupported
//                override val keyReuseSupported get() = source.keyReuseSupported
//
//                override fun getRefreshKey(state: PagingState<Int, Message>): Int? {
//                    return source.getRefreshKey(state)
//                }
//
//                override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Message> {
//                    val result = source.load(params)
//                    if (result is LoadResult.Page && result.nextKey == null) {
//                        suspendCancellableCoroutine<Unit> { }
//                    }
//                    return result
//                }
//
//            }
//        }

    private fun getAllRecipients(msg: javax.mail.Message): List<String> {
        return sequenceOf(RecipientType.TO, RecipientType.CC, RecipientType.BCC)
            .mapNotNull(msg::getRecipients)
            .flatMap {
                it.map {
                    val addr = it as InternetAddress
                    if (addr.personal.isNullOrEmpty()) {
                        addr.address
                    } else {
                        "${addr.personal} <${addr.address}>"
                    }
                }
            }
            .toList()
    }

    private fun convertToEntity(msg: javax.mail.Message): Message? {
        if (msg.allRecipients == null || msg.subject == null) {
            return null
        }

        val from = msg.from.first() as InternetAddress

        return Message(
            id = 0,
            msgNum = msg.messageNumber,
            accountId = account.id,
            folderId = folderEntity.id,
            subject = msg.subject,
            recipients = getAllRecipients(msg),
            from = from.address,
            date = msg.receivedDate,
            isUnread = !msg.isSet(Flags.Flag.SEEN),
        )
    }

    private suspend fun downloadMessageRange(folder: IMAPFolder, startNum: Int, endNum: Int) {
        for (msgNum in endNum downTo startNum step FETCH_COUNT) {
            val pageStart = max(msgNum - FETCH_COUNT, startNum)

            Log.d(TAG, "fetching messages from $pageStart to $msgNum")

            val page = folder.getMessages(pageStart, msgNum)
            folder.fetch(page, FETCH_PROFILE)

            val entities = page.mapNotNull(this@MessageService::convertToEntity)
            db.messageDao().insertAll(entities)
        }
    }

    fun startMessageListUpdate() {
        fetchJob?.cancel()
        fetchJob = coroutineScope.launch {
            try {
                useFolder { folder ->
                    Log.d(TAG, "starting message list update for folder ${folderEntity.name}")

                    _fetchState.value = FetchState.FETCHING_OLD

                    val localOldest = db.messageDao().getOldest(account.id, folderEntity.id)?.msgNum
                    if (localOldest == null || localOldest > 1) {
                        Log.d(TAG, "downloading history for folder ${folderEntity.name}")
                        downloadMessageRange(folder, 1, localOldest ?: folder.messageCount)
                    }

                    if (!folder.hasNewMessages()) {
                        Log.d(TAG, "${folderEntity.name} already up to date")
                        _fetchState.value = FetchState.DONE
                        return@useFolder
                    }

                    _fetchState.value = FetchState.FETCHING_NEW

                    val localRecent = db.messageDao().getMostRecent(account.id, folderEntity.id)
                    downloadMessageRange(folder, localRecent?.msgNum ?: 1, folder.messageCount)

                    _fetchState.value = FetchState.DONE
                }
            } catch (e: MessagingException) {
                e.printStackTrace()
                Log.d(TAG, "error fetching messages, retrying in 10 secs")

                delay(10.toDuration(DurationUnit.SECONDS))

                fetchJob = null
                _fetchState.value = FetchState.ERROR
                startMessageListUpdate()
            }
        }
    }

    suspend fun getById(messageId: Int) = db.messageDao().getById(messageId)

    suspend fun downloadAttachment(
        attachment: Attachment,
        progressCb: (Float) -> Unit,
    ): Uri? {
        val message = db.messageDao().getById(attachment.msgId)!!

        return useFolder { folder ->
            val remoteMessage = folder.getMessage(message.msgNum)
            val mixed = remoteMessage.content as MimeMultipart

            val part = (1 until mixed.count)
                .map(mixed::getBodyPart)
                .find { MimeUtility.decodeText(it.fileName) == attachment.fileName }!!
            val input = part.inputStream

            File("${context.filesDir}/$ATTACHMENTS_DIR").mkdir()
            val file = File("${context.filesDir}/$ATTACHMENTS_DIR/${attachment.id.toUInt()})")

            Log.d(TAG, "${account.imap.creds!!.login}: downloading ${attachment.fileName}")

            try {
                file.outputStream().use { output ->
                    val buf = ByteArray(DOWNLOAD_BLOCK_SIZE)
                    var totalRead = 0

                    progressCb(0F)

                    while (true) {
                        yield()

                        val read = input.read(buf)
                        if (read <= 0) {
                            progressCb(1.0F)
                            break
                        }

                        output.write(buf, 0, read)
                        totalRead += read
                        progressCb(totalRead.toFloat() / part.size.toFloat())
                    }
                }

                Log.d(TAG, "${account.imap.creds.login}: downloaded ${attachment.fileName}")

                return@useFolder FileProvider.getUriForFile(
                    context,
                    "ru.vizbash.paramail.attachmentprovider",
                    file,
                )
            } catch (e: CancellationException) {
                file.delete()
                Log.d(TAG, "${account.imap.creds.login}: canceled download of ${attachment.fileName}")
                return@useFolder null
            }
        }
    }

    suspend fun getMessageBody(message: Message): Pair<MessageBody?, List<Attachment>> {
        val storedBody = db.messageDao().getMessageBody(message.id)
        if (storedBody != null) {
            val attachments = db.messageDao().getAttachments(message.id)
            return Pair(storedBody, attachments)
        } else {
            return useFolder { folder ->
                Log.d(TAG, "${account.imap.creds!!.login}: downloading body for message ${message.msgNum}")

                val remoteMsg = folder.getMessage(message.msgNum)

                val attachments = extractAttachments(remoteMsg, message.id)

                val textPart = findTextPart(remoteMsg) ?: return@useFolder Pair(null, attachments)
                val body = MessageBody(message.id, textPart.inputStream.readBytes(), textPart.contentType)

                db.withTransaction {
                    db.messageDao().insertBody(body)
                    db.messageDao().insertAttachments(attachments)
                }
                Pair(body, attachments)
            }
        }
    }

    fun searchMessages(query: String): Flow<List<Message>> {
        return db.messageDao().searchEnvelopes(account.id, folderEntity.id, "%$query%")
    }

    private fun extractAttachments(msg: javax.mail.Message, msgId: Int): List<Attachment> {
        if (!msg.contentType.startsWith("multipart/mixed")) {
            return listOf()
        }

        val multipart = msg.content as MimeMultipart
        return (1 until multipart.count)
            .map(multipart::getBodyPart)
            .map {
                Attachment(
                    id = 0,
                    msgId = msgId,
                    fileName = MimeUtility.decodeText(it.fileName),
                    mime = it.contentType,
                    size = it.size,
                )
            }
    }

    private fun findTextPart(part: Part): Part? {
        return when {
            part.contentType.startsWith("text/") -> part
            part.contentType.startsWith("multipart/mixed") || part.contentType.startsWith("multipart/related") -> {
                // В mixed первой идёт основная часть
                findTextPart((part.content as MimeMultipart).getBodyPart(0))
            }
            part.contentType.startsWith("multipart/alternative") -> {
                val multipart = part.content as MimeMultipart
                // В alternative предпочтительный вариант идёт последним
                findTextPart(multipart.getBodyPart(multipart.count - 1))
            }
            else -> null
        }
    }
}