package com.cloakdroid.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {

    @Query("SELECT * FROM profiles ORDER BY created_at DESC")
    fun observeAll(): Flow<List<ProfileEntity>>

    @Query("SELECT * FROM profiles WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): ProfileEntity?

    @Upsert
    suspend fun upsert(profile: ProfileEntity)

    @Delete
    suspend fun delete(profile: ProfileEntity)

    @Query("DELETE FROM profiles WHERE id = :profileId")
    suspend fun deleteById(profileId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTestResult(result: ProxyTestResultEntity)

    @Query("DELETE FROM proxy_test_results WHERE profile_id = :profileId")
    suspend fun deleteTestResultsFor(profileId: String)

    @Transaction
    suspend fun deleteProfileAndResults(profileId: String) {
        deleteTestResultsFor(profileId)
        deleteById(profileId)
    }

    @Query(
        """
        SELECT * FROM proxy_test_results
        WHERE profile_id = :profileId
        ORDER BY tested_at DESC
        LIMIT 1
        """
    )
    fun latestResultFor(profileId: String): Flow<ProxyTestResultEntity?>
}
