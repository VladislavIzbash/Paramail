package ru.vizbash.paramail.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import ru.vizbash.paramail.mail.MailService
import javax.inject.Inject

sealed class SearchState {
    object Opened : SearchState()
    object Closed : SearchState()
    data class Searched(val query: String) : SearchState()
}

@HiltViewModel
class MainViewModel @Inject constructor(
    private val mailService: MailService,
) : ViewModel() {
    val searchState = MutableStateFlow<SearchState>(SearchState.Closed)

    val accountList = mailService.accountList()
    var selectedAccountId = MutableStateFlow<Int?>(null)

    suspend fun getAccountById(id: Int) = mailService.getAccountById(id)

    suspend fun getFolderList(): List<String> {
        checkNotNull(selectedAccountId.value)
        return mailService.getMessageService(selectedAccountId.value!!).listFolders()
    }
}