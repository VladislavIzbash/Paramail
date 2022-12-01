package ru.vizbash.paramail.accountsetup

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import androidx.fragment.app.replace
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.viewpager2.adapter.FragmentStateAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import ru.vizbash.paramail.R
import ru.vizbash.paramail.databinding.FragmentAccountSetupWizardBinding
import java.util.*

class AccountSetupWizardFragment : Fragment() {
    private var _ui: FragmentAccountSetupWizardBinding? = null
    private val ui get() = _ui!!

    private val stepStack = Stack<AccountSetupStep>()
    private var stepJob: Job? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _ui = FragmentAccountSetupWizardBinding.inflate(inflater, container, false)
        return ui.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        ui.backBtn.isEnabled = false
        ui.nextBtn.isEnabled = false
        ui.checkProgress.isVisible = false
        ui.connectionError.isVisible = false

        ui.nextBtn.setOnClickListener { onNextClicked() }
        ui.backBtn.setOnClickListener { onPrevClicked() }

        addToStack(AccountSetupStartFragment())
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _ui = null
    }

    private suspend fun checkStep(): Boolean {
        ui.nextBtn.isEnabled = false
        ui.checkProgress.isVisible = true
        ui.connectionError.isVisible = false

        val error = stepStack.peek().proceed()

        ui.checkProgress.isVisible = false
        ui.nextBtn.isEnabled = true

        return if (error != null) {
            ui.connectionError.isVisible = true
            ui.connectionError.text = error
            false
        } else {
            true
        }
    }

    private fun switchStep(step: AccountSetupStep) {
        if (step.isFinal) {
            ui.nextBtn.setText(R.string.done)
        } else {
            ui.nextBtn.setText(R.string.next)
        }

        stepJob?.cancel()
        stepJob = viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                step.canContinue.collect { ui.nextBtn.isEnabled = it }
            }
        }
    }

    private fun addToStack(step: AccountSetupStep) {
        childFragmentManager.commit {
            setReorderingAllowed(true)

            if (!stepStack.isEmpty()) {
                hide(stepStack.peek() as Fragment)
            }
            add(R.id.step_container, step as Fragment)
            addToBackStack(null)
        }
        stepStack.push(step)
        switchStep(step)
    }

    private fun onNextClicked() {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Main) {
            if (!checkStep()) {
                return@launch
            }

            if (!stepStack.peek().isFinal) {
                val next = stepStack.peek().createNextFragment()!! as AccountSetupStep
                addToStack(next)
            } else {
                // TODO:
            }

            ui.backBtn.isEnabled = true
        }
    }

    private fun onPrevClicked() {
        stepStack.pop()
        childFragmentManager.popBackStack()
        switchStep(stepStack.peek())
        ui.backBtn.isEnabled = false
    }
}