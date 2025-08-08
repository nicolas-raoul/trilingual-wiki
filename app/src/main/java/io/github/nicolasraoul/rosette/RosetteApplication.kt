package io.github.nicolasraoul.rosette

import android.app.Application
import io.github.nicolasraoul.rosette.data.db.BookmarkDatabase

class RosetteApplication : Application() {
    val database: BookmarkDatabase by lazy { BookmarkDatabase.getDatabase(this) }
}
