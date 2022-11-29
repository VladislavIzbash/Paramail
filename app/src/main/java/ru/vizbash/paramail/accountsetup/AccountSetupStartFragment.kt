package ru.vizbash.paramail.accountsetup

import android.content.Context
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.content.res.AppCompatResources
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.RecyclerView
import ru.vizbash.paramail.R
import ru.vizbash.paramail.databinding.FragmentAccountSetupStartBinding
import ru.vizbash.paramail.databinding.ItemAccountTypeBinding

class AccountSetupStartFragment : AccountSetupStep() {
    private var _ui: FragmentAccountSetupStartBinding? = null
    private val ui get() = _ui!!

    private var selected = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _ui = FragmentAccountSetupStartBinding.inflate(inflater, container, false)
        return ui.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val supportedTypes = listOf(
            MailService(
                AppCompatResources.getDrawable(requireContext(), R.drawable.gmail_logo)!!,
                "Gmail"
            ),
            MailService(
                AppCompatResources.getDrawable(requireContext(), R.drawable.other_mail_logo)!!,
                getString(R.string.other_mail),
            ),
        )

        val adapter = AccountListAdapter(supportedTypes) {
            selected = true
            (requireParentFragment() as AccountSetupWizardFragment).canContinue = true
        }
        ui.accountTypeList.adapter = adapter
        ui.accountTypeList.addItemDecoration(DividerItemDecoration(
            requireContext(),
            DividerItemDecoration.VERTICAL),
        )
    }

    override fun onResume() {
        super.onResume()

        val wizard = requireParentFragment() as AccountSetupWizardFragment
        wizard.canContinue = selected
        wizard.isFinalStep = false
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _ui = null
    }

    override fun createNextStep() = AccountSetupSmtpFragment()

    class MailService(
        val icon: Drawable,
        val name: String,
    )

    class AccountListAdapter(
        private val services: List<MailService>,
        private val onSelected: (MailService) -> Unit,
    ) : RecyclerView.Adapter<AccountListAdapter.ViewHolder>() {

        private var selectedPos = -1

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_account_type, parent, false)
            view.setBackgroundResource(R.color.item_background) // ломается редактор

            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(position)
        }

        override fun getItemCount() = services.size

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            private val ui: ItemAccountTypeBinding = ItemAccountTypeBinding.bind(view)

            fun bind(pos: Int) {
                val type = services[pos]

                ui.root.isActivated = pos == selectedPos
                ui.root.setOnClickListener {
                    val prevPos = selectedPos
                    selectedPos = pos

                    notifyItemChanged(pos)
                    notifyItemChanged(prevPos)

                    onSelected(services[selectedPos])
                }

                ui.icon.setImageDrawable(type.icon)
                ui.name.text = type.name
            }
        }
    }
}