package ru.vizbash.paramail.ui.settings

import android.accounts.Account
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import ru.vizbash.paramail.databinding.FragmentSettingsBinding
import ru.vizbash.paramail.mail.MailService
import ru.vizbash.paramail.storage.account.MailAccount
import javax.inject.Inject

@AndroidEntryPoint
class SettingsFragment : Fragment() {
    private var _ui: FragmentSettingsBinding? = null
    private val ui get() = _ui!!

    @Inject lateinit var mailService: MailService

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _ui = FragmentSettingsBinding.inflate(inflater, container, false)
        return ui.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _ui = null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        viewLifecycleOwner.lifecycleScope.launch {
            val accountList = mailService.accountList().first()
            ui.accountList.adapter = ArrayAdapter(
                requireContext(),
                android.R.layout.simple_list_item_1,
                accountList.map { it.imap.creds!!.login },
            )
        }
    }
}