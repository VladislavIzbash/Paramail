package ru.vizbash.paramail.ui.accountsetup

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.createViewModelLazy
import androidx.hilt.navigation.HiltViewModelFactory
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.fragment.findNavController
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import ru.vizbash.paramail.R
import ru.vizbash.paramail.databinding.FragmentAccountSetupWizardBinding

@AndroidEntryPoint
class AccountSetupWizardFragment : Fragment() {
    private var _ui: FragmentAccountSetupWizardBinding? = null
    private val ui get() = _ui!!

//    private val model: AccountSetupModel by viewModels(
//        ownerProducer = {
//            val navHost = childFragmentManager.findFragmentById(R.id.nav_host_fragment)
//                as NavHostFragment
//            navHost.navController.getViewModelStoreOwner(R.id.account_setup_wizard)
//        }
//    )

    private val model: AccountSetupModel by createViewModelLazy(
        viewModelClass = AccountSetupModel::class,
        storeProducer = {
            val navHost = childFragmentManager.findFragmentById(R.id.nav_host_fragment)
                    as NavHostFragment

            navHost.navController.getViewModelStoreOwner(R.id.account_setup_wizard).viewModelStore
        },
        factoryProducer = {
            val navHost = childFragmentManager.findFragmentById(R.id.nav_host_fragment)
                    as NavHostFragment

            HiltViewModelFactory(
                requireActivity(),
                navHost.navController.getBackStackEntry(R.id.account_setup_wizard),
            )
        }
    )

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
        ui.progress.isVisible = false
        ui.connectionError.isVisible = false

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                model.wizardState.collect { state ->
                    ui.backBtn.isEnabled = !state.isFirst
                    if (state.isFinal) {
                        ui.nextBtn.setText(R.string.done)
                    } else {
                        ui.nextBtn.setText(R.string.next)
                    }

                    when (state.phase) {
                        is WizardPhase.Filling -> {
                            ui.nextBtn.isEnabled = state.phase.isValid
                            ui.progress.isVisible = false
                            ui.connectionError.isVisible = false
                        }
                        is WizardPhase.Loading -> {
                            ui.nextBtn.isEnabled = false
                            ui.progress.isVisible = true
                            ui.connectionError.isVisible = false
                        }
                        is WizardPhase.Error -> {
                            ui.nextBtn.isEnabled = true
                            ui.progress.isVisible = false
                            ui.connectionError.isVisible = true
                            ui.connectionError.setText(when (state.phase.err) {
                                CheckResult.ConnError -> R.string.connection_error
                                CheckResult.AuthError -> R.string.auth_error
                                else -> throw IllegalStateException()
                            })
                        }
                        is WizardPhase.Done -> {
                            findNavController().popBackStack()
                        }
                    }
                }
            }
        }

        ui.nextBtn.setOnClickListener {
            stepJob?.cancel()
            stepJob = lifecycleScope.launch(Dispatchers.Main) {
                model.onNext()
            }
        }
        ui.backBtn.setOnClickListener {
            childFragmentManager.findFragmentById(R.id.nav_host_fragment)!!
                .findNavController()
                .popBackStack()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _ui = null
    }
}