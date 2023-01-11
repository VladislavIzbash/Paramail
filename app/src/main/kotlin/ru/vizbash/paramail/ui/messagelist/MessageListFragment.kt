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
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.ItemTouchHelper
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import ru.vizbash.paramail.R
import ru.vizbash.paramail.databinding.FragmentMessageListBinding
import ru.vizbash.paramail.mail.ComposedMessage
import ru.vizbash.paramail.mail.FetchState
import ru.vizbash.paramail.storage.message.Message
import ru.vizbash.paramail.storage.message.MessageWithRecipients
import ru.vizbash.paramail.ui.MainActivity
import ru.vizbash.paramail.ui.MainViewModel
import ru.vizbash.paramail.ui.MessageComposerFragment
import ru.vizbash.paramail.ui.messageview.MessageViewFragment
import ru.vizbash.paramail.ui.SearchState

@AndroidEntryPoint
class MessageListFragment : Fragment() {
    companion object {
        const val ARG_ACCOUNT_ID = "account_id"
        const val ARG_FOLDER_NAME = "folder_name"
    }

    private var _ui: FragmentMessageListBinding? = null
    private val ui get() = _ui!!

    private val model: MessageListModel by viewModels()
    private val mainModel: MainViewModel by activityViewModels()

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        model.startUpdate()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        ui.composeMessageButton.setOnClickListener {
            val args = bundleOf(
                MessageComposerFragment.ARG_ACCOUNT_ID to model.accountId,
            )
            findNavController().navigate(R.id.action_messageListFragment_to_messageComposerFragment, args)
        }

        ui.messageList.addItemDecoration(DividerItemDecoration(
            requireContext(),
            DividerItemDecoration.VERTICAL,
        ))

        val messageSearchAdapter = ListMessageAdapter()

        viewLifecycleOwner.lifecycleScope.launch {
            (requireActivity() as MainActivity).setFolderName(model.folderName)

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
                            messageSearchAdapter.highlightedText = state.query
                            model.searchMessages(state.query).collect {
                                messageSearchAdapter.submitList(it)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun onMessageClicked(msg: MessageWithRecipients) {
        val args = bundleOf(
            MessageViewFragment.ARG_ACCOUNT_ID to model.accountId,
            MessageViewFragment.ARG_FOLDER_NAME to model.folderName,
            MessageViewFragment.ARG_MESSAGE_ID to msg.msg.id,
        )
        findNavController().navigate(R.id.action_messageListFragment_to_messageViewFragment, args)
    }

    private suspend fun setupMessagePaging() {
        val messageAdapter = PagingMessageAdapter()
        messageAdapter.onMessageClickListener = this::onMessageClicked

        val loadStateAdapter = MessageLoadStateAdapter()
        ui.messageList.adapter = ConcatAdapter(messageAdapter, loadStateAdapter)

        val touchCallback = MessageTouchCallback(ui.root)
        touchCallback.swipeStateListener = { isSwiping ->
            ui.root.isEnabled = !isSwiping
        }
        ItemTouchHelper(touchCallback).attachToRecyclerView(ui.messageList)

        ui.root.setOnRefreshListener {
            model.startUpdate()
        }

        messageAdapter.addLoadStateListener { loadState ->
            lifecycleScope.launch {
                val refreshingEmpty = loadState.refresh is LoadState.Loading
                        && messageAdapter.itemCount == 0
                val fetchingHistory = messageAdapter.itemCount > 0
                        && model.getFetchState().value == FetchState.FETCHING_OLD

                ui.loadingProgress.isVisible = refreshingEmpty

                loadStateAdapter.loadState = if (fetchingHistory) LoadState.Loading else LoadState.NotLoading(false)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                model.getFetchState().collect {
                    when (it) {
                        FetchState.FETCHING_NEW -> LoadState.NotLoading(true)
                        FetchState.FETCHING_OLD -> {
                            val hasItems = messageAdapter.itemCount > 0

                            ui.loadingProgress.isVisible = !hasItems
                            loadStateAdapter.loadState = if (hasItems) LoadState.Loading else LoadState.NotLoading(false)
                        }
                        FetchState.DONE -> {
                            ui.root.isRefreshing = false
                            ui.loadingProgress.isVisible = false
                            loadStateAdapter.loadState = LoadState.NotLoading(true)
                        }
                        FetchState.ERROR -> LoadState.Error(Throwable())
                    }
                }
            }
        }

        model.getMessageFlow().collectLatest {
            messageAdapter.submitData(it)
        }
    }
}