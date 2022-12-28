package ru.vizbash.paramail.ui

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow

sealed class SearchState {
    object Opened : SearchState()
    object Closed : SearchState()
    data class Searched(val query: String) : SearchState()
}

class MainViewModel : ViewModel() {
    val searchState = MutableStateFlow<SearchState>(SearchState.Closed)
}