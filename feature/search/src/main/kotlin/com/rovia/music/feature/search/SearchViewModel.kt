
package com.rovia.music.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rovia.music.core.library.MusicRepository
import com.rovia.music.core.model.Track
import java.text.Normalizer
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModel(
    musicRepository: MusicRepository,
) : ViewModel() {

    private val query = MutableStateFlow("")

    /*
     * Observe the shared catalog rather than requesting a one-time
     * snapshot from getAllTracks().
     *
     * The Room-backed repository will emit updates when the catalog
     * changes after MediaStore synchronization.
     */
    private val allTracks: Flow<List<Track>> =
        musicRepository.observeAllTracks()

    private val indexedTracks: Flow<List<SearchEntry>> =
        allTracks
            .map { tracks ->
                tracks.map { track ->
                    SearchEntry(
                        track = track,
                        title = normalize(track.title),
                        artist = normalize(track.artist.orEmpty()),
                        album = normalize(track.album.orEmpty()),
                    )
                }
            }
            .flowOn(Dispatchers.Default)

    val uiState: StateFlow<SearchUiState> =
        combine(
            indexedTracks,
            query,
        ) { tracks, currentQuery ->
            tracks to currentQuery
        }
            .mapLatest { (tracks, currentQuery) ->
                if (currentQuery.isBlank()) {
                    SearchUiState.Content(
                        query = currentQuery,
                        results = emptyList(),
                    )
                } else {
                    SearchUiState.Content(
                        query = currentQuery,
                        results = searchTracks(
                            entries = tracks,
                            query = currentQuery,
                        ),
                    )
                }
            }
            .flowOn(Dispatchers.Default)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = SearchUiState.Loading,
            )

    fun setQuery(value: String) {
        query.value = value
    }

    private fun searchTracks(
        entries: List<SearchEntry>,
        query: String,
    ): List<Track> {
        val normalizedQuery = normalize(query)

        if (normalizedQuery.isBlank()) {
            return emptyList()
        }

        val queryTokens =
            normalizedQuery.split(' ')

        return entries
            .asSequence()
            .mapNotNull { entry ->
                val score =
                    scoreTrack(
                        entry = entry,
                        query = normalizedQuery,
                        queryTokens = queryTokens,
                    )

                if (score > 0) {
                    entry.track to score
                } else {
                    null
                }
            }
            .sortedByDescending { it.second }
            .take(MAX_RESULTS)
            .map { it.first }
            .toList()
    }

    private fun scoreTrack(
        entry: SearchEntry,
        query: String,
        queryTokens: List<String>,
    ): Int {
        val titleScore =
            scoreField(
                value = entry.title,
                query = query,
                queryTokens = queryTokens,
            )

        val artistScore =
            scoreField(
                value = entry.artist,
                query = query,
                queryTokens = queryTokens,
            )

        val albumScore =
            scoreField(
                value = entry.album,
                query = query,
                queryTokens = queryTokens,
            )

        val weightedArtistScore =
            (artistScore * ARTIST_WEIGHT).toInt()

        val weightedAlbumScore =
            (albumScore * ALBUM_WEIGHT).toInt()

        return max(
            titleScore,
            max(
                weightedArtistScore,
                weightedAlbumScore,
            ),
        )
    }

    private fun scoreField(
        value: String,
        query: String,
        queryTokens: List<String>,
    ): Int {
        if (value.isBlank()) {
            return 0
        }

        if (value == query) {
            return 1_000
        }

        if (value.startsWith(query)) {
            return 900
        }

        if (value.contains(query)) {
            return 800
        }

        val fieldTokens =
            value.split(' ')

        var totalScore = 0

        for (queryToken in queryTokens) {
            var bestTokenScore = 0

            for (fieldToken in fieldTokens) {
                if (fieldToken == queryToken) {
                    bestTokenScore =
                        max(
                            bestTokenScore,
                            850,
                        )
                    continue
                }

                if (fieldToken.startsWith(queryToken)) {
                    bestTokenScore =
                        max(
                            bestTokenScore,
                            750,
                        )
                    continue
                }

                if (fieldToken.contains(queryToken)) {
                    bestTokenScore =
                        max(
                            bestTokenScore,
                            650,
                        )
                    continue
                }

                if (
                    queryToken.length >= 3 &&
                    fieldToken.length >= 3
                ) {
                    val similarity =
                        similarity(
                            queryToken,
                            fieldToken,
                        )

                    if (similarity >= FUZZY_THRESHOLD) {
                        val fuzzyScore =
                            350 +
                                (similarity * 300.0).toInt()

                        bestTokenScore =
                            max(
                                bestTokenScore,
                                fuzzyScore,
                            )
                    }
                }
            }

            if (bestTokenScore == 0) {
                return 0
            }

            totalScore += bestTokenScore
        }

        return totalScore / queryTokens.size
    }

    private fun similarity(
        first: String,
        second: String,
    ): Double {
        val distance =
            levenshteinDistance(
                first,
                second,
            )

        val longestLength =
            max(
                first.length,
                second.length,
            )

        if (longestLength == 0) {
            return 1.0
        }

        return 1.0 -
            (
                distance.toDouble() /
                    longestLength
            )
    }

    private fun levenshteinDistance(
        first: String,
        second: String,
    ): Int {
        if (first == second) {
            return 0
        }

        if (first.isEmpty()) {
            return second.length
        }

        if (second.isEmpty()) {
            return first.length
        }

        val previousRow =
            IntArray(second.length + 1) {
                it
            }

        val currentRow =
            IntArray(second.length + 1)

        for (i in first.indices) {
            currentRow[0] = i + 1

            for (j in second.indices) {
                val insertion =
                    currentRow[j] + 1

                val deletion =
                    previousRow[j + 1] + 1

                val substitution =
                    previousRow[j] +
                        if (first[i] == second[j]) {
                            0
                        } else {
                            1
                        }

                currentRow[j + 1] =
                    min(
                        insertion,
                        min(
                            deletion,
                            substitution,
                        ),
                    )
            }

            System.arraycopy(
                currentRow,
                0,
                previousRow,
                0,
                currentRow.size,
            )
        }

        return previousRow[second.length]
    }

    private fun normalize(
        value: String,
    ): String {
        return Normalizer
            .normalize(
                value,
                Normalizer.Form.NFD,
            )
            .replace(
                DIACRITICS_REGEX,
                "",
            )
            .lowercase(Locale.ROOT)
            .trim()
            .replace(
                MULTIPLE_SPACES_REGEX,
                " ",
            )
    }

    private data class SearchEntry(
        val track: Track,
        val title: String,
        val artist: String,
        val album: String,
    )

    private companion object {
        const val MAX_RESULTS = 50
        const val ARTIST_WEIGHT = 0.85
        const val ALBUM_WEIGHT = 0.65
        const val FUZZY_THRESHOLD = 0.55

        val DIACRITICS_REGEX =
            "\\p{Mn}+".toRegex()

        val MULTIPLE_SPACES_REGEX =
            "\\s+".toRegex()
    }
}
