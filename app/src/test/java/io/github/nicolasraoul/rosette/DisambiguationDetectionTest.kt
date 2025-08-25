package io.github.nicolasraoul.rosette

import org.junit.Test
import org.junit.Assert.*

/**
 * Test for disambiguation page detection functionality.
 * This tests the logic that will be used in MainActivity.isDisambiguationPage()
 */
class DisambiguationDetectionTest {

    // Helper function that mirrors the logic in MainActivity.isDisambiguationPage()
    private fun isDisambiguationPage(entity: EntityClaim): Boolean {
        // Check if the entity has P31 (instance of) claim with Q4167410 (disambiguation page)
        val instanceOfClaims = entity.claims?.get("P31") ?: return false
        
        return instanceOfClaims.any { claim ->
            val value = claim.mainsnak.datavalue?.value
            when (value) {
                is String -> value == "Q4167410"
                is Map<*, *> -> {
                    // Handle case where value is a complex object with id
                    @Suppress("UNCHECKED_CAST")
                    val valueMap = value as? Map<String, Any>
                    valueMap?.get("id") == "Q4167410"
                }
                else -> false
            }
        }
    }

    @Test
    fun `disambiguation page with string value is detected`() {
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
        
        assertTrue("Should detect disambiguation page with string value", 
                  isDisambiguationPage(disambiguationEntity))
    }

    @Test
    fun `disambiguation page with map value is detected`() {
        val disambiguationEntityWithMap = EntityClaim(
            id = "Q123",
            claims = mapOf(
                "P31" to listOf(
                    Claim(
                        mainsnak = MainSnak(
                            datavalue = DataValue(
                                value = mapOf("id" to "Q4167410", "type" to "wikibase-entityid")
                            )
                        )
                    )
                )
            ),
            sitelinks = null,
            labels = null
        )
        
        assertTrue("Should detect disambiguation page with map value",
                  isDisambiguationPage(disambiguationEntityWithMap))
    }

    @Test
    fun `regular article is not detected as disambiguation`() {
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
            sitelinks = null,
            labels = null
        )
        
        assertFalse("Should not detect regular article as disambiguation", 
                   isDisambiguationPage(regularEntity))
    }

    @Test
    fun `entity without P31 claims is not detected as disambiguation`() {
        val entityWithoutClaims = EntityClaim(
            id = "Q789",
            claims = null,
            sitelinks = null,
            labels = null
        )
        
        assertFalse("Should not detect entity without claims as disambiguation", 
                   isDisambiguationPage(entityWithoutClaims))

        val entityWithoutP31 = EntityClaim(
            id = "Q789",
            claims = mapOf(
                "P17" to listOf(
                    Claim(
                        mainsnak = MainSnak(
                            datavalue = DataValue(value = "Q30")
                        )
                    )
                )
            ),
            sitelinks = null,
            labels = null
        )
        
        assertFalse("Should not detect entity without P31 claims as disambiguation", 
                   isDisambiguationPage(entityWithoutP31))
    }

    @Test
    fun `entity with multiple P31 claims containing disambiguation is detected`() {
        val entityWithMultipleP31 = EntityClaim(
            id = "Q999",
            claims = mapOf(
                "P31" to listOf(
                    Claim(
                        mainsnak = MainSnak(
                            datavalue = DataValue(value = "Q5") // human
                        )
                    ),
                    Claim(
                        mainsnak = MainSnak(
                            datavalue = DataValue(value = "Q4167410") // disambiguation page
                        )
                    )
                )
            ),
            sitelinks = null,
            labels = null
        )
        
        assertTrue("Should detect disambiguation when it's one of multiple P31 values", 
                  isDisambiguationPage(entityWithMultipleP31))
    }

    @Test
    fun `entity with null datavalue is not detected as disambiguation`() {
        val entityWithNullValue = EntityClaim(
            id = "Q888",
            claims = mapOf(
                "P31" to listOf(
                    Claim(
                        mainsnak = MainSnak(
                            datavalue = DataValue(value = null)
                        )
                    )
                )
            ),
            sitelinks = null,
            labels = null
        )
        
        assertFalse("Should not detect entity with null value as disambiguation", 
                   isDisambiguationPage(entityWithNullValue))
    }

    @Test
    fun `entity with non-string non-map value is not detected as disambiguation`() {
        val entityWithIntValue = EntityClaim(
            id = "Q777",
            claims = mapOf(
                "P31" to listOf(
                    Claim(
                        mainsnak = MainSnak(
                            datavalue = DataValue(value = 123) // integer value
                        )
                    )
                )
            ),
            sitelinks = null,
            labels = null
        )
        
        assertFalse("Should not detect entity with integer value as disambiguation", 
                   isDisambiguationPage(entityWithIntValue))
    }
}