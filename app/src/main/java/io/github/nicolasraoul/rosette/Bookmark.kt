package io.github.nicolasraoul.rosette

/**
 * Data class representing a bookmarked article
 */
data class Bookmark(
    val id: String,  // Wikidata ID if available, otherwise generated unique ID
    val title: String,  // Article title
    val language: String,  // Language code of the source article (e.g., "en", "fr", "ja")
    val wikidataId: String? = null,  // Wikidata ID if available for cross-language lookup
    val timestamp: Long = System.currentTimeMillis()  // When the bookmark was created
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as Bookmark
        return id == other.id
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }
}