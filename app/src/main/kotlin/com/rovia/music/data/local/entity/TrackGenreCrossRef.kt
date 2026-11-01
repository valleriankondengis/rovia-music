package com.rovia.music.data.local.entity

import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index

@Entity(
    tableName = "track_genre_cross_ref",
    primaryKeys = ["trackId", "genreId"],
    indices = [Index(value = ["genreId", "trackId"])],
    foreignKeys = [
        ForeignKey(
            entity = TrackEntity::class,
            parentColumns = ["trackId"],
            childColumns = ["trackId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = GenreEntity::class,
            parentColumns = ["genreId"],
            childColumns = ["genreId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class TrackGenreCrossRef(
    val trackId: Long,
    val genreId: Long,
    val genreOrder: Int = 0
)
