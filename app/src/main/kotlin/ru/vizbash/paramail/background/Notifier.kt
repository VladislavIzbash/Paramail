package ru.vizbash.paramail.background

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.navigation.NavDeepLinkBuilder
import dagger.hilt.android.qualifiers.ApplicationContext
import ru.vizbash.paramail.ParamailApp
import ru.vizbash.paramail.R
import ru.vizbash.paramail.mail.MailService
import ru.vizbash.paramail.mail.MessageService
import ru.vizbash.paramail.storage.message.MessageWithRecipients
import ru.vizbash.paramail.ui.MessageComposerFragment
import ru.vizbash.paramail.ui.messageview.MessageViewFragment
import javax.inject.Inject
import javax.inject.Singleton

private val DO_NOT_REPLY_PREFIXES = arrayOf("noreply", "do-not-reply")
private val FOLDER = "INBOX"

@Singleton
class Notifier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mailService: MailService,
) {
    private val notificationManager by lazy { NotificationManagerCompat.from(context) }

    suspend fun notifyNewMessage(messageService: MessageService, message: MessageWithRecipients, accountId: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
            && ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED) {

            return
        }

        val contentIntent = NavDeepLinkBuilder(context)
            .setGraph(R.navigation.main)
            .setDestination(R.id.messageViewFragment)
            .setArguments(bundleOf(
                MessageViewFragment.ARG_ACCOUNT_ID to accountId,
                MessageViewFragment.ARG_FOLDER_NAME to FOLDER,
                MessageViewFragment.ARG_MESSAGE_ID to message.msg.id,
            ))
            .createPendingIntent()

        val builder = NotificationCompat.Builder(context, ParamailApp.MESSAGE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_envelope)
            .setContentTitle(context.getString(R.string.new_message))
            .setContentText(message.msg.subject)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
        if (DO_NOT_REPLY_PREFIXES.all { !message.from.address.contains(it) }) {
            val reply = messageService.composeReply(message, false)
            val replyIntent = NavDeepLinkBuilder(context)
                .setGraph(R.navigation.main)
                .setDestination(R.id.messageComposerFragment)
                .setArguments(bundleOf(
                    MessageComposerFragment.ARG_ACCOUNT_ID to accountId,
                    MessageComposerFragment.ARG_COMPOSED_MESSAGE to reply,
                ))
                .createPendingIntent()

            builder.addAction(R.drawable.ic_reply, context.getString(R.string.reply), replyIntent)
        }

        notificationManager.notify(message.msg.id, builder.build())
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun createNotificationChannel() {
        val channel = NotificationChannel(
            ParamailApp.MESSAGE_CHANNEL_ID,
            context.getString(R.string.message_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        )

        notificationManager.createNotificationChannel(channel)
    }

    suspend fun cancelForAccount(accountId: Int) {
        val messageService = mailService.getMessageService(accountId, FOLDER)

        context.getSystemService(NotificationManager::class.java).activeNotifications
            .filter { messageService.getById(it.id)!!.msg.accountId == accountId }
            .forEach { notificationManager.cancel(it.id) }
    }
}