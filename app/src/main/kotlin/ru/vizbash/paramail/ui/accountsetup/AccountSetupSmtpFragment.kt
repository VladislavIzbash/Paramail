package ru.vizbash.paramail.ui.accountsetup

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.hilt.navigation.fragment.hiltNavGraphViewModels
import androidx.navigation.fragment.findNavController
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.update
import ru.vizbash.paramail.R
import ru.vizbash.paramail.databinding.FragmentAccountSetupSmtpBinding
import ru.vizbash.paramail.storage.account.Creds
import ru.vizbash.paramail.storage.account.MailData
import java.util.*

@AndroidEntryPoint
class AccountSetupSmtpFragment : Fragment() {
    private var _ui: FragmentAccountSetupSmtpBinding? = null
    private val ui get() = _ui!!

    private val model: AccountSetupModel by hiltNavGraphViewModels(R.id.account_setup_wizard)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _ui = FragmentAccountSetupSmtpBinding.inflate(inflater, container, false)
        return ui.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        ui.useAuth.setOnCheckedChangeListener { _, isChecked ->
            ui.loginInputLayout.isEnabled = isChecked
            ui.passwordInputLayout.isEnabled = isChecked
        }
    }

    override fun onResume() {
        super.onResume()

        model.wizardState.value = WizardState(
            isFirst = false,
            isFinal = false,
            phase = WizardPhase.Filling(
                isValid = true,
            ),
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
        props["mail.smtp.auth"] = ui.useAuth.isChecked.toString()
        props["mail.smtp.ssl.enable"] = ui.useSsl.isChecked.toString()
        props["mail.smtp.connectiontimeout"] = "5000"
        props["mail.smtp.timeout"] = "5000"
        props["mail.smtp.writetimeout"] = "5000"

        val smtpData = MailData(
            ui.serverInput.text.toString(),
            ui.portInput.text.toString().toInt(),
            if (ui.useAuth.isChecked) {
                Creds(
                    ui.loginInput.text.toString(),
                    ui.passwordInput.text.toString(),
                )
            } else {
                null
            },
        )

        when (val res = model.prepareSmtp(props, smtpData)) {
            CheckResult.Ok -> {
                findNavController().navigate(R.id.action_accountSetupSmtpFragment_to_accountSetupImapFragment)
            }
            else -> model.wizardState.update {
                it.copy(phase = WizardPhase.Error(res))
            }
        }
    }
}