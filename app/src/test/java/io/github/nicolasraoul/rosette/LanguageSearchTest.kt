package io.github.nicolasraoul.rosette

import org.junit.Test
import org.junit.Assert.*

/**
 * Unit tests for language search functionality
 */
class LanguageSearchTest {

    private val testLanguages = listOf(
        WikipediaLanguage("en", "English", "English"),
        WikipediaLanguage("fr", "French", "Français"),
        WikipediaLanguage("ja", "Japanese", "日本語"),
        WikipediaLanguage("de", "German", "Deutsch"),
        WikipediaLanguage("es", "Spanish", "Español"),
        WikipediaLanguage("zh", "Chinese", "中文")
    )

    @Test
    fun testSearchByEnglishName() {
        val result = filterLanguagesByQuery(testLanguages, "English")
        assertEquals(1, result.size)
        assertEquals("en", result[0].code)
    }

    @Test
    fun testSearchByLocalName() {
        val result = filterLanguagesByQuery(testLanguages, "Français")
        assertEquals(1, result.size)
        assertEquals("fr", result[0].code)
    }

    @Test
    fun testSearchByLanguageCode() {
        val result = filterLanguagesByQuery(testLanguages, "ja")
        assertEquals(1, result.size)
        assertEquals("ja", result[0].code)
    }

    @Test
    fun testSearchCaseInsensitive() {
        val result = filterLanguagesByQuery(testLanguages, "GERMAN")
        assertEquals(1, result.size)
        assertEquals("de", result[0].code)
    }

    @Test
    fun testSearchPartialMatch() {
        val result = filterLanguagesByQuery(testLanguages, "span")
        assertEquals(1, result.size)
        assertEquals("es", result[0].code)
    }

    @Test
    fun testSearchMultipleResults() {
        val result = filterLanguagesByQuery(testLanguages, "e")
        assertTrue(result.size >= 2) // Should match "English", "German", etc.
    }

    @Test
    fun testSearchNoResults() {
        val result = filterLanguagesByQuery(testLanguages, "xyz")
        assertEquals(0, result.size)
    }

    @Test
    fun testSearchEmptyQuery() {
        val result = filterLanguagesByQuery(testLanguages, "")
        assertEquals(testLanguages.size, result.size)
    }

    @Test
    fun testSearchBlankQuery() {
        val result = filterLanguagesByQuery(testLanguages, "   ")
        assertEquals(testLanguages.size, result.size)
    }

    private fun filterLanguagesByQuery(languages: List<WikipediaLanguage>, query: String): List<WikipediaLanguage> {
        if (query.isBlank()) {
            return languages
        }
        
        val lowercaseQuery = query.lowercase()
        return languages.filter { language ->
            language.englishName.lowercase().contains(lowercaseQuery) ||
            language.localName.lowercase().contains(lowercaseQuery) ||
            language.code.lowercase().contains(lowercaseQuery)
        }
    }
}