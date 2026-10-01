package com.cloakdroid.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [ProfileEntity::class, ProxyTestResultEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao

    companion object {
        const val NAME = "cloakdroid.db"
    }
}
