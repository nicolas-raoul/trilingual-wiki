package io.github.nicolasraoul.rosette

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.*

/**
 * Dialog fragment for displaying and managing bookmarks
 */
class BookmarksDialog : DialogFragment() {
    
    private lateinit var bookmarkManager: BookmarkManager
    private lateinit var bookmarksAdapter: BookmarksAdapter
    private var onBookmarkClickListener: ((Bookmark) -> Unit)? = null
    
    companion object {
        fun newInstance(onBookmarkClick: ((Bookmark) -> Unit)? = null): BookmarksDialog {
            return BookmarksDialog().apply {
                onBookmarkClickListener = onBookmarkClick
            }
        }
    }
    
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        dialog.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        return dialog
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_bookmarks, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        bookmarkManager = BookmarkManager(requireContext())
        
        val recyclerView = view.findViewById<RecyclerView>(R.id.bookmarks_recycler_view)
        val noBookmarksText = view.findViewById<TextView>(R.id.no_bookmarks_text)
        val closeButton = view.findViewById<Button>(R.id.close_button)
        
        bookmarksAdapter = BookmarksAdapter(
            onBookmarkClick = { bookmark ->
                onBookmarkClickListener?.invoke(bookmark)
                dismiss()
            },
            onRemoveClick = { bookmark ->
                bookmarkManager.removeBookmark(bookmark)
                loadBookmarks()
            }
        )
        
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = bookmarksAdapter
        
        closeButton.setOnClickListener { dismiss() }
        
        loadBookmarks()
    }
    
    private fun loadBookmarks() {
        val bookmarks = bookmarkManager.getBookmarks()
        bookmarksAdapter.updateBookmarks(bookmarks)
        
        view?.findViewById<RecyclerView>(R.id.bookmarks_recycler_view)?.visibility = 
            if (bookmarks.isEmpty()) View.GONE else View.VISIBLE
        view?.findViewById<TextView>(R.id.no_bookmarks_text)?.visibility = 
            if (bookmarks.isEmpty()) View.VISIBLE else View.GONE
    }
}

/**
 * Adapter for displaying bookmarks in a RecyclerView
 */
class BookmarksAdapter(
    private val onBookmarkClick: (Bookmark) -> Unit,
    private val onRemoveClick: (Bookmark) -> Unit
) : RecyclerView.Adapter<BookmarksAdapter.BookmarkViewHolder>() {
    
    private var bookmarks = listOf<Bookmark>()
    private val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
    
    fun updateBookmarks(newBookmarks: List<Bookmark>) {
        bookmarks = newBookmarks
        notifyDataSetChanged()
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BookmarkViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_bookmark, parent, false)
        return BookmarkViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: BookmarkViewHolder, position: Int) {
        holder.bind(bookmarks[position])
    }
    
    override fun getItemCount(): Int = bookmarks.size
    
    inner class BookmarkViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val titleTextView: TextView = itemView.findViewById(R.id.bookmark_title)
        private val languageTextView: TextView = itemView.findViewById(R.id.bookmark_language)
        private val dateTextView: TextView = itemView.findViewById(R.id.bookmark_date)
        private val removeButton: ImageButton = itemView.findViewById(R.id.remove_button)
        
        fun bind(bookmark: Bookmark) {
            titleTextView.text = bookmark.title
            languageTextView.text = "Language: ${bookmark.language.uppercase()}"
            dateTextView.text = dateFormat.format(Date(bookmark.timestamp))
            
            itemView.setOnClickListener { onBookmarkClick(bookmark) }
            removeButton.setOnClickListener { onRemoveClick(bookmark) }
        }
    }
}