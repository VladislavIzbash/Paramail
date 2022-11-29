package ru.vizbash.paramail.accountsetup

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.viewpager2.adapter.FragmentStateAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.vizbash.paramail.R
import ru.vizbash.paramail.databinding.FragmentAccountSetupWizardBinding

class AccountSetupWizardFragment : Fragment() {
    private var _ui: FragmentAccountSetupWizardBinding? = null
    private val ui get() = _ui!!

    private lateinit var stepAdapter: SetupWizardAdapter

    var canContinue
        get() = ui.nextBtn.isEnabled
        set(value) { ui.nextBtn.isEnabled = value }

    var isFinalStep = false
        set(value) {
            if (value) {
                ui.nextBtn.setText(R.string.done)
            } else {
                ui.nextBtn.setText(R.string.next)
            }
            field = value
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _ui = FragmentAccountSetupWizardBinding.inflate(inflater, container, false)
        return ui.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        stepAdapter = SetupWizardAdapter(this)

        ui.checkProgress.isVisible = false
        ui.connectionError.isVisible = false

        ui.viewPager.adapter = stepAdapter
        ui.viewPager.isUserInputEnabled = false

        ui.nextBtn.isEnabled = false
        ui.nextBtn.setOnClickListener { onNextClicked() }

        ui.backBtn.isEnabled = false
        ui.backBtn.setOnClickListener {
            ui.viewPager.currentItem--
            ui.backBtn.isEnabled = ui.viewPager.currentItem != 0
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _ui = null
    }

    private fun onNextClicked() {
        lifecycleScope.launch(Dispatchers.Main) {
            val currentStep = stepAdapter.getStep(ui.viewPager.currentItem)

            canContinue = false
            ui.checkProgress.isVisible = true
            ui.connectionError.isVisible = false

            val success = currentStep.check()

            ui.checkProgress.isVisible = false
            canContinue = true

            if (success) {
                stepAdapter.nextStep(ui.viewPager.currentItem)
                ui.viewPager.currentItem++
                ui.backBtn.isEnabled = true
            } else {
                ui.connectionError.isVisible = true
            }
        }
    }

    class SetupWizardAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {
        private val steps = mutableListOf<AccountSetupStep>()
        private var insertNew = true

        override fun getItemCount() = if (insertNew) steps.size + 1 else steps.size

        override fun createFragment(position: Int): Fragment {
            insertNew = false

            return if (steps.size == 0) {
                steps.add(AccountSetupStartFragment())
                steps[0]
            } else {
                val next = steps[position - 1].createNextStep()!!
                steps.add(next)
                next
            }
        }

        fun getStep(pos: Int) = steps[pos]

        fun nextStep(currentStep: Int) {
            if (currentStep == steps.size - 1) {
                insertNew = true
                notifyItemInserted(currentStep + 1)
            }
        }
    }
}