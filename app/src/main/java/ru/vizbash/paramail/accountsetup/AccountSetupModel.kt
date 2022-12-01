package ru.vizbash.paramail.accountsetup

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow

sealed class WizardPhase {
    data class Filling(val isValid: Boolean) : WizardPhase()
    object Loading : WizardPhase()
    data class Error(val error: String): WizardPhase()
}

data class WizardState(
    val isFirst: Boolean,
    val isFinal: Boolean,
    val phase: WizardPhase,
)

class AccountSetupModel : ViewModel() {
    val wizardState = MutableStateFlow(WizardState(
        isFirst = true,
        isFinal = false,
        phase = WizardPhase.Filling(isValid = false),
    ))
    var onNext: suspend () -> Unit = {}
}