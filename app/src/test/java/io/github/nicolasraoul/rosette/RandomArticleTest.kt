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
    fun `disambiguation page data structure works`() {
        // Test that our EntityClaim structure can represent disambiguation page data
        val disambiguationClaim = Claim(
            mainsnak = MainSnak(
                datavalue = DataValue(
                    value = mapOf("id" to "Q4167410") // Wikimedia disambiguation page
                )
            )
        )
        
        val entity = EntityClaim(
            id = "Q123",
            claims = mapOf("P31" to listOf(disambiguationClaim)),
            sitelinks = null,
            labels = null
        )
        
        // Verify the structure can hold disambiguation data
        assertNotNull(entity.claims)
        assertTrue(entity.claims!!.containsKey("P31"))
        assertEquals(1, entity.claims!!["P31"]?.size)
        
        val claim = entity.claims!!["P31"]?.first()
        assertNotNull(claim?.mainsnak?.datavalue?.value)
        
        // This verifies our data structure can represent the disambiguation check
        val dataValue = claim?.mainsnak?.datavalue?.value as? Map<*, *>
        assertEquals("Q4167410", dataValue?.get("id"))
    }

    @Test
    fun `isDisambiguationPage detects disambiguation with Map value format`() {
        // Test disambiguation detection with Map format (id field)
        val disambiguationClaim = Claim(
            mainsnak = MainSnak(
                datavalue = DataValue(
                    value = mapOf("id" to "Q4167410")
                )
            )
        )
        
        val entity = EntityClaim(
            id = "Q254638", // Wonderland
            claims = mapOf("P31" to listOf(disambiguationClaim)),
            sitelinks = null,
            labels = null
        )
        
        // This should be detected as a disambiguation page
        // Note: We can't directly test MainActivity.isDisambiguationPage() because it's private
        // So we test the logic here
        val instanceOfClaims = entity.claims["P31"]!!
        val isDisambiguation = instanceOfClaims.any { claim ->
            val dataValue = claim.mainsnak.datavalue?.value
            when (dataValue) {
                is Map<*, *> -> (dataValue["id"] as? String) == "Q4167410"
                is String -> dataValue == "Q4167410"
                else -> false
            }
        }
        
        assertTrue("Should detect disambiguation page with Map format", isDisambiguation)
    }

    @Test
    fun `isDisambiguationPage detects disambiguation with String value format`() {
        // Test disambiguation detection with direct String format
        val disambiguationClaim = Claim(
            mainsnak = MainSnak(
                datavalue = DataValue(
                    value = "Q4167410"
                )
            )
        )
        
        val entity = EntityClaim(
            id = "Q254638", // Wonderland
            claims = mapOf("P31" to listOf(disambiguationClaim)),
            sitelinks = null,
            labels = null
        )
        
        val instanceOfClaims = entity.claims["P31"]!!
        val isDisambiguation = instanceOfClaims.any { claim ->
            val dataValue = claim.mainsnak.datavalue?.value
            when (dataValue) {
                is Map<*, *> -> (dataValue["id"] as? String) == "Q4167410"
                is String -> dataValue == "Q4167410"
                else -> false
            }
        }
        
        assertTrue("Should detect disambiguation page with String format", isDisambiguation)
    }

    @Test
    fun `isDisambiguationPage handles null and missing data gracefully`() {
        // Test with null entity
        var entity: EntityClaim? = null
        var isDisambiguation = entity?.claims != null && 
            entity.claims["P31"]?.any { claim ->
                val dataValue = claim.mainsnak.datavalue?.value
                when (dataValue) {
                    is Map<*, *> -> (dataValue["id"] as? String) == "Q4167410"
                    is String -> dataValue == "Q4167410"
                    else -> false
                }
            } == true
        assertFalse("Should handle null entity", isDisambiguation)
        
        // Test with null claims
        entity = EntityClaim(id = "Q123", claims = null, sitelinks = null, labels = null)
        isDisambiguation = entity.claims != null && 
            entity.claims["P31"]?.any { claim ->
                val dataValue = claim.mainsnak.datavalue?.value
                when (dataValue) {
                    is Map<*, *> -> (dataValue["id"] as? String) == "Q4167410"
                    is String -> dataValue == "Q4167410"
                    else -> false
                }
            } == true
        assertFalse("Should handle null claims", isDisambiguation)
        
        // Test with no P31 claims
        entity = EntityClaim(id = "Q123", claims = mapOf("P279" to emptyList()), sitelinks = null, labels = null)
        isDisambiguation = entity.claims != null && 
            entity.claims["P31"]?.any { claim ->
                val dataValue = claim.mainsnak.datavalue?.value
                when (dataValue) {
                    is Map<*, *> -> (dataValue["id"] as? String) == "Q4167410"
                    is String -> dataValue == "Q4167410"
                    else -> false
                }
            } == true
        assertFalse("Should handle missing P31 claims", isDisambiguation)
    }

    @Test
    fun `isDisambiguationPage rejects non-disambiguation pages`() {
        // Test with a regular article (not disambiguation)
        val regularClaim = Claim(
            mainsnak = MainSnak(
                datavalue = DataValue(
                    value = mapOf("id" to "Q5") // Human
                )
            )
        )
        
        val entity = EntityClaim(
            id = "Q123",
            claims = mapOf("P31" to listOf(regularClaim)),
            sitelinks = null,
            labels = null
        )
        
        val instanceOfClaims = entity.claims["P31"]!!
        val isDisambiguation = instanceOfClaims.any { claim ->
            val dataValue = claim.mainsnak.datavalue?.value
            when (dataValue) {
                is Map<*, *> -> (dataValue["id"] as? String) == "Q4167410"
                is String -> dataValue == "Q4167410"
                else -> false
            }
        }
        
        assertFalse("Should not detect regular page as disambiguation", isDisambiguation)
    }

    @Test
    fun `isDisambiguationPage handles potential URI format values`() {
        // Test if API might return full URIs instead of just IDs
        val disambiguationClaimWithUri = Claim(
            mainsnak = MainSnak(
                datavalue = DataValue(
                    value = mapOf("id" to "http://www.wikidata.org/entity/Q4167410")
                )
            )
        )
        
        val entity = EntityClaim(
            id = "Q254638",
            claims = mapOf("P31" to listOf(disambiguationClaimWithUri)),
            sitelinks = null,
            labels = null
        )
        
        val instanceOfClaims = entity.claims["P31"]!!
        
        // Test current logic (should fail with URI format)
        val currentLogicResult = instanceOfClaims.any { claim ->
            val dataValue = claim.mainsnak.datavalue?.value
            when (dataValue) {
                is Map<*, *> -> (dataValue["id"] as? String) == "Q4167410"
                is String -> dataValue == "Q4167410"
                else -> false
            }
        }
        
        // Test enhanced logic (should pass with URI format)
        val enhancedLogicResult = instanceOfClaims.any { claim ->
            val dataValue = claim.mainsnak.datavalue?.value
            when (dataValue) {
                is Map<*, *> -> {
                    val id = dataValue["id"] as? String
                    id == "Q4167410" || id?.endsWith("/Q4167410") == true
                }
                is String -> dataValue == "Q4167410" || dataValue.endsWith("/Q4167410")
                else -> false
            }
        }
        
        assertFalse("Current logic should fail with URI format", currentLogicResult)
        assertTrue("Enhanced logic should handle URI format", enhancedLogicResult)
    }

    @Test
    fun `disambiguation detection works with various URI formats`() {
        // Test different possible URI formats that might be returned by Wikidata API
        val testCases = listOf(
            "Q4167410", // Simple ID
            "http://www.wikidata.org/entity/Q4167410", // Full HTTP URI  
            "https://www.wikidata.org/entity/Q4167410", // Full HTTPS URI
            "wikidata:Q4167410", // Prefixed format
            "/entity/Q4167410" // Relative path
        )
        
        testCases.forEach { value ->
            val claim = Claim(
                mainsnak = MainSnak(
                    datavalue = DataValue(
                        value = mapOf("id" to value)
                    )
                )
            )
            
            val entity = EntityClaim(
                id = "Q254638",
                claims = mapOf("P31" to listOf(claim)),
                sitelinks = null,
                labels = null
            )
            
            // Test the enhanced logic
            val instanceOfClaims = entity.claims["P31"]!!
            val isDetected = instanceOfClaims.any { claim ->
                val dataValue = claim.mainsnak.datavalue?.value
                when (dataValue) {
                    is Map<*, *> -> {
                        val id = dataValue["id"] as? String
                        id == "Q4167410" || id?.endsWith("/Q4167410") == true
                    }
                    is String -> dataValue == "Q4167410" || dataValue.endsWith("/Q4167410")
                    else -> false
                }
            }
            
            assertTrue("Should detect disambiguation with value: $value", isDetected)
        }
    }
}