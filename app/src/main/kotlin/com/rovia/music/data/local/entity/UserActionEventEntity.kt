package com.rovia.music.data.local.entity

import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey

@Entity(
    tableName = "user_action_events",
    indices = [Index(value = ["actionKey"], unique = true), Index(value = ["occurredAtEpochMillis"]), Index(value = ["actionType", "occurredAtEpochMillis"]), Index(value = ["targetType", "targetKey"]), Index(value = ["artworkAssetKeySnapshot"])],
    foreignKeys = [
        ForeignKey(
            entity = ArtworkAssetEntity::class,
            parentColumns = ["assetKey"],
            childColumns = ["artworkAssetKeySnapshot"],
            onDelete = ForeignKey.SET_NULL
        )
    ]
)
data class UserActionEventEntity(
    @PrimaryKey(autoGenerate = true)
    val userActionEventId: Long = 0,
    val actionKey: String,
    val actionType: String,
    val targetType: String,
    val targetKey: String,
    val occurredAtEpochMillis: Long,
    val timeZoneIdAtEvent: String,
    val titleSnapshot: String? = null,
    val subtitleSnapshot: String? = null,
    val artworkAssetKeySnapshot: String? = null,
    val artworkSourceUriSnapshot: String? = null
)
