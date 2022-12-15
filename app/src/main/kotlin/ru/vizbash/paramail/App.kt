package ru.vizbash.paramail

import android.app.Application
import android.util.Log
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.runBlocking
import ru.vizbash.paramail.storage.AccountDao
import ru.vizbash.paramail.storage.entity.Creds
import ru.vizbash.paramail.storage.entity.MailAccount
import ru.vizbash.paramail.storage.entity.MailData
import java.util.Properties
import javax.inject.Inject

@HiltAndroidApp
class App : Application() {
    @Inject lateinit var accountDao: AccountDao

    override fun onCreate() {
        super.onCreate()

        if (BuildConfig.DEBUG) {
            val props = Properties()
            props["mail.smtp.auth"] = "true"
            props["mail.smtp.ssl.enable"] = "true"
            props["mail.smtp.connectiontimeout"] = "1000"
            props["mail.smtp.timeout"] = "1000"
            props["mail.imap.ssl.enable"] = "true"
            props["mail.imap.connectiontimeout"] = "1000"
            props["mail.imap.timeout"] = "1000"
            props["mail.imap.writetimeout"] = "1000"

            val creds = Creds(BuildConfig.LOGIN, BuildConfig.PASSWORD)

            runBlocking {
                accountDao.insert(MailAccount(
                    0,
                    props,
                    MailData(BuildConfig.SMTP_HOST, BuildConfig.SMTP_PORT, creds),
                    MailData(BuildConfig.IMAP_HOST, BuildConfig.IMAP_PORT, creds),
                ))
            }
        }
    }
}