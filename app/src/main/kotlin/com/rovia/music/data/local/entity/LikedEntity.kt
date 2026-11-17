package com.rovia.music.data.local.entity

import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey

/**
 * A liked artist or release. Unlike liked tracks, these point at catalog
 * identities (an artist name, a release key) and keep display snapshots so
 * the library can render them without resolving the catalog entry first.
 */
@Entity(
    tableName = "liked_entities",
    indices = [
        Index(value = ["entityType", "entityKey"], unique = true),
        Index(value = ["likedAtEpochMillis"])
    ]
)
data class LikedEntity(
    @PrimaryKey(autoGenerate = true)
    val likedEntityId: Long = 0,
    val entityType: String,
    val entityKey: String,
    val titleSnapshot: String,
    val subtitleSnapshot: String? = null,
    val artworkSourceUriSnapshot: String? = null,
    val likedAtEpochMillis: Long
)
