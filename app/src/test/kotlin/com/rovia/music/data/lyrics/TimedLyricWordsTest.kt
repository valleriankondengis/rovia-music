package com.rovia.music.data.lyrics

import com.rovia.music.ui.components.LyricWord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TimedLyricWordsTest {
    @Test
    fun joinsTimestampedSyllablesWithoutInsertingSpaces() {
        val words = buildTimedLyricWords(
            sourceRuns = listOf(
                TimedLyricRun("Se", 1_000L, 1_200L),
                TimedLyricRun("mes", 1_200L, 1_400L),
                TimedLyricRun("ti", 1_400L, 1_600L),
                TimedLyricRun("nya ", 1_600L, 1_900L),
                TimedLyricRun("kau ", 1_900L, 2_200L),
                TimedLyricRun("dan ", 2_200L, 2_400L),
                TimedLyricRun("a", 2_400L, 2_500L),
                TimedLyricRun("ku ", 2_500L, 2_800L),
            ),
            fallbackTimestampMs = 1_000L,
        )

        assertEquals(
            listOf("Semestinya", "kau", "dan", "aku"),
            words.map(LyricWord::text),
        )
        assertTrue(words.all(LyricWord::spaceAfter))
        assertEquals(4, words.first().syllables.size)
        assertEquals(0, words.first().syllables.first().startChar)
        assertEquals(10, words.first().syllables.last().endChar)
        assertEquals(1_600L, words.first().syllables.last().startTimeMs)
        assertEquals(1_900L, words.first().endTimeMs)
    }

    @Test
    fun preservesUnspacedRunsWhenTheSourceHasNoWhitespace() {
        val words = buildTimedLyricWords(
            sourceRuns = listOf(
                TimedLyricRun("東", 1_000L, 1_200L),
                TimedLyricRun("京", 1_200L, 1_400L),
            ),
            fallbackTimestampMs = 1_000L,
        )

        assertEquals(listOf("東", "京"), words.map(LyricWord::text))
        assertFalse(words.first().spaceAfter)
    }

    @Test
    fun treatsAWhitespaceOnlyRunAsAWordBoundary() {
        val words = buildTimedLyricWords(
            sourceRuns = listOf(
                TimedLyricRun("hello", 1_000L, 1_200L),
                TimedLyricRun(" ", 1_200L, 1_250L),
                TimedLyricRun("world", 1_250L, 1_600L),
            ),
            fallbackTimestampMs = 1_000L,
        )

        assertEquals(listOf("hello", "world"), words.map(LyricWord::text))
        assertTrue(words.first().spaceAfter)
    }

    @Test
    fun splitsWordsInsideOneTimedRunButKeepsTheSameTimestamp() {
        val words = buildTimedLyricWords(
            sourceRuns = listOf(
                TimedLyricRun("hello world", 1_000L, 2_000L),
            ),
            fallbackTimestampMs = 1_000L,
        )

        assertEquals(listOf("hello", "world"), words.map(LyricWord::text))
        assertTrue(words.first().spaceAfter)
        assertEquals(words[0].startTimeMs, words[1].startTimeMs)
    }
}
