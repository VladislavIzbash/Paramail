package ru.vizbash.paramail.ui.messageview

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import ru.vizbash.paramail.mail.ComposedMessage
import ru.vizbash.paramail.mail.MailService
import ru.vizbash.paramail.storage.message.Attachment
import javax.inject.Inject

@HiltViewModel
class MessageViewModel @Inject constructor(
    mailService: MailService,
    savedState: SavedStateHandle,
) : ViewModel() {
    val accountId = savedState.get<Int>(MessageViewFragment.ARG_ACCOUNT_ID)!!
    private val messageId = savedState.get<Int>(MessageViewFragment.ARG_MESSAGE_ID)!!

    private val messageService = viewModelScope.async {
        val folderName = savedState.get<String>(MessageViewFragment.ARG_FOLDER_NAME)!!
        mailService.getMessageService(accountId, folderName)
    }

    val message = viewModelScope.async {
        val messageService = messageService.await()
        messageService.getById(messageId)!!.also {
            try {
                messageService.markAsSeen(it.msg)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    val messageBody = viewModelScope.async {
        messageService.await().getMessageBody(message.await().msg)
    }

    val downloadProgress = MutableStateFlow(0F)

    fun downloadAttachmentAsync(attachment: Attachment): Deferred<Uri?> {
        return viewModelScope.async {
            try {
                messageService.await().downloadAttachment(attachment) {
                    downloadProgress.value = it
                }!!
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }

        // TODO: обработка ошибок
    }

    suspend fun composeReply(replyToAll: Boolean): ComposedMessage {
        return messageService.await().composeReply(message.await(), replyToAll)
    }

    suspend fun composeForward(): ComposedMessage {
        return messageService.await().composeForward(message.await())
    }
}