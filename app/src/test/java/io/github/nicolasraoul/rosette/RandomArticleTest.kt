package io.github.nicolasraoul.rosette

import org.junit.Test
import org.junit.Assert.*

/**
 * Unit tests for random article functionality logic
 */
class RandomArticleTest {

    @Test
    fun testHasAllLanguages_withAllLanguagesPresent() {
        val displayLanguages = arrayOf("en", "fr", "ja")
        val sitelinks = mapOf(
            "enwiki" to "Eiffel Tower",
            "frwiki" to "Tour Eiffel", 
            "jawiki" to "エッフェル塔",
            "eswiki" to "Torre Eiffel" // Extra language
        )
        
        val result = hasAllLanguages(sitelinks, displayLanguages)
        assertTrue("Should return true when all languages are present", result)
    }

    @Test
    fun testHasAllLanguages_withMissingLanguage() {
        val displayLanguages = arrayOf("en", "fr", "ja")
        val sitelinks = mapOf(
            "enwiki" to "Eiffel Tower",
            "frwiki" to "Tour Eiffel"
            // Missing Japanese
        )
        
        val result = hasAllLanguages(sitelinks, displayLanguages)
        assertFalse("Should return false when a language is missing", result)
    }

    @Test
    fun testHasAllLanguages_withEmptySitelinks() {
        val displayLanguages = arrayOf("en", "fr", "ja")
        val sitelinks = emptyMap<String, String>()
        
        val result = hasAllLanguages(sitelinks, displayLanguages)
        assertFalse("Should return false when sitelinks are empty", result)
    }

    @Test
    fun testHasAllLanguagesInLangLinks_withSourceLanguage() {
        val displayLanguages = arrayOf("en", "fr", "ja")
        val sourceLang = "fr"
        val langLinksMap = mapOf(
            "en" to "Eiffel Tower",
            "ja" to "エッフェル塔"
        )
        
        val result = hasAllLanguagesInLangLinks(langLinksMap, sourceLang, "Tour Eiffel", displayLanguages)
        assertTrue("Should return true when source language is French and others are in langlinks", result)
    }

    @Test
    fun testHasAllLanguagesInLangLinks_missingLanguage() {
        val displayLanguages = arrayOf("en", "fr", "ja")
        val sourceLang = "fr"
        val langLinksMap = mapOf(
            "en" to "Eiffel Tower"
            // Missing Japanese
        )
        
        val result = hasAllLanguagesInLangLinks(langLinksMap, sourceLang, "Tour Eiffel", displayLanguages)
        assertFalse("Should return false when a language is missing from langlinks", result)
    }

    @Test
    fun testRandomArticleDataClass() {
        val randomArticle = RandomArticle(id = 12345, title = "Test Article")
        assertEquals("ID should match", 12345, randomArticle.id)
        assertEquals("Title should match", "Test Article", randomArticle.title)
    }

    // Helper functions matching the ones in MainActivity
    private fun hasAllLanguages(sitelinks: Map<String, String>, displayLanguages: Array<String>): Boolean {
        return displayLanguages.all { lang ->
            sitelinks.containsKey("${lang}wiki")
        }
    }

    private fun hasAllLanguagesInLangLinks(
        langLinksMap: Map<String, String>, 
        sourceLang: String, 
        sourceTitle: String, 
        displayLanguages: Array<String>
    ): Boolean {
        return displayLanguages.all { lang ->
            lang == sourceLang || langLinksMap.containsKey(lang)
        }
    }
}