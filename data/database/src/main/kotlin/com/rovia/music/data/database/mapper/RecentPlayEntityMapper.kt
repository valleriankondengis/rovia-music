package com.rovia.music.data.database.mapper

import com.rovia.music.core.model.Track
import com.rovia.music.data.database.entity.RecentPlayEntity

fun Track.toRecentPlayEntity(
    lastPlayedAtEpochMillis: Long,
): RecentPlayEntity {
    return RecentPlayEntity(
        trackId = id,
        uri = uri,
        title = title,
        artist = artist,
        album = album,
        genre = genre,
        durationMs = durationMs,
        dateAddedEpochSeconds = dateAddedEpochSeconds,
        mimeType = mimeType,
        sampleRateHz = sampleRateHz,
        bitrateBps = bitrateBps,
        relativePath = relativePath,
        artworkUri = artworkUri,
        lastPlayedAtEpochMillis =
            lastPlayedAtEpochMillis,
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
        durationMs = durationMs,
        dateAddedEpochSeconds =
            dateAddedEpochSeconds,
        mimeType = mimeType,
        sampleRateHz = sampleRateHz,
        bitrateBps = bitrateBps,
        relativePath = relativePath,
        artworkUri = artworkUri,
    )
}