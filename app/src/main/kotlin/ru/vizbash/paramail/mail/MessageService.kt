package ru.vizbash.paramail.mail

import android.util.Log
import androidx.paging.*
import androidx.room.withTransaction
import com.sun.mail.imap.IMAPFolder
import com.sun.mail.imap.IMAPStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.vizbash.paramail.storage.MailDatabase
import ru.vizbash.paramail.storage.entity.MailAccount
import ru.vizbash.paramail.storage.message.Message
import ru.vizbash.paramail.storage.message.MessagePagingKey
import ru.vizbash.paramail.storage.message.MessagePart
import javax.mail.Address
import javax.mail.Flags
import javax.mail.Folder
import javax.mail.Message.RecipientType
import javax.mail.MessagingException
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMultipart

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

    private fun fetchMessagePage(folder: IMAPFolder, pageNum: Int, pageSize: Int): List<Message> {
        val offset = pageNum * pageSize
        val msgCount = folder.messageCount
        return folder.getMessages(msgCount - offset - pageSize + 1, msgCount - offset)
            .filter { it.allRecipients != null && it.subject != null }
            .reversed()
            .map {
                val from = it.from.first() as InternetAddress

                Message(
                    id = 0,
                    msgnum = it.messageNumber,
                    accountId = account.id,
                    subject = it.subject,
                    recipients = it.getRecipients(RecipientType.TO).map(Address::toString),
                    from = from.address,
                    date = it.receivedDate,
                    isUnread = !it.isSet(Flags.Flag.SEEN),
                )
            }
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
                val pageNum = when (loadType) {
                    LoadType.REFRESH -> {
                        val currentItem = state.anchorPosition?.let {
                            state.closestItemToPosition(it)
                        }
                        val pagingKey = currentItem?.let {
                            db.messagePagingDao().getPagingKeyById(it.id)
                        }
                        pagingKey?.nextPage?.minus(1) ?: 0
                    }
                    LoadType.PREPEND -> return@withContext MediatorResult.Success(true)
                    LoadType.APPEND -> {
                        val pagingKey = state.lastItemOrNull()?.let {
                            db.messagePagingDao().getPagingKeyById(it.id)!!
                        }
                        pagingKey?.nextPage ?: return@withContext MediatorResult.Success(true)
                    }
                }

                val pageSize = state.config.pageSize

                Log.d(TAG, "loading $pageSize messages starting from page $pageNum")

                try {
                    val folder = connectStore().getFolder("INBOX").apply {
                        open(Folder.READ_ONLY)
                    }

                    val reachedEnd = pageNum + pageSize >= folder.messageCount
                    if (!reachedEnd) {
                        val messages = fetchMessagePage(folder as IMAPFolder, pageNum, pageSize)
                        db.withTransaction {
                            if (loadType == LoadType.REFRESH) {
                                db.messagePagingDao().clearPagingKeys()
                                db.messageDao().clearAll()
                            }

                            val ids = db.messageDao().insertAll(messages)
                            val pagingKeys = ids.map {
                                MessagePagingKey(msg_id = it.toInt(), nextPage = pageNum + 1)
                            }
                            db.messagePagingDao().insertPagingKeys(pagingKeys)
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

    suspend fun getMessageBody(message: Message): List<MessagePart> {
        return db.messageDao().getBodyParts(message.id).ifEmpty {
            withContext(Dispatchers.IO) {
                val folder = connectStore().getFolder("INBOX").apply {
                    open(Folder.READ_ONLY)
                }
                val remoteMsg = folder.getMessage(message.msgnum)

                val parts = fetchParts(remoteMsg, message.id)
                db.messageDao().insertBodyParts(parts)
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