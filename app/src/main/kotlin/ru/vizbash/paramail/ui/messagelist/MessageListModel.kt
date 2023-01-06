package ru.vizbash.paramail.ui.messagelist

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import ru.vizbash.paramail.mail.MailService
import ru.vizbash.paramail.storage.message.Message
import javax.inject.Inject

@HiltViewModel
class MessageListModel @Inject constructor(
    mailService: MailService,
    savedState: SavedStateHandle,
) : ViewModel() {
    val accountId = savedState.get<Int>(MessageListFragment.ARG_ACCOUNT_ID)!!
    val folder = savedState.get<String>(MessageListFragment.ARG_FOLDER_NAME)!!

    private val messageService = viewModelScope.async {
        mailService.getFolderService(accountId)
    }

    @OptIn(ExperimentalPagingApi::class)
    val messageFlow = viewModelScope.async {
        val messageService = messageService.await()

        val pager = Pager(
            config = PagingConfig(
                pageSize = 20,
                enablePlaceholders = true,
            ),
            remoteMediator = messageService.getRemoteMediator(folder),
        ) {
            messageService.getStoredMessages(folder)
        }

        pager.flow.cachedIn(viewModelScope)
    }

    suspend fun searchMessages(query: String): List<Message>? {
        return messageService.await().searchMessages(query, folder)
    }
}