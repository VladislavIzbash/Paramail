package ru.vizbash.paramail.accountsetup

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import ru.vizbash.paramail.databinding.FragmentAccountSetupImapBinding

class AccountSetupImapFragment : Fragment() {
    private var _ui: FragmentAccountSetupImapBinding? = null
    private val ui get() = _ui!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _ui = FragmentAccountSetupImapBinding.inflate(inflater, container, false)
        return ui.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {

    }

    override fun onResume() {
        super.onResume()

        val wizard = requireParentFragment() as AccountSetupWizardFragment
//        wizard.canContinue = false
//        wizard.isFinalStep = true
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _ui = null
    }

}