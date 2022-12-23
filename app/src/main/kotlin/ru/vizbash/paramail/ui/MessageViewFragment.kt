package ru.vizbash.paramail.ui

import android.annotation.SuppressLint
import android.os.Bundle
import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import ru.vizbash.paramail.databinding.FragmentMessageViewBinding


@AndroidEntryPoint
class MessageViewFragment : Fragment() {
    companion object {
        const val ARG_ACCOUNT_ID = "account_id"
        const val ARG_MESSAGE_ID = "messaage_id"
    }

    private var _ui: FragmentMessageViewBinding? = null
    private val ui get() = _ui!!

    private val model: MessageViewModel by viewModels()

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
        ui.bodyWebView.onPause()
    }

    override fun onResume() {
        super.onResume()
        ui.bodyWebView.onResume()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val timeFormat = DateFormat.getTimeFormat(view.context)
        val dateFormat = DateFormat.getDateFormat(view.context)

        viewLifecycleOwner.lifecycleScope.launch {
            val msg = model.message.await()

            (requireActivity() as AppCompatActivity).supportActionBar?.title = msg.subject

            ui.from.text = msg.from
            @SuppressLint("SetTextI18n")
            ui.date.text = "${dateFormat.format(msg.date)} ${timeFormat.format(msg.date)}"
            ui.recipients.text = msg.recipients.joinToString(", ")

            ui.bodyWebView.webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) = false
            }
//            ui.bodyContent.text = model.body.await().content
//            ui.bodyWebView.loadUrl("https://linux.org.ru")
            ui.bodyWebView.loadDataWithBaseURL(
                "email://",
                model.body.await().content,
                "text/html; charset=utf-8",
                "utf-8",
                null,
            )
            ui.bodyLoadProgress.isVisible = false
        }
    }
}