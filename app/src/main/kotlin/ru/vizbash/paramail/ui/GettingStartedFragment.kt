package ru.vizbash.paramail.ui

import android.content.Context
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.os.bundleOf
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import ru.vizbash.paramail.R
import ru.vizbash.paramail.ui.messagelist.MessageListFragment

@AndroidEntryPoint
class GettingStartedFragment : Fragment() {
    private val mainModel: MainViewModel by activityViewModels()

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

        val accountList = runBlocking {
            mainModel.accountList.first()
        }
        if (accountList.isEmpty()) {
            return
        }

        val prefs = requireActivity().getPreferences(Context.MODE_PRIVATE)
        val accountId = prefs.getInt(MainActivity.LAST_ACCOUNT_ID_KEY, accountList.first().id)

        mainModel.selectedAccountId.value = accountId
        findNavController().navigate(R.id.action_gettingStartedFragment_to_messageListFragment, bundleOf(
            MessageListFragment.ARG_ACCOUNT_ID to accountId
        ))
    }
}