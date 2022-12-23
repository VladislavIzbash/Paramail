package ru.vizbash.paramail.mail

import android.util.Log
import androidx.paging.*
import com.sun.mail.imap.IMAPStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.vizbash.paramail.storage.MessageDao
import ru.vizbash.paramail.storage.entity.MailAccount
import ru.vizbash.paramail.storage.entity.Message
import ru.vizbash.paramail.storage.entity.MessageBody
import javax.activation.MimeType
import javax.mail.Address
import javax.mail.Flags
import javax.mail.Folder
import javax.mail.MessagingException
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMultipart

private const val TAG = "MessageService"

@OptIn(ExperimentalPagingApi::class)
class MessageService(
    private val account: MailAccount,
    private val messageDao: MessageDao,
    private val mailService: MailService,
) {
    private var store: IMAPStore? = null

    val storedMessages: PagingSource<Int, Message>
        get() = messageDao.pageAll(account.id)

    private suspend fun connectStore(): IMAPStore {
        if (store == null || !store!!.isConnected) {
            store = mailService.connectImap(account.props, account.imap)
        }
        return store!!
    }

    private suspend fun fetchMessages(folder: Folder, startNum: Int, count: Int) {
        val messages = folder.getMessages(startNum, startNum + count)
            .filter { it.subject != null }
            .map {
                val from = it.from.first() as InternetAddress

                Message(
                    id = 0,
                    msgnum = it.messageNumber,
                    accountId = account.id,
                    subject = it.subject,
                    recipients = it.allRecipients.map(Address::toString),
                    from = from.address,
                    body_id = null,
                    date = it.receivedDate,
                    isUnread = !it.isSet(Flags.Flag.SEEN),
                )
            }

        messageDao.insert(messages)
    }

    val remoteMediator: RemoteMediator<Int, Message>
        get() = object : RemoteMediator<Int, Message>() {

            override suspend fun load(
                loadType: LoadType,
                state: PagingState<Int, Message>,
            ): MediatorResult = withContext(Dispatchers.IO) {
                val startNum = when (loadType) {
                    LoadType.REFRESH -> 1
                    LoadType.PREPEND -> return@withContext MediatorResult.Success(true)
                    LoadType.APPEND -> (state.lastItemOrNull()?.msgnum ?: 0) + 1
                }
                val pageSize = if (loadType == LoadType.REFRESH) {
                    state.config.initialLoadSize
                } else {
                    state.config.pageSize
                }

                Log.d(TAG, "loading $pageSize message starting from $startNum")

                try {
                    val folder = connectStore().getFolder("INBOX").apply {
                        open(Folder.READ_ONLY)
                    }
                    fetchMessages(folder, startNum, pageSize)

                    val reachedEnd = startNum + pageSize >= folder.messageCount
                    if (reachedEnd) {
                        Log.d(TAG, "reached end of folder")
                    }

                    MediatorResult.Success(reachedEnd)
                } catch (e: MessagingException) {
                    MediatorResult.Error(e)
                }
            }
        }

    suspend fun getById(messageId: Int) = messageDao.getById(messageId)

    suspend fun getMessageBody(message: Message): MessageBody {
        return if (message.body_id == null) {
            withContext(Dispatchers.IO) {
                val folder = connectStore().getFolder("INBOX").apply {
                    open(Folder.READ_ONLY)
                }
                val remoteMsg = folder.getMessage(message.msgnum)
                val content = parseContent(remoteMsg.contentType, remoteMsg.content)

                val body = MessageBody(
                    id = 0,
                    content = content,
                    mime = remoteMsg.contentType,
                )
                messageDao.setBody(message, body)
                body
            }
        } else {
            messageDao.getBodyById(message.body_id)!!
        }
    }

    private fun parseContent(contentType: String, content: Any): String {
        return when {
            contentType.startsWith("text/") -> content.toString()
            contentType.startsWith("multipart/alternative") -> {
                assembleMultipart(content as MimeMultipart)
            }
            else -> "$contentType are not supported yet"
        }
    }

    private fun assembleMultipart(multipart: MimeMultipart): String {
        val content = (0 until multipart.count).asSequence()
            .map {
                val bodyPart = multipart.getBodyPart(it)
                parseContent(bodyPart.contentType, bodyPart.content)
            }
            .joinToString("\n=====================\n")

        return content
    }
}