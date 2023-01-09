package ru.vizbash.paramail.ui.messageview

import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts.CreateDocument
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import ru.vizbash.paramail.R
import ru.vizbash.paramail.databinding.DialogAttachmentBinding
import ru.vizbash.paramail.storage.message.Attachment

class AttachmentDialogFragment(
    private val attachment: Attachment,
    private val model: MessageViewModel,
) : DialogFragment() {
    private var _ui: DialogAttachmentBinding? = null
    private val ui get() = _ui!!

    private val attachmentMime = attachment.mime.split(';').first()
    private lateinit var saveAttachmentLauncher: ActivityResultLauncher<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        saveAttachmentLauncher = registerForActivityResult(CreateDocument(attachmentMime)) { saveUri ->
            if (saveUri == null) {
                return@registerForActivityResult
            }

            val input = requireContext().contentResolver.openInputStream(model.downloadedUri!!)!!
            val output = requireContext().contentResolver.openOutputStream(saveUri)!!

            input.use { output.use { input.copyTo(output) } }
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _ui = DialogAttachmentBinding.inflate(layoutInflater)

        ui.name.text = getString(R.string.downloading, attachment.fileName)
        ui.cancelButton.isVisible = true
        ui.downloadProgress.isVisible = true
        ui.saveButton.isVisible = false
        ui.openButton.isVisible = false

        ui.cancelButton.setOnClickListener {
            model.cancelDownload()
            dismiss()
        }

        model.startDownload(attachment)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                model.downloadProgress.collect {
                    ui.downloadProgress.progress = (it * 100F).toInt()

                    if (it == 1F) {
                        onDownloadFinished()
                    }
                }
            }
        }

        return object : Dialog(requireContext()) {
            override fun onCreate(savedInstanceState: Bundle?) {
                super.onCreate(savedInstanceState)
                setContentView(ui.root)
            }
        }
    }

    private fun onDownloadFinished() {
        ui.name.text = getString(R.string.downloaded, attachment.fileName)
        ui.cancelButton.isVisible = false
        ui.openButton.isVisible = true
        ui.saveButton.isVisible = true

        val viewIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(model.downloadedUri!!, attachmentMime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        ui.openButton.isEnabled = viewIntent.resolveActivity(requireContext().packageManager) != null
        ui.openButton.setOnClickListener {
            startActivity(viewIntent)
            dismiss()
        }

        ui.saveButton.setOnClickListener {
            saveAttachmentLauncher.launch(attachment.fileName)
            dismiss()
        }
    }
}