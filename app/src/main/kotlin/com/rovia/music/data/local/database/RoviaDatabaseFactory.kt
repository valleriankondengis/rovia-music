package com.rovia.music.data.local.database

import android.content.Context
import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import kotlinx.coroutines.Dispatchers

object RoviaDatabaseFactory {
    const val DATABASE_NAME = "rovia.db"

    @Volatile
    private var instance: RoviaDatabase? = null

    fun create(context: Context): RoviaDatabase {
        instance?.let { return it }

        return synchronized(this) {
            instance ?: build(context.applicationContext).also {
                instance = it
            }
        }
    }

    private fun build(context: Context): RoviaDatabase {
        val databaseFile = context.getDatabasePath(DATABASE_NAME)

        return Room.databaseBuilder<RoviaDatabase>(
            context = context,
            name = databaseFile.absolutePath
        )
            .setDriver(AndroidSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()
    }
}