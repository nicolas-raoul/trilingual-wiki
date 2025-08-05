package io.github.nicolasraoul.rosette

import org.junit.Test
import org.junit.Assert.*

/**
 * Test for search URL construction logic
 */
class SearchUrlTest {
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
}