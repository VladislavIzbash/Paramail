package ru.vizbash.paramail.background

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import ru.vizbash.paramail.mail.MailException
import ru.vizbash.paramail.mail.MailService

private const val TAG = "InboxUpdateWorker"

@HiltWorker
class InboxUpdateWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val mailService: MailService,
    private val notifier: Notifier,
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val WORK_NAME = "inbox_update"
    }

    override suspend fun doWork(): Result {
        Log.i(TAG, "starting update for all accounts")

        var errorOccurred = false

        for (account in mailService.accountList().first()) {
            val messageService = mailService.getMessageService(account.id, "INBOX")

            try {
                val messages = messageService.fetchNewMessages()
                messages.forEach { notifier.notifyNewMessage(messageService, it, account.id) }
            } catch (e: MailException) {
                Log.w(TAG, "failed to update messages for ${account.imap.creds!!.login}")
                e.printStackTrace()

                errorOccurred = true
            }

        }

        return if (!errorOccurred) Result.success() else Result.retry()
    }
}