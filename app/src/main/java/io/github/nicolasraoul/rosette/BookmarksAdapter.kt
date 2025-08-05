package io.github.nicolasraoul.rosette

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

class BookmarksAdapter(
    private val onItemClick: (Bookmark) -> Unit,
    private val onRemoveClick: (Bookmark) -> Unit
) : RecyclerView.Adapter<BookmarksAdapter.ViewHolder>() {

    private var bookmarks = mutableListOf<Bookmark>()

    fun updateBookmarks(newBookmarks: List<Bookmark>) {
        bookmarks.clear()
        bookmarks.addAll(newBookmarks)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.bookmark_item, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(bookmarks[position])
    }

    override fun getItemCount(): Int = bookmarks.size

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val titleTextView: TextView = itemView.findViewById(R.id.bookmark_title)
        private val descriptionTextView: TextView = itemView.findViewById(R.id.bookmark_description)
        private val thumbnailImageView: ImageView = itemView.findViewById(R.id.bookmark_thumbnail)
        private val removeButton: ImageView = itemView.findViewById(R.id.bookmark_remove)

        fun bind(bookmark: Bookmark) {
            titleTextView.text = bookmark.title
            descriptionTextView.text = bookmark.description

            if (bookmark.thumbnailUrl != null) {
                Glide.with(itemView.context)
                    .load(bookmark.thumbnailUrl)
                    .centerCrop()
                    .into(thumbnailImageView)
            } else {
                thumbnailImageView.setImageResource(android.R.drawable.ic_menu_gallery)
            }

            itemView.setOnClickListener { onItemClick(bookmark) }
            removeButton.setOnClickListener { onRemoveClick(bookmark) }
        }
    }
}