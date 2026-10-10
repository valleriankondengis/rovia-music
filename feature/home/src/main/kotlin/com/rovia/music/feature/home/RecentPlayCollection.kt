
package com.rovia.music.feature.home

import com.rovia.music.core.model.Track
import java.util.Locale

/**
 * An artist collection derived from the complete Recent Play list.
 *
 * The input list is expected to be ordered by most recently played.
 * The first matching track therefore supplies the collection's
 * representative artwork.
 */
data class RecentArtist(
    val name: String,
    val latestPlayedTrack: Track,
    val recentPlays: List<Track>,
) {
    /**
     * Uses the artwork of the latest matching playback only.
     *
     * A missing artwork remains null rather than falling back
     * to artwork from an older track.
     */
    val artworkUri: String?
        get() = latestPlayedTrack.artworkUri
}

/**
 * An album collection derived from the complete Recent Play list.
 *
 * Album artist identity is retained so albums with the same title
 * from different artists can remain separate collections.
 */
data class RecentAlbum(
    val title: String,
    val artist: String?,
    val latestPlayedTrack: Track,
    val recentPlays: List<Track>,
) {
    val artworkUri: String?
        get() = latestPlayedTrack.artworkUri
}

/**
 * A genre collection derived from the complete Recent Play list.
 */
data class RecentGenre(
    val name: String,
    val latestPlayedTrack: Track,
    val recentPlays: List<Track>,
) {
    val artworkUri: String?
        get() = latestPlayedTrack.artworkUri
}

/**
 * Groups Recent Play entries by artist.
 *
 * Artist metadata is preferred, with album artist used only
 * when the track artist is missing.
 *
 * Collection order follows the first appearance of each artist
 * in the supplied list. When the input is ordered by playback
 * recency, the most recently played artists appear first.
 */
fun buildRecentArtists(
    recentPlays: List<Track>,
): List<RecentArtist> {
    val groupedTracks =
        linkedMapOf<String, MutableList<Track>>()

    recentPlays.forEach { track ->
        val artistName =
            cleanMetadata(track.artist)
                ?: cleanMetadata(track.albumArtist)
                ?: return@forEach

        val key =
            normalizeMetadataName(artistName)

        groupedTracks
            .getOrPut(key) {
                mutableListOf()
            }
            .add(track)
    }

    return groupedTracks.values.map { tracks ->
        val latestPlayedTrack =
            tracks.first()

        val artistName =
            cleanMetadata(latestPlayedTrack.artist)
                ?: requireNotNull(
                    cleanMetadata(
                        latestPlayedTrack.albumArtist,
                    ),
                )

        RecentArtist(
            name = artistName,
            latestPlayedTrack = latestPlayedTrack,
            recentPlays = tracks.toList(),
        )
    }
}

/**
 * Groups Recent Play entries by album title and album artist.
 *
 * Album artist is preferred over track artist so tracks from the
 * same album can share one collection even when their individual
 * track artist metadata differs.
 *
 * When album artist is missing, track artist is used as a fallback.
 * Entries without an album title are excluded.
 */
fun buildRecentAlbums(
    recentPlays: List<Track>,
): List<RecentAlbum> {
    val groupedTracks =
        linkedMapOf<Pair<String, String?>, MutableList<Track>>()

    recentPlays.forEach { track ->
        val albumTitle =
            cleanMetadata(track.album)
                ?: return@forEach

        val albumArtist =
            cleanMetadata(track.albumArtist)
                ?: cleanMetadata(track.artist)

        val key =
            normalizeMetadataName(albumTitle) to
                albumArtist?.let(
                    ::normalizeMetadataName,
                )

        groupedTracks
            .getOrPut(key) {
                mutableListOf()
            }
            .add(track)
    }

    return groupedTracks.values.map { tracks ->
        val latestPlayedTrack =
            tracks.first()

        RecentAlbum(
            title =
                requireNotNull(
                    cleanMetadata(
                        latestPlayedTrack.album,
                    ),
                ),
            artist =
                cleanMetadata(
                    latestPlayedTrack.albumArtist,
                ) ?: cleanMetadata(
                    latestPlayedTrack.artist,
                ),
            latestPlayedTrack =
                latestPlayedTrack,
            recentPlays =
                tracks.toList(),
        )
    }
}

/**
 * Groups Recent Play entries by genre.
 *
 * Missing or blank genre metadata is ignored. Genre matching
 * is case-insensitive while the displayed name preserves the
 * spelling from the most recently played matching track.
 */
fun buildRecentGenres(
    recentPlays: List<Track>,
): List<RecentGenre> {
    val groupedTracks =
        linkedMapOf<String, MutableList<Track>>()

    recentPlays.forEach { track ->
        val genreName =
            cleanMetadata(track.genre)
                ?: return@forEach

        val key =
            normalizeMetadataName(genreName)

        groupedTracks
            .getOrPut(key) {
                mutableListOf()
            }
            .add(track)
    }

    return groupedTracks.values.map { tracks ->
        val latestPlayedTrack =
            tracks.first()

        RecentGenre(
            name =
                requireNotNull(
                    cleanMetadata(
                        latestPlayedTrack.genre,
                    ),
                ),
            latestPlayedTrack =
                latestPlayedTrack,
            recentPlays =
                tracks.toList(),
        )
    }
}

private fun cleanMetadata(
    value: String?,
): String? {
    return value
        ?.trim()
        ?.takeIf(String::isNotEmpty)
}

private fun normalizeMetadataName(
    value: String,
): String {
    return value
        .trim()
        .lowercase(Locale.ROOT)
}
