
package com.rovia.music.data.database.repository

import com.rovia.music.core.library.MusicRepository
import com.rovia.music.core.model.Track
import com.rovia.music.data.database.dao.MusicTrackDao
import com.rovia.music.data.database.entity.MusicTrackEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Provides the application's persistent music catalog.
 *
 * Room is the read source for catalog consumers. MediaStore scanning
 * and synchronization are handled separately by the data pipeline.
 *
 * Home, Library, Search, and future catalog features should share
 * the same repository rather than independently scanning MediaStore.
 */
class RoomMusicCatalogRepository(
    private val dao: MusicTrackDao,
) : MusicRepository {

    override suspend fun getRecentlyAdded(
        limit: Int,
    ): List<Track> {
        if (limit <= 0) {
            return emptyList()
        }

        return dao
            .getRecentlyAdded(limit)
            .map(MusicTrackEntity::toTrack)
    }

    override suspend fun getAllTracks(): List<Track> {
        return dao
            .getAllTracks()
            .map(MusicTrackEntity::toTrack)
    }

    override fun observeRecentlyAdded(
        limit: Int,
    ): Flow<List<Track>> {
        if (limit <= 0) {
            return dao
                .observeRecentlyAdded(0)
                .map { emptyList() }
        }

        return dao
            .observeRecentlyAdded(limit)
            .map { entities ->
                entities.map(
                    MusicTrackEntity::toTrack,
                )
            }
    }

    override fun observeAllTracks(): Flow<List<Track>> {
        return dao
            .observeAllTracks()
            .map { entities ->
                entities.map(
                    MusicTrackEntity::toTrack,
                )
            }
    }

    override fun observeTrackByUri(
        uri: String,
    ): Flow<Track?> {
        return dao
            .observeTrackByUri(uri)
            .map { entity ->
                entity?.toTrack()
            }
    }
}

private fun MusicTrackEntity.toTrack(): Track {
    return Track(
        id = trackId,
        uri = uri,
        title = title,
        artist = artist,
        album = album,
        genre = genre,
        displayName = displayName,
        albumArtist = albumArtist,
        composer = composer,
        author = author,
        writer = writer,
        year = year,
        releaseDate = releaseDate,
        trackNumber = trackNumber,
        discNumber = discNumber,
        cdTrackNumber = cdTrackNumber,
        compilation = compilation,
        label = label,
        copyright = copyright,
        releaseType = releaseType,
        durationMs = durationMs,
        dateAddedEpochSeconds = dateAddedEpochSeconds,
        dateModifiedEpochSeconds = dateModifiedEpochSeconds,
        metadataDateEpochMillis = metadataDateEpochMillis,
        inferredDateEpochMillis = inferredDateEpochMillis,
        mimeType = mimeType,
        sampleRateHz = sampleRateHz,
        bitsPerSample = bitsPerSample,
        bitrateBps = bitrateBps,
        fileSizeBytes = fileSizeBytes,
        relativePath = relativePath,
        volumeName = volumeName,
        artworkUri = artworkUri,
    )
}
