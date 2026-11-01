package com.rovia.music.data.local.entity

import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index

@Entity(
    tableName = "track_artist_cross_ref",
    primaryKeys = ["trackId", "artistId"],
    indices = [Index(value = ["artistId", "trackId"])],
    foreignKeys = [
        ForeignKey(
            entity = TrackEntity::class,
            parentColumns = ["trackId"],
            childColumns = ["trackId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ArtistEntity::class,
            parentColumns = ["artistId"],
            childColumns = ["artistId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class TrackArtistCrossRef(
    val trackId: Long,
    val artistId: Long,
    val creditOrder: Int,
    val creditRole: String = "CREDITED"
)
