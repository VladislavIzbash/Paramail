package ru.vizbash.paramail.mail

import android.content.Context
import android.util.Log
import androidx.room.withTransaction
import com.sun.mail.imap.IMAPStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.vizbash.paramail.storage.MailDatabase
import ru.vizbash.paramail.storage.account.FolderEntity
import ru.vizbash.paramail.storage.account.MailAccount
import ru.vizbash.paramail.storage.account.MailData
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton
import javax.mail.Session
import javax.mail.Transport

private const val TAG = "MailService"

@Singleton
class MailService @Inject constructor(
    private val db: MailDatabase,
    @ApplicationContext private val context: Context,
) {
    private val coroutineScope = CoroutineScope(Dispatchers.IO)

    suspend fun connectSmtp(
        props: Properties,
        smtpData: MailData,
    ): Pair<Session, Transport> = withContext(Dispatchers.IO) {
        val newProps = Properties(props)
        newProps["mail.smtp.host"] = smtpData.host
        newProps["mail.smtp.port"] = smtpData.port

        val session = Session.getInstance(newProps)
//        session.debug = BuildConfig.DEBUG

        val transport = session.getTransport("smtp")
        if (smtpData.creds != null) {
            transport.connect(smtpData.creds.login, smtpData.creds.secret)
        } else {
            transport.connect()
        }

        Pair(session, transport)
    }

    suspend fun connectImap(
        props: Properties,
        imapData: MailData,
    ): IMAPStore = withContext(Dispatchers.IO) {
        requireNotNull(imapData.creds)

        val session = Session.getInstance(props)
//        session.debug = BuildConfig.DEBUG

        val store = session.getStore("imap")
        store.connect(
            imapData.host,
            imapData.port,
            imapData.creds.login,
            imapData.creds.secret,
        )

        Log.d(TAG, "${imapData.creds.login}: connected to ${imapData.host}:${imapData.port}")

        store as IMAPStore
    }

    fun accountList() = db.accountDao().getAll()

    suspend fun addAccount(props: Properties, smtpData: MailData, imapData: MailData) {
        requireNotNull(imapData.creds)

        val account = MailAccount(0, props, smtpData, imapData)
        db.accountDao().insert(account)
    }

    suspend fun getMessageService(accountId: Int, folderName: String): MessageService {
        downloadFolderList(accountId)

        val account = db.accountDao().getById(accountId)!!
        val folder = db.accountDao().getFolderByName(folderName, accountId)!!

        return MessageService(account, db, this, context, coroutineScope, folder)
    }

    private suspend fun downloadFolderList(accountId: Int) = withContext(Dispatchers.IO) {
        val account = db.accountDao().getById(accountId)!!

        db.withTransaction {
            if (db.accountDao().getFolders(account.id).isEmpty()) {
                connectImap(account.props, account.imap).use { store ->
                    val folders =
                        store.defaultFolder.list().map { FolderEntity(0, account.id, it.name) }
                    db.accountDao().insertFolders(folders)
                }
            }
        }
    }

    suspend fun listFolders(accountId: Int): List<FolderEntity> = withContext(Dispatchers.IO) {
        downloadFolderList(accountId)
        return@withContext db.accountDao().getFolders(accountId)
    }

    suspend fun getAddressCompletions(startsWith: String): List<String> {
        return db.messageDao().searchAddresses("$startsWith%", 6)
    }


}