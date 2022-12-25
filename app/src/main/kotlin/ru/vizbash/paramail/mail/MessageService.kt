package ru.vizbash.paramail.mail

import android.util.Log
import androidx.paging.*
import com.sun.mail.imap.IMAPStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.vizbash.paramail.storage.MessageDao
import ru.vizbash.paramail.storage.entity.MailAccount
import ru.vizbash.paramail.storage.entity.Message
import ru.vizbash.paramail.storage.entity.MessagePart
import javax.mail.Address
import javax.mail.Flags
import javax.mail.Folder
import javax.mail.MessagingException
import javax.mail.internet.ContentType
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

                Log.d(TAG, "loading $pageSize messages starting from $startNum")

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

    suspend fun getMessageBody(message: Message): List<MessagePart> {
        return messageDao.getBodyParts(message.id).ifEmpty {
            withContext(Dispatchers.IO) {
                val folder = connectStore().getFolder("INBOX").apply {
                    open(Folder.READ_ONLY)
                }
                val remoteMsg = folder.getMessage(message.msgnum)

                val parts = fetchParts(remoteMsg, message.id)
                messageDao.insertBodyParts(parts)
                parts
            }
        }
    }

    private fun fetchParts(msg: javax.mail.Message, msgId: Int): List<MessagePart> {
        return when {
            msg.contentType.startsWith("multipart/mixed") -> {
                fetchMixed(msg.content as MimeMultipart, msgId)
            }
            msg.contentType.startsWith("multipart/alternative") -> {
                 listOf(fetchAlternative(msg.content as MimeMultipart, msgId))
            }
            else -> listOf(MessagePart(
                id = 0,
                messageId = msgId,
                content = msg.inputStream.readBytes(),
                mime = msg.contentType,
            ))
        }
    }

    private fun fetchMixed(multipart: MimeMultipart, msgId: Int): List<MessagePart> {
        return (0 until multipart.count).map {
            val part = multipart.getBodyPart(it)
            if (part.contentType.startsWith("multipart/alternative")) {
                fetchAlternative(part.content as MimeMultipart, msgId)
            } else {
                MessagePart(
                    id = 0,
                    messageId = msgId,
                    content = part.inputStream.readBytes(),
                    mime = part.contentType,
                )
            }
        }
    }

    private fun fetchAlternative(multipart: MimeMultipart, msgId: Int): MessagePart {
        val last = multipart.getBodyPart(multipart.count - 1)
        return MessagePart(
            id = 0,
            messageId = msgId,
            content = last.inputStream.readBytes(),
            mime = last.contentType,
        )
    }
}