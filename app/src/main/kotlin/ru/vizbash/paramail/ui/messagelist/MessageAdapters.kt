package ru.vizbash.paramail.ui.messagelist

import android.annotation.SuppressLint
import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableString
import android.text.format.DateFormat
import android.text.format.DateUtils
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.paging.LoadState
import androidx.paging.LoadStateAdapter
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import ru.vizbash.paramail.R
import ru.vizbash.paramail.databinding.ItemMessageBinding
import ru.vizbash.paramail.storage.message.MessageWithRecipients

class MessageViewHolder(
    view: View,
    private val onMessageClickListener: (MessageWithRecipients) -> Unit,
) : RecyclerView.ViewHolder(view) {
    var boundItem: MessageWithRecipients? = null
        private set

    private val ui = ItemMessageBinding.bind(view)

    private val timeFormat = DateFormat.getTimeFormat(view.context)
    private val dateFormat = DateFormat.getMediumDateFormat(view.context)

    fun bind(message: MessageWithRecipients, highlight: String?) {
        boundItem = message

        ui.mailSubject.text = if (highlight == null) {
            message.msg.subject
        } else {
            makeHighlightSpan(message.msg.subject, highlight)
        }

        ui.mailFrom.text = if (highlight == null) {
            message.from.toString()
        } else {
            makeHighlightSpan(message.from.toString(), highlight)
        }

        @SuppressLint("SetTextI18n")
        ui.mailDate.text = if (DateUtils.isToday(message.msg.date.time)) {
            timeFormat.format(message.msg.date)
        } else {
            dateFormat.format(message.msg.date)
        }

        if (highlight == null) {
            ui.mailSubject.typeface =
                if (message.msg.isUnread) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }

        ui.root.setOnClickListener { onMessageClickListener(message) }
    }

    private fun makeHighlightSpan(str: String, subStr: String): SpannableString {
        val ind = str.indexOf(subStr, ignoreCase = true)
        return SpannableString(str).apply {
            if (ind != -1) {
                setSpan(
                    StyleSpan(Typeface.BOLD),
                    ind,
                    ind + subStr.length,
                    Spannable.SPAN_INCLUSIVE_EXCLUSIVE,
                )
            }
        }
    }
}

class PagingMessageAdapter : PagingDataAdapter<MessageWithRecipients, MessageViewHolder>(DIFF_CALLBACK) {
    var onMessageClickListener: (MessageWithRecipients) -> Unit = {}

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_message, parent, false)
        return MessageViewHolder(view, onMessageClickListener)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        getItem(position)?.let { holder.bind(it, null) }
    }
}

class MessageLoadStateAdapter : LoadStateAdapter<MessageLoadStateAdapter.ViewHolder>() {
    override fun onCreateViewHolder(parent: ViewGroup, loadState: LoadState): ViewHolder {
        val view = TextView(parent.context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
            textAlignment = TextView.TEXT_ALIGNMENT_CENTER
            textSize = parent.context.resources.getDimension(R.dimen.loading_text_size)
        }

        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, loadState: LoadState) {
        when (loadState) {
            is LoadState.NotLoading -> {
                holder.textView.isVisible = false
            }
            is LoadState.Loading -> {
                holder.textView.isVisible = true
                holder.textView.text = holder.itemView.context.getString(R.string.loading_messages)
            }
            else -> {}
        }
    }

    class ViewHolder(val textView: TextView) : RecyclerView.ViewHolder(textView)
}

class ListMessageAdapter : ListAdapter<MessageWithRecipients, MessageViewHolder>(DIFF_CALLBACK) {
    var onMessageClickListener: (MessageWithRecipients) -> Unit = {}

    var highlightedText: String? = null
        @SuppressLint("NotifyDataSetChanged")
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_message, parent, false)
        return MessageViewHolder(view, onMessageClickListener)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        getItem(position)?.let { holder.bind(it, highlightedText) }
    }
}

private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<MessageWithRecipients>() {
    override fun areItemsTheSame(
        oldItem: MessageWithRecipients,
        newItem: MessageWithRecipients,
    ): Boolean {
        return oldItem.msg.id == newItem.msg.id
    }

    override fun areContentsTheSame(
        oldItem: MessageWithRecipients,
        newItem: MessageWithRecipients,
    ): Boolean {
        return oldItem == newItem
    }
}
