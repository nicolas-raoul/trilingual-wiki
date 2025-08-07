package io.github.nicolasraoul.rosette

import android.content.Context
import android.content.SharedPreferences
import org.junit.Test
import org.junit.Assert.*
import org.mockito.Mockito.*

/**
 * Unit tests for BookmarkManager class
 */
class BookmarkManagerTest {

    @Test
    fun testAddBookmark() {
        val mockContext = mock(Context::class.java)
        val mockSharedPreferences = mock(SharedPreferences::class.java)
        val mockEditor = mock(SharedPreferences.Editor::class.java)
        
        `when`(mockContext.getSharedPreferences(anyString(), anyInt())).thenReturn(mockSharedPreferences)
        `when`(mockSharedPreferences.edit()).thenReturn(mockEditor)
        `when`(mockEditor.putString(anyString(), anyString())).thenReturn(mockEditor)
        `when`(mockEditor.apply()).then { }
        `when`(mockSharedPreferences.getString(anyString(), isNull())).thenReturn(null)
        
        val bookmarkManager = BookmarkManager(mockContext)
        val bookmark = Bookmark(
            id = "test_id",
            title = "Test Article",
            language = "en"
        )
        
        val result = bookmarkManager.addBookmark(bookmark)
        
        assertTrue(result)
        verify(mockEditor).putString(anyString(), anyString())
        verify(mockEditor).apply()
    }

    @Test
    fun testGetBookmarksEmpty() {
        val mockContext = mock(Context::class.java)
        val mockSharedPreferences = mock(SharedPreferences::class.java)
        
        `when`(mockContext.getSharedPreferences(anyString(), anyInt())).thenReturn(mockSharedPreferences)
        `when`(mockSharedPreferences.getString(anyString(), isNull())).thenReturn(null)
        
        val bookmarkManager = BookmarkManager(mockContext)
        val bookmarks = bookmarkManager.getBookmarks()
        
        assertTrue(bookmarks.isEmpty())
    }

    @Test
    fun testIsBookmarked() {
        val mockContext = mock(Context::class.java)
        val mockSharedPreferences = mock(SharedPreferences::class.java)
        
        `when`(mockContext.getSharedPreferences(anyString(), anyInt())).thenReturn(mockSharedPreferences)
        `when`(mockSharedPreferences.getString(anyString(), isNull())).thenReturn(
            """[{"id":"test_id","title":"Test Article","language":"en","wikidataId":null,"timestamp":1234567890}]"""
        )
        
        val bookmarkManager = BookmarkManager(mockContext)
        
        assertTrue(bookmarkManager.isBookmarked("Test Article", "en"))
        assertFalse(bookmarkManager.isBookmarked("Other Article", "en"))
        assertFalse(bookmarkManager.isBookmarked("Test Article", "fr"))
    }

    @Test
    fun testBookmarkCount() {
        val mockContext = mock(Context::class.java)
        val mockSharedPreferences = mock(SharedPreferences::class.java)
        
        `when`(mockContext.getSharedPreferences(anyString(), anyInt())).thenReturn(mockSharedPreferences)
        `when`(mockSharedPreferences.getString(anyString(), isNull())).thenReturn(
            """[{"id":"test_id","title":"Test Article","language":"en","wikidataId":null,"timestamp":1234567890}]"""
        )
        
        val bookmarkManager = BookmarkManager(mockContext)
        
        assertEquals(1, bookmarkManager.getBookmarkCount())
    }

    @Test
    fun testBookmarkEquality() {
        val bookmark1 = Bookmark("id1", "Title", "en")
        val bookmark2 = Bookmark("id1", "Different Title", "fr")
        val bookmark3 = Bookmark("id2", "Title", "en")
        
        assertEquals(bookmark1, bookmark2) // Same ID
        assertNotEquals(bookmark1, bookmark3) // Different ID
    }
}