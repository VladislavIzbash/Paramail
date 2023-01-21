package ru.vizbash.paramail.ui.accountsetup

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import ru.vizbash.paramail.mail.MailService
import ru.vizbash.paramail.storage.account.MailAccount
import ru.vizbash.paramail.storage.account.MailData
import java.util.*
import javax.inject.Inject
import javax.mail.AuthenticationFailedException
import javax.mail.MessagingException

sealed class WizardPhase {
    data class Filling(val isValid: Boolean) : WizardPhase()
    object Loading : WizardPhase()
    data class Error(val err: CheckResult): WizardPhase()
    object Done : WizardPhase()
}

data class WizardState(
    val isFirst: Boolean,
    val isFinal: Boolean,
    val phase: WizardPhase,
)

enum class CheckResult { Ok, ConnError, AuthError }

@HiltViewModel
class AccountSetupModel @Inject constructor(
    private val mailService: MailService,
) : ViewModel() {
    val wizardState = MutableStateFlow(WizardState(
        isFirst = true,
        isFinal = false,
        phase = WizardPhase.Filling(isValid = false),
    ))
    var onNext: suspend () -> Unit = {}

    private val props = Properties()
    var smtpData: MailData? = null
        private set
    var imapData: MailData? = null
        private set

    suspend fun prepareSmtp(props: Properties, smtpData: MailData): CheckResult {
        return try {
            mailService.connectSmtp(props, smtpData)
            this.smtpData = smtpData
            this.props += props
            CheckResult.Ok
        } catch (e: AuthenticationFailedException) {
            CheckResult.AuthError
        } catch (e: MessagingException) {
            e.printStackTrace()
            CheckResult.ConnError
        }
    }

    suspend fun prepareImap(props: Properties, imapData: MailData): CheckResult {
        return try {
            mailService.connectImap(props, imapData)
            this.imapData = imapData
            this.props += props
            CheckResult.Ok
        } catch (e: AuthenticationFailedException) {
            CheckResult.AuthError
        } catch (e: MessagingException) {
            e.printStackTrace()
            CheckResult.ConnError
        }
    }

    suspend fun addAccount(): MailAccount {
        checkNotNull(smtpData)
        checkNotNull(imapData)

        return mailService.addAccount(props, smtpData!!, imapData!!)
    }
}