package ru.vizbash.paramail.mail

import android.util.Log
import androidx.paging.*
import androidx.room.withTransaction
import com.sun.mail.iap.CommandFailedException
import com.sun.mail.imap.IMAPFolder
import com.sun.mail.imap.IMAPStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.vizbash.paramail.storage.MailDatabase
import ru.vizbash.paramail.storage.entity.MailAccount
import ru.vizbash.paramail.storage.message.Message
import ru.vizbash.paramail.storage.message.MessageBody
import javax.mail.BodyPart
import javax.mail.Flags
import javax.mail.Folder
import javax.mail.Message.RecipientType
import javax.mail.MessagingException
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMultipart
import javax.mail.search.BodyTerm
import javax.mail.search.FromStringTerm
import javax.mail.search.OrTerm
import javax.mail.search.SubjectTerm

private const val TAG = "MessageService"

class MessageService(
    private val account: MailAccount,
    private val db: MailDatabase,
    private val mailService: MailService,
) {
    private var store: IMAPStore? = null

    val storedMessages: PagingSource<Int, Message>
        get() = db.messageDao().pageAll(account.id)

    private suspend fun connectStore(): IMAPStore {
        if (store == null || !store!!.isConnected) {
            store = mailService.connectImap(account.props, account.imap)
        }
        return store!!
    }

    private suspend fun openFolder(): IMAPFolder {
        return connectStore().getFolder("INBOX").apply {
            open(Folder.READ_ONLY)
        } as IMAPFolder
    }

    private fun fetchMessages(folder: IMAPFolder, startNum: Int, count: Int): List<Message> {
        return folder.getMessages(startNum - count + 1, startNum)
            .filter { it.allRecipients != null && it.subject != null }
            .reversed()
            .mapNotNull(this::convertToEntity)
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
    val remoteMediator: RemoteMediator<Int, Message>
        get() = object : RemoteMediator<Int, Message>() {

            override suspend fun initialize() = withContext(Dispatchers.IO) {
                val hasNewMsgs = connectStore().defaultFolder.getFolder("INBOX").hasNewMessages()
                val hasCachedMsgs = db.messageDao().getMessageCount() > 0

                if (!hasCachedMsgs || hasNewMsgs) {
                    InitializeAction.LAUNCH_INITIAL_REFRESH
                } else {
                    InitializeAction.SKIP_INITIAL_REFRESH
                }
            }

            override suspend fun load(
                loadType: LoadType,
                state: PagingState<Int, Message>,
            ): MediatorResult = withContext(Dispatchers.IO) {
                try {
                    val folder = openFolder()

                    val startNum = when (loadType) {
                        LoadType.REFRESH -> folder.messageCount
                        LoadType.PREPEND -> return@withContext MediatorResult.Success(true)
                        LoadType.APPEND -> state.lastItemOrNull()?.msgNum ?: folder.messageCount
                    }

                    val pageSize = state.config.pageSize

                    Log.d(TAG, "loading $pageSize messages starting from page $startNum")

                    val reachedEnd = startNum - pageSize <= 0
                    if (!reachedEnd) {
                        val messages = fetchMessages(folder, startNum, pageSize)
                        db.withTransaction {
                            if (loadType == LoadType.REFRESH) {
                                db.messageDao().clearAll()
                            }

                           db.messageDao().insertAll(messages)
                        }
                    }

                    MediatorResult.Success(reachedEnd)
                } catch (e: MessagingException) {
                    e.printStackTrace()
                    MediatorResult.Error(e)
                }
            }
        }

    suspend fun getById(messageId: Int) = db.messageDao().getById(messageId)

    suspend fun getTextBody(message: Message): MessageBody? {
        return db.messageDao().getMessageBody(message.id)
            ?: withContext(Dispatchers.IO) {
                val folder = openFolder()
                val remoteMsg = folder.getMessage(message.msgNum)

                val (content, mime) = extractTextBody(remoteMsg) ?: return@withContext null
                val body = MessageBody(message.id, content, mime)

                db.messageDao().insertBody(body)
                body
            }
    }

    private fun extractTextBody(msg: javax.mail.Message): Pair<ByteArray, String>? {
        return when {
            msg.contentType.startsWith("text/") -> {
                Pair(msg.inputStream.readBytes(), msg.contentType)
            }
            msg.contentType.startsWith("multipart/") -> {
                findTextPartInMultipart(msg.content as MimeMultipart, msg.contentType)?.let {
                    Pair(it.inputStream.readBytes(), it.contentType)
                }
            }
            else -> null
        }
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
            contentType.startsWith("multipart/mixed") -> {
                findTextPartInMixed(multipart)
            }
            contentType.startsWith("multipart/related") -> {
                findTextPartInRelated(multipart)
            }
            contentType.startsWith("multipart/alternative") -> {
                findTextPartInAlternative(multipart)
            }
            else -> null
        }
    }

    private fun findTextPartInMixed(mixed: MimeMultipart): BodyPart? {
        for (i in 0 until mixed.count) {
            val part = mixed.getBodyPart(i)
            if (
                part.contentType.startsWith("multipart/mixed") ||
                part.contentType.startsWith("multipart/alternative") ||
                part.contentType.startsWith("multipart/related") ||
                part.contentType.startsWith("text/")
            ) {
                return findTextPart(part)
            }
        }
        return null // TODO: attachments
    }

    private fun findTextPartInAlternative(alternative: MimeMultipart): BodyPart? {
        return if (alternative.count > 0) {
             findTextPart(alternative.getBodyPart(alternative.count - 1))
        } else {
            null
        }
    }

    private fun findTextPartInRelated(related: MimeMultipart): BodyPart? {
        return if (related.count > 0) {
            findTextPart(related.getBodyPart(0))
        } else {
            null
        }
    }

    suspend fun searchMessages(pattern: String): List<Message>? = withContext(Dispatchers.IO) {
        val folder = openFolder()

        val term = OrTerm(arrayOf(FromStringTerm(pattern), SubjectTerm(pattern), BodyTerm(pattern)))
        val nums = folder.doCommand { p ->
            try {
                p.search(term)
            } catch (e: CommandFailedException) {
                null
            }
        } as Array<Int>? ?: return@withContext null

        nums.mapNotNull { msgNum ->
            db.messageDao().getByMsgNum(msgNum)
                ?: convertToEntity(folder.getMessage(msgNum))
        }
    }
}