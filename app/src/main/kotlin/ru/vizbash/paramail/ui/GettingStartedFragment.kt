package ru.vizbash.paramail.ui

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.os.bundleOf
import androidx.navigation.fragment.findNavController
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.runBlocking
import ru.vizbash.paramail.R
import ru.vizbash.paramail.mail.MailService
import ru.vizbash.paramail.ui.messagelist.MessageListFragment
import javax.inject.Inject

@AndroidEntryPoint
class GettingStartedFragment : Fragment() {

    @Inject lateinit var mailService: MailService

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        return inflater.inflate(R.layout.fragment_getting_started, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val addButton = view.findViewById<TextView>(R.id.add_account_button)
        addButton.setOnClickListener {
            findNavController().navigate(R.id.action_gettingStartedFragment_to_accountSetupWizardFragment)
        }

        runBlocking {
            val account = mailService.accountList().firstOrNull()
            if (account != null) {
                findNavController().navigate(R.id.action_gettingStartedFragment_to_messageListFragment, bundleOf(
                    MessageListFragment.ARG_ACCOUNT_ID to account.id
                ))
            }
        }
    }



}