package ru.vizbash.paramail.ui.messagelist

import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.view.ContextThemeWrapper
import androidx.core.view.isVisible
import androidx.paging.LoadState
import androidx.paging.LoadStateAdapter
import androidx.recyclerview.widget.RecyclerView
import ru.vizbash.paramail.R

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
            is LoadState.Error -> {
                holder.textView.isVisible = false
                holder.textView.text = "Ошибка" // TODO
            }
        }
    }

    class ViewHolder(val textView: TextView) : RecyclerView.ViewHolder(textView)
}