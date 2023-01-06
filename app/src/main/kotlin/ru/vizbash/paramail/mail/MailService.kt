package ru.vizbash.paramail.mail

import android.content.Context
import android.util.Log
import com.sun.mail.imap.IMAPStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.vizbash.paramail.BuildConfig
import ru.vizbash.paramail.storage.MailDatabase
import ru.vizbash.paramail.storage.account.FolderEntity
import ru.vizbash.paramail.storage.account.MailAccount
import ru.vizbash.paramail.storage.account.MailData
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton
import javax.mail.MessagingException
import javax.mail.Session
import javax.mail.Transport

private const val TAG = "MailService"

@Singleton
class MailService @Inject constructor(
    private val db: MailDatabase,
    @ApplicationContext private val context: Context,
) {
    suspend fun connectSmtp(
        props: Properties,
        smtpData: MailData,
    ): Transport = withContext(Dispatchers.IO) {
        val newProps = Properties(props)
        newProps["mail.smtp.host"] = smtpData.host
        newProps["mail.smtp.port"] = smtpData.port

        val session = Session.getInstance(newProps)
        session.debug = BuildConfig.DEBUG

        val transport = session.getTransport("smtp")
        if (smtpData.creds != null) {
            transport.connect(smtpData.creds.login, smtpData.creds.secret)
        } else {
            transport.connect()
        }

        transport
    }

    suspend fun connectImap(
        props: Properties,
        imapData: MailData,
    ): IMAPStore = withContext(Dispatchers.IO) {
        requireNotNull(imapData.creds)

        val session = Session.getInstance(props)
        session.debug = BuildConfig.DEBUG

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

//    suspend fun getAccountById(accountId: Int) = db.accountDao().getById(accountId)

    suspend fun getFolderService(accountId: Int, folderId: Int): FolderService {
        val account = db.accountDao().getById(accountId)!!
        val folder = db.accountDao().getFolderById(folderId)!!

        return FolderService(account, db, this, context, folder)
    }

    suspend fun listFolders(accountId: Int): List<FolderEntity> = withContext(Dispatchers.IO) {
        val account = db.accountDao().getById(accountId)!!

        try {
            connectImap(account.props, account.imap).use { store ->
                val folders = store.defaultFolder.list().map { FolderEntity(0, account.id, it.name) }
                db.accountDao().insertFolders(folders)
                return@withContext folders
            }
        } catch (e: MessagingException) {
            return@withContext db.accountDao().getFolders(accountId)
        }
    }
}