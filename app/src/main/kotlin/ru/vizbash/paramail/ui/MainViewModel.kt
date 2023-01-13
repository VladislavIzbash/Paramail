package ru.vizbash.paramail.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.vizbash.paramail.mail.ComposedMessage
import ru.vizbash.paramail.mail.MailService
import ru.vizbash.paramail.storage.account.FolderEntity
import javax.inject.Inject

private const val MESSAGE_SEND_DELAY_MS = 4000L

sealed class SearchState {
    object Opened : SearchState()
    object Closed : SearchState()
    data class Searched(val query: String) : SearchState()
}

sealed class MessageSendState {
    class AboutToSend(val delayMs: Long) : MessageSendState()
    object Sent : MessageSendState()
    object Error : MessageSendState()
}

@HiltViewModel
class MainViewModel @Inject constructor(
    private val mailService: MailService,
) : ViewModel() {
    val searchState = MutableStateFlow<SearchState>(SearchState.Closed)

    val accountList = mailService.accountList()

    private val _folderList = MutableStateFlow(listOf<FolderEntity>())
    val folderList = _folderList.asStateFlow()

    fun updateFolderList(accountId: Int) {
        viewModelScope.launch {
            try {
                _folderList.value = mailService.folderList(accountId)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private val _messageSendState = MutableStateFlow<MessageSendState?>(null)
    val messageSendState = _messageSendState.asStateFlow()

    private var messageSendJob: Job? = null

    fun sendMessageDelayed(message: ComposedMessage, accountId: Int) {
        cancelMessageSend()
        messageSendJob = viewModelScope.launch {
            _messageSendState.value = MessageSendState.AboutToSend(MESSAGE_SEND_DELAY_MS)
            delay(MESSAGE_SEND_DELAY_MS)

            try {
                mailService
                    .getMessageService(accountId, message.origMsgFolder ?: DEFAULT_FOLDER)
                    .sendMessage(message)
                _messageSendState.value = MessageSendState.Sent
            } catch (e: Exception) {
                e.printStackTrace()
                _messageSendState.value = MessageSendState.Error
            }
        }
    }

    fun cancelMessageSend() {
        messageSendJob?.cancel()
    }
}