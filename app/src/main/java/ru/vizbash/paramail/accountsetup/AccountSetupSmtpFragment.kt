package ru.vizbash.paramail.accountsetup

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.navGraphViewModels
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import ru.vizbash.paramail.R
import ru.vizbash.paramail.mail.MailService
import ru.vizbash.paramail.databinding.FragmentAccountSetupSmtpBinding
import ru.vizbash.paramail.mail.Creds
import ru.vizbash.paramail.mail.MailData
import java.util.*
import javax.inject.Inject

@AndroidEntryPoint
class AccountSetupSmtpFragment : Fragment() {
    private var _ui: FragmentAccountSetupSmtpBinding? = null
    private val ui get() = _ui!!

    private val model: AccountSetupModel by navGraphViewModels(R.id.account_setup_wizard)

    @Inject lateinit var mailService: MailService

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

        model.onNext = {
            model.wizardState.update {
                it.copy(phase = WizardPhase.Loading)
            }

            val res = checkSmtp()
            when (res) {
                MailService.CheckResult.Ok -> {
                    findNavController().navigate(R.id.action_accountSetupSmtpFragment_to_accountSetupImapFragment)
                }
                else -> model.wizardState.update {
                    it.copy(phase = WizardPhase.Error(getString(res.errorId!!)))
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _ui = null
    }

    suspend fun checkSmtp(): MailService.CheckResult {
        val props = Properties()
        //props["mail.imaps.user"] = ui.loginInput.text
        //props["mail.imaps.host"] = ui.serverInput.text
//        props["mail.store.protocol"] = "imaps"
//        props["mail.imaps.port"] = ui.portInput.toString().toInt()
//        props["mail.imaps.connectiontimeout"] = 1000
        //props["mail.imaps.ssl.enable"] = true

        props["mail.smtp.auth"] = ui.useAuth.isChecked
        props["mail.smtp.ssl.enable"] = ui.useSsl.isChecked
        //props["mail.smtp.host"] = ui.serverInput.text.toString()
        //props["mail.smtp.port"] = ui.portInput.text.toString().toInt()

        val smtpData = MailData(
            ui.serverInput.text.toString(),
            ui.portInput.text.toString().toInt(),
            if (ui.useSsl.isChecked) {
                Creds(
                    ui.loginInput.text.toString(),
                    ui.passwordInput.text.toString(),
                )
            } else {
                null
            },
        )

        return mailService.checkSmtp(props, smtpData)
    }
}