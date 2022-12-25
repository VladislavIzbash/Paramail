package ru.vizbash.paramail.ui.messagelist

import android.annotation.SuppressLint
import android.graphics.Typeface
import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import ru.vizbash.paramail.R
import ru.vizbash.paramail.databinding.ItemMessageBinding
import ru.vizbash.paramail.storage.message.Message

class MessageAdapter : PagingDataAdapter<Message, MessageAdapter.ViewHolder>(DIFF_CALLBACK) {
    var onMessageClickListener: (Message) -> Unit = {}

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_message, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        getItem(position)?.let { holder.bind(it) }
    }

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val ui = ItemMessageBinding.bind(view)

        private val timeFormat = DateFormat.getTimeFormat(view.context)
        private val dateFormat = DateFormat.getDateFormat(view.context)

        fun bind(message: Message) {
            ui.mailSubject.text = message.subject
            ui.mailFrom.text = message.from
            @SuppressLint("SetTextI18n")
            ui.mailDate.text = "${dateFormat.format(message.date)}\n${timeFormat.format(message.date)}"
            ui.mailSubject.typeface = if (message.isUnread) Typeface.DEFAULT_BOLD else Typeface.DEFAULT

            ui.root.setOnClickListener { onMessageClickListener(message) }
        }
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
        return oldItem.equals(newItem)
    }
}