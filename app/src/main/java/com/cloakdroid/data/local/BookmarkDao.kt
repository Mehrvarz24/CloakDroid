package com.cloakdroid.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BookmarkDao {

    @Query(
        """
        SELECT * FROM bookmarks
        WHERE profile_id = :profileId
        ORDER BY created_at DESC
        """
    )
    fun observeFor(profileId: String): Flow<List<BookmarkEntity>>

    @Query(
        """
        SELECT EXISTS(
            SELECT 1 FROM bookmarks
            WHERE profile_id = :profileId AND url = :url LIMIT 1
        )
        """
    )
    suspend fun existsFor(profileId: String, url: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(bookmark: BookmarkEntity)

    @Query("DELETE FROM bookmarks WHERE profile_id = :profileId AND url = :url")
    suspend fun deleteFor(profileId: String, url: String)

    @Query("DELETE FROM bookmarks WHERE profile_id = :profileId")
    suspend fun deleteAllFor(profileId: String)
}
