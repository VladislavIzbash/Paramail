package ru.vizbash.paramail.mail

import android.util.Log
import androidx.paging.*
import com.sun.mail.imap.IMAPStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.vizbash.paramail.storage.MessageDao
import ru.vizbash.paramail.storage.entity.MailAccount
import ru.vizbash.paramail.storage.entity.Message
import javax.mail.Address
import javax.mail.Flags
import javax.mail.Folder
import javax.mail.MessagingException
import javax.mail.internet.InternetAddress

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

    private suspend fun checkConnection() {
        if (store == null || !store!!.isConnected) {
            store = mailService.connectImap(account.props, account.imap)
        }
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
                    content = null,
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
                    checkConnection()

                    val folder = store!!.getFolder("INBOX").apply {
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
}