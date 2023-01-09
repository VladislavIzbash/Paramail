package ru.vizbash.paramail.ui

import android.annotation.SuppressLint
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts.OpenMultipleDocuments
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.google.android.material.chip.Chip
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.runBlocking
import ru.vizbash.paramail.R
import ru.vizbash.paramail.databinding.FragmentMessageComposerBinding
import ru.vizbash.paramail.mail.ComposedMessage
import ru.vizbash.paramail.mail.MailService
import ru.vizbash.paramail.mail.MessageType
import ru.vizbash.paramail.storage.message.Message
import javax.inject.Inject

@AndroidEntryPoint
class MessageComposerFragment : Fragment() {
    companion object {
        const val ARG_ACCOUNT_ID = "account_id"
        const val ARG_REPLY_TO_MSG_ID = "reply_to"

        const val SHARED_PREFS_NAME = "composer"

        const val KEY_TO = "to"
    }

    @Inject lateinit var mailService: MailService

    private var _ui: FragmentMessageComposerBinding? = null
    private val ui get() = _ui!!

    private val mainModel: MainViewModel by activityViewModels()

    private lateinit var openAttachmentLauncher: ActivityResultLauncher<Array<String>>

    private val attachments = mutableListOf<Pair<Uri, String>>()
    private var replyToMsg: Message? = null

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
        _ui = null
    }

    @SuppressLint("SetTextI18n")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        ui.addChip.setOnClickListener {
            openAttachmentLauncher.launch(arrayOf("*/*"))
        }

        ui.sendButton.setOnClickListener { onSendClicked() }

        val replyToId = requireArguments().getInt(ARG_REPLY_TO_MSG_ID)
        if (replyToId != 0) {
            replyToMsg = runBlocking { mailService.getMessageById(replyToId)!! }
            ui.subjectInput.setText("RE: " + replyToMsg!!.subject)
            ui.toInput.setText(replyToMsg!!.from)
            ui.toInput.isEnabled = false
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        // TODO save all
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
            listOf(),
            attachments,
            ui.messageText.text.toString(),
            if (replyToMsg != null) MessageType.REPLY else MessageType.DEFAULT,
            replyToMsg,
        )
        mainModel.sendMessageDelayed(message, requireArguments().getInt(ARG_ACCOUNT_ID))
        findNavController().popBackStack()
    }
}