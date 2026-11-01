package com.rovia.music.data.local.database

import androidx.room3.Database
import androidx.room3.RoomDatabase
import com.rovia.music.data.local.dao.AlbumDao
import com.rovia.music.data.local.dao.ArtistDao
import com.rovia.music.data.local.dao.ArtworkAssetDao
import com.rovia.music.data.local.dao.CatalogRelationDao
import com.rovia.music.data.local.dao.ExcludedFolderDao
import com.rovia.music.data.local.dao.GenreDao
import com.rovia.music.data.local.dao.LikedTrackDao
import com.rovia.music.data.local.dao.ListeningSegmentDao
import com.rovia.music.data.local.dao.MediaStoreSyncStateDao
import com.rovia.music.data.local.dao.PlaybackActivityDao
import com.rovia.music.data.local.dao.PlaybackSessionDao
import com.rovia.music.data.local.dao.PlaybackTrackEventDao
import com.rovia.music.data.local.dao.PlaylistDao
import com.rovia.music.data.local.dao.TrackDao
import com.rovia.music.data.local.dao.UserActionEventDao
import com.rovia.music.data.local.entity.AlbumArtistCrossRef
import com.rovia.music.data.local.entity.AlbumEntity
import com.rovia.music.data.local.entity.ArtistEntity
import com.rovia.music.data.local.entity.ArtworkAssetEntity
import com.rovia.music.data.local.entity.ExcludedFolderEntity
import com.rovia.music.data.local.entity.GenreEntity
import com.rovia.music.data.local.entity.LikedTrackEntity
import com.rovia.music.data.local.entity.ListeningSegmentEntity
import com.rovia.music.data.local.entity.MediaStoreSyncStateEntity
import com.rovia.music.data.local.entity.PlaybackActivityEntity
import com.rovia.music.data.local.entity.PlaybackSessionEntity
import com.rovia.music.data.local.entity.PlaybackTrackEventEntity
import com.rovia.music.data.local.entity.PlaylistEntity
import com.rovia.music.data.local.entity.PlaylistTrackEntity
import com.rovia.music.data.local.entity.TrackArtistCrossRef
import com.rovia.music.data.local.entity.TrackEntity
import com.rovia.music.data.local.entity.TrackGenreCrossRef
import com.rovia.music.data.local.entity.UserActionEventEntity

@Database(
    entities = [
        ArtworkAssetEntity::class,
        ArtistEntity::class,
        AlbumEntity::class,
        GenreEntity::class,
        TrackEntity::class,
        TrackArtistCrossRef::class,
        AlbumArtistCrossRef::class,
        TrackGenreCrossRef::class,
        PlaylistEntity::class,
        PlaylistTrackEntity::class,
        LikedTrackEntity::class,
        PlaybackActivityEntity::class,
        PlaybackSessionEntity::class,
        PlaybackTrackEventEntity::class,
        ListeningSegmentEntity::class,
        MediaStoreSyncStateEntity::class,
        ExcludedFolderEntity::class,
        UserActionEventEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class RoviaDatabase : RoomDatabase() {
    abstract fun trackDao(): TrackDao
    abstract fun artistDao(): ArtistDao
    abstract fun albumDao(): AlbumDao
    abstract fun genreDao(): GenreDao
    abstract fun catalogRelationDao(): CatalogRelationDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun likedTrackDao(): LikedTrackDao
    abstract fun playbackActivityDao(): PlaybackActivityDao
    abstract fun playbackSessionDao(): PlaybackSessionDao
    abstract fun playbackTrackEventDao(): PlaybackTrackEventDao
    abstract fun listeningSegmentDao(): ListeningSegmentDao
    abstract fun mediaStoreSyncStateDao(): MediaStoreSyncStateDao
    abstract fun artworkAssetDao(): ArtworkAssetDao
    abstract fun excludedFolderDao(): ExcludedFolderDao
    abstract fun userActionEventDao(): UserActionEventDao
}
