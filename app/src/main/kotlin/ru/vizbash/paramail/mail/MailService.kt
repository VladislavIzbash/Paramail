package ru.vizbash.paramail.mail

import android.content.Context
import com.sun.mail.imap.IMAPStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.vizbash.paramail.storage.AccountDao
import ru.vizbash.paramail.storage.MailDatabase
import ru.vizbash.paramail.storage.message.MessageDao
import ru.vizbash.paramail.storage.entity.MailAccount
import ru.vizbash.paramail.storage.entity.MailData
import java.util.Properties
import javax.inject.Inject
import javax.inject.Singleton
import javax.mail.Session
import javax.mail.Transport

@Singleton
class MailService @Inject constructor(
    private val db: MailDatabase,
    @ApplicationContext private val context: Context,
) {
    private val messageServices = mutableMapOf<Int, MessageService>()

    suspend fun connectSmtp(
        props: Properties,
        smtpData: MailData,
    ): Transport = withContext(Dispatchers.IO) {
        val newProps = Properties(props)
        newProps["mail.smtp.host"] = smtpData.host
        newProps["mail.smtp.port"] = smtpData.port

        val session = Session.getInstance(newProps)

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
        val store = session.getStore("imap")
        store.connect(
            imapData.host,
            imapData.port,
            imapData.creds.login,
            imapData.creds.secret,
        )

        store as IMAPStore
    }

    suspend fun accountList() = db.accountDao().getAll()

    suspend fun addAccount(props: Properties, smtpData: MailData, imapData: MailData) {
        requireNotNull(imapData.creds)

        val account = MailAccount(0, props, smtpData, imapData)
        db.accountDao().insert(account)
    }

    suspend fun getAccountById(accountId: Int) = db.accountDao().getById(accountId)

    suspend fun getMessageService(accountId: Int): MessageService {
        return messageServices.getOrPut(accountId) {
            val account = db.accountDao().getById(accountId)!!
            MessageService(account, db, this, context)
        }
    }
}