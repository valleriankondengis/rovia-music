package com.rovia.music.data.local.entity

import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index

@Entity(
    tableName = "album_artist_cross_ref",
    primaryKeys = ["albumId", "artistId"],
    indices = [Index(value = ["artistId", "albumId"])],
    foreignKeys = [
        ForeignKey(
            entity = AlbumEntity::class,
            parentColumns = ["albumId"],
            childColumns = ["albumId"],
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
data class AlbumArtistCrossRef(
    val albumId: Long,
    val artistId: Long,
    val creditOrder: Int
)
