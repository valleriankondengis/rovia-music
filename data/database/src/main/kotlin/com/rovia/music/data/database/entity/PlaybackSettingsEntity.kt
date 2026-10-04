package com.rovia.music.data.database.entity

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.PrimaryKey

@Entity(
    tableName = "playback_settings",
)
data class PlaybackSettingsEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: Int = SETTINGS_ID,

    @ColumnInfo(name = "repeat_mode")
    val repeatMode: Int,

    @ColumnInfo(name = "shuffle_enabled")
    val shuffleEnabled: Boolean,
) {
    companion object {
        const val SETTINGS_ID = 1
    }
}