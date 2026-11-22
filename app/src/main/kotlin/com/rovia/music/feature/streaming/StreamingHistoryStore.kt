package com.rovia.music.feature.streaming

import android.content.Context
import com.rovia.music.playback.streaming.streamLabel
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

/** One recent stream: the address the user played and the label it goes by. */
data class StreamingHistoryEntry(
    val url: String,
    val label: String,
)

/**
 * Recently played streams, stored as one JSON file in the app's cache
 * directory — never in the database, so clearing the app cache removes the
 * history with it. Most recent first; recording an address that is already
 * listed moves it back to the top.
 */
class StreamingHistoryStore(context: Context) {

    private val file = File(context.applicationContext.cacheDir, FILE_NAME)
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _entries = MutableStateFlow(readEntries())
    val entries: StateFlow<List<StreamingHistoryEntry>> = _entries.asStateFlow()

    fun record(rawUrl: String) {
        val url = rawUrl.trim()
        if (url.isEmpty()) return
        val entry = StreamingHistoryEntry(url = url, label = streamLabel(url))
        val remaining = _entries.value.filterNot { existing ->
            existing.url.equals(entry.url, ignoreCase = true)
        }
        update(listOf(entry) + remaining)
    }

    fun remove(entry: StreamingHistoryEntry) {
        update(
            _entries.value.filterNot { existing ->
                existing.url.equals(entry.url, ignoreCase = true)
            },
        )
    }

    fun clear() {
        update(emptyList())
    }

    private fun update(value: List<StreamingHistoryEntry>) {
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

    private fun readEntries(): List<StreamingHistoryEntry> {
        if (!file.isFile) return emptyList()
        return runCatching {
            val array = JSONArray(file.readText())
            List(array.length()) { index ->
                decode(array.getJSONObject(index))
            }
        }.getOrDefault(emptyList())
    }

    private fun encode(entries: List<StreamingHistoryEntry>): String {
        val array = JSONArray()
        entries.forEach { entry ->
            array.put(
                JSONObject()
                    .put("url", entry.url)
                    .put("label", entry.label),
            )
        }
        return array.toString()
    }

    private fun decode(json: JSONObject): StreamingHistoryEntry =
        StreamingHistoryEntry(
            url = json.getString("url"),
            label = json.optString("label")
                .takeIf { it.isNotEmpty() }
                ?: streamLabel(json.getString("url")),
        )

    private companion object {
        const val FILE_NAME = "streaming_history.json"
    }
}
