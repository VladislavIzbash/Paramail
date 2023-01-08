package ru.vizbash.paramail.ui.messageview

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.vizbash.paramail.mail.MailService
import ru.vizbash.paramail.storage.message.Attachment
import javax.inject.Inject

@HiltViewModel
class MessageViewModel @Inject constructor(
    mailService: MailService,
    savedState: SavedStateHandle,
) : ViewModel() {
    private val folderService = viewModelScope.async {
        val accountId = savedState.get<Int>(MessageViewFragment.ARG_ACCOUNT_ID)!!
        val folderName = savedState.get<String>(MessageViewFragment.ARG_FOLDER_NAME)!!
        mailService.getFolderService(accountId, folderName)
    }

    val message = viewModelScope.async {
        val msgId = savedState.get<Int>(MessageViewFragment.ARG_MESSAGE_ID)!!
        folderService.await().getById(msgId)!!
    }
    val messageBody = viewModelScope.async {
        folderService.await().getMessageBody(message.await())
    }

    private val _downloadProgress = MutableStateFlow(0F)
    val downloadProgress = _downloadProgress.asStateFlow()

    private var downloadJob: Job? = null

    var downloadedUri: Uri? = null

    fun startDownload(attachment: Attachment) {
        downloadJob?.cancel()
        downloadJob = viewModelScope.launch {
            downloadedUri = folderService.await().downloadAttachment(attachment) {
                _downloadProgress.value = it
            }
        }
    }

    fun cancelDownload() {
        downloadJob?.cancel()
        _downloadProgress.value = 0F
    }
}