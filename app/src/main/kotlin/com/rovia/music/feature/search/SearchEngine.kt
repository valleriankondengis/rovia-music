package com.rovia.music.feature.search

import com.rovia.music.data.mediastore.AlbumParser
import com.rovia.music.data.mediastore.AlbumRelease
import com.rovia.music.data.mediastore.ArtistCreditParser
import com.rovia.music.data.mediastore.LocalAudioTrack
import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

private const val SEARCH_CANCELLATION_CHECK_INTERVAL = 64

private val SearchWordSeparator = Regex("[^\\p{L}\\p{N}]+")
private val SearchWhitespace = Regex("\\s+")
private val SearchDiacritics = Regex("\\p{M}+")

internal data class SearchIndexField(
    val normalized: String,
    val words: List<String>,
    val weight: Int
)

internal data class IndexedSearchTrack(
    val track: LocalAudioTrack,
    val artistNames: List<String>,
    val fields: List<SearchIndexField>
)

internal data class IndexedSearchArtist(
    val name: String,
    val tracks: List<LocalAudioTrack>,
    val fields: List<SearchIndexField>
)

internal data class IndexedSearchRelease(
    val release: AlbumRelease,
    val fields: List<SearchIndexField>
)

class SearchIndex internal constructor(
    internal val tracks: List<IndexedSearchTrack>,
    internal val artists: List<IndexedSearchArtist>,
    internal val releases: List<IndexedSearchRelease>
)

data class SearchSongResult(
    val track: LocalAudioTrack,
    val score: Int
)

data class SearchArtistResult(
    val name: String,
    val artworkUri: String?,
    val trackCount: Int,
    val score: Int
)

data class SearchAlbumResult(
    val release: AlbumRelease,
    val score: Int
)

data class SearchResults(
    val songs: List<SearchSongResult> = emptyList(),
    val artists: List<SearchArtistResult> = emptyList(),
    val albums: List<SearchAlbumResult> = emptyList()
)

object SearchEngine {

    private class MutableArtistEntry(
        val name: String
    ) {
        val tracks = linkedMapOf<Long, LocalAudioTrack>()
    }

    suspend fun buildIndex(
        tracks: List<LocalAudioTrack>
    ): SearchIndex {
        val activeContext = currentCoroutineContext()
        val indexedTracks = ArrayList<IndexedSearchTrack>(tracks.size)

        tracks.forEachIndexed { index, track ->
            if (index % SEARCH_CANCELLATION_CHECK_INTERVAL == 0) {
                activeContext.ensureActive()
            }

            val trackArtists = track.artistNames
            val fields = buildList {
                addField(track.title, 30)
                addField(track.artist, 12)
                addField(trackArtists.joinToString(" "), 12)
                addField(track.album, 5)
                addField(track.albumArtist, 5)
                addField(
                    ArtistCreditParser.parse(track.albumArtist).joinToString(" "),
                    6
                )
                addField(track.genres.joinToString(" "), 4)
                addField(track.composer, 3)
                addField(track.author, 2)
                addField(track.writer, 2)
                addField(track.label, 2)
                addField(track.copyright, 1)
                addField(track.releaseType, 2)
                addField(track.compilation, 1)
                addField(track.releaseDate, 2)
                addField(track.releaseYear?.toString(), 2)
                addField(track.trackNumber?.toString(), 1)
                addField(track.discNumber?.toString(), 1)
                addField(track.cdTrackNumber, 1)
                addField(track.displayName, 1)
                addField(track.relativePath, 1)
                addField(track.mimeType, 1)
                addField(track.sampleRateHz?.toString(), 1)
                addField(track.bitsPerSample?.toString(), 1)
                addField(track.bitrateBps?.toString(), 1)
            }

            indexedTracks += IndexedSearchTrack(
                track = track,
                artistNames = trackArtists,
                fields = fields
            )
        }

        val artistsByIdentity = linkedMapOf<String, MutableArtistEntry>()

        indexedTracks.forEachIndexed { index, indexedTrack ->
            if (index % SEARCH_CANCELLATION_CHECK_INTERVAL == 0) {
                activeContext.ensureActive()
            }

            val artistNames = (
                indexedTrack.artistNames +
                    ArtistCreditParser.parse(indexedTrack.track.albumArtist)
                ).distinctBy { normalizeSearchText(it) }

            artistNames.forEach artistLoop@{ name ->
                val displayName = name.trim()
                val identity = normalizeSearchText(displayName)
                if (identity.isBlank()) return@artistLoop

                val artist = artistsByIdentity.getOrPut(identity) {
                    MutableArtistEntry(displayName)
                }
                artist.tracks.putIfAbsent(indexedTrack.track.id, indexedTrack.track)
            }
        }

        val indexedArtists = artistsByIdentity.values.map { artist ->
            IndexedSearchArtist(
                name = artist.name,
                tracks = artist.tracks.values.toList(),
                fields = listOfNotNull(createField(artist.name, 10))
            )
        }

        activeContext.ensureActive()
        val releases = AlbumParser.parse(tracks)
        val indexedReleases = ArrayList<IndexedSearchRelease>(releases.size)

        releases.forEachIndexed { index, release ->
            if (index % SEARCH_CANCELLATION_CHECK_INTERVAL == 0) {
                activeContext.ensureActive()
            }

            val fields = buildList {
                addField(release.title, 10)
                addField(release.albumArtist, 7)
                addField(release.artists.joinToString(" "), 7)
                addField(
                    release.tracks.mapNotNull(LocalAudioTrack::title)
                        .distinct()
                        .joinToString(" "),
                    3
                )
                addField(
                    release.tracks.flatMap(LocalAudioTrack::genres)
                        .distinct()
                        .joinToString(" "),
                    3
                )
                addField(
                    release.tracks.mapNotNull(LocalAudioTrack::composer)
                        .distinct()
                        .joinToString(" "),
                    2
                )
                addField(
                    release.tracks.mapNotNull(LocalAudioTrack::releaseYear)
                        .distinct()
                        .joinToString(" "),
                    1
                )
            }

            indexedReleases += IndexedSearchRelease(
                release = release,
                fields = fields
            )
        }

        return SearchIndex(
            tracks = indexedTracks,
            artists = indexedArtists,
            releases = indexedReleases
        )
    }

    suspend fun search(
        index: SearchIndex,
        query: String
    ): SearchResults {
        val normalizedQuery = normalizeSearchText(query)
        if (normalizedQuery.isBlank()) return SearchResults()

        val queryTerms = normalizedQuery
            .split(SearchWhitespace)
            .filter(String::isNotBlank)
            .distinct()
        if (queryTerms.isEmpty()) return SearchResults()

        val activeContext = currentCoroutineContext()
        val scoredTracks = ArrayList<SearchSongResult>()

        index.tracks.forEachIndexed { trackIndex, indexedTrack ->
            if (trackIndex % SEARCH_CANCELLATION_CHECK_INTERVAL == 0) {
                activeContext.ensureActive()
            }

            val score = scoreFields(
                fields = indexedTrack.fields,
                normalizedQuery = normalizedQuery,
                queryTerms = queryTerms
            )

            if (score > 0) {
                scoredTracks += SearchSongResult(
                    track = indexedTrack.track,
                    score = score
                )
            }
        }

        val songResults = scoredTracks.sortedWith(
            compareByDescending<SearchSongResult> { it.score }
                .thenBy { normalizeSearchText(it.track.title.orEmpty()) }
                .thenBy { it.track.id }
        )
        val songScores = songResults.associate { it.track.id to it.score }

        val albumResults = ArrayList<SearchAlbumResult>()
        index.releases.forEachIndexed { releaseIndex, indexedRelease ->
            if (releaseIndex % SEARCH_CANCELLATION_CHECK_INTERVAL == 0) {
                activeContext.ensureActive()
            }

            val release = indexedRelease.release
            val directScore = scoreFields(
                fields = indexedRelease.fields,
                normalizedQuery = normalizedQuery,
                queryTerms = queryTerms
            )
            val relatedSongScore = release.tracks.maxOfOrNull { track ->
                songScores[track.id] ?: 0
            } ?: 0
            val relatedScore = relatedSongScore * RELATED_ALBUM_SCORE_PERCENT / 100
            val score = maxOf(directScore, relatedScore)

            if (score > 0) {
                albumResults += SearchAlbumResult(release, score)
            }
        }

        val sortedAlbums = albumResults.sortedWith(
            compareByDescending<SearchAlbumResult> { it.score }
                .thenBy { normalizeSearchText(it.release.title) }
                .thenBy { it.release.key }
        )

        val releaseScoresByArtist = linkedMapOf<String, Int>()
        sortedAlbums.forEach { result ->
            result.release.artists.forEach { artistName ->
                val identity = normalizeSearchText(artistName)
                if (identity.isNotBlank()) {
                    val score = result.score * RELATED_ARTIST_RELEASE_SCORE_PERCENT / 100
                    releaseScoresByArtist[identity] = maxOf(
                        releaseScoresByArtist[identity] ?: 0,
                        score
                    )
                }
            }
        }

        val artistResults = ArrayList<SearchArtistResult>()
        index.artists.forEachIndexed { artistIndex, artist ->
            if (artistIndex % SEARCH_CANCELLATION_CHECK_INTERVAL == 0) {
                activeContext.ensureActive()
            }

            val directScore = scoreFields(
                fields = artist.fields,
                normalizedQuery = normalizedQuery,
                queryTerms = queryTerms
            )
            val relatedSongScore = artist.tracks.maxOfOrNull { track ->
                songScores[track.id] ?: 0
            } ?: 0
            val relatedReleaseScore = releaseScoresByArtist[
                normalizeSearchText(artist.name)
            ] ?: 0

            val score = maxOf(
                directScore,
                relatedSongScore * RELATED_ARTIST_SONG_SCORE_PERCENT / 100,
                relatedReleaseScore
            )

            if (score > 0) {
                val artworkTrack = artist.tracks.maxWithOrNull(
                    compareBy<LocalAudioTrack> { it.dateAddedSeconds }
                        .thenBy { it.dateModifiedSeconds }
                )

                artistResults += SearchArtistResult(
                    name = artist.name,
                    artworkUri = artworkTrack?.uri?.toString(),
                    trackCount = artist.tracks.size,
                    score = score
                )
            }
        }

        val sortedArtists = artistResults.sortedWith(
            compareByDescending<SearchArtistResult> { it.score }
                .thenBy { normalizeSearchText(it.name) }
        )

        return SearchResults(
            songs = songResults,
            artists = sortedArtists,
            albums = sortedAlbums
        )
    }

    private fun MutableList<SearchIndexField>.addField(
        value: String?,
        weight: Int
    ) {
        createField(value, weight)?.let(::add)
    }

    private fun createField(
        value: String?,
        weight: Int
    ): SearchIndexField? {
        val normalized = normalizeSearchText(value.orEmpty())
        if (normalized.isBlank()) return null

        return SearchIndexField(
            normalized = normalized,
            words = normalized.split(SearchWhitespace).filter(String::isNotBlank).distinct(),
            weight = weight
        )
    }

    private fun scoreFields(
        fields: List<SearchIndexField>,
        normalizedQuery: String,
        queryTerms: List<String>
    ): Int {
        if (fields.isEmpty()) return 0

        val phraseScore = fields.maxOfOrNull { field ->
            when {
                field.normalized == normalizedQuery -> 12_000 + field.weight * 300
                field.normalized.startsWith(normalizedQuery) -> 8_000 + field.weight * 200
                field.normalized.contains(normalizedQuery) -> 6_000 + field.weight * 150
                else -> 0
            }
        } ?: 0

        val termScores = queryTerms.map { term ->
            fields.maxOfOrNull { field ->
                scoreTerm(term, field) * field.weight
            } ?: 0
        }

        if (termScores.any { it == 0 }) return 0
        return phraseScore + termScores.sum()
    }

    private fun scoreTerm(
        term: String,
        field: SearchIndexField
    ): Int {
        var bestScore = 0
        field.words.forEach { word ->
            val score = when {
                word == term -> 100
                word.startsWith(term) -> 70
                word.contains(term) -> 50
                term.length >= 3 -> {
                    val maxDistance = if (term.length <= 4) 1 else 2
                    if (
                        abs(word.length - term.length) <= maxDistance &&
                        editDistanceWithin(term, word, maxDistance)
                    ) {
                        25
                    } else {
                        0
                    }
                }
                else -> 0
            }
            bestScore = maxOf(bestScore, score)
        }
        return bestScore
    }

    private fun normalizeSearchText(value: String): String =
        Normalizer.normalize(value, Normalizer.Form.NFD)
            .replace(SearchDiacritics, "")
            .lowercase(Locale.ROOT)
            .replace(SearchWordSeparator, " ")
            .replace(SearchWhitespace, " ")
            .trim()

    private fun editDistanceWithin(
        left: String,
        right: String,
        maxDistance: Int
    ): Boolean {
        if (abs(left.length - right.length) > maxDistance) return false

        val outsideRange = maxDistance + 1
        var previousRow = IntArray(right.length + 1) { index ->
            if (index <= maxDistance) index else outsideRange
        }

        for (leftIndex in 1..left.length) {
            val currentRow = IntArray(right.length + 1) { outsideRange }
            if (leftIndex <= maxDistance) currentRow[0] = leftIndex

            val startColumn = maxOf(1, leftIndex - maxDistance)
            val endColumn = minOf(right.length, leftIndex + maxDistance)
            var rowMinimum = outsideRange

            for (rightIndex in startColumn..endColumn) {
                val substitutionCost = if (
                    left[leftIndex - 1] == right[rightIndex - 1]
                ) 0 else 1

                currentRow[rightIndex] = minOf(
                    currentRow[rightIndex - 1] + 1,
                    previousRow[rightIndex] + 1,
                    previousRow[rightIndex - 1] + substitutionCost
                )
                rowMinimum = minOf(rowMinimum, currentRow[rightIndex])
            }

            if (rowMinimum > maxDistance) return false
            previousRow = currentRow
        }

        return previousRow[right.length] <= maxDistance
    }

    private const val RELATED_ALBUM_SCORE_PERCENT = 72
    private const val RELATED_ARTIST_SONG_SCORE_PERCENT = 70
    private const val RELATED_ARTIST_RELEASE_SCORE_PERCENT = 55
}
