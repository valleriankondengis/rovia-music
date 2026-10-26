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
                        val (singerId, cleanedLine) = parseSingerPrefix(line)
                        if (cleanedLine.isNotBlank()) {
                            timedTexts += TimedText(
                                text = cleanedLine,
                                timestampMs = timestampMs,
                                singerId = singerId,
                            )
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
        var lineSingerId: String? = null
        var previousTimestampMs: Long? = null

        fun flushLine() {
            if (words.isNotEmpty()) {
                lines += LyricLine(
                    startTimeMs = lineStartMs,
                    words = words.toList(),
                    singerId = lineSingerId,
                )
                words = mutableListOf()
            }
        }

        orderedTexts.forEach { timedText ->
            val gap = previousTimestampMs?.let {
                timedText.timestampMs - it
            }

            if (
                words.isNotEmpty() &&
                (timedText.singerId != lineSingerId ||
                    (gap != null && gap > SYLT_LINE_GAP_MS))
            ) {
                flushLine()
            }

            if (words.isEmpty()) {
                lineStartMs = timedText.timestampMs
                lineSingerId = timedText.singerId
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
        val backgroundLines = mutableListOf<LyricLine>()

        text.lineSequence().forEach { sourceLine ->
            val backgroundPrefix = BACKGROUND_LINE_PREFIX_REGEX.find(sourceLine)
            if (backgroundPrefix != null) {
                val payload = sourceLine
                    .substring(backgroundPrefix.range.last + 1)
                    .substringBeforeLast("]")
                    .trim()
                val wordTimestamps = WORD_TIMESTAMP_REGEX.findAll(payload).toList()
                if (wordTimestamps.isEmpty()) return@forEach

                val startTimeMs = parseTimestamp(wordTimestamps.first())
                val words = parseEnhancedWords(
                    text = payload,
                    fallbackTimestampMs = startTimeMs,
                )
                if (words.isEmpty()) return@forEach

                val lastTimestamp = wordTimestamps.last()
                val hasExplicitCueEnd = payload
                    .substring(lastTimestamp.range.last + 1)
                    .isBlank()
                val explicitEndTimeMs = if (hasExplicitCueEnd) {
                    parseTimestamp(lastTimestamp).takeIf { it > startTimeMs }
                } else {
                    null
                }
                val inferredEndTimeMs = inferCueEndTimeMs(
                    words = words,
                    startTimeMs = startTimeMs,
                )

                backgroundLines += LyricLine(
                    startTimeMs = startTimeMs,
                    words = words,
                    isBackground = true,
                    endTimeMs = explicitEndTimeMs ?: inferredEndTimeMs,
                )
                return@forEach
            }

            val timestampMatches = LINE_TIMESTAMP_REGEX.findAll(sourceLine).toList()
            if (timestampMatches.isEmpty()) return@forEach

            val textAfterLineTimestamps =
                LINE_TIMESTAMP_REGEX.replace(sourceLine, "").trim()
            val (singerId, lyricText) = parseSingerPrefix(textAfterLineTimestamps)
            if (lyricText.isBlank()) return@forEach

            val words = parseEnhancedWords(
                text = lyricText,
                fallbackTimestampMs = parseTimestamp(timestampMatches.first()),
            )
            val innerWordTimestamps = WORD_TIMESTAMP_REGEX.findAll(lyricText).toList()
            val lastWordTimestamp = innerWordTimestamps.lastOrNull()
            val hasExplicitWordEnd = lastWordTimestamp != null && lyricText
                .substring(lastWordTimestamp.range.last + 1)
                .isBlank()
            val explicitWordEndMs = if (hasExplicitWordEnd) {
                lastWordTimestamp.let(::parseTimestamp)
            } else {
                null
            }

            timestampMatches.forEach { timestampMatch ->
                val timestampMs = parseTimestamp(timestampMatch)
                val adjustedWords = words.map { word ->
                    if (WORD_TIMESTAMP_REGEX.containsMatchIn(lyricText)) {
                        word
                    } else {
                        word.copy(startTimeMs = timestampMs)
                    }
                }
                lines += LyricLine(
                    startTimeMs = timestampMs,
                    words = adjustedWords,
                    singerId = singerId,
                    endTimeMs = explicitWordEndMs?.takeIf { it > timestampMs },
                )
            }
        }

        val orderedLines = lines.sortedBy(LyricLine::startTimeMs)
        if (orderedLines.isEmpty()) return null

        val backgroundsByLineIndex = mutableMapOf<Int, MutableList<LyricLine>>()
        backgroundLines.sortedBy(LyricLine::startTimeMs).forEach { background ->
            // Keep a BG cue tied to the latest main lyric already underway at its start.
            // This allows the next main line to begin while the previous BG is still active.
            val parentIndex = orderedLines.indexOfLast { it.startTimeMs <= background.startTimeMs }
                .let { index -> if (index >= 0) index else 0 }
            backgroundsByLineIndex
                .getOrPut(parentIndex) { mutableListOf() }
                .add(background)
        }

        return SyncedLyrics(
            lines = orderedLines.mapIndexed { index, line ->
                line.copy(
                    backgroundVocals = backgroundsByLineIndex[index]
                        .orEmpty()
                        .sortedBy(LyricLine::startTimeMs),
                )
            },
        )
    }

    private fun inferCueEndTimeMs(
        words: List<LyricWord>,
        startTimeMs: Long,
    ): Long {
        val lastWord = words.lastOrNull() ?: return startTimeMs + 500L
        val previousWord = words.getOrNull(words.lastIndex - 1)
        val estimatedDuration = previousWord
            ?.let { lastWord.startTimeMs - it.startTimeMs }
            ?.takeIf { it > 0L }
            ?.coerceIn(250L, 900L)
            ?: 500L
        return maxOf(startTimeMs + 1L, lastWord.startTimeMs + estimatedDuration)
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

    private fun parseSingerPrefix(text: String): Pair<String?, String> {
        val match = SINGER_PREFIX_REGEX.find(text) ?: return null to text.trimStart()
        val rawId = match.groupValues[1].lowercase(Locale.ROOT)
        val singerId = if (rawId.all(Char::isDigit)) "v$rawId" else rawId
        return singerId to text.removeRange(match.range).trimStart()
    }

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
            .filter(String::isNotBlank)
            .map { sourceLine ->
                val (singerId, line) = parseSingerPrefix(sourceLine)
                LyricLine(
                    startTimeMs = 0L,
                    words = listOf(
                        LyricWord(
                            text = line,
                            startTimeMs = 0L,
                        ),
                    ),
                    singerId = singerId,
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
        val singerId: String? = null,
    )

    private val LINE_TIMESTAMP_REGEX = Regex("""\[(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?]""")
    private val WORD_TIMESTAMP_REGEX = Regex("""<(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?>""")
    private val WORD_TOKEN_REGEX = Regex("\\S+")
    private val BACKGROUND_LINE_PREFIX_REGEX = Regex(
        """^\s*\[\s*bg\s*:\s*""",
        RegexOption.IGNORE_CASE,
    )
    private val SINGER_PREFIX_REGEX = Regex(
        """^\s*(v\d+|\d+)\s*:\s*""",
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