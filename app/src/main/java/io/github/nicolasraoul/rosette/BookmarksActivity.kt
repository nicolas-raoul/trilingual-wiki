package io.github.nicolasraoul.rosette

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.widget.TextView

class BookmarksActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyMessage: TextView
    private lateinit var bookmarkManager: BookmarkManager
    private lateinit var adapter: BookmarksAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_bookmarks)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.bookmarks)

        recyclerView = findViewById(R.id.bookmarks_recycler_view)
        emptyMessage = findViewById(R.id.empty_message)
        bookmarkManager = BookmarkManager(this)

        setupRecyclerView()
        loadBookmarks()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun setupRecyclerView() {
        adapter = BookmarksAdapter(
            onItemClick = { bookmark -> openBookmark(bookmark) },
            onRemoveClick = { bookmark -> removeBookmark(bookmark) }
        )
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
    }

    private fun loadBookmarks() {
        val bookmarks = bookmarkManager.getBookmarks()
        if (bookmarks.isEmpty()) {
            recyclerView.visibility = View.GONE
            emptyMessage.visibility = View.VISIBLE
        } else {
            recyclerView.visibility = View.VISIBLE
            emptyMessage.visibility = View.GONE
            adapter.updateBookmarks(bookmarks)
        }
    }

    private fun openBookmark(bookmark: Bookmark) {
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra("wikidata_id", bookmark.wikidataId)
            putExtra("title", bookmark.title)
        }
        startActivity(intent)
        finish()
    }

    private fun removeBookmark(bookmark: Bookmark) {
        bookmarkManager.removeBookmark(bookmark.wikidataId)
        loadBookmarks()
    }
}