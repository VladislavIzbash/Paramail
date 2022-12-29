package ru.vizbash.paramail.mail

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import androidx.paging.*
import androidx.room.withTransaction
import com.sun.mail.iap.CommandFailedException
import com.sun.mail.imap.IMAPFolder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import ru.vizbash.paramail.storage.MailDatabase
import ru.vizbash.paramail.storage.account.FolderEntity
import ru.vizbash.paramail.storage.account.MailAccount
import ru.vizbash.paramail.storage.message.Attachment
import ru.vizbash.paramail.storage.message.Message
import ru.vizbash.paramail.storage.message.MessageBody
import java.io.File
import javax.mail.BodyPart
import javax.mail.Flags
import javax.mail.Folder
import javax.mail.Message.RecipientType
import javax.mail.MessagingException
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMultipart
import javax.mail.internet.MimeUtility
import javax.mail.search.BodyTerm
import javax.mail.search.FromStringTerm
import javax.mail.search.OrTerm
import javax.mail.search.SubjectTerm

private const val TAG = "MessageService"
private const val DOWNLOAD_BLOCK_SIZE = 16000
private const val ATTACHMENTS_DIR = "attachments"

class MessageService(
    private val account: MailAccount,
    private val db: MailDatabase,
    private val mailService: MailService,
    private val context: Context,
) {
    private val storeMutex = Mutex()

    private suspend inline fun <R> useFolder(name: String, crossinline block: suspend (IMAPFolder) -> R): R {
        return withContext(Dispatchers.IO) {
            storeMutex.withLock {
                mailService.connectImap(account.props, account.imap).use { store ->
                    val folder = store.defaultFolder.getFolder(name).apply {
                        open(Folder.READ_ONLY)
                    }
                    folder.use {
                        block(it as IMAPFolder)
                    }
                }

            }
        }
    }

    fun getStoredMessages(folderName: String): PagingSource<Int, Message> {
        return db.messageDao().getAllPaged(account.id, folderName)
    }

    private fun convertToEntity(msg: javax.mail.Message, folderId: Int): Message? {
        if (msg.allRecipients == null || msg.subject == null) {
            return null
        }

        val from = msg.from.first() as InternetAddress

        return Message(
            id = 0,
            msgNum = msg.messageNumber,
            accountId = account.id,
            folderId = folderId,
            subject = msg.subject,
            recipients = msg.getRecipients(RecipientType.TO).map {
                val addr = it as InternetAddress
                if (addr.personal.isEmpty()) {
                    addr.address
                } else {
                    "${addr.personal} <${addr.address}>"
                }
            },
            from = from.address,
            date = msg.receivedDate,
            isUnread = !msg.isSet(Flags.Flag.SEEN),
        )
    }

    @OptIn(ExperimentalPagingApi::class)
    fun getRemoteMediator(folderName: String): RemoteMediator<Int, Message> {
        return object : RemoteMediator<Int, Message>() {

            override suspend fun initialize(): InitializeAction {
                return try {
                    useFolder(folderName) { folder ->
                        val hasNewMsgs = folder.hasNewMessages()
                        val hasCachedMsgs = db.messageDao().getMessageCount() > 0

                        if (!hasCachedMsgs || hasNewMsgs) {
                            InitializeAction.LAUNCH_INITIAL_REFRESH
                        } else {
                            InitializeAction.SKIP_INITIAL_REFRESH
                        }
                    }
                } catch (e: MessagingException) {
                    InitializeAction.LAUNCH_INITIAL_REFRESH
                }
            }

            override suspend fun load(
                loadType: LoadType,
                state: PagingState<Int, Message>,
            ): MediatorResult {
                val folderId = db.accountDao().getFolderOrInsert(folderName, account.id).id

                return try {
                    useFolder(folderName) { folder ->
                        val startNum = when (loadType) {
                            LoadType.REFRESH -> folder.messageCount
                            LoadType.PREPEND -> return@useFolder MediatorResult.Success(true)
                            LoadType.APPEND -> state.lastItemOrNull()?.msgNum ?: folder.messageCount
                        }

                        val pageSize = state.config.pageSize

                        Log.d(TAG, "loading $pageSize messages from $folder starting from page $startNum")

                        val reachedEnd = startNum - pageSize <= 0
                        if (!reachedEnd) {
                            val messages = folder.getMessages(startNum - pageSize + 1, startNum)
                                .filter { it.allRecipients != null && it.subject != null }
                                .reversed()
                                .mapNotNull { convertToEntity(it, folderId) }

                            if (loadType == LoadType.REFRESH) {
                                db.messageDao().clearAll()
                            }
                            db.messageDao().insertAll(messages)
                        }

                        MediatorResult.Success(reachedEnd)
                    }
                } catch (e: MessagingException) {
                    MediatorResult.Error(e)
                }
            }
        }
    }

    suspend fun getById(messageId: Int) = db.messageDao().getById(messageId)

    fun getAttachmentUri(attachment: Attachment): Uri? {
        val file = File("${context.filesDir}/$ATTACHMENTS_DIR/${attachment.id.toUInt()})")
        if (!file.exists()) {
            return null
        }

        return FileProvider.getUriForFile(
            context,
            "ru.vizbash.paramail.attachmentprovider",
            file,
        )
    }

    suspend fun downloadAttachment(
        attachment: Attachment,
        progressCb: (Float) -> Unit,
    ) {
        val message = db.messageDao().getById(attachment.msgId)!!
        val folderName = db.accountDao().getFolderById(message.folderId)!!.name

        useFolder(folderName) { folder ->
            val remoteMessage = folder.getMessage(message.msgNum)
            val mixed = remoteMessage.content as MimeMultipart

            val part = (1 until mixed.count)
                .map(mixed::getBodyPart)
                .find { MimeUtility.decodeText(it.fileName) == attachment.fileName }!!
            val input = part.inputStream

            File("${context.filesDir}/$ATTACHMENTS_DIR").mkdir()
            val file = File("${context.filesDir}/$ATTACHMENTS_DIR/${attachment.id.toUInt()})")

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
            } catch (e: CancellationException) {
                file.delete()
            }
        }
    }

    suspend fun getMessageBodyWithAttachments(message: Message): Pair<MessageBody?, List<Attachment>> {
        val folderName = db.accountDao().getFolderById(message.folderId)!!.name

        val storedBody = db.messageDao().getMessageBody(message.id)
        if (storedBody != null) {
            val attachments = db.messageDao().getAttachments(message.id)
            return Pair(storedBody, attachments)
        } else {
            return useFolder(folderName) { folder ->
                val remoteMsg = folder.getMessage(message.msgNum)

                val attachments = extractAttachments(remoteMsg, message.id)
                val body = extractTextBody(remoteMsg, message.id)
                    ?: return@useFolder Pair(null, attachments)

                db.withTransaction {
                    db.messageDao().insertBody(body)
                    db.messageDao().insertAttachments(attachments)
                }
                Pair(body, attachments)
            }
        }
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

    private fun extractTextBody(msg: javax.mail.Message, msgId: Int): MessageBody? {
        val (content, contentType) = when {
            msg.contentType.startsWith("text/") -> {
                Pair(msg.inputStream.readBytes(), msg.contentType)
            }
            msg.contentType.startsWith("multipart/") -> {
                findTextPartInMultipart(msg.content as MimeMultipart, msg.contentType)?.let {
                    Pair(it.inputStream.readBytes(), it.contentType)
                } ?: return null
            }
            else -> return null
        }

        return MessageBody(msgId, content, contentType)
    }

    private fun findTextPart(part: BodyPart): BodyPart? {
        return when {
            part.contentType.startsWith("text/") -> part
            part.contentType.startsWith("multipart/") -> {
                findTextPartInMultipart(part.content as MimeMultipart, part.contentType)
            }
            else -> null
        }
    }

    private fun findTextPartInMultipart(multipart: MimeMultipart, contentType: String): BodyPart? {
        return when {
            contentType.startsWith("multipart/mixed") || contentType.startsWith("multipart/related") -> {
                findTextPart(multipart.getBodyPart(0))
            }
            contentType.startsWith("multipart/alternative") -> {
                findTextPart(multipart.getBodyPart(multipart.count - 1))
            }
            else -> null
        }
    }

    suspend fun searchMessages(
        pattern: String,
        folderName: String,
    ): List<Message>? = useFolder(folderName) { folder ->
        val term = OrTerm(arrayOf(FromStringTerm(pattern), SubjectTerm(pattern), BodyTerm(pattern)))
        val nums = folder.doCommand { p ->
            try {
                p.search(term)
            } catch (e: CommandFailedException) {
                null
            }
        } as Array<*>? ?: return@useFolder null

        nums.mapNotNull { msgNum ->
            db.messageDao().getByMsgNum(msgNum as Int)
                ?: convertToEntity(folder.getMessage(msgNum), 0)
        }
    }

    suspend fun listFolders(): List<String> = withContext(Dispatchers.IO) {
        db.accountDao().getFolders(account.id).ifEmpty {
            val folders = storeMutex.withLock {
                val store = mailService.connectImap(account.props, account.imap)
                store.defaultFolder.list()
            }

            db.accountDao().insertFolders(folders.map { FolderEntity(0, account.id, it.name) })
            folders.map(Folder::getName)
        }
    }

}