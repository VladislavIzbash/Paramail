package ru.vizbash.paramail.mail

import android.app.appsearch.AppSearchManager.SearchContext
import androidx.paging.*
import com.sun.mail.imap.IMAPStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.vizbash.paramail.storage.MessageDao
import ru.vizbash.paramail.storage.entity.MailAccount
import ru.vizbash.paramail.storage.entity.Message
import java.util.*
import javax.mail.Address
import javax.mail.FetchProfile
import javax.mail.Flags
import javax.mail.Folder
import javax.mail.MessagingException
import javax.mail.UIDFolder.FetchProfileItem
import javax.mail.internet.InternetAddress
import javax.mail.search.SearchTerm

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

    private suspend fun fetchMessages(folder: Folder, offset: Int, size: Int) {
        val messages = folder.getMessages(offset + 1, offset + size)
            .drop(offset)
            .take(size)
            .filter { it.subject != null }
            .map {
                val from = it.from.first() as InternetAddress

                Message(
                    id = 0,
                    accountId = account.id,
                    subject = it.subject,
                    recipients = it.allRecipients.map(Address::toString),
                    from = from.address,
                    content = null,
                    it.receivedDate,
                    isUnread = !it.isSet(Flags.Flag.SEEN),
                )
            }

        messageDao.insert(messages)
    }

    val remoteMediator: RemoteMediator<Int, Message>
        get() = object : RemoteMediator<Int, Message>() {
            private var offset = 0

            override suspend fun load(
                loadType: LoadType,
                state: PagingState<Int, Message>,
            ): MediatorResult = withContext(Dispatchers.IO) {
                val offset = when (loadType) {
                    LoadType.REFRESH -> {
                        offset = 0
                        0
                    }
                    LoadType.PREPEND -> return@withContext MediatorResult.Success(true)
                    LoadType.APPEND -> offset
                }
                val pageSize = if (loadType == LoadType.REFRESH) {
                    state.config.initialLoadSize
                } else {
                    state.config.pageSize
                }

                println("loading $pageSize messages at offset $offset")

                try {
                    checkConnection()

                    val folder = store!!.getFolder("INBOX").apply {
                        open(Folder.READ_ONLY)
                    }
                    fetchMessages(folder, offset, pageSize)

                    val reachedEnd = offset + pageSize >= folder.messageCount
                    MediatorResult.Success(reachedEnd)
                } catch (e: MessagingException) {
                    MediatorResult.Error(e)
                }
            }
        }
}