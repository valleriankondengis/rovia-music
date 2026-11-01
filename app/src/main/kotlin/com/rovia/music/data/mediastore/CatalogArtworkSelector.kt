package com.rovia.music.data.mediastore

import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn

object CatalogArtworkSelector {
    fun albumArtworkTrack(tracks: List<LocalAudioTrack>): LocalAudioTrack? =
        tracks.minWithOrNull(
            compareBy<LocalAudioTrack> { it.discNumber ?: Int.MAX_VALUE }
                .thenBy { it.trackNumber ?: Int.MAX_VALUE }
                .thenBy { it.title.orEmpty().lowercase() }
                .thenBy { it.id }
        )

    fun bestReleaseMetadataTrack(tracks: List<LocalAudioTrack>): LocalAudioTrack? {
        val tracksWithReleaseYear = tracks.filter { track ->
            track.releaseYear?.let { it in 1000..9999 } == true
        }
        if (tracksWithReleaseYear.isEmpty()) return latestReleaseTrack(tracks)

        return tracksWithReleaseYear.maxWithOrNull(
            compareBy<LocalAudioTrack> { releaseDatePriority(it) }
                .thenBy { releaseSortTimestamp(it) ?: 0L }
                .thenBy { it.dateModifiedSeconds }
                .thenBy { it.dateAddedSeconds }
                .thenBy { it.id }
        )
    }

    fun artistArtworkTrack(
        artistName: String,
        releases: List<AlbumRelease>,
        ungroupedArtistTracks: List<LocalAudioTrack>,
    ): LocalAudioTrack? {
        val releaseCandidates = releases
            .asSequence()
            .filter { release -> release.isForArtist(artistName) }
            .mapNotNull { release -> bestReleaseMetadataTrack(release.tracks) }
            .toList()

        return latestReleaseTrack(
            (releaseCandidates + ungroupedArtistTracks).distinctBy { it.uri }
        )
    }

    fun artistArtworkTrack(
        artistName: String,
        tracks: List<LocalAudioTrack>,
    ): LocalAudioTrack? = artistArtworkTrack(
        artistName = artistName,
        releases = AlbumParser.parse(tracks),
        ungroupedArtistTracks = tracks.filter { it.album.isNullOrBlank() },
    )

    fun latestReleaseTrack(tracks: List<LocalAudioTrack>): LocalAudioTrack? {
        if (tracks.isEmpty()) return null

        val tracksWithReleaseDate = tracks.filter { releaseSortTimestamp(it) != null }
        val candidates = tracksWithReleaseDate.ifEmpty { tracks }

        return candidates.maxWithOrNull(
            compareBy<LocalAudioTrack> { releaseSortTimestamp(it) ?: Long.MIN_VALUE }
                .thenBy { it.dateModifiedSeconds }
                .thenBy { it.dateAddedSeconds }
                .thenBy { it.id }
        )
    }

    fun releaseSortTimestamp(track: LocalAudioTrack): Long? {
        track.metadataDateEpochMillis?.let { return it }

        val releaseDate = track.releaseYear?.let { year ->
            runCatching {
                LocalDate(
                    year = year,
                    month = Month(track.releaseMonth ?: 1),
                    day = track.releaseDay ?: 1,
                ).atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()
            }.getOrNull()
        }

        return releaseDate ?: track.inferredDateEpochMillis
    }

    private fun releaseDatePriority(track: LocalAudioTrack): Int = when (track.releaseDateSource) {
        "EMBEDDED_DATE" -> when (track.releaseDatePrecision) {
            "DAY" -> 500
            "MONTH" -> 400
            else -> 300
        }
        "EMBEDDED_YEAR" -> 250
        "MEDIASTORE_ALBUM_FIRST_YEAR" -> 200
        "MEDIASTORE_YEAR" -> 180
        "MEDIASTORE_ALBUM_FIRST_LAST_YEAR", "MEDIASTORE_ALBUM_LAST_YEAR" -> 100
        else -> 0
    }
}