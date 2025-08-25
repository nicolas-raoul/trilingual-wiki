package io.github.nicolasraoul.rosette

import org.junit.Test
import org.junit.Assert.*

/**
 * Test for random article functionality and disambiguation detection
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
    fun `disambiguation entity structure can be created`() {
        // Test that we can create the data structures needed for disambiguation detection
        val disambiguationEntity = EntityClaim(
            id = "Q123",
            claims = mapOf(
                "P31" to listOf(
                    Claim(
                        mainsnak = MainSnak(
                            datavalue = DataValue(value = "Q4167410")
                        )
                    )
                )
            ),
            sitelinks = null,
            labels = null
        )
        
        assertNotNull("Entity should be created", disambiguationEntity)
        assertNotNull("Claims should be present", disambiguationEntity.claims)
        assertTrue("P31 claims should be present", disambiguationEntity.claims?.containsKey("P31") == true)
        
        val p31Claims = disambiguationEntity.claims?.get("P31")
        assertNotNull("P31 claims list should not be null", p31Claims)
        assertEquals("Should have one P31 claim", 1, p31Claims?.size)
        
        val firstClaim = p31Claims?.firstOrNull()
        assertNotNull("First claim should not be null", firstClaim)
        assertEquals("Value should be Q4167410", "Q4167410", firstClaim?.mainsnak?.datavalue?.value)
    }

    @Test
    fun `regular article entity structure can be created`() {
        // Test entity structure for a regular (non-disambiguation) article
        val regularEntity = EntityClaim(
            id = "Q456", 
            claims = mapOf(
                "P31" to listOf(
                    Claim(
                        mainsnak = MainSnak(
                            datavalue = DataValue(value = "Q5") // human
                        )
                    )
                )
            ),
            sitelinks = mapOf(
                "enwiki" to SiteLink(site = "enwiki", title = "Regular Article")
            ),
            labels = mapOf(
                "en" to Label(language = "en", value = "Regular Article")
            )
        )
        
        assertNotNull("Regular entity should be created", regularEntity)
        assertEquals("Should have correct ID", "Q456", regularEntity.id)
        assertNotNull("Should have sitelinks", regularEntity.sitelinks)
        assertNotNull("Should have labels", regularEntity.labels)
        
        val p31Value = regularEntity.claims?.get("P31")?.firstOrNull()?.mainsnak?.datavalue?.value
        assertEquals("P31 value should be Q5", "Q5", p31Value)
    }
}