package ru.vizbash.paramail.accountsetup

import androidx.fragment.app.Fragment
import kotlinx.coroutines.flow.StateFlow

interface AccountSetupStep {
    val canContinue: StateFlow<Boolean>

    val isFinal: Boolean
        get() = false

    fun createNextFragment(): Fragment?

    suspend fun proceed(): String?
}