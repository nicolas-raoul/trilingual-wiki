package io.github.nicolasraoul.rosette

import org.junit.Test
import org.junit.Assert.*

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }
    
    @Test
    fun searchSuggestion_hasArticleInAnyLanguage_defaultsToTrue() {
        val suggestion = SearchSuggestion(
            id = "Q123",
            label = "Test",
            description = "Test description",
            thumbnailUrl = null
        )
        assertTrue(suggestion.hasArticleInAnyLanguage)
    }
    
    @Test
    fun searchSuggestion_hasArticleInAnyLanguage_canBeSetToFalse() {
        val suggestion = SearchSuggestion(
            id = "Q123",
            label = "Test",
            description = "Test description", 
            thumbnailUrl = null,
            hasArticleInAnyLanguage = false
        )
        assertFalse(suggestion.hasArticleInAnyLanguage)
    }
}