package io.github.nicolasraoul.rosette

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Manages bookmark persistence and operations using SharedPreferences
 */
class BookmarkManager(private val context: Context) {
    
    private val sharedPreferences: SharedPreferences = 
        context.getSharedPreferences(BOOKMARKS_PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()
    
    companion object {
        private const val BOOKMARKS_PREFS_NAME = "bookmarks_prefs"
        private const val BOOKMARKS_KEY = "bookmarks"
        private const val TAG = "BookmarkManager"
    }
    
    /**
     * Add a bookmark
     */
    fun addBookmark(bookmark: Bookmark): Boolean {
        return try {
            val bookmarks = getBookmarks().toMutableList()
            if (bookmarks.contains(bookmark)) {
                Log.d(TAG, "Bookmark already exists: ${bookmark.title}")
                false
            } else {
                bookmarks.add(bookmark)
                saveBookmarks(bookmarks)
                Log.d(TAG, "Bookmark added: ${bookmark.title}")
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add bookmark: ${bookmark.title}", e)
            false
        }
    }
    
    /**
     * Remove a bookmark
     */
    fun removeBookmark(bookmark: Bookmark): Boolean {
        return try {
            val bookmarks = getBookmarks().toMutableList()
            val removed = bookmarks.remove(bookmark)
            if (removed) {
                saveBookmarks(bookmarks)
                Log.d(TAG, "Bookmark removed: ${bookmark.title}")
            }
            removed
        } catch (e: Exception) {
            Log.e(TAG, "Failed to remove bookmark: ${bookmark.title}", e)
            false
        }
    }
    
    /**
     * Get all bookmarks sorted by timestamp (newest first)
     */
    fun getBookmarks(): List<Bookmark> {
        return try {
            val bookmarksJson = sharedPreferences.getString(BOOKMARKS_KEY, null)
            if (bookmarksJson.isNullOrEmpty()) {
                emptyList()
            } else {
                val type = object : TypeToken<List<Bookmark>>() {}.type
                val bookmarks: List<Bookmark> = gson.fromJson(bookmarksJson, type)
                bookmarks.sortedByDescending { it.timestamp }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load bookmarks", e)
            emptyList()
        }
    }
    
    /**
     * Check if an article is bookmarked
     */
    fun isBookmarked(title: String, language: String): Boolean {
        return getBookmarks().any { it.title == title && it.language == language }
    }
    
    /**
     * Check if an article is bookmarked by Wikidata ID
     */
    fun isBookmarkedByWikidataId(wikidataId: String): Boolean {
        return getBookmarks().any { it.wikidataId == wikidataId }
    }
    
    /**
     * Get bookmark count
     */
    fun getBookmarkCount(): Int {
        return getBookmarks().size
    }
    
    /**
     * Clear all bookmarks
     */
    fun clearAllBookmarks(): Boolean {
        return try {
            sharedPreferences.edit().remove(BOOKMARKS_KEY).apply()
            Log.d(TAG, "All bookmarks cleared")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear bookmarks", e)
            false
        }
    }
    
    private fun saveBookmarks(bookmarks: List<Bookmark>) {
        val bookmarksJson = gson.toJson(bookmarks)
        sharedPreferences.edit().putString(BOOKMARKS_KEY, bookmarksJson).apply()
    }
}