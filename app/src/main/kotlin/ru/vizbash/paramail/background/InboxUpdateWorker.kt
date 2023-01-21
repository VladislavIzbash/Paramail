package ru.vizbash.paramail.background

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.hilt.work.HiltWorker
import androidx.navigation.NavDeepLinkBuilder
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import ru.vizbash.paramail.ParamailApp
import ru.vizbash.paramail.R
import ru.vizbash.paramail.mail.MailException
import ru.vizbash.paramail.mail.MailService
import ru.vizbash.paramail.mail.MessageService
import ru.vizbash.paramail.storage.message.MessageWithRecipients
import ru.vizbash.paramail.ui.MessageComposerFragment
import ru.vizbash.paramail.ui.messageview.MessageViewFragment

private const val TAG = "InboxUpdateWorker"
private const val FOLDER = "INBOX"
private val DO_NOT_REPLY_PREFIXES = arrayOf("noreply", "do-not-reply")

@HiltWorker
class InboxUpdateWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val mailService: MailService,
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val WORK_NAME = "inbox_update"
    }

    override suspend fun doWork(): Result {
        Log.i(TAG, "starting update for all accounts")

        var errorOccurred = false

        for (account in mailService.accountList().first()) {
            val messageService = mailService.getMessageService(account.id, FOLDER)

            try {
                val messages = messageService.fetchNewMessages()
                messages.forEach { createNotification(messageService, it, account.id) }
            } catch (e: MailException) {
                Log.w(TAG, "failed to update messages for ${account.imap.creds!!.login}")
                e.printStackTrace()

                errorOccurred = true
            }

        }

        return if (!errorOccurred) Result.success() else Result.retry()
    }

    private suspend fun createNotification(messageService: MessageService, message: MessageWithRecipients, accountId: Int) {
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

        NotificationManagerCompat.from(context).notify(message.msg.id, builder.build())
    }
}