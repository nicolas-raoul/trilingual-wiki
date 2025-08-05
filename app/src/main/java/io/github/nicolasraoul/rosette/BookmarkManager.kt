package io.github.nicolasraoul.rosette

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

data class Bookmark(
    val wikidataId: String,
    val title: String,
    val description: String,
    val thumbnailUrl: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

class BookmarkManager(context: Context) {
    private val sharedPreferences: SharedPreferences = context.getSharedPreferences("bookmarks", Context.MODE_PRIVATE)
    private val gson = Gson()

    fun addBookmark(bookmark: Bookmark) {
        val bookmarks = getBookmarks().toMutableList()
        // Remove existing bookmark with same ID if exists
        bookmarks.removeIf { it.wikidataId == bookmark.wikidataId }
        bookmarks.add(0, bookmark) // Add to beginning
        saveBookmarks(bookmarks)
    }

    fun removeBookmark(wikidataId: String) {
        val bookmarks = getBookmarks().toMutableList()
        bookmarks.removeIf { it.wikidataId == wikidataId }
        saveBookmarks(bookmarks)
    }

    fun isBookmarked(wikidataId: String): Boolean {
        return getBookmarks().any { it.wikidataId == wikidataId }
    }

    fun getBookmarks(): List<Bookmark> {
        val bookmarksJson = sharedPreferences.getString("bookmarks_list", null)
        return if (bookmarksJson != null) {
            val type = object : TypeToken<List<Bookmark>>() {}.type
            gson.fromJson(bookmarksJson, type)
        } else {
            emptyList()
        }
    }

    private fun saveBookmarks(bookmarks: List<Bookmark>) {
        val bookmarksJson = gson.toJson(bookmarks)
        sharedPreferences.edit().putString("bookmarks_list", bookmarksJson).apply()
    }
}