package ru.vizbash.paramail.accountsetup

import androidx.fragment.app.Fragment

abstract class AccountSetupStep : Fragment() {
    abstract fun createNextStep(): AccountSetupStep?

    open suspend fun check(): Boolean = true
}