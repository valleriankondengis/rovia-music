package com.rovia.music.data.lyrics

import com.rovia.music.ui.components.LyricSyllable
import com.rovia.music.ui.components.LyricWord

internal data class TimedLyricRun(
    val rawText: String,
    val startTimeMs: Long,
    val endTimeMs: Long,
)

internal fun buildTimedLyricWords(
    sourceRuns: List<TimedLyricRun>,
    fallbackTimestampMs: Long,
): List<LyricWord> {
    if (sourceRuns.isEmpty()) return emptyList()

    val runs = expandRunsAtWhitespace(sourceRuns)
    val hasExplicitSpacing = runs.any { run -> run.rawText.any(Char::isWhitespace) }
    val words = mutableListOf<LyricWord>()
    val currentText = StringBuilder()
    val currentSyllables = mutableListOf<LyricSyllable>()
    var currentStartMs = fallbackTimestampMs
    var currentEndMs = fallbackTimestampMs

    fun flush(spaceAfter: Boolean = false) {
        if (currentText.isEmpty()) return

        val text = currentText.toString()
        val syllables = if (currentSyllables.size > 1) {
            normalizeSyllables(currentSyllables, text.length)
        } else {
            emptyList()
        }

        words += LyricWord(
            text = text,
            startTimeMs = currentStartMs,
            endTimeMs = currentEndMs.coerceAtLeast(currentStartMs),
            syllables = syllables,
            spaceAfter = spaceAfter,
        )

        currentText.setLength(0)
        currentSyllables.clear()
        currentStartMs = fallbackTimestampMs
        currentEndMs = fallbackTimestampMs
    }

    for (run in runs) {
        val raw = run.rawText

        if (raw.isEmpty()) continue

        if (raw.isBlank()) {
            flush(spaceAfter = true)
            continue
        }

        val content = raw.trim()
        if (content.isEmpty()) {
            flush(spaceAfter = true)
            continue
        }

        val hasLeadingSpace = raw.first().isWhitespace()
        val hasTrailingSpace = raw.last().isWhitespace()

        if (hasLeadingSpace && currentText.isNotEmpty()) {
            flush(spaceAfter = true)
        }

        val joinsPreviousWord =
            hasExplicitSpacing &&
                currentText.isNotEmpty() &&
                !hasLeadingSpace &&
                !currentText.last().isUnspacedScript() &&
                !content.first().isUnspacedScript()

        if (currentText.isNotEmpty() && !joinsPreviousWord) {
            flush(spaceAfter = false)
        }

        if (currentText.isEmpty()) {
            currentStartMs = run.startTimeMs
            currentEndMs = run.startTimeMs
        }

        val startChar = currentText.length
        currentText.append(content)
        val endChar = currentText.length
        val syllableEndMs = run.endTimeMs.coerceAtLeast(run.startTimeMs)

        currentSyllables += LyricSyllable(
            startTimeMs = run.startTimeMs,
            endTimeMs = syllableEndMs,
            startChar = startChar,
            endChar = endChar,
        )

        currentEndMs = maxOf(currentEndMs, syllableEndMs)

        if (hasTrailingSpace) {
            flush(spaceAfter = true)
        } else if (!hasExplicitSpacing) {
            flush(spaceAfter = false)
        }
    }

    flush()
    if (words.isNotEmpty()) return words

    val fallbackText = sourceRuns.joinToString(separator = "") { it.rawText }.trim()
    return fallbackText.takeIf(String::isNotEmpty)?.let { text ->
        listOf(LyricWord(text = text, startTimeMs = fallbackTimestampMs))
    }.orEmpty()
}

private fun expandRunsAtWhitespace(
    sourceRuns: List<TimedLyricRun>,
): List<TimedLyricRun> {
    val expanded = ArrayList<TimedLyricRun>()

    sourceRuns.forEach { run ->
        val tokenStarts = mutableListOf<Int>()
        var cursor = 0

        while (cursor < run.rawText.length) {
            while (cursor < run.rawText.length && run.rawText[cursor].isWhitespace()) {
                cursor++
            }
            if (cursor >= run.rawText.length) break

            tokenStarts += cursor
            while (cursor < run.rawText.length && !run.rawText[cursor].isWhitespace()) {
                cursor++
            }
        }

        if (tokenStarts.isEmpty()) {
            if (run.rawText.isNotEmpty()) expanded += run
            return@forEach
        }

        tokenStarts.forEachIndexed { index, tokenStart ->
            val segmentStart = if (index == 0) 0 else tokenStart
            val segmentEnd = tokenStarts.getOrNull(index + 1) ?: run.rawText.length
            expanded += run.copy(
                rawText = run.rawText.substring(segmentStart, segmentEnd),
            )
        }
    }

    return expanded
}

private fun normalizeSyllables(
    syllables: List<LyricSyllable>,
    textLength: Int,
): List<LyricSyllable> {
    val usable = syllables
        .filter { it.endChar > it.startChar && it.startChar < textLength }
        .sortedBy(LyricSyllable::startChar)

    if (usable.size < 2) return emptyList()

    return usable.mapIndexed { index, syllable ->
        LyricSyllable(
            startTimeMs = syllable.startTimeMs,
            endTimeMs = syllable.endTimeMs.coerceAtLeast(syllable.startTimeMs),
            startChar = if (index == 0) 0 else syllable.startChar,
            endChar = usable.getOrNull(index + 1)?.startChar ?: textLength,
        )
    }
}

private fun Char.isUnspacedScript(): Boolean =
    this in '\u3400'..'\u9FFF' ||
        this in '\u3040'..'\u30FF' ||
        this in '\u3000'..'\u303F' ||
        this in '\uFF00'..'\uFFEF' ||
        this in '\u0E00'..'\u0E7F'

