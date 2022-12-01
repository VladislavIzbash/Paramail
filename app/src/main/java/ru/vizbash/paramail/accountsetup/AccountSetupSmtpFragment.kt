package ru.vizbash.paramail.accountsetup

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import ru.vizbash.paramail.mail.MailService
import ru.vizbash.paramail.databinding.FragmentAccountSetupSmtpBinding
import java.util.*
import javax.inject.Inject

@AndroidEntryPoint
class AccountSetupSmtpFragment : Fragment(), AccountSetupStep {
    private var _ui: FragmentAccountSetupSmtpBinding? = null
    private val ui get() = _ui!!

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

        val wizard = requireParentFragment() as AccountSetupWizardFragment
//        wizard.canContinue = true
//        wizard.isFinalStep = false
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _ui = null
    }

    override val canContinue = MutableStateFlow(true)

    override fun createNextFragment(): Fragment? {
        return AccountSetupImapFragment()
    }

    override suspend fun proceed(): String? {
        TODO("Not yet implemented")
    }

    //    override suspend fun check(): Boolean {
//        val props = Properties()
//        //props["mail.imaps.user"] = ui.loginInput.text
//        //props["mail.imaps.host"] = ui.serverInput.text
////        props["mail.store.protocol"] = "imaps"
////        props["mail.imaps.port"] = ui.portInput.toString().toInt()
////        props["mail.imaps.connectiontimeout"] = 1000
//        //props["mail.imaps.ssl.enable"] = true
//
//        props["mail.smtp.auth"] = ui.useAuth.isChecked
//        props["mail.smtp.ssl.enable"] = ui.useSsl.isChecked
//        //props["mail.smtp.host"] = ui.serverInput.text.toString()
//        //props["mail.smtp.port"] = ui.portInput.text.toString().toInt()
//
//        return mailService.checkSmtp(
//            props,
//            ui.serverInput.text.toString(),
//            ui.portInput.text.toString().toInt(),
//            ui.loginInput.text.toString(),
//            ui.passwordInput.text.toString(),
//        )
//    }
}