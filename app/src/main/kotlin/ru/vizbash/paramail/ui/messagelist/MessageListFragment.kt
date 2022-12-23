package ru.vizbash.paramail.ui.messagelist

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.paging.LoadState
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.ItemTouchHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import ru.vizbash.paramail.R
import ru.vizbash.paramail.databinding.FragmentMessageListBinding
import ru.vizbash.paramail.ui.MessageViewFragment

@AndroidEntryPoint
class MessageListFragment : Fragment() {
    companion object {
        const val ARG_ACCOUNT_ID = "account_id"
    }

    private var _ui: FragmentMessageListBinding? = null
    private val ui get() = _ui!!

    private val model: MessageListModel by viewModels()

    private val messageAdapter = MessageAdapter()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _ui = FragmentMessageListBinding.inflate(inflater, container, false)
        return ui.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _ui = null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        ui.messageList.adapter = messageAdapter.withLoadStateHeaderAndFooter(MessageLoadStateAdapter(), MessageLoadStateAdapter())
        ui.messageList.addItemDecoration(DividerItemDecoration(
            requireContext(),
            DividerItemDecoration.VERTICAL,
        ))
        val touchHelper = ItemTouchHelper(MessageTouchCallback(ui.root))
        touchHelper.attachToRecyclerView(ui.messageList)

        ui.root.setOnRefreshListener {
            messageAdapter.refresh()
        }

        messageAdapter.addLoadStateListener { loadState ->
            when (loadState.refresh) {
                is LoadState.NotLoading -> {
                    ui.root.isRefreshing = false
                    ui.loadingProgress.isVisible = false
                }
                is LoadState.Loading -> {
                    ui.loadingProgress.isVisible = messageAdapter.itemCount == 0
                }
                else -> {}
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            val messageFlow = model.messageFlow.await()

            repeatOnLifecycle(Lifecycle.State.STARTED) {
                messageFlow.collectLatest {
                    messageAdapter.submitData(it)
                }
            }
        }

        messageAdapter.onMessageClickListener = { msg ->
            val args = bundleOf(
                MessageViewFragment.ARG_ACCOUNT_ID to model.accountId,
                MessageViewFragment.ARG_MESSAGE_ID to msg.id,
            )
            findNavController().navigate(R.id.action_messageListFragment_to_messageViewFragment, args)
        }
    }

}