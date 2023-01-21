package ru.vizbash.paramail.ui.messagelist

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import ru.vizbash.paramail.mail.FetchState
import ru.vizbash.paramail.mail.MailService
import ru.vizbash.paramail.mail.MessageService
import ru.vizbash.paramail.storage.message.Message
import javax.inject.Inject

class ProgressVisibility(
    val general: Boolean,
    val fetchMore: Boolean,
    val refresh: Boolean,
)

@HiltViewModel
class MessageListModel @Inject constructor(
    private val mailService: MailService,
    savedState: SavedStateHandle,
) : ViewModel() {
    val accountId = savedState.get<Int>(MessageListFragment.ARG_ACCOUNT_ID)!!
    val folderName = savedState.get<String>(MessageListFragment.ARG_FOLDER_NAME) ?: "INBOX"

    private val _progressVisibility = MutableStateFlow(ProgressVisibility(
        general = true,
        fetchMore = false,
        refresh = false,
    ))
    val progressVisibility = _progressVisibility.asStateFlow()

    private lateinit var messageService: MessageService

    private var loadedFirstPage = false

    var errorListener: () -> Unit = {}

    val messages by lazy {
        val pager = Pager(
            config = PagingConfig(
                pageSize = 30,
                enablePlaceholders = true,
            ),
        ) {
            messageService.pagingSource
        }

        pager.flow.cachedIn(viewModelScope)
    }

    suspend fun fetchHistory() {
        messageService = mailService.getMessageService(accountId, folderName)

        viewModelScope.launch {
            messageService.startMessageUpdate()

            _progressVisibility.value = ProgressVisibility(
                general = true,
                fetchMore = false,
                refresh = true,
            )

            launch { observeLoadState() }
            launch { observeFetchState() }
        }
    }

    fun updateMessages() {
        messageService.startMessageUpdate()
    }

    fun onLoadedFirstPage() {
        loadedFirstPage = true
    }

    private suspend fun observeLoadState() {
        messages.collect {
            val fetchState = messageService.updateState.value
            _progressVisibility.value = ProgressVisibility(
                general = false,
                refresh = fetchState == FetchState.FETCHING_NEW || fetchState == FetchState.FETCHING_OLD,
                fetchMore = fetchState == FetchState.FETCHING_OLD,
            )
        }
    }

    private suspend fun observeFetchState() {
        messageService.updateState.collect {
            _progressVisibility.value = ProgressVisibility(
                general = it == FetchState.FETCHING_OLD && !loadedFirstPage,
                refresh = it == FetchState.DONE || it == FetchState.ERROR,
                fetchMore = it == FetchState.FETCHING_OLD && loadedFirstPage,
            )
        }
    }

    fun searchMessages(query: String) = messageService.searchMessages(query)

    fun moveToSpam(message: Message) {
        launchCatching { messageService.moveToSpam(message) }
    }

    fun moveToArchive(message: Message) {
        launchCatching { messageService.moveToArchive(message) }
    }

    private fun launchCatching(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: Exception) {
                e.printStackTrace()
                errorListener()
            }
        }
    }
}