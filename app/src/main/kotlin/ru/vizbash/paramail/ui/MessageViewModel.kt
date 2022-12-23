package ru.vizbash.paramail.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import ru.vizbash.paramail.mail.MailService
import javax.inject.Inject

@HiltViewModel
class MessageViewModel @Inject constructor(
    mailService: MailService,
    savedState: SavedStateHandle,
) : ViewModel() {
    private val messageService = viewModelScope.async {
        val accountId = savedState.get<Int>(MessageViewFragment.ARG_ACCOUNT_ID)!!
        mailService.getMessageService(accountId)
    }

    val message = viewModelScope.async {
        val msgId = savedState.get<Int>(MessageViewFragment.ARG_MESSAGE_ID)!!
        messageService.await().getById(msgId)!!
    }
    val body = viewModelScope.async {
        messageService.await().getMessageBody(message.await())
    }
}