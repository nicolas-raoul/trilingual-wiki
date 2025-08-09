package io.github.nicolasraoul.rosette.bookmarks

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import io.github.nicolasraoul.rosette.R
import java.text.SimpleDateFormat
import java.util.Locale

data class BookmarkViewData(
    val wikidataId: String,
    val imageUrl: String?,
    val titles: Map<String, String>, // Map of lang code to title
    val timestamp: Long
)

class BookmarksAdapter(private val onClick: (BookmarkViewData) -> Unit) :
    ListAdapter<BookmarkViewData, BookmarksAdapter.BookmarkViewHolder>(BookmarkDiffCallback) {

    class BookmarkViewHolder(itemView: View, val onClick: (BookmarkViewData) -> Unit) :
        RecyclerView.ViewHolder(itemView) {
        private val imageView: ImageView = itemView.findViewById(R.id.bookmark_image)
        private val titleTextView1: TextView = itemView.findViewById(R.id.bookmark_title1)
        private val titleTextView2: TextView = itemView.findViewById(R.id.bookmark_title2)
        private val titleTextView3: TextView = itemView.findViewById(R.id.bookmark_title3)
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

            if (bookmark.imageUrl != null) {
                Glide.with(itemView.context)
                    .load(bookmark.imageUrl)
                    .centerCrop()
                    .into(imageView)
            } else {
                imageView.setImageResource(R.drawable.ic_star)
            }

            val titles = bookmark.titles.values.toList()
            val textViews = listOf(titleTextView1, titleTextView2, titleTextView3)
            textViews.forEachIndexed { index, textView ->
                val title = titles.getOrNull(index)
                if (title != null) {
                    textView.text = title
                    textView.visibility = View.VISIBLE
                } else {
                    textView.visibility = View.GONE
                }
            }
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
