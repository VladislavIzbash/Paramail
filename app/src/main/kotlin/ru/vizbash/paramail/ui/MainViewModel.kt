package ru.vizbash.paramail.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.vizbash.paramail.mail.MailService
import ru.vizbash.paramail.storage.account.FolderEntity
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

    private val _folderList = MutableStateFlow(listOf<FolderEntity>())
    val folderList = _folderList.asStateFlow()

    fun switchAccount(accountId: Int) {
        viewModelScope.launch {
            _folderList.value = mailService.listFolders(accountId)
        }
    }
}