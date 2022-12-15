package ru.vizbash.paramail.ui.messagelist

import android.annotation.SuppressLint
import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import ru.vizbash.paramail.R
import ru.vizbash.paramail.databinding.ItemMessageBinding
import ru.vizbash.paramail.storage.entity.Message

class MessageAdapter : PagingDataAdapter<Message, MessageAdapter.ViewHolder>(DIFF_CALLBACK) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_message, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position)!!)
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val ui = ItemMessageBinding.bind(view)

        private val timeFormat = DateFormat.getTimeFormat(view.context)
        private val dateFormat = DateFormat.getDateFormat(view.context)

        @SuppressLint("SetTextI18n")
        fun bind(message: Message) {
            ui.mailSubject.text = message.subject
            ui.mailFrom.text = message.from
            ui.mailDate.text = "${dateFormat.format(message.date)} ${timeFormat.format(message.date)}"
            ui.unreadIndicator.isVisible = message.isUnread
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