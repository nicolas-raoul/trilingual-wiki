package io.github.nicolasraoul.rosette

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.CancellationException

class LanguageManager(private val context: Context) {
    private val preferences: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val wikipediaApiService = RetrofitClient.wikipediaApiService

    companion object {
        private const val TAG = "LanguageManager"
        private const val PREFS_NAME = "language_preferences"
        private const val KEY_DISPLAY_LANGUAGES = "display_languages"
        private const val KEY_SEARCH_PRIORITY_LANGUAGES = "search_priority_languages"
        private const val KEY_LAST_WIKIDATA_ID = "last_wikidata_id"

        // Default languages - same as original hardcoded values
        private val DEFAULT_DISPLAY_LANGUAGES = arrayOf("en", "fr", "ja")
        private val DEFAULT_SEARCH_PRIORITY_LANGUAGES = arrayOf("fr", "ja", "en")
    }

    /**
     * Get the current display languages (for the 3 WebViews)
     */
    fun getDisplayLanguages(): Array<String> {
        val savedLanguages = preferences.getString(KEY_DISPLAY_LANGUAGES, null)
        return if (savedLanguages != null) {
            savedLanguages.split(",").toTypedArray()
        } else {
            DEFAULT_DISPLAY_LANGUAGES
        }
    }

    /**
     * Get the current search priority languages (order to search for articles)
     */
    fun getSearchPriorityLanguages(): Array<String> {
        val savedLanguages = preferences.getString(KEY_SEARCH_PRIORITY_LANGUAGES, null)
        return if (savedLanguages != null) {
            savedLanguages.split(",").toTypedArray()
        } else {
            DEFAULT_SEARCH_PRIORITY_LANGUAGES
        }
    }

    /**
     * Save selected languages
     */
    fun saveLanguages(displayLanguages: Array<String>) {
        // For simplicity, use the same order for both display and search priority
        // This can be enhanced later if users need different search order
        preferences.edit()
            .putString(KEY_DISPLAY_LANGUAGES, displayLanguages.joinToString(","))
            .putString(KEY_SEARCH_PRIORITY_LANGUAGES, displayLanguages.joinToString(","))
            .apply()
        Log.d(TAG, "Saved languages: ${displayLanguages.joinToString(", ")}")
    }

    /**
     * Get list of available Wikipedia languages from the API
     */
    suspend fun getAvailableWikipediaLanguages(): List<WikipediaLanguage> {
        try {
            val response = wikipediaApiService.getWikipediaLanguages()
            Log.d(TAG, "API response received: ${response.code()}")
            
            if (response.isSuccessful) {
                val body = response.body()
                Log.d(TAG, "Response body: ${body != null}")
                
                val languages = mutableListOf<WikipediaLanguage>()
                
                body?.sitematrix?.forEach { (key, value) ->
                    // Skip special entries like "count" and "specials"
                    if (key != "count" && key != "specials" && value is Map<*, *>) {
                        try {
                            @Suppress("UNCHECKED_CAST")
                            val site = value as Map<String, Any?>
                            val code = site["code"] as? String
                            val name = site["name"] as? String  
                            val localname = site["localname"] as? String
                            
                            if (code != null && name != null) {
                                languages.add(WikipediaLanguage(code, name, localname ?: name))
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to parse language entry for key $key: ${e.message}")
                        }
                    }
                }
                
                Log.d(TAG, "Parsed ${languages.size} languages from sitematrix")
                // Sort by English name for better UX (case-insensitive)
                return languages.sortedBy { it.englishName.lowercase() }
            } else {
                Log.e(TAG, "API request failed with code: ${response.code()}, message: ${response.message()}")
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e(TAG, "Failed to fetch Wikipedia languages: ${e.javaClass.simpleName}: ${e.message}", e)
        }
        
        // Return empty list if API fails
        Log.w(TAG, "Returning empty language list due to API failure")
        return emptyList()
    }

    /**
     * Check if user has configured custom languages
     */
    fun hasCustomLanguages(): Boolean {
        return preferences.contains(KEY_DISPLAY_LANGUAGES)
    }

    /**
     * Save the Wikidata ID of the last visited article.
     */
    fun saveLastWikidataId(wikidataId: String) {
        preferences.edit()
            .putString(KEY_LAST_WIKIDATA_ID, wikidataId)
            .apply()
        Log.d(TAG, "Saved last Wikidata ID: $wikidataId")
    }

    /**
     * Get the Wikidata ID of the last visited article.
     */
    fun getLastWikidataId(): String? {
        return preferences.getString(KEY_LAST_WIKIDATA_ID, null)
    }

    /**
     * Fetches the number of articles for a single Wikipedia language edition.
     *
     * @param languageCode The language code (e.g., "en", "ja", "fr").
     * @return The article count, or null if the request fails or the code is invalid.
     */
    suspend fun getWikipediaArticleCount(languageCode: String): Int? {
        val storedCount = preferences.getInt("article_count_$languageCode", -1)
        if (storedCount != -1) {
            return storedCount
        }

        return try {
            val baseUrlForApi = "https://$languageCode.wikipedia.org/w/api.php"
            val response = wikipediaApiService.getWikipediaArticleCount(baseUrl = baseUrlForApi)

            if (response.isSuccessful) {
                val articleCount = response.body()?.query?.statistics?.articles
                if (articleCount != null) {
                    preferences.edit().putInt("article_count_$languageCode", articleCount).apply()
                }
                articleCount
            } else {
                null
            }
        } catch (e: Exception) {
            // Handles network errors or invalid language codes (e.g., "xx")
            Log.e(TAG, "Error fetching count for '$languageCode': ${e.message}")
            null
        }
    }
}

/**
 * Data class representing a Wikipedia language
 */
data class WikipediaLanguage(
    val code: String,           // Language code (e.g., "en", "fr", "ja")
    val englishName: String,    // English name (e.g., "English", "French", "Japanese")
    val localName: String       // Local name (e.g., "English", "Français", "日本語")
) {
    override fun toString(): String {
        return if (localName != englishName) {
            "$englishName ($localName)"
        } else {
            englishName
        }
    }
}