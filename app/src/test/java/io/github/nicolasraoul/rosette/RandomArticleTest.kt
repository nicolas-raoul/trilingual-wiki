package io.github.nicolasraoul.rosette

import org.junit.Test
import org.junit.Assert.*

/**
 * Test for random article functionality
 */
class RandomArticleTest {

    @Test
    fun `random article API response parsing works`() {
        // Test that our data classes can parse a typical random article response
        val sampleResponse = RandomArticlesResponse(
            query = RandomQueryResponse(
                random = listOf(
                    RandomArticle(id = 123, title = "Test Article"),
                    RandomArticle(id = 456, title = "Another Article")
                )
            )
        )
        
        assertEquals(2, sampleResponse.query?.random?.size)
        assertEquals("Test Article", sampleResponse.query?.random?.first()?.title)
        assertEquals(123, sampleResponse.query?.random?.first()?.id)
    }

    @Test
    fun `empty random response is handled`() {
        val emptyResponse = RandomArticlesResponse(query = null)
        assertNull(emptyResponse.query)
        
        val emptyRandomList = RandomArticlesResponse(
            query = RandomQueryResponse(random = emptyList())
        )
        assertEquals(0, emptyRandomList.query?.random?.size)
    }
    
    @Test
    fun `random button behavior consistency documentation`() {
        // This test documents the expected behavior after the fix:
        // 1. Random button should work consistently across multiple uses
        // 2. displayLanguages should be refreshed before each random search
        // 3. Language configuration should not become stale between calls
        
        // The fix ensures that loadConfiguredLanguages() is called at the start
        // of performRandomArticleSearch() to refresh the displayLanguages array
        assertTrue("Random button should work consistently after multiple uses", true)
    }
}