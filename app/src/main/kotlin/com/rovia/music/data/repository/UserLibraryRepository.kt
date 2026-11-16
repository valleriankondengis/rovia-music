package com.rovia.music.data.repository

import android.net.Uri
import com.rovia.music.data.local.database.RoviaDatabase
import com.rovia.music.data.local.entity.AlbumEntity
import com.rovia.music.data.local.entity.LikedTrackEntity
import com.rovia.music.data.local.entity.PlaylistEntity
import com.rovia.music.data.local.entity.PlaylistTrackEntity
import com.rovia.music.data.local.entity.TrackEntity
import com.rovia.music.data.mediastore.LocalAudioTrack
import java.text.Normalizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.withContext

data class PlaylistSummary(
    val id: Long,
    val name: String,
    val description: String?,
    val trackCount: Int,
    val updatedAtEpochMillis: Long,
)

data class PlaylistTrackItem(
    val entryId: Long,
    val track: LocalAudioTrack,
)

data class PlaylistDetail(
    val id: Long,
    val name: String,
    val description: String?,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val tracks: List<PlaylistTrackItem> = emptyList(),
) {
    val playableTracks: List<LocalAudioTrack>
        get() = tracks.map { item -> item.track }
}

/**
 * User-owned library data: playlists and liked tracks. The Room schema for both
 * already exists (playlists, playlist_tracks, liked_tracks); this repository is
 * the bridge between those tables and the [LocalAudioTrack] model the UI uses.
 * Playlist and like rows keep metadata snapshots, so they stay meaningful even
 * when the underlying track disappears from the catalog.
 */
class UserLibraryRepository(
    private val database: RoviaDatabase,
) {
    private val playlistDao get() = database.playlistDao()
    private val likedTrackDao get() = database.likedTrackDao()
    private val trackDao get() = database.trackDao()
    private val albumDao get() = database.albumDao()

    fun observePlaylists(): Flow<List<PlaylistSummary>> =
        playlistDao.observePlaylistSummaries().map { rows ->
            rows.map { row ->
                PlaylistSummary(
                    id = row.playlistId,
                    name = row.name,
                    description = row.description,
                    trackCount = row.trackCount.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt(),
                    updatedAtEpochMillis = row.updatedAtEpochMillis,
                )
            }
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observePlaylistDetail(playlistId: Long): Flow<PlaylistDetail?> =
        combine(
            playlistDao.observePlaylistById(playlistId),
            playlistDao.observePlaylistTracks(playlistId),
        ) { playlist, entries ->
            playlist to entries
        }.mapLatest { (playlist, entries) ->
            if (playlist == null) {
                null
            } else {
                withContext(Dispatchers.Default) {
                    val resolvedTracks = resolveTracks(entries.mapNotNull { entry -> entry.trackId })
                    val tracksById = resolvedTracks.associateBy { entity -> entity.trackId }
                    val albumsById = loadAlbums(resolvedTracks)
                    PlaylistDetail(
                        id = playlist.playlistId,
                        name = playlist.name,
                        description = playlist.description,
                        createdAtEpochMillis = playlist.createdAtEpochMillis,
                        updatedAtEpochMillis = playlist.updatedAtEpochMillis,
                        tracks = entries.mapNotNull { entry ->
                            val entity = entry.trackId?.let { trackId -> tracksById[trackId] }
                                ?: return@mapNotNull null
                            PlaylistTrackItem(
                                entryId = entry.playlistTrackId,
                                track = entity.toLocalAudioTrack(
                                    album = entity.albumReleaseId?.let { albumId -> albumsById[albumId] },
                                ),
                            )
                        },
                    )
                }
            }
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeLikedTracks(): Flow<List<LocalAudioTrack>> =
        likedTrackDao.observeLikedTracks().mapLatest { likes ->
            withContext(Dispatchers.Default) {
                val resolvedTracks = resolveTracks(likes.mapNotNull { like -> like.trackId })
                val tracksById = resolvedTracks.associateBy { entity -> entity.trackId }
                val albumsById = loadAlbums(resolvedTracks)
                likes.mapNotNull { like ->
                    val entity = like.trackId?.let { trackId -> tracksById[trackId] }
                        ?: return@mapNotNull null
                    entity.toLocalAudioTrack(
                        album = entity.albumReleaseId?.let { albumId -> albumsById[albumId] },
                    )
                }
            }
        }

    /**
     * Identity keys of liked tracks in the same shape the UI can compute from a
     * [LocalAudioTrack]: "volumeName:mediaStoreId" (see [likedTrackKey]).
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeLikedTrackKeys(): Flow<Set<String>> =
        likedTrackDao.observeLikedTracks().mapLatest { likes ->
            val trackIds = likes.mapNotNull { like -> like.trackId }.distinct()
            if (trackIds.isEmpty()) {
                emptySet()
            } else {
                trackDao.getByIds(trackIds)
                    .map { entity -> likedTrackKey(entity) }
                    .toSet()
            }
        }

    fun observeLikedCount(): Flow<Int> =
        likedTrackDao.observeLikedTrackCount().map { count ->
            count.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
        }

    suspend fun createPlaylist(name: String): Long {
        val trimmedName = name.trim()
        require(trimmedName.isNotBlank()) { "Playlist name must not be blank" }
        val now = System.currentTimeMillis()
        return playlistDao.insertPlaylist(
            PlaylistEntity(
                name = trimmedName,
                normalizedName = normalizeUserLibraryName(trimmedName),
                createdAtEpochMillis = now,
                updatedAtEpochMillis = now,
            ),
        )
    }

    suspend fun renamePlaylist(playlistId: Long, name: String) {
        val trimmedName = name.trim()
        require(trimmedName.isNotBlank()) { "Playlist name must not be blank" }
        val playlist = playlistDao.getPlaylistById(playlistId) ?: return
        playlistDao.updatePlaylist(
            playlist.copy(
                name = trimmedName,
                normalizedName = normalizeUserLibraryName(trimmedName),
                updatedAtEpochMillis = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun deletePlaylist(playlistId: Long) {
        playlistDao.deletePlaylistById(playlistId)
    }

    /** Returns false when the track is already in the playlist. */
    suspend fun addTrackToPlaylist(playlistId: Long, track: LocalAudioTrack): Boolean {
        val entity = resolveTrackEntity(track) ?: return false
        val existingEntries = playlistDao.getPlaylistTracks(playlistId)
        if (existingEntries.any { entry -> entry.trackId == entity.trackId }) {
            return false
        }
        val now = System.currentTimeMillis()
        playlistDao.insertPlaylistTrack(
            PlaylistTrackEntity(
                playlistId = playlistId,
                trackId = entity.trackId,
                sortOrder = (existingEntries.maxOfOrNull { entry -> entry.sortOrder } ?: -1L) + 1L,
                addedAtEpochMillis = now,
                trackTitleSnapshot = entity.title,
                artistCreditSnapshot = entity.artistCreditRaw,
                albumTitleSnapshot = entity.albumTitleSnapshot,
                artworkAssetKeySnapshot = entity.artworkAssetKey,
                artworkSourceUriSnapshot = entity.artworkSourceUri,
            ),
        )
        playlistDao.getPlaylistById(playlistId)?.let { playlist ->
            playlistDao.updatePlaylist(playlist.copy(updatedAtEpochMillis = now))
        }
        return true
    }

    suspend fun removePlaylistTrack(entryId: Long) {
        playlistDao.removePlaylistTrack(entryId)
    }

    /** Returns the liked state after the toggle. */
    suspend fun toggleLike(track: LocalAudioTrack): Boolean {
        val entity = resolveTrackEntity(track) ?: return false
        val existing = findLike(entity)
        return if (existing != null) {
            likedTrackDao.removeLikeForTrack(entity.trackId)
            false
        } else {
            insertLike(entity)
            true
        }
    }

    suspend fun setLiked(track: LocalAudioTrack, liked: Boolean) {
        val entity = resolveTrackEntity(track) ?: return
        val existing = findLike(entity)
        if (liked && existing == null) {
            insertLike(entity)
        } else if (!liked && existing != null) {
            likedTrackDao.removeLikeForTrack(entity.trackId)
        }
    }

    private suspend fun findLike(entity: TrackEntity): LikedTrackEntity? {
        likedTrackDao.getLikeForTrack(entity.trackId)?.let { return it }
        val sourceKey = entity.sourceKey?.takeIf(String::isNotBlank) ?: return null
        return likedTrackDao.getLikeForSourceKey(sourceKey)
    }

    private suspend fun insertLike(entity: TrackEntity) {
        likedTrackDao.insertLike(
            LikedTrackEntity(
                trackId = entity.trackId,
                sourceKeySnapshot = entity.sourceKey,
                titleSnapshot = entity.title,
                artistCreditSnapshot = entity.artistCreditRaw,
                albumTitleSnapshot = entity.albumTitleSnapshot,
                artworkAssetKeySnapshot = entity.artworkAssetKey,
                artworkSourceUriSnapshot = entity.artworkSourceUri,
                likedAtEpochMillis = System.currentTimeMillis(),
            ),
        )
    }

    private suspend fun resolveTracks(trackIds: List<Long>): List<TrackEntity> {
        val distinctIds = trackIds.distinct()
        if (distinctIds.isEmpty()) return emptyList()
        return trackDao.getAvailableTracksByIds(distinctIds)
    }

    private suspend fun loadAlbums(tracks: List<TrackEntity>): Map<Long, AlbumEntity> {
        val albumIds = tracks.mapNotNull { track -> track.albumReleaseId }.distinct()
        if (albumIds.isEmpty()) return emptyMap()
        return albumDao.getByIds(albumIds).associateBy { album -> album.albumId }
    }

    private suspend fun resolveTrackEntity(track: LocalAudioTrack): TrackEntity? {
        trackDao.getByMediaStoreIdentity(
            mediaStoreId = track.id,
            volumeName = track.volumeName,
        )?.let { return it }
        // LocalAudioTrack.id falls back to the Room trackId when the track has
        // no MediaStore id, so try the primary key as well.
        return trackDao.getById(track.id)?.takeIf { entity ->
            entity.mediaStoreId == null || entity.mediaStoreId == track.id
        }
    }

    private fun TrackEntity.toLocalAudioTrack(album: AlbumEntity?): LocalAudioTrack {
        val resolvedYear = releaseYear ?: album?.releaseYear ?: mediaStoreYear
        return LocalAudioTrack(
            id = mediaStoreId ?: trackId,
            uri = contentUri
                ?.takeIf(String::isNotBlank)
                ?.let(Uri::parse)
                ?: Uri.EMPTY,
            title = title,
            artist = artistCreditRaw ?: album?.albumArtistCreditRaw,
            album = albumTitleSnapshot ?: album?.title,
            durationMs = durationMs,
            dateAddedSeconds = dateAddedSeconds,
            relativePath = relativePath,
            dateModifiedSeconds = dateModifiedSeconds,
            albumId = album?.providerAlbumId,
            albumArtist = albumArtistCreditRaw ?: album?.albumArtistCreditRaw,
            releaseYear = resolvedYear,
            releaseDate = releaseDateRaw ?: album?.releaseDateRaw ?: resolvedYear?.toString(),
            trackNumber = trackNumber,
            discNumber = discNumber,
            mimeType = mimeType,
            displayName = displayName,
            fileSizeBytes = fileSizeBytes,
            volumeName = volumeName,
            releaseMonth = releaseMonth ?: album?.releaseMonth,
            releaseDay = releaseDay ?: album?.releaseDay,
        )
    }

    companion object {
        fun likedTrackKey(track: LocalAudioTrack): String =
            "${track.volumeName.orEmpty()}:${track.id}"

        fun likedTrackKey(entity: TrackEntity): String =
            "${entity.volumeName.orEmpty()}:${entity.mediaStoreId ?: entity.trackId}"
    }
}

private fun normalizeUserLibraryName(value: String): String = Normalizer
    .normalize(value.trim().replace(Regex("\\s+"), " "), Normalizer.Form.NFC)
    .lowercase()
