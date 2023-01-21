package ru.vizbash.paramail.ui.messagelist

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.core.content.edit
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import ru.vizbash.paramail.ParamailApp
import ru.vizbash.paramail.R
import ru.vizbash.paramail.databinding.FragmentMessageListBinding
import ru.vizbash.paramail.storage.message.MessageWithRecipients
import ru.vizbash.paramail.ui.MainActivity
import ru.vizbash.paramail.ui.MainViewModel
import ru.vizbash.paramail.ui.MessageComposerFragment
import ru.vizbash.paramail.ui.SearchState
import ru.vizbash.paramail.ui.messageview.MessageViewFragment

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

    private lateinit var folderName: String
    private var accountId: Int? = null

    private val loadStateAdapter = MessageLoadStateAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        accountId = arguments?.getInt(ARG_ACCOUNT_ID)
        folderName = arguments?.getString(ARG_FOLDER_NAME) ?: ParamailApp.DEFAULT_FOLDER

        if (accountId != null) {
            return
        }

        runBlocking(Dispatchers.Default) {
            val init = (requireActivity().application as ParamailApp).getInitialFolder()
            accountId = init?.first
            folderName = init?.second ?: ParamailApp.DEFAULT_FOLDER
        }

        if (accountId == null) {
            findNavController().navigate(R.id.action_messageListFragment_to_gettingStartedFragment)
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
        if (folderName == null) {
            return
        }

        (requireActivity() as MainActivity).setFolderName(folderName!!)

        ui.composeMessageButton.setOnClickListener { onComposeClicked() }

        ui.messageList.addItemDecoration(DividerItemDecoration(
            requireContext(),
            DividerItemDecoration.VERTICAL,
        ))

        val messageAdapter = PagingMessageAdapter()
        messageAdapter.onMessageClickListener = this::onMessageClicked
        messageAdapter.addLoadStateListener {
            if (messageAdapter.itemCount > 0) {
                model.onLoadedFirstPage()
            }
        }

        ui.messageList.adapter = ConcatAdapter(messageAdapter, loadStateAdapter)

        val touchCallback = MessageTouchCallback(requireContext())
        touchCallback.swipeStateListener = { isSwiping ->
            ui.root.isEnabled = !isSwiping
        }
        touchCallback.onArchiveListener = {
            model.moveToArchive((it as MessageViewHolder).boundItem!!.msg)
        }
        touchCallback.onSpamListener = {
            model.moveToSpam((it as MessageViewHolder).boundItem!!.msg)
        }
        ItemTouchHelper(touchCallback).attachToRecyclerView(ui.messageList)

        val messageSearchAdapter = ListMessageAdapter()
        messageSearchAdapter.onMessageClickListener = this::onMessageClicked

        model.errorListener = {
            Snackbar.make(ui.root, R.string.error_occurred, Snackbar.LENGTH_SHORT).show()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            model.initialize(accountId!!, folderName!!)

            launch {
                model.messages.collectLatest {
                    messageAdapter.submitData(it)
                }
            }

            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    model.progressVisibility.collect {
                        setProgressVisibility(it)
                    }
                }

                mainModel.searchState.collectLatest { state ->
                    when (state) {
                        SearchState.Closed -> {
                            ui.messageList.adapter = messageAdapter
                            ui.root.setOnRefreshListener { model.updateMessages() }
                        }
                        SearchState.Opened -> {
                            ui.messageList.adapter = messageSearchAdapter
                            ui.root.setOnRefreshListener { }
                        }
                        is SearchState.Searched -> {
                            messageSearchAdapter.highlightedText = state.query

                            if (state.query.length > 3) {
                                model.searchMessages(state.query).collect {
                                    messageSearchAdapter.submitList(it)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun setProgressVisibility(vis: ProgressVisibility) {
        if (mainModel.searchState.value !is SearchState.Closed) {
            return
        }

        ui.loadingProgress.isVisible = vis.general
        loadStateAdapter.loadState = if (vis.fetchMore) {
            LoadState.Loading
        } else {
            LoadState.NotLoading(true)
        }
        if (!vis.refresh) {
            ui.root.isRefreshing = false
        }
    }

    private fun onMessageClicked(msg: MessageWithRecipients) {
        val args = bundleOf(
            MessageViewFragment.ARG_ACCOUNT_ID to accountId,
            MessageViewFragment.ARG_FOLDER_NAME to folderName,
            MessageViewFragment.ARG_MESSAGE_ID to msg.msg.id,
        )
        findNavController().navigate(R.id.action_messageListFragment_to_messageViewFragment, args)
    }

    private fun onComposeClicked() {
        val prefs = requireContext()
            .getSharedPreferences(MessageComposerFragment.SHARED_PREFS_NAME, Context.MODE_PRIVATE)

        if (prefs.getString("text", "") != "") {
            AlertDialog.Builder(requireContext())
                .setMessage(R.string.open_saved_message_dialog)
                .setPositiveButton(R.string.yes) { _, _ ->
                    navigateToComposer()
                }
                .setNegativeButton(R.string.no) { _, _ ->
                    prefs.edit { clear() }
                    navigateToComposer()
                }
                .show()
        } else {
            navigateToComposer()
        }
    }

    private fun navigateToComposer() {
        val args = bundleOf(
            MessageComposerFragment.ARG_ACCOUNT_ID to accountId,
        )
        findNavController().navigate(R.id.action_messageListFragment_to_messageComposerFragment, args)

    }
}