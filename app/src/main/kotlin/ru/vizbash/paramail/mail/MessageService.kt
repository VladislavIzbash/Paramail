package ru.vizbash.paramail.mail

import android.content.Context
import android.net.Uri
import android.text.format.DateFormat
import android.util.Log
import androidx.core.content.FileProvider
import androidx.room.withTransaction
import com.sun.mail.imap.IMAPFolder
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import ru.vizbash.paramail.R
import ru.vizbash.paramail.storage.MailDatabase
import ru.vizbash.paramail.storage.account.FolderEntity
import ru.vizbash.paramail.storage.account.MailAccount
import ru.vizbash.paramail.storage.message.*
import ru.vizbash.paramail.storage.message.Address
import ru.vizbash.paramail.storage.message.Message
import java.io.File
import java.lang.Integer.max
import javax.mail.*
import javax.mail.Message.RecipientType
import javax.mail.internet.*
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

    private val longDateFormat = DateFormat.getLongDateFormat(context)

    private val _updateState = MutableStateFlow(FetchState.DONE)
    val updateState = _updateState.asStateFlow()

    private suspend inline fun <R> useFolder(mode: Int = Folder.READ_ONLY, crossinline block: suspend (IMAPFolder) -> R): R {
        return withContext(Dispatchers.IO) {
            mailService.connectImap(account.props, account.imap).use { store ->
                val folder = store.defaultFolder.getFolder(folderEntity.name).apply {
                    open(mode)
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

    private fun convertToEntity(msg: javax.mail.Message): MessageWithRecipients? {
        if (msg.allRecipients == null || msg.subject == null) {
            return null
        }

        val from = msg.from.first() as InternetAddress
        val to = msg.getRecipients(RecipientType.TO).first() as InternetAddress

        return MessageWithRecipients(
            msg = Message(
                id = 0,
                msgNum = msg.messageNumber,
                accountId = account.id,
                folderId = folderEntity.id,
                subject = msg.subject,
                fromId = 0,
                toId = 0,
                date = msg.receivedDate,
                isUnread = !msg.isSet(Flags.Flag.SEEN),
            ),
            from = Address(0, from.address, from.personal),
            to = Address(0, to.address, from.personal),
            cc = (msg.getRecipients(RecipientType.CC) ?: arrayOf()).map {
                val addr = it as InternetAddress
                Address(0, addr.address, addr.personal)
            },
        )
    }

    private suspend fun downloadMessageRange(
        folder: IMAPFolder,
        startNum: Int,
        endNum: Int,
    ) {
        for (msgNum in endNum downTo startNum step FETCH_COUNT) {
            val pageStart = max(msgNum - FETCH_COUNT + 1, startNum)

            Log.d(TAG, "fetching messages from ${folder.name} from $pageStart to $msgNum")

            val page = folder.getMessages(pageStart, msgNum)
            folder.fetch(page, FETCH_PROFILE)

            val entities = page.mapNotNull(this@MessageService::convertToEntity)
            db.messageDao().insertMessagesWithRecipients(entities)
        }
    }

    private suspend fun fetchNewMessages(folder: IMAPFolder): IntRange {
        val localRecent = db.messageDao().getMostRecent(account.id, folderEntity.id)
        if (localRecent?.msgNum == null || localRecent.msgNum == folder.messageCount) {
            Log.d(TAG, "${folderEntity.name} already up to date")
            return folder.messageCount until folder.messageCount
        }

        downloadMessageRange(folder, localRecent.msgNum, folder.messageCount)
        return (localRecent.msgNum + 1)..folder.messageCount
    }

    suspend fun fetchNewMessages(): List<MessageWithRecipients> {
        val newRange = useFolder { fetchNewMessages(it) }
        return db.messageDao().getInRange(account.id, folderEntity.id, newRange.first, newRange.last)
    }

    fun startMessageUpdate() {
        fetchJob?.cancel()
        fetchJob = coroutineScope.launch {
            try {
                useFolder { folder ->
                    Log.d(TAG, "starting message list update for folder ${folderEntity.name}")

                    _updateState.value = FetchState.FETCHING_OLD

                    val localOldest = db.messageDao().getOldest(account.id, folderEntity.id)?.msgNum
                    if (localOldest == null || localOldest > 1) {
                        Log.d(TAG, "downloading history for folder ${folderEntity.name}")
                        downloadMessageRange(folder, 1, localOldest?.minus(1) ?: folder.messageCount)
                    }

                    _updateState.value = FetchState.FETCHING_NEW

                    fetchNewMessages(folder)

                    _updateState.value = FetchState.DONE
                }
            } catch (e: MessagingException) {
                Log.w(TAG, "error fetching messages")
                e.printStackTrace()

                delay(10.toDuration(DurationUnit.SECONDS))

                fetchJob = null
                _updateState.value = FetchState.ERROR
                startMessageUpdate()
            }
        }
    }

    suspend fun getById(messageId: Int) = db.messageDao().getById(messageId)

    suspend fun downloadAttachment(
        attachment: Attachment,
        progressCb: (Float) -> Unit,
    ): Uri?  {
        val message = db.messageDao().getById(attachment.msgId)!!.msg

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

                FileProvider.getUriForFile(
                    context,
                    "ru.vizbash.paramail.attachmentprovider",
                    file,
                )
            } catch (e: CancellationException) {
                file.delete()
                Log.d(TAG, "${account.imap.creds.login}: canceled download of ${attachment.fileName}")
                null
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

                val attachments = findAttachments(remoteMsg, message.id)

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

    fun searchMessages(query: String): Flow<List<MessageWithRecipients>> {
        return db.messageDao().searchEnvelopes(account.id, folderEntity.id, "%$query%")
    }

    private fun findAttachments(msg: javax.mail.Message, msgId: Int): List<Attachment> {
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

    private suspend fun loadTextBody(msg: Message): String {
        val body = getMessageBody(msg).first
        return if (body?.mime?.startsWith("text/plain") == true) {
            body.content.toString(Charsets.UTF_8)
        } else {
            context.getString(R.string.non_plain_text_content)
        }
    }

    suspend fun composeReply(message: MessageWithRecipients, replyToAll: Boolean): ComposedMessage {
        val origText = loadTextBody(message.msg)

        val header = context.getString(
            R.string.reply_block_header,
            longDateFormat.format(message.msg.date),
            message.from.toString(),
        )

        val text = "\n\n" + (header + origText).prependIndent("  \u007c ")

        return ComposedMessage(
            subject = "RE: ${message.msg.subject}",
            to = message.from.address,
            cc = if (replyToAll) message.cc.map(Address::address).toSet() else setOf(),
            text = text,
            type = if (replyToAll) MessageType.REPLY_TO_ALL else MessageType.REPLY,
            origMsgNum = message.msg.msgNum,
            origMsgFolder = folderEntity.name,
        )
    }

    suspend fun composeForward(message: MessageWithRecipients): ComposedMessage {
        val origText = loadTextBody(message.msg)

        val header = context.getString(
            R.string.forward_block_header,
            message.from.toString(),
            message.to.toString(),
            longDateFormat.format(message.msg.date),
            message.msg.subject,
        )

        val text = '\n' + header + origText

        return ComposedMessage(
            subject = "FWD: ${message.msg.subject}",
            text = text,
            type = MessageType.FORWARD,
            origMsgNum = message.msg.msgNum,
            origMsgFolder = folderEntity.name,
        )
    }

    suspend fun sendMessage(message: ComposedMessage) = withContext(Dispatchers.IO) {
        val (session, transport) = mailService.connectSmtp(account.props, account.smtp)

        val outMsg = when (message.type) {
            MessageType.DEFAULT, MessageType.FORWARD -> {
                MimeMessage(session).apply {
                    setFrom(InternetAddress(account.imap.creds!!.login))
                    setRecipient(RecipientType.TO, InternetAddress(message.to))
                    setRecipients(RecipientType.CC, message.cc.map(::InternetAddress).toTypedArray())
                    subject = message.subject
                }
            }
            MessageType.REPLY, MessageType.REPLY_TO_ALL -> {
                useFolder { folder ->
                    val origMsg = folder.getMessage(message.origMsgNum!!)

                    origMsg.reply(message.type == MessageType.REPLY_TO_ALL).apply {
                        setFrom(InternetAddress(account.imap.creds!!.login))
                    }
                }
            }
        }

        if (message.attachments.isNotEmpty()) {
            val content = MimeMultipart("mixed")

            val textPart = MimeBodyPart().apply {
                setContent(message.text, "text/plain")
            }
            content.addBodyPart(textPart)

            for ((uri, fileName) in message.attachments) {
                val type = context.contentResolver.getType(uri) ?: "application/octet-stream"

                val attachmentPart = MimeBodyPart()
                attachmentPart.fileName = fileName

                context.contentResolver.openInputStream(uri)!!.use {
                    attachmentPart.setContent(it.readBytes(), type)
                }

                content.addBodyPart(attachmentPart)
            }

            outMsg.setContent(content)
        } else {
            outMsg.setText(message.text)
        }

        outMsg.saveChanges()
        transport.sendMessage(outMsg, outMsg.allRecipients)

        Log.d(TAG, "${account.imap.creds!!.login}: sent message to ${message.to} (subject: ${message.subject})")
    }

    suspend fun moveToSpam(message: Message) {
        moveToFolder(message) { folder ->
            folder.list().find {
                val attrs = (it as IMAPFolder).attributes
                attrs.contains("\\Spam") || attrs.contains("\\Junk")
            }
        }
    }

    suspend fun moveToArchive(message: Message) {
        moveToFolder(message) { folder ->
            val byAttr = folder.list().find {
                (it as IMAPFolder).attributes.contains("\\Archive")
            }
            byAttr ?: folder.list("Archive").firstOrNull()
        }
    }

    suspend fun markAsSeen(message: Message) {
        if (!message.isUnread) {
            return
        }

        useFolder(Folder.READ_WRITE) {
            it.getMessage(message.msgNum).setFlag(Flags.Flag.SEEN, true)
        }

        db.messageDao().update(message.copy(isUnread = false))
    }

    private suspend fun moveToFolder(message: Message, destFolderFinder: (IMAPFolder) -> Folder?) {
        db.withTransaction {
            db.messageDao().delete(message)

            mailService.connectImap(account.props, account.imap).use { store ->
                val destFolder = destFolderFinder(store.defaultFolder as IMAPFolder)
                    ?: throw MessagingException("Destination folder not found")
                destFolder.open(Folder.READ_WRITE)

                val srcFolderName = db.accountDao().getFolderById(message.folderId)!!.name
                val srcFolder = store.defaultFolder.getFolder(srcFolderName) as IMAPFolder
                srcFolder.open(Folder.READ_WRITE)

                val remoteMsg = srcFolder.getMessage(message.msgNum)
                srcFolder.moveMessages(arrayOf(remoteMsg), destFolder)

                val destFolderId = db.accountDao().getOrInsertFolder(destFolder.name, account.id).toInt()
                db.messageDao().insert(message.copy(id = 0, folderId = destFolderId))
            }
        }
    }
}