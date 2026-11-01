package com.rovia.music.util

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

internal object KotlinDateTime {
    fun nowEpochMilliseconds(): Long = Clock.System.now().toEpochMilliseconds()

    fun epochSecondsYear(epochSeconds: Long): Int? = runCatching {
        Instant.fromEpochSeconds(epochSeconds)
            .toLocalDateTime(TimeZone.UTC)
            .year
    }.getOrNull()?.takeIf { it in 1000..9999 }

    fun yearStartEpochMillis(year: Int): Long? {
        if (year !in 1000..9999) return null

        return runCatching {
            LocalDate(year, 1, 1)
                .atStartOfDayIn(TimeZone.UTC)
                .toEpochMilliseconds()
        }.getOrNull()
    }
}
