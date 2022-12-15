package ru.vizbash.paramail.ui.messagelist

import android.annotation.SuppressLint
import android.graphics.*
import android.view.View
import androidx.core.content.res.ResourcesCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import ru.vizbash.paramail.R
import kotlin.math.abs

@SuppressLint("ResourceType")
class MessageTouchCallback(private val root: View) : ItemTouchHelper.SimpleCallback(
    0,
    ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT,
) {
    private val archiveIcon = ResourcesCompat.getDrawable(
        root.context.resources,
        R.drawable.ic_archive,
        null,
    )!!
    private val spamIcon = ResourcesCompat.getDrawable(
        root.context.resources,
        R.drawable.ic_to_spam,
        null,
    )!!

    private val spamActionColor: Int
    private val archiveActionColor: Int
    private val incompleteColor: Int

    init {
        val attrs = root.context.theme.obtainStyledAttributes(intArrayOf(
            R.attr.colorOnSwipeAction,
            R.attr.colorSpamAction,
            R.attr.colorArchiveAction,
            R.attr.colorIncompleteAction,
        ))

        val iconColor = attrs.getColor(0, Color.WHITE)
        archiveIcon.colorFilter = PorterDuffColorFilter(iconColor, PorterDuff.Mode.SRC_IN)
        spamIcon.colorFilter = PorterDuffColorFilter(iconColor, PorterDuff.Mode.SRC_IN)

        spamActionColor = attrs.getColor(1, Color.RED)
        archiveActionColor = attrs.getColor(2, Color.YELLOW)
        incompleteColor = attrs.getColor(3, Color.GRAY)

        attrs.recycle()
    }

    override fun onMove(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder,
    ) = true

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
        val text = if (direction == ItemTouchHelper.RIGHT) {
            "Добавлено в архив"
        } else {
            "Добавлено в спам"
        }

        Snackbar.make(root, text, Snackbar.LENGTH_SHORT).show()
    }

    override fun getSwipeThreshold(viewHolder: RecyclerView.ViewHolder) = 0.3f

    override fun onChildDraw(
        c: Canvas,
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        dX: Float,
        dY: Float,
        actionState: Int,
        isCurrentlyActive: Boolean,
    ) {
        c.save()
        val itemView = viewHolder.itemView

        val actionColor = when {
            abs(dX) < itemView.width / 3 -> incompleteColor
            dX > 0 -> archiveActionColor
            else -> spamActionColor
        }
        val paint = Paint().apply {
            color = actionColor
        }

        if (dX > 0) {
            val clipRect = Rect(itemView.left, itemView.top, dX.toInt(), itemView.bottom)
            c.clipRect(clipRect)
            c.drawRect(clipRect, paint)

            val padding = (itemView.height - archiveIcon.intrinsicHeight) / 2
            archiveIcon.bounds = Rect(
                itemView.left + padding,
                itemView.top + padding,
                itemView.left + padding + archiveIcon.intrinsicWidth,
                itemView.bottom - padding,
            )
            archiveIcon.draw(c)
        } else if (dX < 0) {
            val clipRect = Rect(itemView.width + dX.toInt(), itemView.top, itemView.right, itemView.bottom)
            c.clipRect(clipRect)
            c.drawRect(clipRect, paint)

            val padding = (itemView.height - spamIcon.intrinsicHeight) / 2
            spamIcon.bounds = Rect(
                itemView.right - padding - spamIcon.intrinsicWidth,
                itemView.top + padding,
                itemView.right - padding,
                itemView.bottom - padding,
            )
            spamIcon.draw(c)
        }

        c.restore()
        super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
    }
}