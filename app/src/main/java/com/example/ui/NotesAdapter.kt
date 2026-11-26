package com.example.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.R
import com.example.model.NoteItem
import com.example.model.StoragePresence
import java.text.DateFormat
import java.util.Date
import java.util.Locale

class NotesAdapter(
    private val isGridView: Boolean = false,
    private val onNoteClick: (NoteItem) -> Unit,
    private val onSelectionChanged: ((Int) -> Unit)? = null
) : ListAdapter<NoteItem, NotesAdapter.NoteViewHolder>(DiffCallback) {

    private val selectedIds = mutableSetOf<String>()
    var isSelectionMode = false
        set(value) {
            field = value
            if (!value) selectedIds.clear()
            notifyDataSetChanged()
        }

    fun toggleSelection(id: String) {
        if (selectedIds.contains(id)) {
            selectedIds.remove(id)
        } else {
            selectedIds.add(id)
        }
        if (selectedIds.isEmpty() && isSelectionMode) {
            isSelectionMode = false
        }
        onSelectionChanged?.invoke(selectedIds.size)
        notifyDataSetChanged()
    }

    fun getSelectedIds(): Set<String> = selectedIds.toSet()

    fun clearSelection() {
        selectedIds.clear()
        isSelectionMode = false
        onSelectionChanged?.invoke(0)
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int {
        return if (isGridView) 1 else 0
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NoteViewHolder {
        val layoutRes = if (viewType == 1) R.layout.item_note_grid else R.layout.item_note
        val view = LayoutInflater.from(parent.context).inflate(layoutRes, parent, false)
        return NoteViewHolder(view, onNoteClick, ::toggleSelection)
    }

    override fun onBindViewHolder(holder: NoteViewHolder, position: Int) {
        val item = getItem(position)
        holder.bind(item, isSelectionMode, selectedIds.contains(item.note.id))
    }

    class NoteViewHolder(
        itemView: View,
        private val onNoteClick: (NoteItem) -> Unit,
        private val onToggleSelection: (String) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {

        private val tvTitle: TextView = itemView.findViewById(R.id.tvNoteTitle)
        private val tvContent: TextView = itemView.findViewById(R.id.tvNoteContent)
        private val tvBadge: TextView = itemView.findViewById(R.id.tvStorageBadge)
        private val tvUpdatedAt: TextView = itemView.findViewById(R.id.tvNoteUpdatedAt)
        private val cbSelect: CheckBox? = itemView.findViewById(R.id.cbSelectNote)

        fun bind(item: NoteItem, isSelectionMode: Boolean, isSelected: Boolean) {
            val context = itemView.context
            tvTitle.text = item.note.title
            tvContent.text = item.note.content

            val dateFormatter = DateFormat.getDateTimeInstance(
                DateFormat.MEDIUM,
                DateFormat.SHORT,
                Locale.getDefault()
            )
            val formattedDate = dateFormatter.format(Date(item.note.updatedAt))
            tvUpdatedAt.text = context.getString(R.string.updated_at_format, formattedDate)

            when (item.presence) {
                StoragePresence.SQLITE_ONLY -> {
                    tvBadge.setText(R.string.storage_sqlite)
                    tvBadge.setBackgroundResource(R.drawable.bg_badge_sqlite)
                    tvBadge.setTextColor(ContextCompat.getColor(context, R.color.badge_sqlite_fg))
                }
                StoragePresence.JSON_ONLY -> {
                    tvBadge.setText(R.string.storage_json)
                    tvBadge.setBackgroundResource(R.drawable.bg_badge_json)
                    tvBadge.setTextColor(ContextCompat.getColor(context, R.color.badge_json_fg))
                }
                StoragePresence.BOTH -> {
                    tvBadge.setText(R.string.storage_both)
                    tvBadge.setBackgroundResource(R.drawable.bg_badge_both)
                    tvBadge.setTextColor(ContextCompat.getColor(context, R.color.badge_both_fg))
                }
            }

            if (cbSelect != null) {
                cbSelect.visibility = if (isSelectionMode) View.VISIBLE else View.GONE
                cbSelect.isChecked = isSelected
            }

            itemView.setOnClickListener {
                if (isSelectionMode) {
                    onToggleSelection(item.note.id)
                } else {
                    onNoteClick(item)
                }
            }

            itemView.setOnLongClickListener {
                onToggleSelection(item.note.id)
                true
            }
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<NoteItem>() {
        override fun areItemsTheSame(oldItem: NoteItem, newItem: NoteItem): Boolean =
            oldItem.note.id == newItem.note.id

        override fun areContentsTheSame(oldItem: NoteItem, newItem: NoteItem): Boolean =
            oldItem == newItem
    }
}
