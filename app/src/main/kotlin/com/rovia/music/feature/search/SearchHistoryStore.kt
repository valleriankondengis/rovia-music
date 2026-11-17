package com.rovia.music.feature.search

import android.content.Context
import com.rovia.music.data.mediastore.LocalAudioTrack
import com.rovia.music.data.repository.SearchAlbumResult
import com.rovia.music.data.repository.SearchArtistResult
import com.rovia.music.data.repository.SearchGenreResult
import com.rovia.music.data.repository.SearchPlaylistResult
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/** The kinds of search results that can reappear as recent-search shortcuts. */
enum class SearchHistoryType {
    TRACK,
    ARTIST,
    ALBUM,
    GENRE,
    PLAYLIST
}

/**
 * One recent-search shortcut: a snapshot of a result the user opened, with
 * everything its history row renders and everything needed to reopen it.
 * [key] is the navigation identity for the entry's type (the artist name,
 * the album release key, the genre key, the playlist id, or the track's
 * volume and MediaStore id pair).
 */
data class SearchHistoryEntry(
    val type: SearchHistoryType,
    val key: String,
    val title: String,
    val subtitle: String? = null,
    val artworkUri: String? = null,
    val trackCount: Int = -1,
    val trackId: Long = -1L,
    val volumeName: String? = null
)

fun searchHistoryTrackEntry(track: LocalAudioTrack): SearchHistoryEntry =
    SearchHistoryEntry(
        type = SearchHistoryType.TRACK,
        key = "${track.volumeName}:${track.id}",
        title = track.title.orEmpty().trim(),
        subtitle = track.artist.orEmpty().trim()
            .ifBlank { track.album.orEmpty().trim() },
        artworkUri = track.uri.toString(),
        trackId = track.id,
        volumeName = track.volumeName
    )

fun searchHistoryArtistEntry(result: SearchArtistResult): SearchHistoryEntry =
    SearchHistoryEntry(
        type = SearchHistoryType.ARTIST,
        key = result.name,
        title = result.name,
        artworkUri = result.artworkUri,
        trackCount = result.trackCount
    )

fun searchHistoryAlbumEntry(result: SearchAlbumResult): SearchHistoryEntry =
    SearchHistoryEntry(
        type = SearchHistoryType.ALBUM,
        key = result.release.key,
        title = result.release.title,
        subtitle = result.release.albumArtist
            ?.takeIf(String::isNotBlank)
            ?: result.release.artists.joinToString(", ")
                .takeIf(String::isNotBlank),
        artworkUri = result.release.artworkUri,
        trackCount = result.release.tracks.size
    )

fun searchHistoryGenreEntry(result: SearchGenreResult): SearchHistoryEntry =
    SearchHistoryEntry(
        type = SearchHistoryType.GENRE,
        key = result.genreKey,
        title = result.name,
        artworkUri = result.artworkUri,
        trackCount = result.trackCount
    )

fun searchHistoryPlaylistEntry(result: SearchPlaylistResult): SearchHistoryEntry =
    SearchHistoryEntry(
        type = SearchHistoryType.PLAYLIST,
        key = result.playlistId.toString(),
        title = result.name,
        trackCount = result.trackCount
            .coerceIn(0L, Int.MAX_VALUE.toLong())
            .toInt()
    )

/**
 * Recent searches, stored as one JSON file in the app's cache directory —
 * never in the database, so clearing the app cache removes the history with
 * it. The list has no size limit. Most recent first; recording an entry that
 * is already listed moves it back to the top.
 */
class SearchHistoryStore(context: Context) {

    private val file = File(context.applicationContext.cacheDir, FILE_NAME)
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _entries = MutableStateFlow(readEntries())
    val entries: StateFlow<List<SearchHistoryEntry>> = _entries.asStateFlow()

    fun record(entry: SearchHistoryEntry) {
        val remaining = _entries.value.filterNot { existing ->
            existing.type == entry.type &&
                existing.key.equals(entry.key, ignoreCase = true)
        }
        update(listOf(entry) + remaining)
    }

    fun remove(entry: SearchHistoryEntry) {
        update(
            _entries.value.filterNot { existing ->
                existing.type == entry.type &&
                    existing.key.equals(entry.key, ignoreCase = true)
            }
        )
    }

    fun clear() {
        update(emptyList())
    }

    private fun update(value: List<SearchHistoryEntry>) {
        _entries.value = value
        ioScope.launch {
            runCatching {
                if (value.isEmpty()) {
                    file.delete()
                } else {
                    file.writeText(encode(value))
                }
            }.onFailure { }
        }
    }

    private fun readEntries(): List<SearchHistoryEntry> {
        if (!file.isFile) return emptyList()
        return runCatching {
            val array = JSONArray(file.readText())
            List(array.length()) { index ->
                decode(array.getJSONObject(index))
            }
        }.getOrDefault(emptyList())
    }

    private fun encode(entries: List<SearchHistoryEntry>): String {
        val array = JSONArray()
        entries.forEach { entry ->
            array.put(
                JSONObject()
                    .put("type", entry.type.name)
                    .put("key", entry.key)
                    .put("title", entry.title)
                    .put("subtitle", entry.subtitle ?: JSONObject.NULL)
                    .put("artworkUri", entry.artworkUri ?: JSONObject.NULL)
                    .put("trackCount", entry.trackCount)
                    .put("trackId", entry.trackId)
                    .put("volumeName", entry.volumeName ?: JSONObject.NULL)
            )
        }
        return array.toString()
    }

    private fun decode(json: JSONObject): SearchHistoryEntry =
        SearchHistoryEntry(
            type = SearchHistoryType.valueOf(json.getString("type")),
            key = json.getString("key"),
            title = json.getString("title"),
            subtitle = json.optString("subtitle")
                .takeIf { it.isNotEmpty() && !json.isNull("subtitle") },
            artworkUri = json.optString("artworkUri")
                .takeIf { it.isNotEmpty() && !json.isNull("artworkUri") },
            trackCount = json.optInt("trackCount", -1),
            trackId = json.optLong("trackId", -1L),
            volumeName = json.optString("volumeName")
                .takeIf { it.isNotEmpty() && !json.isNull("volumeName") }
        )

    private companion object {
        const val FILE_NAME = "search_history.json"
    }
}
