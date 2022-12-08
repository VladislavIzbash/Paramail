package ru.vizbash.paramail.accountsetup

import android.content.Context
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.fragment.findNavController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.vizbash.paramail.R
import ru.vizbash.paramail.mail.AccountService
import ru.vizbash.paramail.mail.MailData
import java.util.*
import javax.inject.Inject

sealed class WizardPhase {
    data class Filling(val isValid: Boolean) : WizardPhase()
    object Loading : WizardPhase()
    data class Error(val error: String): WizardPhase()
    object Done : WizardPhase()
}

data class WizardState(
    val isFirst: Boolean,
    val isFinal: Boolean,
    val phase: WizardPhase,
)

@HiltViewModel
class AccountSetupModel @Inject constructor(
    private val accountService: AccountService,
) : ViewModel() {
    val wizardState = MutableStateFlow(WizardState(
        isFirst = true,
        isFinal = false,
        phase = WizardPhase.Filling(isValid = false),
    ))
    var onNext: suspend () -> Unit = {}

    private val props = Properties()
    private var smtpData: MailData? = null
    private var imapData: MailData? = null

    suspend fun prepareSmtp(props: Properties, smtpData: MailData): AccountService.CheckResult {
        val res = accountService.checkSmtp(props, smtpData)
        if (res == AccountService.CheckResult.Ok) {
            this.smtpData = smtpData
            this.props += props
        }
        return res
    }

    suspend fun prepareImap(props: Properties, imapData: MailData): AccountService.CheckResult {
        val res = accountService.checkImap(props, imapData)
        if (res == AccountService.CheckResult.Ok) {
            this.imapData = imapData
            this.props += props
        }
        return res
    }

    suspend fun addAccount() {
        requireNotNull(smtpData)
        requireNotNull(imapData)

        accountService.addAccount(props, smtpData!!, imapData!!);
    }
}