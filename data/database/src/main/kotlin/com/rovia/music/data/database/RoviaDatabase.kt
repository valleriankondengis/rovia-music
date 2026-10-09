package com.rovia.music.data.database

import android.content.Context
import androidx.room3.Database
import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.sqlite.driver.AndroidSQLiteDriver
import com.rovia.music.data.database.dao.ExcludedFolderDao
import com.rovia.music.data.database.dao.MediaStoreSyncStateDao
import com.rovia.music.data.database.dao.MusicTrackDao
import com.rovia.music.data.database.dao.PlaybackSettingsDao
import com.rovia.music.data.database.dao.RecentPlayDao
import com.rovia.music.data.database.entity.ExcludedFolderEntity
import com.rovia.music.data.database.entity.MediaStoreSyncStateEntity
import com.rovia.music.data.database.entity.MusicTrackEntity
import com.rovia.music.data.database.entity.PlaybackSettingsEntity
import com.rovia.music.data.database.entity.RecentPlayEntity

private const val DATABASE_NAME = "rovia.db"
private const val DATABASE_VERSION = 1

@Database(
    entities = [
        RecentPlayEntity::class,
        ExcludedFolderEntity::class,
        PlaybackSettingsEntity::class,
        MusicTrackEntity::class,
        MediaStoreSyncStateEntity::class,
    ],
    version = DATABASE_VERSION,
    exportSchema = true,
)
abstract class RoviaDatabase : RoomDatabase() {

    abstract fun recentPlayDao(): RecentPlayDao

    abstract fun excludedFolderDao(): ExcludedFolderDao

    abstract fun playbackSettingsDao(): PlaybackSettingsDao

    abstract fun musicTrackDao(): MusicTrackDao

    abstract fun mediaStoreSyncStateDao(): MediaStoreSyncStateDao

    companion object {

        @Volatile
        private var INSTANCE: RoviaDatabase? = null

        fun getInstance(
            context: Context,
        ): RoviaDatabase {
            return INSTANCE
                ?: synchronized(this) {
                    INSTANCE
                        ?: Room
                            .databaseBuilder<RoviaDatabase>(
                                context.applicationContext,
                                DATABASE_NAME,
                            )
                            .setDriver(
                                AndroidSQLiteDriver(),
                            )
                            .build()
                            .also { database ->
                                INSTANCE = database
                            }
                }
        }
    }
}