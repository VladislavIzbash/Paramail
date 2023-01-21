package ru.vizbash.paramail

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.webkit.WebView
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.work.HiltWorkerFactory
import androidx.preference.PreferenceManager
import androidx.work.*
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import ru.vizbash.paramail.background.InboxUpdateWorker
import ru.vizbash.paramail.storage.account.AccountDao
import ru.vizbash.paramail.storage.account.Creds
import ru.vizbash.paramail.storage.account.MailAccount
import ru.vizbash.paramail.storage.account.MailData
import java.util.*
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltAndroidApp
class App : Application(), Configuration.Provider {
    companion object {
        const val MESSAGE_CHANNEL_ID = "message_channel"
    }

    @Inject lateinit var accountDao: AccountDao
    @Inject lateinit var workerFactory: HiltWorkerFactory

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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannel()
        }

        scheduleUpdateWork()
    }

    override fun getWorkManagerConfiguration(): Configuration {
        return Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
    }

    private fun scheduleUpdateWork() {
        val updateInterval = PreferenceManager.getDefaultSharedPreferences(this)
            .getInt("message_update_interval", 15)
            .coerceIn(15, 60)
            .toLong()

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val workRequest = PeriodicWorkRequestBuilder<InboxUpdateWorker>(updateInterval, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .setInitialDelay(updateInterval, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            InboxUpdateWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest,
        )
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            MESSAGE_CHANNEL_ID,
            getString(R.string.message_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        )

        NotificationManagerCompat.from(this).createNotificationChannel(channel)
    }
}