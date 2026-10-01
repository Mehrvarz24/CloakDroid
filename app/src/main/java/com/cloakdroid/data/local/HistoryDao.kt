package com.cloakdroid.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {

    @Query(
        """
        SELECT * FROM history
        WHERE profile_id = :profileId
        ORDER BY visited_at DESC
        LIMIT :limit
        """
    )
    fun observeFor(profileId: String, limit: Int = 500): Flow<List<HistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: HistoryEntity)

    @Query("DELETE FROM history WHERE profile_id = :profileId")
    suspend fun deleteAllFor(profileId: String)
}
