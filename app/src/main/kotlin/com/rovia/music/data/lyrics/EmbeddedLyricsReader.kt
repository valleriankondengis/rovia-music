package com.rovia.music.data.lyrics

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.Metadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.source.TrackGroupArray
import androidx.media3.extractor.metadata.id3.BinaryFrame
import androidx.media3.extractor.metadata.id3.CommentFrame
import androidx.media3.extractor.metadata.id3.TextInformationFrame
import androidx.media3.extractor.metadata.vorbis.VorbisComment
import androidx.media3.inspector.MetadataRetriever
import com.rovia.music.ui.components.LyricLine
import com.rovia.music.ui.components.LyricWord
import com.rovia.music.ui.components.SyncedLyrics
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.concurrent.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(UnstableApi::class)
object EmbeddedLyricsReader {
    suspend fun read(
        context: Context,
        mediaItem: MediaItem,
    ): SyncedLyrics? = withContext(Dispatchers.IO) {
        val retriever = try {
            MetadataRetriever.Builder(
                context.applicationContext,
                mediaItem,
            ).build()
        } catch (_: Exception) {
            return@withContext null
        }

        try {
            extractLyrics(retriever.retrieveTrackGroups().get())
        } catch (exception: CancellationException) {
            throw exception
        } catch (_: Exception) {
            null
        } finally {
            retriever.close()
        }
    }

    private fun extractLyrics(
        trackGroups: TrackGroupArray,
    ): SyncedLyrics? {
        var synchronizedLyrics: SyncedLyrics? = null
        var fallbackLyrics: SyncedLyrics? = null

        for (groupIndex in 0 until trackGroups.length) {
            val group = trackGroups.get(groupIndex)

            for (formatIndex in 0 until group.length) {
                val metadata = group.getFormat(formatIndex).metadata ?: continue

                for (metadataIndex in 0 until metadata.length()) {
                    val lyrics = parseEntry(metadata[metadataIndex]) ?: continue

                    if (hasMeaningfulTimestamps(lyrics)) {
                        if (synchronizedLyrics == null) {
                            synchronizedLyrics = lyrics
                        }
                    } else if (fallbackLyrics == null) {
                        fallbackLyrics = lyrics
                    }
                }
            }
        }

        return synchronizedLyrics ?: fallbackLyrics
    }

    private fun parseEntry(
        entry: Metadata.Entry,
    ): SyncedLyrics? = when (entry) {
        is BinaryFrame -> when (entry.id.uppercase(Locale.ROOT)) {
            "USLT", "ULT" -> parseLyricsText(decodeUnsynchronizedLyrics(entry.data))
            "SYLT", "SLT" -> decodeSynchronizedLyrics(entry.data)
            else -> null
        }

        is VorbisComment -> {
            if (entry.key.uppercase(Locale.ROOT) in LYRICS_COMMENT_KEYS) {
                parseLyricsText(entry.value)
            } else {
                null
            }
        }

        is TextInformationFrame -> {
            val description = entry.description.orEmpty()
            if (
                entry.id.equals("TXXX", ignoreCase = true) &&
                description.uppercase(Locale.ROOT) in LYRICS_COMMENT_KEYS
            ) {
                entry.values.firstNotNullOfOrNull(::parseLyricsText)
            } else {
                null
            }
        }

        is CommentFrame -> {
            if (entry.description.contains("lyric", ignoreCase = true)) {
                parseLyricsText(entry.text)
            } else {
                null
            }
        }

        else -> null
    }

    private fun decodeUnsynchronizedLyrics(
        data: ByteArray,
    ): String? {
        if (data.size < 5) return null

        val encoding = data[0].toInt() and 0xFF
        val charset = charsetFor(encoding) ?: return null
        val descriptionStart = 4
        val descriptionEnd = findTerminator(data, descriptionStart, encoding)
        if (descriptionEnd >= data.size) return null

        val lyricsStart = (descriptionEnd + terminatorLength(encoding))
            .coerceAtMost(data.size)

        return decodeText(data, lyricsStart, data.size, charset)
            .trim()
            .takeIf(String::isNotBlank)
    }

    private fun decodeSynchronizedLyrics(
        data: ByteArray,
    ): SyncedLyrics? {
        if (data.size < 7) return null

        val encoding = data[0].toInt() and 0xFF
        val charset = charsetFor(encoding) ?: return null
        val timestampFormat = data[4].toInt() and 0xFF
        var cursor = 6
        val descriptionEnd = findTerminator(data, cursor, encoding)
        if (descriptionEnd >= data.size) return null
        cursor = (descriptionEnd + terminatorLength(encoding)).coerceAtMost(data.size)

        val timedTexts = mutableListOf<TimedText>()

        while (cursor < data.size) {
            val textEnd = findTerminator(data, cursor, encoding)
            if (textEnd >= data.size) break

            val text = decodeText(data, cursor, textEnd, charset).trim()
            cursor = textEnd + terminatorLength(encoding)

            if (cursor + 4 > data.size) break

            val timestamp = readUnsignedInt(data, cursor)
            cursor += 4

            val timestampMs = when (timestampFormat) {
                2 -> timestamp
                1 -> timestamp * MPEG_FRAME_DURATION_MS
                else -> continue
            }

            if (text.isNotBlank()) {
                text.replace("\r\n", "\n")
                    .replace('\r', '\n')
                    .lines()
                    .map(String::trim)
                    .filter(String::isNotBlank)
                    .forEach { line ->
                        val cleanedLine = stripSingerPrefix(line)
                        if (cleanedLine.isNotBlank()) {
                            timedTexts += TimedText(cleanedLine, timestampMs)
                        }
                    }
            }
        }

        return timedTexts
            .takeIf { it.isNotEmpty() }
            ?.let(::groupTimedTexts)
    }

    private fun groupTimedTexts(
        timedTexts: List<TimedText>,
    ): SyncedLyrics? {
        val orderedTexts = timedTexts.sortedBy(TimedText::timestampMs)
        val lines = mutableListOf<LyricLine>()
        var words = mutableListOf<LyricWord>()
        var lineStartMs = 0L
        var previousTimestampMs: Long? = null

        fun flushLine() {
            if (words.isNotEmpty()) {
                lines += LyricLine(
                    startTimeMs = lineStartMs,
                    words = words.toList(),
                )
                words = mutableListOf()
            }
        }

        orderedTexts.forEach { timedText ->
            val gap = previousTimestampMs?.let {
                timedText.timestampMs - it
            }

            if (words.isNotEmpty() && gap != null && gap > SYLT_LINE_GAP_MS) {
                flushLine()
            }

            if (words.isEmpty()) {
                lineStartMs = timedText.timestampMs
            }

            words += LyricWord(
                text = timedText.text,
                startTimeMs = timedText.timestampMs,
            )
            previousTimestampMs = timedText.timestampMs

            if (timedText.text.endsWithAny(".", "?", "!", "…")) {
                flushLine()
                previousTimestampMs = null
            }
        }

        flushLine()

        return lines
            .takeIf { it.isNotEmpty() }
            ?.let(::SyncedLyrics)
    }

    private fun parseLyricsText(
        rawText: String?,
    ): SyncedLyrics? {
        val text = rawText
            ?.replace("\u0000", "")
            ?.replace("\r\n", "\n")
            ?.replace('\r', '\n')
            ?.removePrefix("\uFEFF")
            ?.trim()
            ?.takeIf(String::isNotBlank)
            ?: return null

        return parseLrc(text) ?: parsePlainText(text)
    }

    private fun parseLrc(
        text: String,
    ): SyncedLyrics? {
        val lines = mutableListOf<LyricLine>()

        text.lineSequence().forEach { sourceLine ->
            val timestampMatches = LINE_TIMESTAMP_REGEX.findAll(sourceLine).toList()
            if (timestampMatches.isEmpty()) return@forEach

            val lyricText = stripSingerPrefix(
                LINE_TIMESTAMP_REGEX.replace(sourceLine, "").trim(),
            )
            if (lyricText.isBlank()) return@forEach

            val words = parseEnhancedWords(
                text = lyricText,
                fallbackTimestampMs = parseTimestamp(timestampMatches.first()),
            )

            timestampMatches.forEach { timestampMatch ->
                val timestampMs = parseTimestamp(timestampMatch)
                lines += LyricLine(
                    startTimeMs = timestampMs,
                    words = words.map { word ->
                        if (WORD_TIMESTAMP_REGEX.containsMatchIn(lyricText)) {
                            word
                        } else {
                            word.copy(startTimeMs = timestampMs)
                        }
                    },
                )
            }
        }

        return lines
            .takeIf { it.isNotEmpty() }
            ?.sortedBy(LyricLine::startTimeMs)
            ?.let(::SyncedLyrics)
    }

    private fun parseEnhancedWords(
        text: String,
        fallbackTimestampMs: Long,
    ): List<LyricWord> {
        val timestampMatches = WORD_TIMESTAMP_REGEX.findAll(text).toList()
        if (timestampMatches.isEmpty()) {
            return listOf(
                LyricWord(
                    text = text.trim(),
                    startTimeMs = fallbackTimestampMs,
                ),
            )
        }

        val words = mutableListOf<LyricWord>()
        var cursor = 0
        var currentTimestampMs = fallbackTimestampMs

        timestampMatches.forEach { match ->
            appendWords(
                words = words,
                text = text.substring(cursor, match.range.first),
                timestampMs = currentTimestampMs,
            )
            currentTimestampMs = parseTimestamp(match)
            cursor = match.range.last + 1
        }

        appendWords(
            words = words,
            text = text.substring(cursor),
            timestampMs = currentTimestampMs,
        )

        return words.takeIf { it.isNotEmpty() }
            ?: listOf(LyricWord(text.trim(), fallbackTimestampMs))
    }

    private fun appendWords(
        words: MutableList<LyricWord>,
        text: String,
        timestampMs: Long,
    ) {
        WORD_TOKEN_REGEX.findAll(text).forEach { match ->
            words += LyricWord(
                text = match.value,
                startTimeMs = timestampMs,
            )
        }
    }

    private fun stripSingerPrefix(text: String): String =
        SINGER_PREFIX_REGEX.replaceFirst(text, "").trimStart()

    private fun parseTimestamp(
        match: MatchResult,
    ): Long {
        val minutes = match.groupValues[1].toLongOrNull() ?: 0L
        val seconds = match.groupValues[2].toLongOrNull() ?: 0L
        val fraction = match.groupValues.getOrNull(3).orEmpty()
        val fractionMs = when (fraction.length) {
            0 -> 0L
            1 -> (fraction.toLongOrNull() ?: 0L) * 100L
            2 -> (fraction.toLongOrNull() ?: 0L) * 10L
            else -> fraction.take(3).toLongOrNull() ?: 0L
        }

        return (minutes * 60_000L) + (seconds * 1_000L) + fractionMs
    }

    private fun parsePlainText(
        text: String,
    ): SyncedLyrics? {
        val lines = text.lineSequence()
            .map(String::trim)
            .map(::stripSingerPrefix)
            .filter(String::isNotBlank)
            .map { line ->
                LyricLine(
                    startTimeMs = 0L,
                    words = listOf(
                        LyricWord(
                            text = line,
                            startTimeMs = 0L,
                        ),
                    ),
                )
            }
            .toList()

        return lines
            .takeIf { it.isNotEmpty() }
            ?.let(::SyncedLyrics)
    }

    private fun hasMeaningfulTimestamps(
        lyrics: SyncedLyrics,
    ): Boolean {
        val hasLineProgression = lyrics.lines
            .zipWithNext()
            .any { (current, next) ->
                next.startTimeMs > current.startTimeMs
            }

        if (hasLineProgression) return true

        return lyrics.lines.any { line ->
            line.words.zipWithNext().any { (current, next) ->
                next.startTimeMs > current.startTimeMs
            }
        }
    }

    private fun charsetFor(
        encoding: Int,
    ): Charset? = when (encoding) {
        0 -> StandardCharsets.ISO_8859_1
        1 -> StandardCharsets.UTF_16
        2 -> StandardCharsets.UTF_16BE
        3 -> StandardCharsets.UTF_8
        else -> null
    }

    private fun decodeText(
        data: ByteArray,
        start: Int,
        end: Int,
        charset: Charset,
    ): String {
        if (start >= end || start !in 0..data.size || end > data.size) {
            return ""
        }

        return String(data, start, end - start, charset).removePrefix("\uFEFF")
    }

    private fun findTerminator(
        data: ByteArray,
        start: Int,
        encoding: Int,
    ): Int {
        if (encoding == 1 || encoding == 2) {
            var index = start
            while (index + 1 < data.size) {
                if (data[index] == 0.toByte() && data[index + 1] == 0.toByte()) {
                    return index
                }
                index += 2
            }
            return data.size
        }

        for (index in start.coerceAtLeast(0) until data.size) {
            if (data[index] == 0.toByte()) return index
        }

        return data.size
    }

    private fun terminatorLength(
        encoding: Int,
    ): Int = if (encoding == 1 || encoding == 2) 2 else 1

    private fun readUnsignedInt(
        data: ByteArray,
        start: Int,
    ): Long =
        ((data[start].toLong() and 0xFFL) shl 24) or
            ((data[start + 1].toLong() and 0xFFL) shl 16) or
            ((data[start + 2].toLong() and 0xFFL) shl 8) or
            (data[start + 3].toLong() and 0xFFL)

    private fun String.endsWithAny(
        vararg suffixes: String,
    ): Boolean = suffixes.any(::endsWith)

    private data class TimedText(
        val text: String,
        val timestampMs: Long,
    )

    private val LINE_TIMESTAMP_REGEX = Regex("""\[(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?]""")
    private val WORD_TIMESTAMP_REGEX = Regex("""<(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?>""")
    private val WORD_TOKEN_REGEX = Regex("\\S+")
    private val SINGER_PREFIX_REGEX = Regex(
        """^\s*(?:v\d+|\d+|bg)\s*:\s*""",
        RegexOption.IGNORE_CASE,
    )
    private val LYRICS_COMMENT_KEYS = setOf(
        "LYRICS",
        "UNSYNCEDLYRICS",
        "SYNCEDLYRICS",
        "LYRIC",
        "UNSYNCED_LYRICS",
        "SYNCED_LYRICS",
    )
    private const val MPEG_FRAME_DURATION_MS = 26L
    private const val SYLT_LINE_GAP_MS = 1_250L
}