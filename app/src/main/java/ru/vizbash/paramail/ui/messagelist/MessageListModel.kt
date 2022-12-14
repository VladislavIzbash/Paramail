package ru.vizbash.paramail.ui.messagelist

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.runBlocking
import ru.vizbash.paramail.mail.MailService
import ru.vizbash.paramail.mail.MessageService
import ru.vizbash.paramail.storage.entity.Message
import javax.inject.Inject

@OptIn(ExperimentalPagingApi::class)
@HiltViewModel
class MessageListModel @Inject constructor(
    mailService: MailService,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private var messageService: MessageService

    val messageFlow: Flow<PagingData<Message>>

    init {
        val accountId = savedStateHandle.get<Int>(MessageListFragment.ARG_ACCOUNT_ID)!!

        messageService = runBlocking {
            val account = mailService.getAccountById(accountId)!!
            mailService.getMessageService(account)
        }
        val pager =  Pager(
            config = PagingConfig(
                pageSize = 20,
                enablePlaceholders = false,
            ),
            remoteMediator = messageService.remoteMediator,
        ) {
            messageService.storedMessages
        }

        messageFlow = pager.flow.cachedIn(viewModelScope)
    }
}