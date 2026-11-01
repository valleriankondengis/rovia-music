package com.rovia.music.playback.service

import com.rovia.music.util.KotlinDateTime
import kotlinx.datetime.TimeZone
import kotlin.uuid.Uuid
import android.app.PendingIntent
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.rovia.music.MainActivity
import com.rovia.music.R
import com.rovia.music.data.local.database.RoviaDatabase
import com.rovia.music.data.local.database.RoviaDatabaseFactory
import com.rovia.music.data.local.entity.ListeningSegmentEntity
import com.rovia.music.data.local.entity.PlaybackActivityEntity
import com.rovia.music.data.local.entity.PlaybackSessionEntity
import com.rovia.music.data.local.entity.PlaybackTrackEventEntity
import com.rovia.music.playback.queue.PlaybackMetadataKeys
import com.rovia.music.playback.queue.PlaybackOriginType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val TAG = "PlaybackService"
private const val LISTENING_CHECKPOINT_INTERVAL_MS = 15_000L
private const val QUALIFIED_PLAY_THRESHOLD_MS = 30_000L

@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private var player: ExoPlayer? = null
    private lateinit var database: RoviaDatabase

    private val recorderScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val recorderCommands = Channel<suspend () -> Unit>(Channel.UNLIMITED)
    private var recorderJob: Job? = null

    private val mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var checkpointJob: Job? = null
    private var playerListener: Player.Listener? = null

    private var activePlayback: ActivePlayback? = null
    private var activeSessionKey: String? = null
    private var playbackSequence = 0
    private var eventSequence = 0L
    private var fallbackSessionKey: String? = null

    override fun onCreate() {
        super.onCreate()

        database = RoviaDatabaseFactory.create(applicationContext)
        recorderJob = recorderScope.launch {
            for (command in recorderCommands) {
                try {
                    command()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (exception: Exception) {
                    Log.e(TAG, "Unable to persist playback statistics", exception)
                }
            }
        }

        setMediaNotificationProvider(
            DefaultMediaNotificationProvider(this).apply {
                setSmallIcon(R.drawable.ic_launcher_foreground)
            },
        )

        val exoPlayer = ExoPlayer.Builder(this)
            .setHandleAudioBecomingNoisy(true)
            .build()

        player = exoPlayer
        exoPlayer.setAudioAttributes(AudioAttributes.DEFAULT, true)

        val listener = object : Player.Listener {
            override fun onMediaItemTransition(
                mediaItem: MediaItem?,
                reason: Int,
            ) {
                val oldSessionKey = activePlayback?.sessionKey ?: activeSessionKey
                val nextSessionKey = mediaItem?.let {
                    resolveSessionKey(it, oldSessionKey)
                }
                val shouldCloseSession = mediaItem == null ||
                    (oldSessionKey != null && nextSessionKey != oldSessionKey)

                finishCurrentPlayback(
                    player = exoPlayer,
                    closeSession = shouldCloseSession,
                    wasInterrupted = false,
                )

                if (mediaItem != null && exoPlayer.isPlaying) {
                    startTrackEvent(exoPlayer, nextSessionKey)
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) {
                    val currentItem = exoPlayer.currentMediaItem
                    val current = activePlayback
                    val currentOccurrence = currentItem?.let(::occurrenceKey)

                    if (
                        current == null ||
                        current.mediaId != currentItem?.mediaId ||
                        current.occurrenceKey != currentOccurrence
                    ) {
                        startTrackEvent(exoPlayer)
                    } else {
                        startListeningSegment(current, exoPlayer)
                    }
                } else {
                    activePlayback?.let {
                        flushListeningSegment(it, exoPlayer, restart = false)
                    }
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    finishCurrentPlayback(
                        player = exoPlayer,
                        closeSession = true,
                        wasInterrupted = false,
                    )
                } else if (
                    playbackState == Player.STATE_IDLE &&
                    exoPlayer.mediaItemCount == 0
                ) {
                    finishCurrentPlayback(
                        player = exoPlayer,
                        closeSession = true,
                        wasInterrupted = false,
                    )
                }
            }

            override fun onEvents(player: Player, events: Player.Events) {
                activePlayback?.let { active ->
                    if (player.currentMediaItem?.mediaId == active.mediaId) {
                        active.lastKnownPositionMs = player.currentPosition.coerceAtLeast(0L)
                    }
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                val failedIndex = exoPlayer.currentMediaItemIndex

                if (failedIndex in 0 until exoPlayer.mediaItemCount) {
                    exoPlayer.removeMediaItem(failedIndex)
                }

                if (exoPlayer.mediaItemCount == 0) {
                    finishCurrentPlayback(
                        player = exoPlayer,
                        closeSession = true,
                        wasInterrupted = true,
                    )
                    exoPlayer.stop()
                } else {
                    val recoveryIndex = failedIndex.coerceIn(
                        0,
                        exoPlayer.mediaItemCount - 1,
                    )
                    exoPlayer.seekToDefaultPosition(recoveryIndex)
                    exoPlayer.prepare()
                    exoPlayer.play()
                }
            }
        }

        playerListener = listener
        exoPlayer.addListener(listener)
        startListeningCheckpointLoop(exoPlayer)

        val sessionActivity = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                action = Intent.ACTION_MAIN
                addCategory(Intent.CATEGORY_LAUNCHER)
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or
                PendingIntent.FLAG_IMMUTABLE,
        )

        mediaSession = MediaSession.Builder(this, exoPlayer)
            .setSessionActivity(sessionActivity)
            .build()
    }

    override fun onGetSession(
        controllerInfo: MediaSession.ControllerInfo,
    ): MediaSession? = mediaSession

    override fun onDestroy() {
        checkpointJob?.cancel()
        checkpointJob = null

        val currentPlayer = player
        finishCurrentPlayback(
            player = currentPlayer,
            closeSession = true,
            wasInterrupted = currentPlayer?.isPlaying == true,
        )

        playerListener?.let { listener -> currentPlayer?.removeListener(listener) }
        playerListener = null

        recorderCommands.close()
        recorderJob?.invokeOnCompletion { recorderScope.cancel() }
        mainScope.cancel()

        mediaSession?.let { session ->
            session.player.release()
            session.release()
        }

        mediaSession = null
        player = null
        super.onDestroy()
    }

    private fun startListeningCheckpointLoop(player: Player) {
        checkpointJob?.cancel()
        checkpointJob = mainScope.launch {
            while (isActive) {
                delay(LISTENING_CHECKPOINT_INTERVAL_MS)
                val active = activePlayback ?: continue
                if (!player.isPlaying) continue

                if (player.currentMediaItem?.mediaId == active.mediaId) {
                    active.lastKnownPositionMs = player.currentPosition.coerceAtLeast(0L)
                }
                flushListeningSegment(active, player, restart = true)
            }
        }
    }

    private fun startTrackEvent(
        player: Player,
        requestedSessionKey: String? = null,
    ) {
        if (!player.isPlaying) return
        val item = player.currentMediaItem ?: return
        val extras = item.mediaMetadata.extras
        val occurrenceKey = occurrenceKey(item)
        val existing = activePlayback

        if (
            existing != null &&
            existing.mediaId == item.mediaId &&
            existing.occurrenceKey == occurrenceKey
        ) {
            startListeningSegment(existing, player)
            return
        }

        val sessionKey = requestedSessionKey
            ?: resolveSessionKey(item, existing?.sessionKey ?: activeSessionKey)

        if (existing != null) {
            finishCurrentPlayback(
                player = player,
                closeSession = existing.sessionKey != sessionKey,
                wasInterrupted = false,
            )
        }

        if (activeSessionKey != sessionKey) {
            activeSessionKey = sessionKey
            playbackSequence = 0
        }
        playbackSequence += 1
        eventSequence += 1

        val trackTitle = item.mediaMetadata.title
            ?.toString()
            ?.takeIf(String::isNotBlank)
            ?: extras?.getString(PlaybackMetadataKeys.TRACK_DISPLAY_NAME)
                ?.substringBeforeLast('.')
                ?.takeIf(String::isNotBlank)
            ?: item.mediaId

        val artist = item.mediaMetadata.artist?.toString()
        val album = item.mediaMetadata.albumTitle?.toString()
        val trackIdFromMediaStore = extras?.getLong(
            PlaybackMetadataKeys.TRACK_ID,
            item.mediaId.toLongOrNull() ?: -1L,
        )?.takeIf { it >= 0L }

        val volumeName = extras
            ?.getString(PlaybackMetadataKeys.TRACK_VOLUME_NAME)
            ?.takeIf(String::isNotBlank)
        val sourceKey = if (trackIdFromMediaStore != null && volumeName != null) {
            "media-store:$volumeName:$trackIdFromMediaStore"
        } else {
            null
        }

        val declaredOriginType = extras
            ?.getString(PlaybackMetadataKeys.ORIGIN_TYPE)
            ?.takeIf { value -> PlaybackOriginType.entries.any { it.name == value } }
            ?: PlaybackOriginType.TRACK.name
        val originEntityKey = extras
            ?.getString(PlaybackMetadataKeys.ORIGIN_ENTITY_KEY)
            ?.takeIf(String::isNotBlank)
            ?: sourceKey
            ?: "media-item:${item.mediaId}"
        val originTitle = extras
            ?.getString(PlaybackMetadataKeys.ORIGIN_TITLE)
            ?.takeIf(String::isNotBlank)
            ?: trackTitle
        val originSubtitle = extras
            ?.getString(PlaybackMetadataKeys.ORIGIN_SUBTITLE)
            ?.takeIf(String::isNotBlank)
        val originArtworkUri = extras
            ?.getString(PlaybackMetadataKeys.ORIGIN_ARTWORK_SOURCE_URI)
            ?.takeIf(String::isNotBlank)

        val artworkSourceUri = item.mediaMetadata.artworkUri?.toString()
            ?: extras?.getString(PlaybackMetadataKeys.TRACK_ARTWORK_SOURCE_URI)
                ?.takeIf(String::isNotBlank)
        val contentUriSnapshot = item.localConfiguration?.uri?.toString()
            ?: extras?.getString(PlaybackMetadataKeys.TRACK_URI)
                ?.takeIf(String::isNotBlank)

        val startedAt = KotlinDateTime.nowEpochMilliseconds()
        val trackDurationMs = extras?.getLong(
            PlaybackMetadataKeys.TRACK_DURATION_MS,
            item.mediaMetadata.durationMs ?: 0L,
        )?.takeIf { it > 0L } ?: item.mediaMetadata.durationMs?.takeIf { it > 0L }

        val active = ActivePlayback(
            eventKey = "$sessionKey:$occurrenceKey:$eventSequence",
            sessionKey = sessionKey,
            occurrenceKey = occurrenceKey,
            mediaId = item.mediaId,
            sourceKey = sourceKey,
            mediaStoreIdSnapshot = trackIdFromMediaStore,
            volumeNameSnapshot = volumeName,
            contentUriSnapshot = contentUriSnapshot,
            playbackSourceType = declaredOriginType,
            playbackSourceEntityKey = originEntityKey,
            playbackSourceTitleSnapshot = originTitle,
            trackTitleSnapshot = trackTitle,
            artistCreditSnapshot = artist,
            albumTitleSnapshot = album,
            artworkSourceUriSnapshot = artworkSourceUri,
            trackDurationMsSnapshot = trackDurationMs,
            startedAtEpochMillis = startedAt,
            startPositionMs = player.currentPosition.coerceAtLeast(0L),
            playbackSequence = playbackSequence,
            timeZoneIdAtStart = TimeZone.currentSystemDefault().id,
            sessionSnapshot = SessionSnapshot(
                sessionKey = sessionKey,
                originType = declaredOriginType,
                originEntityKey = originEntityKey,
                originTitleSnapshot = originTitle,
                originSubtitleSnapshot = originSubtitle,
                originArtworkSourceUriSnapshot = originArtworkUri ?: artworkSourceUri,
                startedAtEpochMillis = startedAt,
                timeZoneIdAtStart = TimeZone.currentSystemDefault().id,
            ),
        )

        activePlayback = active
        enqueueEventStart(active)
        startListeningSegment(active, player)
    }

    private fun startListeningSegment(
        active: ActivePlayback,
        player: Player,
    ) {
        if (!player.isPlaying || player.currentMediaItem?.mediaId != active.mediaId) return
        if (active.segmentStartedAtEpochMillis != null) return

        val position = player.currentPosition.coerceAtLeast(0L)
        active.segmentStartedAtEpochMillis = KotlinDateTime.nowEpochMilliseconds()
        active.segmentStartedElapsedRealtime = SystemClock.elapsedRealtime()
        active.segmentStartPositionMs = position
        active.lastKnownPositionMs = position
    }

    private fun flushListeningSegment(
        active: ActivePlayback,
        player: Player?,
        restart: Boolean,
    ) {
        val segmentStartedEpoch = active.segmentStartedAtEpochMillis
        val segmentStartedElapsed = active.segmentStartedElapsedRealtime
        if (segmentStartedEpoch == null || segmentStartedElapsed == null) return

        val nowElapsed = SystemClock.elapsedRealtime()
        val durationMs = (nowElapsed - segmentStartedElapsed).coerceAtLeast(0L)
        val startPositionMs = active.segmentStartPositionMs ?: active.lastKnownPositionMs
        val currentItemMatches = player?.currentMediaItem?.mediaId == active.mediaId
        val endPositionMs = if (currentItemMatches) {
            player.currentPosition.coerceAtLeast(0L)
        } else {
            maxOf(active.lastKnownPositionMs, startPositionMs + durationMs)
        }

        active.lastKnownPositionMs = endPositionMs

        if (durationMs > 0L) {
            active.heardDurationMs += durationMs
            active.segmentSequence += 1

            val segment = ListeningSegmentEntity(
                segmentKey = "${active.eventKey}:segment:${active.segmentSequence}",
                playbackEventId = null,
                sessionId = null,
                trackId = null,
                startedAtEpochMillis = segmentStartedEpoch,
                endedAtEpochMillis = segmentStartedEpoch + durationMs,
                durationMs = durationMs,
                startPositionMs = startPositionMs,
                endPositionMs = endPositionMs,
                timeZoneIdAtStart = active.timeZoneIdAtStart,
            )
            val eventKey = active.eventKey
            val sessionKey = active.sessionKey
            val heardDuration = active.heardDurationMs
            val trackDuration = active.trackDurationMsSnapshot
            val shouldQualify = isQualifiedPlay(trackDuration, heardDuration)

            enqueueRecorder {
                val eventDao = database.playbackTrackEventDao()
                val sessionDao = database.playbackSessionDao()
                val event = eventDao.getByEventKey(eventKey)
                val session = sessionDao.getBySessionKey(sessionKey)

                database.listeningSegmentDao().insertIfAbsent(
                    segment.copy(
                        playbackEventId = event?.eventId,
                        sessionId = session?.sessionId,
                        trackId = event?.trackId,
                    ),
                )

                if (event != null) {
                    eventDao.update(
                        event.copy(
                            endPositionMs = endPositionMs,
                            heardDurationMs = heardDuration,
                            qualifiedPlay = shouldQualify,
                        ),
                    )
                }
            }
        }

        active.segmentStartedAtEpochMillis = null
        active.segmentStartedElapsedRealtime = null
        active.segmentStartPositionMs = null

        if (restart && player?.isPlaying == true && currentItemMatches) {
            startListeningSegment(active, player)
        }
    }

    private fun finishCurrentPlayback(
        player: Player?,
        closeSession: Boolean,
        wasInterrupted: Boolean,
    ) {
        val active = activePlayback
        val endedAt = KotlinDateTime.nowEpochMilliseconds()

        if (active != null) {
            flushListeningSegment(active, player, restart = false)
            val endPosition = active.lastKnownPositionMs.coerceAtLeast(0L)
            val eventKey = active.eventKey
            val sessionKey = active.sessionKey
            val heardDuration = active.heardDurationMs
            val trackDuration = active.trackDurationMsSnapshot

            enqueueRecorder {
                val eventDao = database.playbackTrackEventDao()
                val event = eventDao.getByEventKey(eventKey)
                if (event != null) {
                    eventDao.update(
                        event.copy(
                            endedAtEpochMillis = endedAt,
                            endPositionMs = endPosition,
                            heardDurationMs = heardDuration,
                            qualifiedPlay = isQualifiedPlay(trackDuration, heardDuration),
                        ),
                    )
                }

                if (closeSession) {
                    finishSession(sessionKey, endedAt, wasInterrupted)
                }
            }

            activePlayback = null
        } else if (closeSession) {
            activeSessionKey?.let { sessionKey ->
                enqueueRecorder {
                    finishSession(sessionKey, endedAt, wasInterrupted)
                }
            }
        }

        if (closeSession) {
            activeSessionKey = null
            playbackSequence = 0
            fallbackSessionKey = null
        }
    }

    private fun enqueueEventStart(active: ActivePlayback) {
        enqueueRecorder {
            val trackDao = database.trackDao()
            val track = active.sourceKey?.let { sourceKey ->
                trackDao.getBySourceKey(sourceKey)
            } ?: active.mediaStoreIdSnapshot?.let { mediaStoreId ->
                trackDao.getByMediaStoreIdentity(
                    mediaStoreId = mediaStoreId,
                    volumeName = active.volumeNameSnapshot,
                )
            }

            val activityDao = database.playbackActivityDao()
            activityDao.insertIfAbsent(
                PlaybackActivityEntity(
                    activityKey = "playback-track:${active.eventKey}",
                    entityType = PlaybackOriginType.TRACK.name,
                    entityKey = active.sourceKey ?: "media-item:${active.mediaId}",
                    timestampEpochMillis = active.startedAtEpochMillis,
                    timeZoneIdAtEvent = active.timeZoneIdAtStart,
                    triggerTrackId = track?.trackId,
                    titleSnapshot = active.trackTitleSnapshot,
                    subtitleSnapshot = active.artistCreditSnapshot,
                    artworkAssetKeySnapshot = track?.artworkAssetKey,
                    artworkSourceUriSnapshot = active.contentUriSnapshot
                        ?: track?.contentUri
                        ?: active.artworkSourceUriSnapshot,
                ),
            )

            if (active.playbackSourceType != PlaybackOriginType.TRACK.name) {
                activityDao.insertIfAbsent(
                    PlaybackActivityEntity(
                        activityKey = "playback-origin:${active.eventKey}",
                        entityType = active.playbackSourceType,
                        entityKey = active.playbackSourceEntityKey,
                        timestampEpochMillis = active.startedAtEpochMillis,
                        timeZoneIdAtEvent = active.timeZoneIdAtStart,
                        triggerTrackId = track?.trackId,
                        titleSnapshot = active.playbackSourceTitleSnapshot,
                        subtitleSnapshot = active.sessionSnapshot.originSubtitleSnapshot,
                        artworkAssetKeySnapshot = track?.artworkAssetKey,
                        artworkSourceUriSnapshot =
                            active.sessionSnapshot.originArtworkSourceUriSnapshot
                                ?: active.artworkSourceUriSnapshot,
                    ),
                )
            }

            val sessionDao = database.playbackSessionDao()
            val existingSession = sessionDao.getBySessionKey(active.sessionKey)
            val session = existingSession ?: run {
                sessionDao.insertIfAbsent(
                    PlaybackSessionEntity(
                        sessionKey = active.sessionSnapshot.sessionKey,
                        originType = active.sessionSnapshot.originType,
                        originEntityKey = active.sessionSnapshot.originEntityKey,
                        originTitleSnapshot = active.sessionSnapshot.originTitleSnapshot,
                        originSubtitleSnapshot = active.sessionSnapshot.originSubtitleSnapshot,
                        originArtworkAssetKeySnapshot = track?.artworkAssetKey,
                        originArtworkSourceUriSnapshot = active.sessionSnapshot.originArtworkSourceUriSnapshot,
                        primaryTrackId = track?.trackId,
                        startedAtEpochMillis = active.sessionSnapshot.startedAtEpochMillis,
                        timeZoneIdAtStart = active.sessionSnapshot.timeZoneIdAtStart,
                        wasInterrupted = false,
                    ),
                )
                sessionDao.getBySessionKey(active.sessionKey)
            }

            val event = PlaybackTrackEventEntity(
                eventKey = active.eventKey,
                sessionId = session?.sessionId,
                trackId = track?.trackId,
                playbackSourceType = active.playbackSourceType,
                playbackSourceEntityKey = active.playbackSourceEntityKey,
                playbackSourceTitleSnapshot = active.playbackSourceTitleSnapshot,
                trackTitleSnapshot = active.trackTitleSnapshot,
                artistCreditSnapshot = active.artistCreditSnapshot,
                albumTitleSnapshot = active.albumTitleSnapshot,
                artworkAssetKeySnapshot = track?.artworkAssetKey,
                artworkSourceUriSnapshot = active.artworkSourceUriSnapshot,
                trackDurationMsSnapshot = active.trackDurationMsSnapshot,
                startedAtEpochMillis = active.startedAtEpochMillis,
                startPositionMs = active.startPositionMs,
                playbackSequence = active.playbackSequence,
                timeZoneIdAtStart = active.timeZoneIdAtStart,
            )

            database.playbackTrackEventDao().insertIfAbsent(event)
        }
    }

    private suspend fun finishSession(
        sessionKey: String,
        endedAtEpochMillis: Long,
        wasInterrupted: Boolean,
    ) {
        val session = database.playbackSessionDao().getBySessionKey(sessionKey) ?: return
        database.playbackSessionDao().finishSession(
            sessionId = session.sessionId,
            endedAtEpochMillis = endedAtEpochMillis,
            wasInterrupted = wasInterrupted,
        )
    }

    private fun enqueueRecorder(command: suspend () -> Unit) {
        val result = recorderCommands.trySend(command)
        if (result.isFailure) {
            Log.w(TAG, "Playback statistics command was rejected")
        }
    }

    private fun resolveSessionKey(
        item: MediaItem,
        previousSessionKey: String?,
    ): String {
        val declaredSessionKey = item.mediaMetadata.extras
            ?.getString(PlaybackMetadataKeys.QUEUE_SESSION_KEY)
            ?.takeIf(String::isNotBlank)

        if (declaredSessionKey != null) {
            fallbackSessionKey = null
            return declaredSessionKey
        }

        if (previousSessionKey != null) return previousSessionKey
        return fallbackSessionKey ?: Uuid.random().toString().also {
            fallbackSessionKey = it
        }
    }

    private fun occurrenceKey(item: MediaItem): String = item.mediaMetadata.extras
        ?.getString(PlaybackMetadataKeys.QUEUE_ITEM_OCCURRENCE_KEY)
        ?.takeIf(String::isNotBlank)
        ?: item.mediaId

    private fun isQualifiedPlay(
        trackDurationMs: Long?,
        heardDurationMs: Long,
    ): Boolean {
        val threshold = trackDurationMs
            ?.takeIf { it > 0L }
            ?.let { minOf(QUALIFIED_PLAY_THRESHOLD_MS, (it / 2L).coerceAtLeast(1L)) }
            ?: QUALIFIED_PLAY_THRESHOLD_MS
        return heardDurationMs >= threshold
    }

    private data class SessionSnapshot(
        val sessionKey: String,
        val originType: String,
        val originEntityKey: String,
        val originTitleSnapshot: String,
        val originSubtitleSnapshot: String?,
        val originArtworkSourceUriSnapshot: String?,
        val startedAtEpochMillis: Long,
        val timeZoneIdAtStart: String,
    )

    private data class ActivePlayback(
        val eventKey: String,
        val sessionKey: String,
        val occurrenceKey: String,
        val mediaId: String,
        val sourceKey: String?,
        val mediaStoreIdSnapshot: Long?,
        val volumeNameSnapshot: String?,
        val contentUriSnapshot: String?,
        val playbackSourceType: String,
        val playbackSourceEntityKey: String,
        val playbackSourceTitleSnapshot: String,
        val trackTitleSnapshot: String,
        val artistCreditSnapshot: String?,
        val albumTitleSnapshot: String?,
        val artworkSourceUriSnapshot: String?,
        val trackDurationMsSnapshot: Long?,
        val startedAtEpochMillis: Long,
        val startPositionMs: Long,
        val playbackSequence: Int,
        val timeZoneIdAtStart: String,
        val sessionSnapshot: SessionSnapshot,
        var segmentStartedAtEpochMillis: Long? = null,
        var segmentStartedElapsedRealtime: Long? = null,
        var segmentStartPositionMs: Long? = null,
        var segmentSequence: Int = 0,
        var lastKnownPositionMs: Long = 0L,
        var heardDurationMs: Long = 0L,
    )
}
