package ru.vizbash.paramail.ui.messagelist

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.paging.LoadState
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.ItemTouchHelper
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import ru.vizbash.paramail.R
import ru.vizbash.paramail.databinding.FragmentMessageListBinding
import ru.vizbash.paramail.storage.message.Message
import ru.vizbash.paramail.ui.MainViewModel
import ru.vizbash.paramail.ui.messageview.MessageViewFragment
import ru.vizbash.paramail.ui.SearchState

@AndroidEntryPoint
class MessageListFragment : Fragment() {
    companion object {
        const val ARG_ACCOUNT_ID = "account_id"
    }

    private var _ui: FragmentMessageListBinding? = null
    private val ui get() = _ui!!

    private val model: MessageListModel by viewModels()
    private val mainModel: MainViewModel by activityViewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setFragmentResultListener(MessageViewFragment.RESULT_KEY) { _, bundle ->
            if (bundle.getBoolean(MessageViewFragment.RESULT_ERROR_KEY)) {
                Snackbar.make(ui.root, R.string.error_loading_message, Snackbar.LENGTH_SHORT)
                    .show()
            }
        }
    }

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
        ui.messageList.addItemDecoration(DividerItemDecoration(
            requireContext(),
            DividerItemDecoration.VERTICAL,
        ))

        val messageSearchAdapter = ListMessageAdapter()

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                mainModel.searchState.collectLatest { state ->
                    when (state) {
                        SearchState.Closed -> setupMessagePaging()
                        SearchState.Opened -> {
                            messageSearchAdapter.onMessageClickListener = this@MessageListFragment::onMessageClicked
                            ui.messageList.adapter = messageSearchAdapter

                            ui.root.setOnRefreshListener { }
                        }
                        is SearchState.Searched -> {
                            ui.loadingProgress.isVisible = true
                            val results = model.searchMessages(state.query)
                            if (results == null) {
                                Snackbar.make(ui.root, "Сервер не поддерживает поиск", Snackbar.LENGTH_SHORT)
                                    .show()
                            } else {
                                messageSearchAdapter.submitList(results)
                            }
                            ui.loadingProgress.isVisible = false
                        }
                    }
                }
            }
        }
    }

    private fun onMessageClicked(msg: Message) {
        val args = bundleOf(
            MessageViewFragment.ARG_ACCOUNT_ID to model.accountId,
            MessageViewFragment.ARG_MESSAGE_ID to msg.id,
        )
        findNavController().navigate(R.id.action_messageListFragment_to_messageViewFragment, args)
    }

    private suspend fun setupMessagePaging() {
        val messageAdapter = PagingMessageAdapter()
        messageAdapter.onMessageClickListener = this::onMessageClicked
        messageAdapter.addLoadStateListener { loadState ->
            when (loadState.refresh) {
                is LoadState.NotLoading -> {
                    ui.loadingProgress.isVisible = false
                    ui.root.isRefreshing = false
                }
                is LoadState.Loading -> {
                    ui.loadingProgress.isVisible = messageAdapter.itemCount == 0
                    ui.root.isRefreshing = messageAdapter.itemCount != 0
                }
                is LoadState.Error -> {
                    (loadState.refresh as LoadState.Error).error.printStackTrace()

                    Snackbar.make(ui.root, R.string.error_loading_message_list, Snackbar.LENGTH_LONG)
                        .setAction(R.string.try_again) {
                            messageAdapter.refresh()
                        }
                        .show()

                    ui.loadingProgress.isVisible = false
                    ui.root.isRefreshing = false
                }
                else -> {}
            }
        }
        ui.messageList.adapter = messageAdapter.withLoadStateFooter(MessageLoadStateAdapter())

        val touchCallback = MessageTouchCallback(ui.root)
        touchCallback.swipeStateListener = { isSwiping ->
            ui.root.isEnabled = !isSwiping
        }
        ItemTouchHelper(touchCallback).attachToRecyclerView(ui.messageList)

        ui.loadingProgress.isVisible = false
        ui.root.setOnRefreshListener {
            messageAdapter.refresh()
        }

        val messageFlow = this@MessageListFragment.model.messageFlow.await()
        messageFlow.collectLatest {
            messageAdapter.submitData(it)
        }
    }
}