package com.rovia.music.data.mediastore

import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn

object CatalogArtworkSelector {
    private data class ArtistArtworkCandidate(
        val metadataTrack: LocalAudioTrack,
        val artworkTrack: LocalAudioTrack,
    )

    private data class ParsedReleaseDate(
        val year: Int,
        val month: Int?,
        val day: Int?,
    )

    private val releaseDatePattern =
        Regex("""^(\d{4})(?:[-/.](\d{1,2})(?:[-/.](\d{1,2}))?)?(?:[Tt ].*)?$""")

    fun albumArtworkTrack(tracks: List<LocalAudioTrack>): LocalAudioTrack? =
        tracks.minWithOrNull(
            compareBy<LocalAudioTrack> { it.discNumber ?: Int.MAX_VALUE }
                .thenBy { it.trackNumber ?: Int.MAX_VALUE }
                .thenBy { it.title.orEmpty().lowercase() }
                .thenBy { it.id }
        )

    /**
     * Returns the display year for a track without discarding a more precise release date.
     * A valid month/day in releaseDate takes priority over a separate YEAR value, which may
     * be stale or less precise. A year-only releaseDate is used only when releaseYear is absent.
     */
    fun releaseYearForDisplay(track: LocalAudioTrack): Int? {
        val parsedReleaseDate = parseReleaseDate(track.releaseDate)

        return when {
            parsedReleaseDate?.month != null -> parsedReleaseDate.year
            track.releaseDateSource == "EMBEDDED_YEAR" && parsedReleaseDate != null ->
                parsedReleaseDate.year
            track.releaseYear?.let { it in 1000..9999 } == true -> track.releaseYear
            parsedReleaseDate != null -> parsedReleaseDate.year
            else -> null
        }
    }

    /** Returns the release year selected from the strongest metadata on the release tracks. */
    fun releaseYearForDisplay(tracks: List<LocalAudioTrack>): Int? =
        bestReleaseMetadataTrack(tracks)?.let { track -> releaseYearForDisplay(track) }

    fun bestReleaseMetadataTrack(tracks: List<LocalAudioTrack>): LocalAudioTrack? {
        val tracksWithReleaseYear = tracks.filter { releaseYearForDisplay(it) != null }
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
            .mapNotNull { release ->
                val metadataTrack = bestReleaseMetadataTrack(release.tracks)
                    ?: return@mapNotNull null
                ArtistArtworkCandidate(
                    metadataTrack = metadataTrack,
                    artworkTrack = albumArtworkTrack(release.tracks) ?: metadataTrack,
                )
            }
            .toList()

        val ungroupedCandidates = ungroupedArtistTracks.map { track ->
            ArtistArtworkCandidate(
                metadataTrack = track,
                artworkTrack = track,
            )
        }

        return (releaseCandidates + ungroupedCandidates)
            .distinctBy { it.artworkTrack.uri }
            .maxWithOrNull(
                compareBy<ArtistArtworkCandidate> {
                    releaseSortTimestamp(it.metadataTrack) ?: Long.MIN_VALUE
                }
                    .thenBy { it.metadataTrack.dateModifiedSeconds }
                    .thenBy { it.metadataTrack.dateAddedSeconds }
                    .thenBy { it.metadataTrack.id }
            )
            ?.artworkTrack
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
        val parsedReleaseDate = parseReleaseDate(track.releaseDate)
        if (parsedReleaseDate?.month != null) {
            return runCatching {
                LocalDate(
                    year = parsedReleaseDate.year,
                    month = parsedReleaseDate.month,
                    day = parsedReleaseDate.day ?: 1,
                ).atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()
            }.getOrNull()
        }

        track.metadataDateEpochMillis?.let { return it }

        val releaseYear = releaseYearForDisplay(track) ?: return track.inferredDateEpochMillis
        val releaseDate = runCatching {
            LocalDate(
                year = releaseYear,
                month = Month(track.releaseMonth ?: 1),
                day = track.releaseDay ?: 1,
            ).atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()
        }.getOrNull()

        return releaseDate ?: track.inferredDateEpochMillis
    }

    private fun releaseDatePriority(track: LocalAudioTrack): Int {
        val parsedReleaseDate = parseReleaseDate(track.releaseDate)
        if (parsedReleaseDate?.day != null) return 500
        if (parsedReleaseDate?.month != null) return 400

        return when (track.releaseDateSource) {
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

    private fun parseReleaseDate(value: String?): ParsedReleaseDate? {
        val text = value?.trim()?.takeIf(String::isNotBlank) ?: return null
        val match = releaseDatePattern.matchEntire(text) ?: return null
        val year = match.groupValues[1].toIntOrNull()
            ?.takeIf { it in 1000..9999 } ?: return null
        val month = match.groupValues[2]
            .takeIf(String::isNotEmpty)
            ?.toIntOrNull()
        val day = match.groupValues[3]
            .takeIf(String::isNotEmpty)
            ?.toIntOrNull()

        if (day != null && month == null) return null

        return try {
            LocalDate(year, month ?: 1, day ?: 1)
            ParsedReleaseDate(
                year = year,
                month = month,
                day = day,
            )
        } catch (_: Exception) {
            null
        }
    }
}