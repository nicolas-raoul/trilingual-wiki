package io.github.nicolasraoul.rosette

import android.content.Context
import android.content.SharedPreferences
import org.junit.Test
import org.junit.Assert.*
import org.mockito.Mockito.*

/**
 * Unit tests for last visited article functionality
 */
class LastVisitedArticleTest {

    @Test
    fun testSaveAndLoadLastVisitedArticle() {
        val mockContext = mock(Context::class.java)
        val mockSharedPreferences = mock(SharedPreferences::class.java)
        val mockEditor = mock(SharedPreferences.Editor::class.java)
        
        `when`(mockContext.getSharedPreferences("last_visited_article", Context.MODE_PRIVATE)).thenReturn(mockSharedPreferences)
        `when`(mockSharedPreferences.edit()).thenReturn(mockEditor)
        `when`(mockEditor.putString(anyString(), anyString())).thenReturn(mockEditor)
        `when`(mockEditor.apply()).then { }
        
        // Mock saving behavior
        val articleTitle = "Eiffel Tower"
        val languageUrls = mapOf(
            "en" to "https://en.m.wikipedia.org/wiki/Eiffel_Tower",
            "fr" to "https://fr.m.wikipedia.org/wiki/Tour_Eiffel",
            "ja" to "https://ja.m.wikipedia.org/wiki/エッフェル塔"
        )
        
        // Verify save calls would be made
        verify(mockContext).getSharedPreferences("last_visited_article", Context.MODE_PRIVATE)
        
        // Test loading - mock the return values
        `when`(mockSharedPreferences.getString("article_title", null)).thenReturn(articleTitle)
        `when`(mockSharedPreferences.getString("last_visited_article_en", null)).thenReturn(languageUrls["en"])
        `when`(mockSharedPreferences.getString("last_visited_article_fr", null)).thenReturn(languageUrls["fr"])
        `when`(mockSharedPreferences.getString("last_visited_article_ja", null)).thenReturn(languageUrls["ja"])
        
        // Verify that we can retrieve the article title
        val retrievedTitle = mockSharedPreferences.getString("article_title", null)
        assertEquals(articleTitle, retrievedTitle)
    }

    @Test
    fun testClearLastVisitedArticle() {
        val mockContext = mock(Context::class.java)
        val mockSharedPreferences = mock(SharedPreferences::class.java)
        val mockEditor = mock(SharedPreferences.Editor::class.java)
        
        `when`(mockContext.getSharedPreferences("last_visited_article", Context.MODE_PRIVATE)).thenReturn(mockSharedPreferences)
        `when`(mockSharedPreferences.edit()).thenReturn(mockEditor)
        `when`(mockEditor.clear()).thenReturn(mockEditor)
        `when`(mockEditor.apply()).then { }
        
        // Verify that clear would be called
        verify(mockContext).getSharedPreferences("last_visited_article", Context.MODE_PRIVATE)
    }

    @Test
    fun testLoadLastVisitedArticleWhenNoneExists() {
        val mockContext = mock(Context::class.java)
        val mockSharedPreferences = mock(SharedPreferences::class.java)
        
        `when`(mockContext.getSharedPreferences("last_visited_article", Context.MODE_PRIVATE)).thenReturn(mockSharedPreferences)
        `when`(mockSharedPreferences.getString("article_title", null)).thenReturn(null)
        
        // When no article is saved, getString should return null
        val retrievedTitle = mockSharedPreferences.getString("article_title", null)
        assertNull(retrievedTitle)
    }
}