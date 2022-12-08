package ru.vizbash.paramail.mail

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.vizbash.paramail.R
import ru.vizbash.paramail.storage.AccountDao
import ru.vizbash.paramail.storage.AccountEntity
import java.util.Properties
import javax.inject.Inject
import javax.inject.Singleton
import javax.mail.AuthenticationFailedException
import javax.mail.MessagingException
import javax.mail.Session

@Singleton
class AccountService @Inject constructor(
    private val accountDao: AccountDao,
) {
    enum class CheckResult(val errorId: Int?) {
        Ok(null),
        ConnError(R.string.connection_error),
        AuthError(R.string.auth_error),
    }

    suspend fun checkSmtp(
        props: Properties,
        smtpData: MailData,
    ): CheckResult = withContext(Dispatchers.IO) {
        try {
            val newProps = Properties(props)
            newProps["mail.smtp.host"] = smtpData.host
            newProps["mail.smtp.port"] = smtpData.port

            val session = Session.getInstance(newProps, null)

            val transport = session.getTransport("smtp")
            if (smtpData.creds != null) {
                transport.connect(smtpData.creds.login, smtpData.creds.secret)
            } else {
                transport.connect()
            }
            transport.close()

            CheckResult.Ok
        } catch (e: AuthenticationFailedException) {
            CheckResult.AuthError
        } catch (e: MessagingException) {
            CheckResult.ConnError
        }
    }

    suspend fun checkImap(
        props: Properties,
        imapData: MailData,
    ): CheckResult = withContext(Dispatchers.IO) {
        requireNotNull(imapData.creds)

        try {
            val session = Session.getInstance(props, null)
            val store = session.getStore("imap")
            store.connect(imapData.host, imapData.port, imapData.creds.login, imapData.creds.secret)

            CheckResult.Ok
        } catch (e: AuthenticationFailedException) {
            CheckResult.AuthError
        } catch (e: MessagingException) {
            CheckResult.ConnError
        }
    }

    suspend fun addAccount(props: Properties, smtpData: MailData, imapData: MailData) {
        requireNotNull(imapData.creds)

        val entity = AccountEntity(
            0,
            props,
            smtpData.host,
            smtpData.port,
            smtpData.creds?.login,
            smtpData.creds?.secret,
            imapData.host,
            imapData.port,
            imapData.creds.login,
            imapData.creds.secret,
        )
        accountDao.insert(entity)
    }
}