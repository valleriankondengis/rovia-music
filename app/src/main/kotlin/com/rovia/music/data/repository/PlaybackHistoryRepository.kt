package com.rovia.music.data.repository

import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.plus
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.LocalDate
import kotlin.time.Instant
import android.net.Uri
import com.rovia.music.data.local.database.RoviaDatabase
import com.rovia.music.data.local.entity.PlaybackActivityEntity
import com.rovia.music.data.mediastore.ArtistCreditParser
import com.rovia.music.data.mediastore.LocalAudioTrack
import com.rovia.music.playback.queue.PlaybackOrigin
import com.rovia.music.playback.queue.PlaybackOriginType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

private data class DailyTrackGroupKey(
    val trackIdentity: String,
    val localDate: LocalDate,
    val timeZoneId: String,
)

class PlaybackHistoryRepository(
    private val database: RoviaDatabase,
) {
    fun observeLatest(limit: Int): Flow<List<PlaybackActivityEntity>> = combine(
        database.playbackActivityDao().observeLatest(limit),
        database.artistDao().observeAll(),
    ) { activities, artists ->
        val artistsByIdentity = artists.associateBy { it.identityKey }
        activities.map { activity ->
            if (activity.entityType != PlaybackOriginType.ARTIST.name) {
                activity
            } else {
                val identityKey = "artist:${ArtistCreditParser.normalizeIdentity(activity.entityKey.removePrefix("artist:"))}"
                val artist = artistsByIdentity[identityKey]
                if (artist == null) activity else activity.copy(
                    titleSnapshot = artist.name,
                    artworkSourceUriSnapshot = artist.artworkSourceUri
                        ?: activity.artworkSourceUriSnapshot,
                )
            }
        }
    }

    suspend fun getPageBefore(
        beforeTimestampEpochMillis: Long,
        beforeActivityId: Long,
        limit: Int,
    ): List<PlaybackActivityEntity> {
        val activities = database.playbackActivityDao().getPageBefore(
            beforeTimestampEpochMillis = beforeTimestampEpochMillis,
            beforeActivityId = beforeActivityId,
            limit = limit,
        )
        val artistIdentityKeys = activities
            .filter { it.entityType == PlaybackOriginType.ARTIST.name }
            .map { activity ->
                "artist:${ArtistCreditParser.normalizeIdentity(activity.entityKey.removePrefix("artist:"))}"
            }
            .distinct()
        if (artistIdentityKeys.isEmpty()) return activities

        val artistsByIdentity = database.artistDao()
            .getByIdentityKeys(artistIdentityKeys)
            .associateBy { it.identityKey }
        return activities.map { activity ->
            if (activity.entityType != PlaybackOriginType.ARTIST.name) {
                activity
            } else {
                val identityKey = "artist:${ArtistCreditParser.normalizeIdentity(activity.entityKey.removePrefix("artist:"))}"
                val artist = artistsByIdentity[identityKey]
                if (artist == null) activity else activity.copy(
                    titleSnapshot = artist.name,
                    artworkSourceUriSnapshot = artist.artworkSourceUri
                        ?: activity.artworkSourceUriSnapshot,
                )
            }
        }
    }

    suspend fun getDailyTrackPlayCounts(
        activities: List<PlaybackActivityEntity>,
    ): Map<String, Long> {
        val trackActivities = activities.filter {
            it.entityType == PlaybackOriginType.TRACK.name
        }
        if (trackActivities.isEmpty()) return emptyMap()

        val groupedActivities = trackActivities.groupBy { activity ->
            val zoneId = resolveTimeZone(activity.timeZoneIdAtEvent)
            val localDate = Instant.fromEpochMilliseconds(activity.timestampEpochMillis)
                .toLocalDateTime(zoneId)
                .date
            val identity = activity.triggerTrackId?.let { "track:$it" }
                ?: "key:${activity.entityKey}"

            DailyTrackGroupKey(
                trackIdentity = identity,
                localDate = localDate,
                timeZoneId = activity.timeZoneIdAtEvent,
            )
        }

        val countsByActivityKey = mutableMapOf<String, Long>()
        val activityDao = database.playbackActivityDao()

        groupedActivities.forEach { (groupKey, group) ->
            val representative = group.first()
            val zoneId = resolveTimeZone(groupKey.timeZoneId)
            val startEpochMillis = groupKey.localDate.atStartOfDayIn(zoneId).toEpochMilliseconds()
            val endEpochMillis = groupKey.localDate.plus(1, DateTimeUnit.DAY)
                .atStartOfDayIn(zoneId)
                .toEpochMilliseconds()

            val count = activityDao.countTrackPlaysForDay(
                trackId = representative.triggerTrackId,
                entityKey = representative.entityKey,
                timeZoneIdAtEvent = groupKey.timeZoneId,
                startEpochMillis = startEpochMillis,
                endEpochMillis = endEpochMillis,
            ).coerceAtLeast(group.size.toLong())

            group.forEach { activity ->
                countsByActivityKey[activity.activityKey] = count
            }
        }

        return countsByActivityKey
    }

    suspend fun getPlayableTrackForActivity(activityKey: String): LocalAudioTrack? {
        val activity = database.playbackActivityDao().getByActivityKey(activityKey)
            ?: return null
        if (activity.entityType != PlaybackOriginType.TRACK.name) return null

        val indexedTrack = activity.triggerTrackId?.let { trackId ->
            database.trackDao().getById(trackId)
        }
        if (indexedTrack != null && !indexedTrack.isAvailable) return null

        val uriString = indexedTrack?.contentUri
            ?.takeIf(String::isNotBlank)
            ?: activity.artworkSourceUriSnapshot?.takeIf(String::isNotBlank)
            ?: return null
        val uri = runCatching { Uri.parse(uriString) }.getOrNull() ?: return null
        if (uri.scheme.isNullOrBlank()) return null

        val parsedMediaStoreId = activity.entityKey.substringAfterLast(':').toLongOrNull()
        val mediaStoreId = indexedTrack?.mediaStoreId
            ?: parsedMediaStoreId
            ?: -(activity.activityId.coerceAtLeast(1L))

        return LocalAudioTrack(
            id = mediaStoreId,
            uri = uri,
            title = indexedTrack?.title ?: activity.titleSnapshot,
            artist = indexedTrack?.artistCreditRaw ?: activity.subtitleSnapshot,
            album = indexedTrack?.albumTitleSnapshot,
            durationMs = indexedTrack?.durationMs ?: 0L,
            dateAddedSeconds = indexedTrack?.dateAddedSeconds ?: 0L,
            relativePath = indexedTrack?.relativePath,
            dateModifiedSeconds = indexedTrack?.dateModifiedSeconds ?: 0L,
            displayName = indexedTrack?.displayName,
            fileSizeBytes = indexedTrack?.fileSizeBytes,
            volumeName = indexedTrack?.volumeName,
        )
    }

    private fun resolveTimeZone(timeZoneId: String): TimeZone =
        runCatching { TimeZone.of(timeZoneId) }
            .getOrElse { TimeZone.currentSystemDefault() }
}
