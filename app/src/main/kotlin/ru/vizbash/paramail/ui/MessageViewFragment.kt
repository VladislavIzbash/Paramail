package ru.vizbash.paramail.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
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
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.core.view.setPadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import ru.vizbash.paramail.R
import ru.vizbash.paramail.databinding.FragmentMessageViewBinding
import ru.vizbash.paramail.storage.message.MessageBody


@AndroidEntryPoint
class MessageViewFragment : Fragment() {
    companion object {
        const val ARG_ACCOUNT_ID = "account_id"
        const val ARG_MESSAGE_ID = "messaage_id"
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

        viewLifecycleOwner.lifecycleScope.launch {
            val msg = model.message.await()

            (requireActivity() as AppCompatActivity).supportActionBar?.title = msg.subject

            ui.from.text = msg.from
            @SuppressLint("SetTextI18n")
            ui.date.text = "${dateFormat.format(msg.date)}\n${timeFormat.format(msg.date)}"
            ui.recipients.text = msg.recipients.joinToString(", ")

            val body = model.messageBody.await()
            if (body != null) {
                if (body.mime.startsWith("text/plain")) {
                    inflateTextBody(body)
                } else if (body.mime.startsWith("text/html")) {
                    inflateHtmlBody(body)
                }
            } else {
                val textView = TextView(requireContext()).apply {
                    layoutParams = ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT)
                    val marginTop = resources.getDimension(R.dimen.message_no_contents_margin_top).toInt()
                    setPadding(0, marginTop, 0, 0)
                    setText(R.string.failed_to_show_message)
                    textAlignment = TextView.TEXT_ALIGNMENT_CENTER
                }
                ui.bodyContentView.addView(textView)
            }

            ui.bodyLoadProgress.isVisible = false
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