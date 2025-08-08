package io.github.nicolasraoul.rosette.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "bookmarks")
data class Bookmark(
    @PrimaryKey
    val wikidataId: String,
    val timestamp: Long
)

@Dao
interface BookmarkDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(bookmark: Bookmark)

    @Query("DELETE FROM bookmarks WHERE wikidataId = :wikidataId")
    suspend fun delete(wikidataId: String)

    @Query("SELECT * FROM bookmarks ORDER BY timestamp DESC")
    fun getAll(): Flow<List<Bookmark>>
}
