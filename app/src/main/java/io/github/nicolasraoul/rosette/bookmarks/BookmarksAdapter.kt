package io.github.nicolasraoul.rosette.bookmarks

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import io.github.nicolasraoul.rosette.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class BookmarkViewData(
    val wikidataId: String,
    val title: String,
    val timestamp: Long
)

class BookmarksAdapter(private val onClick: (BookmarkViewData) -> Unit) :
    ListAdapter<BookmarkViewData, BookmarksAdapter.BookmarkViewHolder>(BookmarkDiffCallback) {

    class BookmarkViewHolder(itemView: View, val onClick: (BookmarkViewData) -> Unit) :
        RecyclerView.ViewHolder(itemView) {
        private val titleTextView: TextView = itemView.findViewById(R.id.bookmark_title)
        private val timestampTextView: TextView = itemView.findViewById(R.id.bookmark_timestamp)
        private var currentBookmark: BookmarkViewData? = null

        init {
            itemView.setOnClickListener {
                currentBookmark?.let {
                    onClick(it)
                }
            }
        }

        fun bind(bookmark: BookmarkViewData) {
            currentBookmark = bookmark
            titleTextView.text = bookmark.title
            timestampTextView.text = "Saved on ${formatTimestamp(bookmark.timestamp)}"
        }

        private fun formatTimestamp(timestamp: Long): String {
            val sdf = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
            return sdf.format(Date(timestamp))
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BookmarkViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.bookmark_item, parent, false)
        return BookmarkViewHolder(view, onClick)
    }

    override fun onBindViewHolder(holder: BookmarkViewHolder, position: Int) {
        val bookmark = getItem(position)
        holder.bind(bookmark)
    }
}

object BookmarkDiffCallback : DiffUtil.ItemCallback<BookmarkViewData>() {
    override fun areItemsTheSame(oldItem: BookmarkViewData, newItem: BookmarkViewData): Boolean {
        return oldItem.wikidataId == newItem.wikidataId
    }

    override fun areContentsTheSame(oldItem: BookmarkViewData, newItem: BookmarkViewData): Boolean {
        return oldItem == newItem
    }
}
