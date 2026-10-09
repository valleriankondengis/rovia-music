
package com.rovia.music.data.database.mapper

import com.rovia.music.core.model.Track
import com.rovia.music.data.database.entity.RecentPlayEntity

fun Track.toRecentPlayEntity(
    lastPlayedAtEpochMillis: Long,
): RecentPlayEntity {
    return RecentPlayEntity(
        uri = uri,
        trackId = id,
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
        lastPlayedAtEpochMillis = lastPlayedAtEpochMillis,
    )
}

fun RecentPlayEntity.toTrack(): Track {
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
