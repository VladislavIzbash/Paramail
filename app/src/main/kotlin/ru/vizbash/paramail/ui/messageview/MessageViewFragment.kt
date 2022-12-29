package ru.vizbash.paramail.ui.messageview

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.core.view.setPadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResult
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import ru.vizbash.paramail.R
import ru.vizbash.paramail.databinding.AttachmentBinding
import ru.vizbash.paramail.databinding.FragmentMessageViewBinding
import ru.vizbash.paramail.storage.message.Attachment
import ru.vizbash.paramail.storage.message.MessageBody
import java.text.DecimalFormat
import kotlin.math.log10
import kotlin.math.pow


@AndroidEntryPoint
class MessageViewFragment : Fragment() {
    companion object {
        const val ARG_ACCOUNT_ID = "account_id"
        const val ARG_MESSAGE_ID = "messaage_id"

        const val RESULT_KEY = "view_result"
        const val RESULT_ERROR_KEY = "error"
    }

    private var _ui: FragmentMessageViewBinding? = null
    private val ui get() = _ui!!

    private val model: MessageViewModel by viewModels()

    private var bodyWebView: WebView? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _ui = FragmentMessageViewBinding.inflate(inflater, container, false)
        return ui.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _ui = null
    }

    override fun onPause() {
        super.onPause()
        bodyWebView?.onPause()
    }

    override fun onResume() {
        super.onResume()
        bodyWebView?.onResume()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val timeFormat = DateFormat.getTimeFormat(view.context)
        val dateFormat = DateFormat.getMediumDateFormat(view.context)

        val actionBar = (requireActivity() as AppCompatActivity).supportActionBar
        actionBar?.title = "Загрузка..."

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val msg = model.message.await()

                actionBar?.title = msg.subject

                ui.from.text = msg.from
                @SuppressLint("SetTextI18n")
                ui.date.text = "${dateFormat.format(msg.date)}\n${timeFormat.format(msg.date)}"
                ui.recipients.text = msg.recipients.joinToString(", ")

                val (body, attachments) = model.messageBody.await()

                if (body != null) {
                    if (body.mime.startsWith("text/plain")) {
                        inflateTextBody(body)
                    } else if (body.mime.startsWith("text/html")) {
                        inflateHtmlBody(body)
                    }
                } else {
                    inflateError(getString(R.string.failed_to_show_message))
                }

                inflateAttachments(attachments)
            } catch (e: Exception) {
                e.printStackTrace()
                setFragmentResult(RESULT_KEY, bundleOf(RESULT_ERROR_KEY to true))
                findNavController().popBackStack()
            }

            ui.bodyLoadProgress.isVisible = false
        }
    }

    private fun inflateError(error: String) {
        val textView = TextView(requireContext()).apply {
            layoutParams = ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT)
            val marginTop = resources.getDimension(R.dimen.message_no_contents_margin_top).toInt()
            setPadding(0, marginTop, 0, 0)
            text = error
            textAlignment = TextView.TEXT_ALIGNMENT_CENTER
        }
        ui.bodyContentView.addView(textView)
    }

    private fun inflateAttachments(attachments: List<Attachment>) {
        ui.attachmentDivider.isVisible = attachments.isNotEmpty()

        for (attachment in attachments) {
            val binding = AttachmentBinding.inflate(layoutInflater, ui.attachmentLayout, true)

            binding.fileName.text = attachment.fileName
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val info = requireContext().contentResolver.getTypeInfo(attachment.mime)
                binding.fileTypeIcon.setImageIcon(info.icon)
            } else {
                binding.fileTypeIcon.setImageResource(R.drawable.ic_attachment)
            }
            binding.fileSize.text = formatSize(attachment.size)

            binding.root.setOnClickListener {
                val dialog = AttachmentDialogFragment(attachment, model)
                dialog.show(parentFragmentManager, "attachment")
            }
        }
    }

    private fun inflateTextBody(body: MessageBody) {
        val textView = TextView(requireContext()).apply {
            layoutParams = ViewGroup.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
            text = body.content.toString(Charsets.UTF_8)
            setPadding(resources.getDimension(R.dimen.plain_text_content_padding).toInt())
        }
        ui.bodyContentView.addView(textView)
    }

    private fun inflateHtmlBody(body: MessageBody) {
        bodyWebView = WebView(requireContext()).apply {
            layoutParams = ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT)
            webViewClient = MessageWebViewClient(requireContext())
            settings.javaScriptEnabled = false
            settings.loadWithOverviewMode = true
        }
        ui.bodyContentView.addView(bodyWebView)
        bodyWebView!!.loadDataWithBaseURL(
            "email://",
            body.content.toString(Charsets.UTF_8),
            body.mime,
            null,
            null,
        )
    }

    private fun formatSize(size: Int): String {
        val units = resources.getStringArray(R.array.size_units)

        val digitGroup = (log10(size.toDouble()) / log10(1024F)).toInt()
        val num = DecimalFormat("#,##0.#").format(size / 1024.0.pow(digitGroup))
        return "$num ${units[digitGroup]}"
    }

    class MessageWebViewClient(private val ctx: Context) : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            return if (request.url.scheme == "email") {
                false
            } else {
                ctx.startActivity(Intent(Intent.ACTION_VIEW, request.url))
                true
            }
        }
    }
}