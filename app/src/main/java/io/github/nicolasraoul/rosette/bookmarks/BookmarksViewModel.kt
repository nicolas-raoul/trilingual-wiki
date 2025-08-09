package io.github.nicolasraoul.rosette.bookmarks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.nicolasraoul.rosette.LanguageManager
import io.github.nicolasraoul.rosette.RetrofitClient
import io.github.nicolasraoul.rosette.WikipediaApiService
import io.github.nicolasraoul.rosette.data.db.BookmarkDao
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class BookmarksViewModel(
    private val bookmarkDao: BookmarkDao,
    private val wikipediaApiService: WikipediaApiService,
    private val languageManager: LanguageManager
) : ViewModel() {

    val bookmarks: StateFlow<List<BookmarkViewData>> = bookmarkDao.getAll()
        .map { bookmarks ->
            if (bookmarks.isEmpty()) {
                return@map emptyList()
            }
            val ids = bookmarks.joinToString("|") { it.wikidataId }
            try {
                val response = wikipediaApiService.getEntityClaims(ids = ids, props = "sitelinks|claims")
                if (response.isSuccessful) {
                    val entities = response.body()?.entities
                    val displayLangs = languageManager.getDisplayLanguages()

                    bookmarks.map { bookmark ->
                        val entity = entities?.get(bookmark.wikidataId)

                        val imageName = (entity?.claims?.get("P18")?.firstOrNull()?.mainsnak?.datavalue?.value as? String)?.replace(" ", "_")
                        val imageUrl = if (imageName != null) "https://commons.wikimedia.org/w/thumb.php?f=$imageName&w=200" else null

                        val titles = mutableMapOf<String, String>()
                        displayLangs.forEach { langCode ->
                            val siteKey = "${langCode}wiki"
                            val title = entity?.sitelinks?.get(siteKey)?.title
                            if (title != null) {
                                titles[langCode] = title
                            }
                        }
                        if (titles.isEmpty()) {
                            val fallbackTitle = entity?.sitelinks?.values?.firstOrNull()?.title ?: bookmark.wikidataId
                            titles["fallback"] = fallbackTitle
                        }

                        BookmarkViewData(
                            wikidataId = bookmark.wikidataId,
                            imageUrl = imageUrl,
                            titles = titles,
                            timestamp = bookmark.timestamp
                        )
                    }
                } else {
                    bookmarks.map { BookmarkViewData(it.wikidataId, null, mapOf("error" to "Could not load titles"), it.timestamp) }
                }
            } catch (e: Exception) {
                bookmarks.map { BookmarkViewData(it.wikidataId, null, mapOf("error" to "Error: ${e.message}"), it.timestamp) }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun delete(bookmark: BookmarkViewData) {
        viewModelScope.launch {
            bookmarkDao.delete(bookmark.wikidataId)
        }
    }
}

class BookmarksViewModelFactory(
    private val bookmarkDao: BookmarkDao,
    private val languageManager: LanguageManager
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(BookmarksViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return BookmarksViewModel(bookmarkDao, RetrofitClient.wikipediaApiService, languageManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
