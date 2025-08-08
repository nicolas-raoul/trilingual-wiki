package io.github.nicolasraoul.rosette.bookmarks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.nicolasraoul.rosette.RetrofitClient
import io.github.nicolasraoul.rosette.WikipediaApiService
import io.github.nicolasraoul.rosette.data.db.BookmarkDao
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class BookmarksViewModel(
    bookmarkDao: BookmarkDao,
    private val wikipediaApiService: WikipediaApiService
) : ViewModel() {

    val bookmarks: StateFlow<List<BookmarkViewData>> = bookmarkDao.getAll()
        .map { bookmarks ->
            if (bookmarks.isEmpty()) {
                return@map emptyList<BookmarkViewData>()
            }
            val ids = bookmarks.joinToString("|")
            try {
                val response = wikipediaApiService.getEntityClaims(ids = ids, props = "labels")
                if (response.isSuccessful) {
                    val entities = response.body()?.entities
                    bookmarks.map { bookmark ->
                        val entity = entities?.get(bookmark.wikidataId)
                        val label = entity?.labels?.get("en")?.value // Default to English label
                            ?: bookmark.wikidataId // Fallback to ID
                        BookmarkViewData(
                            wikidataId = bookmark.wikidataId,
                            title = label,
                            timestamp = bookmark.timestamp
                        )
                    }
                } else {
                    bookmarks.map {
                        BookmarkViewData(it.wikidataId, "Could not load title", it.timestamp)
                    }
                }
            } catch (e: Exception) {
                bookmarks.map {
                    BookmarkViewData(it.wikidataId, "Error loading title", it.timestamp)
                }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}

class BookmarksViewModelFactory(
    private val bookmarkDao: BookmarkDao
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(BookmarksViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return BookmarksViewModel(bookmarkDao, RetrofitClient.wikipediaApiService) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
