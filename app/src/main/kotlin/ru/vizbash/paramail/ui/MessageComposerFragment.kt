package ru.vizbash.paramail.ui

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ImageSpan
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.inputmethod.EditorInfo
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.MultiAutoCompleteTextView
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts.OpenMultipleDocuments
import androidx.core.content.edit
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipDrawable
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import ru.vizbash.paramail.R
import ru.vizbash.paramail.databinding.FragmentMessageComposerBinding
import ru.vizbash.paramail.mail.ComposedMessage
import ru.vizbash.paramail.mail.MailService
import ru.vizbash.paramail.mail.MessageType
import javax.inject.Inject

@AndroidEntryPoint
class MessageComposerFragment : Fragment() {
    companion object {
        const val ARG_ACCOUNT_ID = "account_id"
        const val ARG_COMPOSED_MESSAGE = "message"

        const val SHARED_PREFS_NAME = "composer"
    }

    @Inject lateinit var mailService: MailService

    private var _ui: FragmentMessageComposerBinding? = null
    private val ui get() = _ui!!

    private val mainModel: MainViewModel by activityViewModels()

    private lateinit var openAttachmentLauncher: ActivityResultLauncher<Array<String>>

    private lateinit var composed: ComposedMessage
    private val attachments = mutableListOf<Pair<Uri, String>>()
    private val ccAddresses = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        openAttachmentLauncher = registerForActivityResult(OpenMultipleDocuments()) { list ->
            list.forEach(this::addAttachment)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _ui = FragmentMessageComposerBinding.inflate(inflater, container, false)
        return ui.root
    }

    override fun onDestroyView() {
        super.onDestroyView()

        requireContext().getSharedPreferences(SHARED_PREFS_NAME, Context.MODE_PRIVATE).edit {
            putString("subject", ui.subjectInput.text.toString())
            putString("to", ui.toInput.text.toString())
            putStringSet("cc", ccAddresses.toSet())
            putString("text", ui.messageText.text.toString())
            putString("type", composed.type.name)
            putInt("orig_msg_num", composed.origMsgNum ?: -1)
            putString("orig_msg_folder", composed.origMsgFolder)
        }

        _ui = null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        @Suppress("DEPRECATION")
        composed = requireArguments().getParcelable(ARG_COMPOSED_MESSAGE) ?: loadTemplate()

        ui.toInput.setText(composed.to)
        ui.toInput.isEnabled = composed.type != MessageType.REPLY && composed.type != MessageType.REPLY_TO_ALL
        composed.cc.forEach(this::addCcChip)
        ui.subjectInput.setText(composed.subject)
        composed.attachments.forEach { addAttachment(it.first) }
        ui.messageText.setText(composed.text)

        ui.addChip.setOnClickListener {
            openAttachmentLauncher.launch(arrayOf("*/*"))
        }

        ui.sendButton.setOnClickListener { onSendClicked() }

        setupAddressCompletion(ui.toInput) {}

        ui.ccInput.setTokenizer(MultiAutoCompleteTextView.CommaTokenizer())
        setupAddressCompletion(ui.ccInput) { addCcChip(it) }
        ui.ccInput.setOnEditorActionListener { tv, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                addCcChip(tv.text.toString().trimStart(','))
                true
            } else {
                false
            }
        }
        ui.ccInput.addTextChangedListener(beforeTextChanged = { text, start, count, after ->
            if (after == count - 1 && text?.getOrNull(start) == ',') {
                ccAddresses.removeLast()
            }
        })
    }

    private fun addCcChip(address: String) {
        ccAddresses.add(address)

        val spannable = SpannableStringBuilder()
        for (addr in ccAddresses) {
            val chip = ChipDrawable.createFromResource(requireContext(), R.xml.address_chip).apply {
                text = addr
                isCloseIconVisible = false
                setBounds(0, 0, intrinsicWidth, ui.ccInput.lineHeight)
            }

            spannable.append(",", ImageSpan(chip), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        ui.ccInput.text = spannable
        ui.ccInput.setSelection(ui.ccInput.length())
    }

    private fun setupAddressCompletion(textView: AutoCompleteTextView, onItemClick: (String) -> Unit) {
        val adapter = ArrayAdapter<String>(requireContext(), android.R.layout.simple_selectable_list_item)
        textView.setAdapter(adapter)

        var completionJob: Job? = null

        textView.addTextChangedListener(onTextChanged = { text, _, before, count ->
            if (text.toString().length > 2) {
                if (count > before) {
                    completionJob?.cancel()
                    completionJob = viewLifecycleOwner.lifecycleScope.launch {
                        val addresses = mailService.getAddressCompletions(text.toString().trimStart(','))
                        adapter.clear()
                        adapter.addAll(addresses)
                    }
                }
            } else {
                completionJob?.cancel()
                adapter.clear()
            }
        })
        textView.setOnItemClickListener { _, _, position, _ ->
            onItemClick(adapter.getItem(position)!!)
        }
    }

    private fun addAttachment(uri: Uri) {
        val contentResolver = requireContext().contentResolver

        val cursor = contentResolver.query(
            uri,
            arrayOf(MediaStore.MediaColumns.DISPLAY_NAME),
            null,
            null,
            null,
        )
        val fileName = cursor?.use {
            if (it.moveToFirst()) {
                it.getString(0)
            } else {
                null
            }
        } ?: uri.lastPathSegment!!

        attachments.add(Pair(uri, fileName))

        val styledContext = ContextThemeWrapper(
            requireContext(),
            com.google.android.material.R.style.Widget_MaterialComponents_Chip_Entry,
        )
        val chip = Chip(styledContext).apply {
            layoutParams = ViewGroup.LayoutParams(WRAP_CONTENT, WRAP_CONTENT)
            text = fileName
            isCloseIconVisible = true

            val mime = contentResolver.getType(uri)
            if (mime != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                chipIcon = contentResolver.getTypeInfo(mime).icon.loadDrawable(requireContext())
            } else {
                setChipIconResource(R.drawable.ic_attachment)
            }
        }
        chip.setOnCloseIconClickListener {
            attachments.remove(Pair(uri, fileName))
            ui.attachmentGroup.removeView(chip)
        }
        ui.attachmentGroup.addView(chip, 0)
    }

    private fun onSendClicked() {
        val message = ComposedMessage(
            ui.subjectInput.text.toString(),
            ui.toInput.text.toString(),
            ccAddresses.toSet(),
            attachments,
            ui.messageText.text.toString(),
            composed.type,
            composed.origMsgNum,
        )
        mainModel.sendMessageDelayed(message, requireArguments().getInt(ARG_ACCOUNT_ID))
        findNavController().popBackStack()
    }

    private fun loadTemplate(): ComposedMessage {
        val prefs = requireContext().getSharedPreferences(SHARED_PREFS_NAME, Context.MODE_PRIVATE)
        with(prefs) {
            val origMsgNum = getInt("orig_msg_num", -1)

            return ComposedMessage(
                subject = getString("subject", "")!!,
                to = getString("to", "")!!,
                cc = getStringSet("cc", setOf())!!,
                text = getString("text", "")!!,
                type = getString("type", null)?.let { MessageType.valueOf(it) }
                    ?: MessageType.DEFAULT,
                origMsgNum = if (origMsgNum != -1) origMsgNum else null,
                origMsgFolder = getString("orig_msg_folder", null),
            )
        }
    }
}