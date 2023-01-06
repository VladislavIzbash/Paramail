package ru.vizbash.paramail.ui

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
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
        return mailService.getFolderService(selectedAccountId.value!!).listFolders()
    }
}