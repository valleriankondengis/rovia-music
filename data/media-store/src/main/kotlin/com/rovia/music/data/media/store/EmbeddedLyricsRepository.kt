package com.rovia.music.data.media.store

import android.content.Context
import android.net.Uri
import com.rovia.music.core.library.LyricsRepository
import com.rovia.music.core.model.LyricLine
import com.rovia.music.core.model.LyricWord
import com.rovia.music.core.model.SyncedLyrics
import com.rovia.music.core.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream
import kotlin.math.min

class EmbeddedLyricsRepository(
    context: Context,
) : LyricsRepository {

    private val contentResolver =
        context.applicationContext.contentResolver

    override suspend fun getEmbeddedLyrics(
        track: Track,
    ): SyncedLyrics? =
        withContext(Dispatchers.IO) {
            runCatching {
                contentResolver
                    .openInputStream(
                        Uri.parse(track.uri),
                    )
                    ?.use { input ->
                        readEmbeddedLyrics(input)
                    }
            }.getOrNull()
        }

    private fun readEmbeddedLyrics(
        input: InputStream,
    ): SyncedLyrics? {
        val header = ByteArray(4)

        if (
            input.readFully(header) !=
                header.size
        ) {
            return null
        }

        val rawLyrics =
            when {
                header[0] == 'I'.code.toByte() &&
                    header[1] == 'D'.code.toByte() &&
                    header[2] == '3'.code.toByte() -> {
                    readId3Lyrics(
                        input = input,
                        header = header,
                    )
                }

                header.contentEquals(
                    byteArrayOf(
                        0x66,
                        0x4C,
                        0x61,
                        0x43,
                    ),
                ) -> {
                    readFlacLyrics(input)
                }

                else -> {
                    null
                }
            }

        return rawLyrics?.let(::parseLyrics)
    }

    private fun readId3Lyrics(
        input: InputStream,
        header: ByteArray,
    ): String? {
        val version =
            header[3].toInt() and 0xFF

        if (version !in 2..4) {
            return null
        }

        val tagHeader = ByteArray(6)

        if (
            input.readFully(tagHeader) !=
                tagHeader.size
        ) {
            return null
        }

        val flags =
            tagHeader[1].toInt() and 0xFF

        val tagSize =
            readSynchsafeInt(
                tagHeader,
                2,
            )

        if (
            tagSize <= 0 ||
            tagSize > MAX_TAG_SIZE
        ) {
            return null
        }

        val tagData = ByteArray(tagSize)

        if (
            input.readFully(tagData) !=
                tagData.size
        ) {
            return null
        }

        if (
            (flags and 0x40) != 0
        ) {
            return null
        }

        var offset = 0

        while (offset < tagData.size) {
            if (version == 2) {
                if (
                    offset + 6 >
                        tagData.size
                ) {
                    break
                }

                val frameId =
                    String(
                        tagData,
                        offset,
                        3,
                        Charsets.ISO_8859_1,
                    )

                if (
                    frameId.all {
                        it == '\u0000'
                    }
                ) {
                    break
                }

                val frameSize =
                    readUInt24(
                        tagData,
                        offset + 3,
                    )

                offset += 6

                if (
                    frameSize <= 0 ||
                    offset + frameSize >
                        tagData.size
                ) {
                    break
                }

                if (frameId == "ULT") {
                    return decodeLyricsFrame(
                        tagData.copyOfRange(
                            offset,
                            offset + frameSize,
                        ),
                        unsynchronised =
                            (flags and 0x80) != 0,
                    )
                }

                offset += frameSize
            } else {
                if (
                    offset + 10 >
                        tagData.size
                ) {
                    break
                }

                val frameId =
                    String(
                        tagData,
                        offset,
                        4,
                        Charsets.ISO_8859_1,
                    )

                if (
                    frameId.all {
                        it == '\u0000'
                    }
                ) {
                    break
                }

                val frameSize =
                    if (version == 4) {
                        readSynchsafeInt(
                            tagData,
                            offset + 4,
                        )
                    } else {
                        readInt32(
                            tagData,
                            offset + 4,
                        )
                    }

                offset += 10

                if (
                    frameSize <= 0 ||
                    offset + frameSize >
                        tagData.size
                ) {
                    break
                }

                if (frameId == "USLT") {
                    return decodeLyricsFrame(
                        tagData.copyOfRange(
                            offset,
                            offset + frameSize,
                        ),
                        unsynchronised =
                            (flags and 0x80) != 0,
                    )
                }

                offset += frameSize
            }
        }

        return null
    }

    private fun decodeLyricsFrame(
        frameData: ByteArray,
        unsynchronised: Boolean,
    ): String? {
        if (frameData.size <= 4) {
            return null
        }

        val data =
            if (unsynchronised) {
                removeUnsynchronisation(
                    frameData,
                )
            } else {
                frameData
            }

        val encoding =
            data[0].toInt() and 0xFF

        val descriptorEnd =
            when (encoding) {
                1,
                2 ->
                    findUtf16Terminator(
                        data,
                        4,
                    )

                0,
                3 ->
                    findSingleByteTerminator(
                        data,
                        4,
                    )

                else -> {
                    return null
                }
            }

        if (descriptorEnd < 0) {
            return null
        }

        val lyricsStart =
            when (encoding) {
                1,
                2 ->
                    descriptorEnd + 2

                else ->
                    descriptorEnd + 1
            }

        if (
            lyricsStart >= data.size
        ) {
            return null
        }

        val charset =
            when (encoding) {
                0 ->
                    Charsets.ISO_8859_1

                1 ->
                    Charsets.UTF_16

                2 ->
                    Charsets.UTF_16BE

                3 ->
                    Charsets.UTF_8

                else ->
                    return null
            }

        return runCatching {
            String(
                data,
                lyricsStart,
                data.size - lyricsStart,
                charset,
            )
        }.getOrNull()
    }

    private fun readFlacLyrics(
        input: InputStream,
    ): String? {
        var lastBlock = false

        while (!lastBlock) {
            val blockHeader = ByteArray(4)

            if (
                input.readFully(
                    blockHeader,
                ) != blockHeader.size
            ) {
                return null
            }

            lastBlock =
                (
                    blockHeader[0].toInt() and 0x80
                ) != 0

            val blockType =
                blockHeader[0].toInt() and 0x7F

            val blockSize =
                readUInt24(
                    blockHeader,
                    1,
                )

            if (
                blockSize < 0 ||
                blockSize > MAX_TAG_SIZE
            ) {
                return null
            }

            if (blockType == 4) {
                val blockData =
                    ByteArray(blockSize)

                if (
                    input.readFully(
                        blockData,
                    ) != blockData.size
                ) {
                    return null
                }

                return parseVorbisComments(
                    blockData,
                )
            }

            skipFully(
                input,
                blockSize,
            )
        }

        return null
    }

    private fun parseVorbisComments(
        data: ByteArray,
    ): String? {
        if (data.size < 8) {
            return null
        }

        var offset = 0

        val vendorLength =
            readLittleEndianInt(
                data,
                offset,
            )

        offset += 4

        if (
            vendorLength < 0 ||
            offset + vendorLength >
                data.size
        ) {
            return null
        }

        offset += vendorLength

        if (
            offset + 4 >
                data.size
        ) {
            return null
        }

        val commentCount =
            readLittleEndianInt(
                data,
                offset,
            )

        offset += 4

        if (
            commentCount < 0 ||
            commentCount > 10_000
        ) {
            return null
        }

        var fallbackLyrics: String? = null

        repeat(commentCount) {
            if (
                offset + 4 >
                    data.size
            ) {
                return@repeat
            }

            val length =
                readLittleEndianInt(
                    data,
                    offset,
                )

            offset += 4

            if (
                length <= 0 ||
                offset + length >
                    data.size
            ) {
                return@repeat
            }

            val comment =
                runCatching {
                    String(
                        data,
                        offset,
                        length,
                        Charsets.UTF_8,
                    )
                }.getOrNull()

            offset += length

            if (comment == null) {
                return@repeat
            }

            val separator =
                comment.indexOf('=')

            if (separator <= 0) {
                return@repeat
            }

            val key =
                comment
                    .substring(
                        0,
                        separator,
                    )
                    .trim()
                    .uppercase()

            val value =
                comment
                    .substring(
                        separator + 1,
                    )
                    .trim()

            if (value.isBlank()) {
                return@repeat
            }

            when {
                key == "LYRICS" ->
                    return value

                key == "UNSYNCEDLYRICS" ->
                    if (
                        fallbackLyrics == null
                    ) {
                        fallbackLyrics = value
                    }

                key.startsWith("LYRICS-") ||
                    key.startsWith("LYRICS_") ->
                    if (
                        fallbackLyrics == null
                    ) {
                        fallbackLyrics = value
                    }

                key.startsWith(
                    "UNSYNCEDLYRICS-",
                ) ||
                    key.startsWith(
                        "UNSYNCEDLYRICS_",
                    ) ->
                    if (
                        fallbackLyrics == null
                    ) {
                        fallbackLyrics = value
                    }
            }
        }

        return fallbackLyrics
    }

    private fun parseLyrics(
        rawLyrics: String,
    ): SyncedLyrics? {
        val normalized =
            rawLyrics
                .replace(
                    "\r\n",
                    "\n",
                )
                .replace(
                    '\r',
                    '\n',
                )

        val lines =
            normalized
                .lineSequence()
                .mapNotNull(::parseLyricsLine)
                .toList()

        if (lines.isEmpty()) {
            val plainText =
                normalized
                    .trim()

            if (plainText.isEmpty()) {
                return null
            }

            return SyncedLyrics(
                lines =
                    listOf(
                        LyricLine(
                            startTimeMs = 0L,
                            words =
                                listOf(
                                    LyricWord(
                                        startTimeMs = 0L,
                                        text = plainText,
                                    ),
                                ),
                        ),
                    ),
            )
        }

        return SyncedLyrics(
            lines =
                lines.sortedBy {
                    it.startTimeMs
                },
        )
    }

    private fun parseLyricsLine(
        rawLine: String,
    ): LyricLine? {
        val line =
            rawLine.trim()

        if (line.isEmpty()) {
            return null
        }

        if (
            line.startsWith(
                "[ti:",
                ignoreCase = true,
            ) ||
            line.startsWith(
                "[ar:",
                ignoreCase = true,
            ) ||
            line.startsWith(
                "[al:",
                ignoreCase = true,
            ) ||
            line.startsWith(
                "[by:",
                ignoreCase = true,
            ) ||
            line.startsWith(
                "[re:",
                ignoreCase = true,
            ) ||
            line.startsWith(
                "[ve:",
                ignoreCase = true,
            ) ||
            line.startsWith(
                "[offset:",
                ignoreCase = true,
            )
        ) {
            return null
        }

        val lineTimestamp =
            LINE_TIMESTAMP_REGEX
                .find(line)
                ?: return null

        val lineStartTimeMs =
            parseTimestamp(
                lineTimestamp.groupValues[1],
            )

        if (lineStartTimeMs < 0L) {
            return null
        }

        var lyricText =
            line.substring(
                lineTimestamp.range.last + 1,
            ).trim()

        lyricText =
            lyricText.replace(
                VARIANT_PREFIX_REGEX,
                "",
            )

        val wordMatches =
            WORD_TIMESTAMP_REGEX
                .findAll(lyricText)
                .toList()

        if (wordMatches.isEmpty()) {
            val text =
                lyricText.trim()

            if (text.isEmpty()) {
                return null
            }

            return LyricLine(
                startTimeMs = lineStartTimeMs,
                words =
                    listOf(
                        LyricWord(
                            startTimeMs =
                                lineStartTimeMs,
                            text = text,
                        ),
                    ),
            )
        }

        val words = mutableListOf<LyricWord>()

        wordMatches.forEachIndexed {
            index,
            match,
            ->
            val timestamp =
                parseTimestamp(
                    match.groupValues[1],
                )

            if (timestamp < 0L) {
                return@forEachIndexed
            }

            val textStart =
                match.range.last + 1

            val textEndExclusive =
                if (
                    index + 1 <
                        wordMatches.size
                ) {
                    wordMatches[index + 1]
                        .range
                        .first
                } else {
                    lyricText.length
                }

            if (
                textStart >= textEndExclusive
            ) {
                return@forEachIndexed
            }

            val text =
                lyricText
                    .substring(
                        textStart,
                        textEndExclusive,
                    )
                    .trim()

            if (text.isEmpty()) {
                return@forEachIndexed
            }

            words +=
                LyricWord(
                    startTimeMs = timestamp,
                    text = text,
                )
        }

        if (words.isEmpty()) {
            return null
        }

        return LyricLine(
            startTimeMs =
                min(
                    lineStartTimeMs,
                    words.first().startTimeMs,
                ),
            words = words,
        )
    }

    private fun parseTimestamp(
        timestamp: String,
    ): Long {
        val parts =
            timestamp.split(
                ":",
                limit = 3,
            )

        if (parts.size != 2) {
            return -1L
        }

        val minutes =
            parts[0]
                .toLongOrNull()
                ?: return -1L

        val secondsPart = parts[1]

        val secondsParts =
            secondsPart.split(
                ".",
                limit = 2,
            )

        val seconds =
            secondsParts[0]
                .toLongOrNull()
                ?: return -1L

        val milliseconds =
            if (
                secondsParts.size == 2
            ) {
                normalizeMilliseconds(
                    secondsParts[1],
                )
            } else {
                0L
            }

        return (
            minutes * 60_000L
        ) + (
            seconds * 1_000L
        ) + milliseconds
    }

    private fun normalizeMilliseconds(
        value: String,
    ): Long {
        val normalized =
            value
                .take(3)
                .padEnd(
                    3,
                    '0',
                )

        return normalized
            .toLongOrNull()
            ?: 0L
    }

    private fun removeUnsynchronisation(
        data: ByteArray,
    ): ByteArray {
        val output =
            ByteArrayOutputStream(
                data.size,
            )

        var index = 0

        while (index < data.size) {
            val value =
                data[index].toInt() and 0xFF

            output.write(value)

            if (
                value == 0xFF &&
                index + 1 < data.size &&
                data[index + 1]
                    .toInt() == 0
            ) {
                index++
            }

            index++
        }

        return output.toByteArray()
    }

    private fun findSingleByteTerminator(
        data: ByteArray,
        start: Int,
    ): Int {
        for (
            index in start until data.size
        ) {
            if (
                data[index].toInt() == 0
            ) {
                return index
            }
        }

        return -1
    }

    private fun findUtf16Terminator(
        data: ByteArray,
        start: Int,
    ): Int {
        var index = start

        while (
            index + 1 < data.size
        ) {
            if (
                data[index].toInt() == 0 &&
                data[index + 1].toInt() == 0
            ) {
                return index
            }

            index += 2
        }

        return -1
    }

    private fun readSynchsafeInt(
        data: ByteArray,
        offset: Int,
    ): Int {
        val b0 =
            data[offset].toInt() and 0x7F

        val b1 =
            data[offset + 1]
                .toInt() and 0x7F

        val b2 =
            data[offset + 2]
                .toInt() and 0x7F

        val b3 =
            data[offset + 3]
                .toInt() and 0x7F

        return (b0 shl 21) or
            (b1 shl 14) or
            (b2 shl 7) or
            b3
    }

    private fun readInt32(
        data: ByteArray,
        offset: Int,
    ): Int {
        val b0 =
            data[offset].toInt() and 0xFF

        val b1 =
            data[offset + 1]
                .toInt() and 0xFF

        val b2 =
            data[offset + 2]
                .toInt() and 0xFF

        val b3 =
            data[offset + 3]
                .toInt() and 0xFF

        return (b0 shl 24) or
            (b1 shl 16) or
            (b2 shl 8) or
            b3
    }

    private fun readUInt24(
        data: ByteArray,
        offset: Int,
    ): Int {
        val b0 =
            data[offset].toInt() and 0xFF

        val b1 =
            data[offset + 1]
                .toInt() and 0xFF

        val b2 =
            data[offset + 2]
                .toInt() and 0xFF

        return (b0 shl 16) or
            (b1 shl 8) or
            b2
    }

    private fun readLittleEndianInt(
        data: ByteArray,
        offset: Int,
    ): Int {
        val b0 =
            data[offset].toInt() and 0xFF

        val b1 =
            data[offset + 1]
                .toInt() and 0xFF

        val b2 =
            data[offset + 2]
                .toInt() and 0xFF

        val b3 =
            data[offset + 3]
                .toInt() and 0xFF

        return b0 or
            (b1 shl 8) or
            (b2 shl 16) or
            (b3 shl 24)
    }

    private fun skipFully(
        input: InputStream,
        byteCount: Int,
    ) {
        var remaining =
            byteCount.toLong()

        while (remaining > 0L) {
            val skipped =
                input.skip(
                    remaining,
                )

            if (skipped > 0L) {
                remaining -= skipped
            } else {
                if (input.read() == -1) {
                    return
                }

                remaining--
            }
        }
    }

    private fun InputStream.readFully(
        buffer: ByteArray,
    ): Int {
        var total = 0

        while (total < buffer.size) {
            val count =
                read(
                    buffer,
                    total,
                    buffer.size - total,
                )

            if (count <= 0) {
                break
            }

            total += count
        }

        return total
    }

    private companion object {
        const val MAX_TAG_SIZE =
            8 * 1024 * 1024

        val LINE_TIMESTAMP_REGEX =
            Regex(
                """\[(\d{1,3}:\d{2}(?:\.\d{1,3})?)\]""",
            )

        val WORD_TIMESTAMP_REGEX =
            Regex(
                """<(\d{1,3}:\d{2}(?:\.\d{1,3})?)>""",
            )

        val VARIANT_PREFIX_REGEX =
            Regex(
                """^v\d+\s*:\s*""",
                RegexOption.IGNORE_CASE,
            )
    }
}