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
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import ru.vizbash.paramail.R
import ru.vizbash.paramail.databinding.ItemMessageBinding
import ru.vizbash.paramail.storage.message.Message

class MessageViewHolder(
    view: View,
    private val onMessageClickListener: (Message) -> Unit,
) : RecyclerView.ViewHolder(view) {
    private val ui = ItemMessageBinding.bind(view)

    private val timeFormat = DateFormat.getTimeFormat(view.context)
    private val dateFormat = DateFormat.getMediumDateFormat(view.context)

    fun bind(message: Message, highlight: String?) {
        ui.mailSubject.text = if (highlight == null) {
            message.subject
        } else {
            makeHighlightSpan(message.subject, highlight)
        }

        ui.mailFrom.text = if (highlight == null) {
            message.from
        } else {
            makeHighlightSpan(message.from, highlight)
        }

        @SuppressLint("SetTextI18n")
        ui.mailDate.text = if (DateUtils.isToday(message.date.time)) {
            timeFormat.format(message.date)
        } else {
            dateFormat.format(message.date)
        }

        if (highlight == null) {
            ui.mailSubject.typeface =
                if (message.isUnread) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
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

class PagingMessageAdapter : PagingDataAdapter<Message, MessageViewHolder>(DIFF_CALLBACK) {
    var onMessageClickListener: (Message) -> Unit = {}

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_message, parent, false)
        return MessageViewHolder(view, onMessageClickListener)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        getItem(position)?.let { holder.bind(it, null) }
    }
}

class ListMessageAdapter : ListAdapter<Message, MessageViewHolder>(DIFF_CALLBACK) {
    var onMessageClickListener: (Message) -> Unit = {}

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

private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<Message>() {
    override fun areItemsTheSame(
        oldItem: Message,
        newItem: Message,
    ): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(
        oldItem: Message,
        newItem: Message,
    ): Boolean {
        return oldItem == newItem
    }
}