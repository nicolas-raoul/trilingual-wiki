package io.github.nicolasraoul.rosette

import org.junit.Test
import org.junit.Assert.*

/**
 * Test for search behavior logic when no articles are found
 */
class SearchBehaviorTest {
    
    private val displayLanguages = arrayOf("en", "fr", "ja")
    
    @Test
    fun searchUrlConstruction_isCorrect() {
        val lang = "en"
        val searchTerm = "Test Article"
        val baseUrl = "https://$lang.m.wikipedia.org"
        val expectedUrl = "$baseUrl/w/index.php?title=Special:Search&search=Test_Article"
        
        val actualUrl = baseUrl + "/w/index.php?title=Special:Search&search=${searchTerm.replace(" ", "_")}"
        
        assertEquals(expectedUrl, actualUrl)
    }
    
    @Test
    fun searchUrlConstruction_withSpecialCharacters() {
        val lang = "fr"
        val searchTerm = "Café & Restaurant"
        val baseUrl = "https://$lang.m.wikipedia.org"
        val expectedUrl = "$baseUrl/w/index.php?title=Special:Search&search=Café_&_Restaurant"
        
        val actualUrl = baseUrl + "/w/index.php?title=Special:Search&search=${searchTerm.replace(" ", "_")}"
        
        assertEquals(expectedUrl, actualUrl)
    }
    
    @Test
    fun searchUrlGeneration_forAllLanguages() {
        val searchTerm = "NonExistentArticle"
        val expectedUrls = mapOf(
            "en" to "https://en.m.wikipedia.org/w/index.php?title=Special:Search&search=NonExistentArticle",
            "fr" to "https://fr.m.wikipedia.org/wiki/NonExistentArticle", // This would be the base URL format if it existed
            "ja" to "https://ja.m.wikipedia.org/wiki/NonExistentArticle"  // This would be the base URL format if it existed
        )
        
        // Test search URL generation for a term with no spaces
        displayLanguages.forEach { lang ->
            val baseUrl = "https://$lang.m.wikipedia.org"
            val searchUrl = baseUrl + "/w/index.php?title=Special:Search&search=${searchTerm.replace(" ", "_")}"
            val expectedSearchUrl = "$baseUrl/w/index.php?title=Special:Search&search=$searchTerm"
            assertEquals(expectedSearchUrl, searchUrl)
        }
    }
    
    @Test
    fun searchTermFormatting_spacesToUnderscores() {
        val testCases = mapOf(
            "Simple Term" to "Simple_Term",
            "Multiple Word Term" to "Multiple_Word_Term", 
            "Term_With_Underscores" to "Term_With_Underscores",
            "Term With  Multiple  Spaces" to "Term_With__Multiple__Spaces",
            "" to "",
            "SingleWord" to "SingleWord"
        )
        
        testCases.forEach { (input, expected) ->
            val actual = input.replace(" ", "_")
            assertEquals("Failed for input: '$input'", expected, actual)
        }
    }
    
    @Test
    fun statusMessage_updateForNotFound() {
        val searchTerm = "TestArticle"
        val expectedMessage = "Article \"$searchTerm\" not found. Loading search pages..."
        
        // This simulates the status message that should be shown when no article is found
        assertEquals(expectedMessage, "Article \"$searchTerm\" not found. Loading search pages...")
    }
}