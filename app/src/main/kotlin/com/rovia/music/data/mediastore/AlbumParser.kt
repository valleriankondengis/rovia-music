package com.rovia.music.data.mediastore

import java.text.Normalizer
import java.util.Locale

/**
 * A release type inferred from the local MediaStore catalog.
 *
 * MediaStore does not provide an authoritative Single/EP/Album release-type field. The parser
 * therefore honours a clear release-type suffix in the album title first, then falls back to
 * track-count and runtime heuristics. This is a best-effort local classification, not label
 * supplied release metadata.
 */
enum class AlbumReleaseType {
    SINGLE,
    EP,
    ALBUM,
    UNKNOWN
}

/** A normalized album/release and all catalog tracks grouped under it. */
data class AlbumRelease(
    val key: String,
    val albumId: Long?,
    val title: String,
    val albumArtist: String?,
    val artists: List<String>,
    val tracks: List<LocalAudioTrack>,
    val totalDurationMs: Long,
    val type: AlbumReleaseType
) {
    val artworkUri: String?
        get() = tracks.firstOrNull()?.uri?.toString()

    /**
     * Album-artist metadata takes priority when present, so a featured guest does not make an
     * entire album appear as that guest's own release. Track artist credits are the fallback.
     */
    fun isForArtist(artistName: String): Boolean {
        val expectedArtist = normalizeAlbumIdentity(artistName)
        if (expectedArtist.isEmpty()) return false

        val albumArtistCredits = tracks
            .flatMap { track -> ArtistCreditParser.parse(track.albumArtist.orEmpty()) }
            .distinctBy(::normalizeAlbumIdentity)

        val candidates = if (albumArtistCredits.isNotEmpty()) {
            albumArtistCredits
        } else {
            tracks.flatMap(LocalAudioTrack::artistNames)
                .distinctBy(::normalizeAlbumIdentity)
        }

        return candidates.any { normalizeAlbumIdentity(it) == expectedArtist }
    }
}

/** Groups tracks into releases and infers each release type. */
object AlbumParser {
    private const val MAX_SHORT_RELEASE_DURATION_MS = 30L * 60L * 1000L
    private const val LONG_TRACK_DURATION_MS = 10L * 60L * 1000L

    private val explicitSingleSuffixes = listOf(
        "single",
        "single release"
    )

    private val explicitEpSuffixes = listOf(
        "ep",
        "extended play"
    )

    private val explicitAlbumSuffixes = listOf(
        "album",
        "full album"
    )

    /**
     * Groups the supplied tracks by MediaStore album ID when available. If that ID is absent,
     * grouping falls back to normalized album title and album artist.
     */
    fun parse(tracks: List<LocalAudioTrack>): List<AlbumRelease> {
        val groupedTracks = linkedMapOf<String, MutableList<LocalAudioTrack>>()

        tracks.forEach { track ->
            val albumTitle = track.album
                ?.trim()
                ?.takeIf(String::isNotEmpty)
                ?: return@forEach

            val albumArtist = track.albumArtist
                ?.trim()
                .orEmpty()
            val fallbackArtist = albumArtist.ifBlank {
                track.artistNames.firstOrNull().orEmpty()
            }

            val key = track.albumId
                ?.takeIf { it > 0L }
                ?.let { "album-id:$it" }
                ?: "album-title:${normalizeAlbumIdentity(albumTitle)}|album-artist:${normalizeAlbumIdentity(fallbackArtist)}"

            groupedTracks.getOrPut(key) { mutableListOf() }.add(track)
        }

        return groupedTracks.mapNotNull { (key, group) ->
            val distinctTracks = group.distinctBy(LocalAudioTrack::id)
            val firstTrack = distinctTracks.firstOrNull() ?: return@mapNotNull null
            val title = firstTrack.album?.trim().orEmpty()
            val durations = distinctTracks.map(LocalAudioTrack::durationMs)
            val albumArtistCredits = distinctTracks
                .flatMap { track -> ArtistCreditParser.parse(track.albumArtist.orEmpty()) }
                .distinctBy(::normalizeAlbumIdentity)
            val trackArtists = distinctTracks
                .flatMap(LocalAudioTrack::artistNames)
                .distinctBy(::normalizeAlbumIdentity)
            val displayArtists = albumArtistCredits.ifEmpty { trackArtists }
            val totalDuration = durations.sumOf { it.coerceAtLeast(0L) }

            AlbumRelease(
                key = key,
                albumId = distinctTracks.mapNotNull(LocalAudioTrack::albumId).firstOrNull(),
                title = title,
                albumArtist = albumArtistCredits.joinToString(", ").ifBlank { null },
                artists = displayArtists,
                tracks = distinctTracks,
                totalDurationMs = totalDuration,
                type = classify(title, durations)
            )
        }.sortedWith(
            compareBy<AlbumRelease> { it.title.lowercase(Locale.ROOT) }
                .thenBy { it.albumArtist.orEmpty().lowercase(Locale.ROOT) }
        )
    }

    /**
     * Classifies a release from its title and per-track durations.
     *
     * When all durations are known, a release over 30 minutes defaults to Album. Otherwise,
     * releases with 1-3 tracks default to Single, unless any track is 10 minutes or longer (EP),
     * and releases with 4-6 tracks default to EP. Seven or more tracks default to Album.
     * A trailing explicit marker such as "- Single" or "(EP)" takes priority over the heuristic.
     */
    fun classify(
        albumTitle: String?,
        trackDurationsMs: List<Long>
    ): AlbumReleaseType {
        val trackCount = trackDurationsMs.size
        if (trackCount == 0) return AlbumReleaseType.UNKNOWN

        val normalizedTitle = normalizeTitle(albumTitle.orEmpty())

        when {
            hasTrailingMarker(normalizedTitle, explicitSingleSuffixes) ->
                return AlbumReleaseType.SINGLE

            hasTrailingMarker(normalizedTitle, explicitEpSuffixes) ->
                return AlbumReleaseType.EP

            hasTrailingMarker(normalizedTitle, explicitAlbumSuffixes) ->
                return AlbumReleaseType.ALBUM
        }

        val allDurationsKnown = trackDurationsMs.all { it > 0L }
        val totalDuration = if (allDurationsKnown) {
            trackDurationsMs.sum()
        } else {
            0L
        }

        if (allDurationsKnown && totalDuration > MAX_SHORT_RELEASE_DURATION_MS) {
            return AlbumReleaseType.ALBUM
        }

        return when (trackCount) {
            in 1..3 -> if (
                allDurationsKnown &&
                trackDurationsMs.any { it >= LONG_TRACK_DURATION_MS }
            ) {
                AlbumReleaseType.EP
            } else {
                AlbumReleaseType.SINGLE
            }

            in 4..6 -> AlbumReleaseType.EP
            else -> AlbumReleaseType.ALBUM
        }
    }

    private fun hasTrailingMarker(
        normalizedTitle: String,
        markers: List<String>
    ): Boolean {
        if (normalizedTitle.isBlank()) return false

        val title = normalizedTitle
            .removeSuffix(")")
            .removeSuffix("]")
            .trim()
            .removeSurrounding("(", ")")
            .removeSurrounding("[", "]")
            .trim()

        return markers.any { marker ->
            normalizedTitle == marker ||
                normalizedTitle.endsWith(" $marker") ||
                normalizedTitle.endsWith(" - $marker") ||
                normalizedTitle.endsWith(" – $marker") ||
                normalizedTitle.endsWith(" — $marker") ||
                normalizedTitle.endsWith("($marker)") ||
                normalizedTitle.endsWith("[$marker]") ||
                title == marker
        }
    }

    private fun normalizeTitle(value: String): String =
        normalizeAlbumIdentity(value)
            .replace(Regex("\\s+"), " ")
            .trim()

}

private fun normalizeAlbumIdentity(value: String): String =
    Normalizer.normalize(value.trim(), Normalizer.Form.NFC)
        .lowercase(Locale.ROOT)