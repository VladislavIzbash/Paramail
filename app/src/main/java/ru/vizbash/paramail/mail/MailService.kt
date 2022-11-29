package ru.vizbash.paramail.mail

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Properties
import javax.inject.Inject
import javax.inject.Singleton
import javax.mail.MessagingException
import javax.mail.Session

@Singleton
class MailService @Inject constructor() {
    suspend fun checkSmtp(
        props: Properties,
        smtpData: MailData,
    ): Boolean = withContext(Dispatchers.IO) {
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

            return@withContext true
        } catch (e: MessagingException) {
            e.printStackTrace()
            return@withContext false
        }
    }
}