package ru.vizbash.paramail.ui.messagelist

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import ru.vizbash.paramail.mail.MailService
import javax.inject.Inject

@OptIn(ExperimentalPagingApi::class)
@HiltViewModel
class MessageListModel @Inject constructor(
    mailService: MailService,
    savedState: SavedStateHandle,
) : ViewModel() {
    val accountId = savedState.get<Int>(MessageListFragment.ARG_ACCOUNT_ID)!!

    private val messageService = viewModelScope.async {
        mailService.getMessageService(accountId)
    }

    val messageFlow = viewModelScope.async {
        val messageService = messageService.await()
        val pager =  Pager(
            config = PagingConfig(
                pageSize = 20,
                enablePlaceholders = false,
            ),
            remoteMediator = messageService.remoteMediator,
        ) {
            messageService.storedMessages
        }

        pager.flow.cachedIn(viewModelScope)
    }
}