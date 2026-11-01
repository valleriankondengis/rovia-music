package com.rovia.music.playback.statistics

import com.rovia.music.data.local.dao.AlbumPlaybackAggregate
import com.rovia.music.data.local.dao.ArtistPlaybackAggregate
import com.rovia.music.data.local.dao.PlaylistPlaybackAggregate
import com.rovia.music.data.local.dao.TrackPlaybackAggregate
import com.rovia.music.data.local.database.RoviaDatabase
import com.rovia.music.data.local.entity.PlaybackTrackEventEntity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
class PlaybackStatisticsRepository(
    private val database: RoviaDatabase,
) {
    private val playbackEventDao = database.playbackTrackEventDao()
    private val listeningSegmentDao = database.listeningSegmentDao()

    suspend fun getDailyTrackPlaybackCounts(
        date: LocalDate,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): List<DailyTrackPlaybackCount> {
        val range = date.toEpochRange(zoneId)

        return playbackEventDao
            .getEventsBetween(range.first, range.second)
            .groupBy { it.trackIdentityKey() }
            .map { (trackKey, events) -> events.toDailyAggregate(trackKey, date) }
            .sortedByDescending { it.lastPlayedAtEpochMillis }
    }

    suspend fun getDailyTrackPlaybackCounts(
        startDate: LocalDate,
        endDateExclusive: LocalDate,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): List<DailyTrackPlaybackCount> {
        require(!endDateExclusive.isBefore(startDate))
        if (endDateExclusive == startDate) return emptyList()

        val range = startDate.toEpochRange(endDateExclusive, zoneId)

        return playbackEventDao
            .getEventsBetween(range.first, range.second)
            .groupBy { event ->
                event.startedAtEpochMillis.toLocalDate(zoneId) to event.trackIdentityKey()
            }
            .map { (key, events) -> events.toDailyAggregate(key.second, key.first) }
            .sortedWith(
                compareByDescending<DailyTrackPlaybackCount> { it.date }
                    .thenByDescending { it.lastPlayedAtEpochMillis },
            )
    }

    suspend fun getHourlyTrackPlaybackCounts(
        date: LocalDate,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): List<HourlyTrackPlaybackCount> {
        val range = date.toEpochRange(zoneId)

        return playbackEventDao
            .getEventsBetween(range.first, range.second)
            .groupBy { event ->
                event.startedAtEpochMillis.toLocalDateTime(zoneId).hour to event.trackIdentityKey()
            }
            .map { (key, events) ->
                val aggregate = events.toDailyAggregate(key.second, date)
                HourlyTrackPlaybackCount(
                    date = date,
                    hourOfDay = key.first,
                    trackKey = aggregate.trackKey,
                    trackId = aggregate.trackId,
                    title = aggregate.title,
                    artistCredit = aggregate.artistCredit,
                    albumTitle = aggregate.albumTitle,
                    artworkAssetKey = aggregate.artworkAssetKey,
                    artworkSourceUri = aggregate.artworkSourceUri,
                    eventCount = aggregate.eventCount,
                    qualifiedPlayCount = aggregate.qualifiedPlayCount,
                    totalListenedDurationMs = aggregate.totalListenedDurationMs,
                    lastPlayedAtEpochMillis = aggregate.lastPlayedAtEpochMillis,
                )
            }
            .sortedWith(
                compareByDescending<HourlyTrackPlaybackCount> { it.hourOfDay }
                    .thenByDescending { it.lastPlayedAtEpochMillis },
            )
    }

    suspend fun getHourlyListeningDuration(
        date: LocalDate,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): List<HourlyListeningDuration> {
        val range = date.toEpochRange(zoneId)
        val segments = listeningSegmentDao.getSegmentsBetween(range.first, range.second)
        val durationByHour = LongArray(24)
        val dayStart = Instant.ofEpochMilli(range.first)
        val dayEnd = Instant.ofEpochMilli(range.second)

        segments.forEach { segment ->
            var cursor = Instant.ofEpochMilli(segment.startedAtEpochMillis).coerceAtLeast(dayStart)
            val segmentEnd = Instant.ofEpochMilli(segment.endedAtEpochMillis).coerceAtMost(dayEnd)

            while (cursor.isBefore(segmentEnd)) {
                val localCursor = cursor.atZone(zoneId)
                val nextHour = localCursor.truncatedTo(ChronoUnit.HOURS)
                    .plusHours(1)
                    .toInstant()
                val chunkEnd = minOf(segmentEnd, nextHour)
                if (!chunkEnd.isAfter(cursor)) break

                val chunkDurationMs = ChronoUnit.MILLIS
                    .between(cursor, chunkEnd)
                    .coerceAtLeast(0L)
                durationByHour[localCursor.hour] += chunkDurationMs
                cursor = chunkEnd
            }
        }

        return durationByHour.mapIndexed { hour, duration ->
            HourlyListeningDuration(
                date = date,
                hourOfDay = hour,
                durationMs = duration,
            )
        }
    }

    suspend fun getSummary(
        startDate: LocalDate,
        endDateExclusive: LocalDate,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): PlaybackPeriodSummary {
        require(!endDateExclusive.isBefore(startDate))
        if (startDate == endDateExclusive) return PlaybackPeriodSummary()

        val range = startDate.toEpochRange(endDateExclusive, zoneId)
        val eventCount = playbackEventDao.countEventsBetween(range.first, range.second)
        val qualifiedPlayCount = playbackEventDao.countQualifiedEventsBetween(
            range.first,
            range.second,
        )
        val uniqueTrackCount = playbackEventDao.countDistinctTrackIdentitiesBetween(
            range.first,
            range.second,
        )
        val listeningDuration = listeningSegmentDao.getListeningDurationBetween(
            startEpochMillis = range.first,
            endEpochMillis = range.second,
        )

        return PlaybackPeriodSummary(
            eventCount = eventCount,
            qualifiedPlayCount = qualifiedPlayCount,
            uniqueTrackCount = uniqueTrackCount,
            totalListeningDurationMs = listeningDuration,
        )
    }

    suspend fun getTopTracks(limit: Int = 20): List<TrackPlaybackAggregate> =
        playbackEventDao.getTopTracks(limit.coerceAtLeast(1))

    suspend fun getTopTracks(
        startDate: LocalDate,
        endDateExclusive: LocalDate,
        zoneId: ZoneId = ZoneId.systemDefault(),
        limit: Int = 20,
    ): List<TrackPlaybackAggregate> {
        val range = startDate.toValidatedEpochRange(endDateExclusive, zoneId) ?: return emptyList()
        return playbackEventDao.getTopTracksBetween(
            startEpochMillis = range.first,
            endEpochMillis = range.second,
            limit = limit.coerceAtLeast(1),
        )
    }

    suspend fun getTopArtists(limit: Int = 20): List<ArtistPlaybackAggregate> =
        playbackEventDao.getTopArtists(limit.coerceAtLeast(1))

    suspend fun getTopArtists(
        startDate: LocalDate,
        endDateExclusive: LocalDate,
        zoneId: ZoneId = ZoneId.systemDefault(),
        limit: Int = 20,
    ): List<ArtistPlaybackAggregate> {
        val range = startDate.toValidatedEpochRange(endDateExclusive, zoneId) ?: return emptyList()
        return playbackEventDao.getTopArtistsBetween(
            startEpochMillis = range.first,
            endEpochMillis = range.second,
            limit = limit.coerceAtLeast(1),
        )
    }

    suspend fun getTopAlbums(limit: Int = 20): List<AlbumPlaybackAggregate> =
        playbackEventDao.getTopAlbums(limit.coerceAtLeast(1))

    suspend fun getTopAlbums(
        startDate: LocalDate,
        endDateExclusive: LocalDate,
        zoneId: ZoneId = ZoneId.systemDefault(),
        limit: Int = 20,
    ): List<AlbumPlaybackAggregate> {
        val range = startDate.toValidatedEpochRange(endDateExclusive, zoneId) ?: return emptyList()
        return playbackEventDao.getTopAlbumsBetween(
            startEpochMillis = range.first,
            endEpochMillis = range.second,
            limit = limit.coerceAtLeast(1),
        )
    }

    suspend fun getTopPlaylists(limit: Int = 20): List<PlaylistPlaybackAggregate> =
        playbackEventDao.getTopPlaylists(limit.coerceAtLeast(1))

    suspend fun getTopPlaylists(
        startDate: LocalDate,
        endDateExclusive: LocalDate,
        zoneId: ZoneId = ZoneId.systemDefault(),
        limit: Int = 20,
    ): List<PlaylistPlaybackAggregate> {
        val range = startDate.toValidatedEpochRange(endDateExclusive, zoneId) ?: return emptyList()
        return playbackEventDao.getTopPlaylistsBetween(
            startEpochMillis = range.first,
            endEpochMillis = range.second,
            limit = limit.coerceAtLeast(1),
        )
    }

    suspend fun getPlaybackEventCount(
        startDate: LocalDate,
        endDateExclusive: LocalDate,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): Long {
        val range = startDate.toValidatedEpochRange(endDateExclusive, zoneId) ?: return 0L
        return playbackEventDao.countEventsBetween(range.first, range.second)
    }

    suspend fun getTotalListeningDuration(
        startDate: LocalDate,
        endDateExclusive: LocalDate,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): Long {
        val range = startDate.toValidatedEpochRange(endDateExclusive, zoneId) ?: return 0L
        return listeningSegmentDao.getListeningDurationBetween(
            startEpochMillis = range.first,
            endEpochMillis = range.second,
        )
    }
}

data class DailyTrackPlaybackCount(
    val date: LocalDate,
    val trackKey: String,
    val trackId: Long?,
    val title: String,
    val artistCredit: String?,
    val albumTitle: String?,
    val artworkAssetKey: String?,
    val artworkSourceUri: String?,
    val eventCount: Long,
    val qualifiedPlayCount: Long,
    val totalListenedDurationMs: Long,
    val lastPlayedAtEpochMillis: Long,
)

data class HourlyTrackPlaybackCount(
    val date: LocalDate,
    val hourOfDay: Int,
    val trackKey: String,
    val trackId: Long?,
    val title: String,
    val artistCredit: String?,
    val albumTitle: String?,
    val artworkAssetKey: String?,
    val artworkSourceUri: String?,
    val eventCount: Long,
    val qualifiedPlayCount: Long,
    val totalListenedDurationMs: Long,
    val lastPlayedAtEpochMillis: Long,
)

data class HourlyListeningDuration(
    val date: LocalDate,
    val hourOfDay: Int,
    val durationMs: Long,
)

data class PlaybackPeriodSummary(
    val eventCount: Long = 0,
    val qualifiedPlayCount: Long = 0,
    val uniqueTrackCount: Long = 0,
    val totalListeningDurationMs: Long = 0,
)

private fun LocalDate.toEpochRange(zoneId: ZoneId): Pair<Long, Long> =
    atStartOfDay(zoneId).toInstant().toEpochMilli() to
        plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()

private fun LocalDate.toEpochRange(
    endDateExclusive: LocalDate,
    zoneId: ZoneId,
): Pair<Long, Long> =
    atStartOfDay(zoneId).toInstant().toEpochMilli() to
        endDateExclusive.atStartOfDay(zoneId).toInstant().toEpochMilli()

private fun LocalDate.toValidatedEpochRange(
    endDateExclusive: LocalDate,
    zoneId: ZoneId,
): Pair<Long, Long>? {
    require(!endDateExclusive.isBefore(this))
    if (this == endDateExclusive) return null
    return toEpochRange(endDateExclusive, zoneId)
}

private fun Long.toLocalDate(zoneId: ZoneId): LocalDate =
    Instant.ofEpochMilli(this).atZone(zoneId).toLocalDate()

private fun Long.toLocalDateTime(zoneId: ZoneId) =
    Instant.ofEpochMilli(this).atZone(zoneId)

private fun PlaybackTrackEventEntity.trackIdentityKey(): String {
    trackId?.let { return "track:$it" }

    return listOf(
        trackTitleSnapshot,
        artistCreditSnapshot.orEmpty(),
        albumTitleSnapshot.orEmpty(),
    ).joinToString("|") {
        it.trim().lowercase().replace(Regex("\\s+"), " ")
    }.let { "snapshot:$it" }
}

private fun List<PlaybackTrackEventEntity>.toDailyAggregate(
    trackKey: String,
    date: LocalDate,
): DailyTrackPlaybackCount {
    val latest = maxBy { it.startedAtEpochMillis }

    return DailyTrackPlaybackCount(
        date = date,
        trackKey = trackKey,
        trackId = latest.trackId,
        title = latest.trackTitleSnapshot,
        artistCredit = latest.artistCreditSnapshot,
        albumTitle = latest.albumTitleSnapshot,
        artworkAssetKey = latest.artworkAssetKeySnapshot,
        artworkSourceUri = latest.artworkSourceUriSnapshot,
        eventCount = size.toLong(),
        qualifiedPlayCount = count { it.qualifiedPlay }.toLong(),
        totalListenedDurationMs = sumOf { it.heardDurationMs },
        lastPlayedAtEpochMillis = latest.startedAtEpochMillis,
    )
}
