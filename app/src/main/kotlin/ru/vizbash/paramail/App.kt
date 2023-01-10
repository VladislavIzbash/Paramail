package ru.vizbash.paramail

import android.app.Application
import android.webkit.WebView
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import ru.vizbash.paramail.storage.account.AccountDao
import ru.vizbash.paramail.storage.account.Creds
import ru.vizbash.paramail.storage.account.MailAccount
import ru.vizbash.paramail.storage.account.MailData
import java.util.Properties
import javax.inject.Inject

@HiltAndroidApp
class App : Application() {
    @Inject lateinit var accountDao: AccountDao

    override fun onCreate() {
        super.onCreate()

        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)

        if (BuildConfig.DEBUG) {
            System.setProperty(
                kotlinx.coroutines.DEBUG_PROPERTY_NAME,
                kotlinx.coroutines.DEBUG_PROPERTY_VALUE_ON,
            )

            val props = Properties()
            props["mail.smtp.auth"] = "true"
            props["mail.smtp.ssl.enable"] = "true"
            props["mail.smtp.connectiontimeout"] = "5000"
            props["mail.smtp.timeout"] = "5000"
            props["mail.imap.ssl.enable"] = "true"
            props["mail.imap.connectiontimeout"] = "5000"
            props["mail.imap.timeout"] = "5000"
            props["mail.imap.writetimeout"] = "5000"

            val creds = Creds(BuildConfig.LOGIN, BuildConfig.PASSWORD)

            runBlocking {
                if (accountDao.getAll().first().isEmpty()) {
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
}