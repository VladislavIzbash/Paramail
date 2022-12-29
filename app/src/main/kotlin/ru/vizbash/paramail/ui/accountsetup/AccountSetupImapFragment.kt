package ru.vizbash.paramail.ui.accountsetup

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.hilt.navigation.fragment.hiltNavGraphViewModels
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.update
import ru.vizbash.paramail.R
import ru.vizbash.paramail.databinding.FragmentAccountSetupImapBinding
import ru.vizbash.paramail.storage.account.Creds
import ru.vizbash.paramail.storage.account.MailData
import java.util.*

@AndroidEntryPoint
class AccountSetupImapFragment : Fragment() {
    private var _ui: FragmentAccountSetupImapBinding? = null
    private val ui get() = _ui!!

    private val model: AccountSetupModel by hiltNavGraphViewModels(R.id.account_setup_wizard)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _ui = FragmentAccountSetupImapBinding.inflate(inflater, container, false)
        return ui.root
    }

    override fun onResume() {
        super.onResume()

        model.wizardState.value = WizardState(
            isFirst = false,
            isFinal = true,
            phase = WizardPhase.Filling(
                isValid = true,
            )
        )

        model.onNext = this::onNext
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _ui = null
    }

    private suspend fun onNext() {
        model.wizardState.update {
            it.copy(phase = WizardPhase.Loading)
        }

        val props = Properties()
        props["mail.imap.ssl.enable"] = "true"
        props["mail.imap.connectiontimeout"] = "1000"
        props["mail.imap.timeout"] = "1000"
        props["mail.imap.writetimeout"] = "1000"

        val imapData = MailData(
            ui.serverInput.text.toString(),
            ui.portInput.text.toString().toInt(),
            Creds(ui.loginInput.text.toString(), ui.passwordInput.text.toString()),
        )

        when (val res = model.prepareImap(props, imapData)) {
            CheckResult.Ok -> {
                model.addAccount()
                model.wizardState.update {
                    it.copy(phase = WizardPhase.Done)
                }
            }
            else -> model.wizardState.update {
                it.copy(phase = WizardPhase.Error(res))
            }
        }
    }
}