package ru.vizbash.paramail.ui.messageview

import android.app.Dialog
import android.content.DialogInterface
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts.CreateDocument
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
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

    private lateinit var uriDeferred: Deferred<Uri?>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        saveAttachmentLauncher = registerForActivityResult(CreateDocument(attachmentMime)) { saveUri ->
            if (saveUri == null) {
                return@registerForActivityResult
            }

            val uri = runBlocking { uriDeferred.await() }

            val input = requireContext().contentResolver.openInputStream(uri!!)!!
            val output = requireContext().contentResolver.openOutputStream(saveUri)!!

            input.use { output.use { input.copyTo(output) } }
        }

        uriDeferred = model.downloadAttachmentAsync(attachment)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _ui = DialogAttachmentBinding.inflate(layoutInflater)

        model.downloadProgress.value = 0F

        ui.title.text = getString(R.string.downloading, attachment.fileName)
        ui.cancelButton.isVisible = true
        ui.downloadProgress.isVisible = true
        ui.saveButton.isVisible = false
        ui.openButton.isVisible = false

        ui.cancelButton.setOnClickListener {
            uriDeferred.cancel()
            dismiss()
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                model.downloadProgress.collect {
                    ui.downloadProgress.progress = (it * 100F).toInt()
                }
            }
        }

        lifecycleScope.launch {
            val uri = uriDeferred.await()
            if (uri != null) {
                onDownloadFinished(uri)
            } else {
                ui.title.text = getString(R.string.cannot_download, attachment.fileName)
            }
        }

        return object : Dialog(requireContext()) {
            override fun onCreate(savedInstanceState: Bundle?) {
                super.onCreate(savedInstanceState)
                setContentView(ui.root)
            }
        }
    }

    override fun onDismiss(dialog: DialogInterface) {
        uriDeferred.cancel()
    }

    private fun onDownloadFinished(uri: Uri) {
        ui.title.text = getString(R.string.downloaded, attachment.fileName)
        ui.cancelButton.isVisible = false
        ui.openButton.isVisible = true
        ui.saveButton.isVisible = true

        val viewIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, attachmentMime)
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