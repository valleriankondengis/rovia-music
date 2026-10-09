package com.rovia.music.data.media.store

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import com.rovia.music.core.model.Track
import com.rovia.music.data.database.dao.MediaStoreSyncStateDao
import com.rovia.music.data.database.dao.MusicTrackDao
import com.rovia.music.data.database.entity.MediaStoreSyncStateEntity
import com.rovia.music.data.database.entity.MusicTrackEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Summary of a catalog synchronization attempt.
 *
 * A failed or unavailable volume is never interpreted as an empty
 * catalog. Existing catalog entries remain untouched when a volume
 * cannot be read successfully.
 */
data class MediaCatalogSyncReport(
    val permissionGranted: Boolean,
    val fullScanVolumes: List<String> = emptyList(),
    val incrementallySyncedVolumes: List<String> = emptyList(),
    val unchangedVolumes: List<String> = emptyList(),
    val failedVolumes: List<String> = emptyList(),
    val volumeDiscoveryFailed: Boolean = false,
)

/**
 * Coordinates MediaStore catalog synchronization with the Room catalog.
 *
 * Responsibilities:
 * - Perform an initial full scan when no valid checkpoint exists.
 * - Perform a full scan when the MediaStore version changes.
 * - Skip catalog scanning when the generation is unchanged.
 * - Update only changed tracks when the generation advances.
 * - Reconcile deleted tracks after a successful MediaStore query.
 * - Preserve valid embedded metadata when its source signature matches.
 * - Advance checkpoints only after catalog operations succeed.
 *
 * This coordinator does not run MediaMetadataRetriever. Embedded
 * metadata extraction is a separate pipeline.
 */
class MediaCatalogSyncCoordinator(
    context: Context,
    private val dataSource: MediaStoreCatalogDataSource,
    private val musicTrackDao: MusicTrackDao,
    private val syncStateDao: MediaStoreSyncStateDao,
    private val currentTimeMillis: () -> Long =
        System::currentTimeMillis,
) {

    private val applicationContext =
        context.applicationContext

    private val syncMutex =
        Mutex()

    /**
     * Synchronizes every currently available external MediaStore volume.
     *
     * If READ_MEDIA_AUDIO is not granted, the method exits without
     * querying or deleting catalog entries.
     */
    suspend fun synchronize(): MediaCatalogSyncReport =
        syncMutex.withLock {
            if (!hasReadAudioPermission()) {
                return@withLock MediaCatalogSyncReport(
                    permissionGranted = false,
                )
            }

            val availableVolumes =
                try {
                    dataSource.getAvailableVolumeNames()
                } catch (
                    cancellation: CancellationException,
                ) {
                    throw cancellation
                } catch (
                    exception: Exception,
                ) {
                    Log.w(
                        TAG,
                        "Unable to discover MediaStore volumes.",
                        exception,
                    )

                    return@withLock MediaCatalogSyncReport(
                        permissionGranted = true,
                        volumeDiscoveryFailed = true,
                    )
                }

            val fullScanVolumes =
                mutableListOf<String>()

            val incrementallySyncedVolumes =
                mutableListOf<String>()

            val unchangedVolumes =
                mutableListOf<String>()

            val failedVolumes =
                mutableListOf<String>()

            for (volumeName in availableVolumes.sorted()) {
                try {
                    when (
                        synchronizeVolume(volumeName)
                    ) {
                        VolumeSyncOutcome.FULL_SCAN ->
                            fullScanVolumes += volumeName

                        VolumeSyncOutcome.INCREMENTAL ->
                            incrementallySyncedVolumes +=
                                volumeName

                        VolumeSyncOutcome.UNCHANGED ->
                            unchangedVolumes += volumeName
                    }
                } catch (
                    cancellation: CancellationException,
                ) {
                    throw cancellation
                } catch (
                    exception: Exception,
                ) {
                    /*
                     * Do not advance the checkpoint for this volume.
                     * Its next synchronization can retry the work.
                     */
                    failedVolumes += volumeName

                    Log.w(
                        TAG,
                        "Failed to synchronize volume: $volumeName",
                        exception,
                    )
                }
            }

            MediaCatalogSyncReport(
                permissionGranted = true,
                fullScanVolumes = fullScanVolumes.toList(),
                incrementallySyncedVolumes =
                    incrementallySyncedVolumes.toList(),
                unchangedVolumes = unchangedVolumes.toList(),
                failedVolumes = failedVolumes.toList(),
            )
        }

    private fun hasReadAudioPermission(): Boolean {
        return applicationContext.checkSelfPermission(
            Manifest.permission.READ_MEDIA_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED
    }

    private suspend fun synchronizeVolume(
        volumeName: String,
    ): VolumeSyncOutcome {
        val currentState =
            dataSource.getVolumeState(
                volumeName = volumeName,
            )

        val previousState =
            syncStateDao.getByVolume(
                volumeName = volumeName,
            )

        val requiresFullScan =
            previousState == null ||
                previousState.mediaStoreVersion !=
                    currentState.version ||
                previousState.lastFullScanEpochMillis == null ||
                currentState.generation <
                    previousState.lastSuccessfulGeneration

        if (requiresFullScan) {
            performFullScan(
                currentState = currentState,
            )

            return VolumeSyncOutcome.FULL_SCAN
        }

        requireNotNull(previousState)

        if (
            currentState.generation ==
                previousState.lastSuccessfulGeneration
        ) {
            /*
             * Version and generation are unchanged.
             * No catalog query or per-file metadata extraction
             * is needed for this volume.
             */
            return VolumeSyncOutcome.UNCHANGED
        }

        performIncrementalSync(
            currentState = currentState,
            previousState = previousState,
        )

        return VolumeSyncOutcome.INCREMENTAL
    }

    private suspend fun performFullScan(
        currentState: MediaStoreVolumeState,
    ) {
        val volumeName =
            currentState.volumeName

        /*
         * Read existing entries before refreshing the catalog.
         * They are used to preserve valid embedded metadata.
         */
        val existingTracks =
            musicTrackDao.getTracksByVolume(
                volumeName = volumeName,
            )

        val existingTracksById =
            existingTracks.associateBy {
                it.trackId
            }

        /*
         * A null cursor or failed query throws before any catalog
         * deletion is attempted.
         */
        val items =
            dataSource.queryAllTracks(
                volumeName = volumeName,
            )

        val entities =
            items.map { item ->
                item.toEntity(
                    volumeName = volumeName,
                    existing = existingTracksById[
                        item.track.id
                    ],
                )
            }

        upsertInBatches(entities)

        val liveTrackIds =
            items
                .mapTo(mutableSetOf()) { item ->
                    item.track.id
                }

        /*
         * Reconcile only after the full query completed and its
         * rows were written successfully.
         */
        reconcileDeletedTracks(
            volumeName = volumeName,
            liveTrackIds = liveTrackIds,
        )

        val now =
            currentTimeMillis()

        /*
         * The captured generation is the scan boundary.
         * If media changes during the scan, a later synchronization
         * can process changes newer than this checkpoint.
         */
        syncStateDao.upsert(
            MediaStoreSyncStateEntity(
                volumeName = volumeName,
                mediaStoreVersion =
                    currentState.version,
                lastSuccessfulGeneration =
                    currentState.generation,
                lastSuccessfulSyncEpochMillis =
                    now,
                lastFullScanEpochMillis =
                    now,
            ),
        )
    }

    private suspend fun performIncrementalSync(
        currentState: MediaStoreVolumeState,
        previousState: MediaStoreSyncStateEntity,
    ) {
        val volumeName =
            currentState.volumeName

        val changedItems =
            dataSource.queryTracksModifiedAfter(
                volumeName = volumeName,
                generationExclusive =
                    previousState.lastSuccessfulGeneration,
            )

        /*
         * Query existing catalog entries only for the changed IDs.
         * Bound the IN query size to avoid SQLite bind-variable limits.
         */
        val existingChangedTracks =
            mutableMapOf<Long, MusicTrackEntity>()

        changedItems
            .asSequence()
            .map { it.track.id }
            .distinct()
            .toList()
            .chunked(DATABASE_BATCH_SIZE)
            .forEach { trackIds ->
                val existing =
                    musicTrackDao.getTracksByIds(
                        volumeName = volumeName,
                        trackIds = trackIds,
                    )

                existing.forEach { entity ->
                    existingChangedTracks[
                        entity.trackId
                    ] = entity
                }
            }

        val changedEntities =
            changedItems.map { item ->
                item.toEntity(
                    volumeName = volumeName,
                    existing = existingChangedTracks[
                        item.track.id
                    ],
                )
            }

        upsertInBatches(changedEntities)

        /*
         * Generation queries cannot return rows that have been deleted.
         * Reconcile the current IDs only after the MediaStore ID query
         * succeeds, and never treat an unavailable volume as empty.
         */
        val liveTrackIds =
            dataSource.queryTrackIds(
                volumeName = volumeName,
            ).toSet()

        reconcileDeletedTracks(
            volumeName = volumeName,
            liveTrackIds = liveTrackIds,
        )

        /*
         * Keep the previous successful full-scan timestamp.
         * Advance the generation checkpoint only after all catalog
         * updates and deletion reconciliation have completed.
         */
        syncStateDao.upsert(
            MediaStoreSyncStateEntity(
                volumeName = volumeName,
                mediaStoreVersion =
                    currentState.version,
                lastSuccessfulGeneration =
                    currentState.generation,
                lastSuccessfulSyncEpochMillis =
                    currentTimeMillis(),
                lastFullScanEpochMillis =
                    previousState.lastFullScanEpochMillis,
            ),
        )
    }

    private suspend fun upsertInBatches(
        entities: List<MusicTrackEntity>,
    ) {
        entities
            .chunked(DATABASE_BATCH_SIZE)
            .forEach { batch ->
                if (batch.isNotEmpty()) {
                    musicTrackDao.upsertAll(batch)
                }
            }
    }

    private suspend fun reconcileDeletedTracks(
        volumeName: String,
        liveTrackIds: Set<Long>,
    ) {
        val existingTrackIds =
            musicTrackDao.getTrackIdsByVolume(
                volumeName = volumeName,
            )

        if (liveTrackIds.isEmpty()) {
            /*
             * This method is reached only after a successful MediaStore
             * query while READ_MEDIA_AUDIO is granted.
             */
            musicTrackDao.deleteAllForVolume(
                volumeName = volumeName,
            )

            return
        }

        val staleTrackIds =
            existingTrackIds.filterNot {
                it in liveTrackIds
            }

        staleTrackIds
            .chunked(DATABASE_BATCH_SIZE)
            .forEach { trackIds ->
                if (trackIds.isNotEmpty()) {
                    musicTrackDao.deleteByVolumeAndTrackIds(
                        volumeName = volumeName,
                        trackIds = trackIds,
                    )
                }
            }
    }

    private fun MediaStoreCatalogItem.toEntity(
        volumeName: String,
        existing: MusicTrackEntity?,
    ): MusicTrackEntity {
        val track =
            this.track

        /*
         * The signature excludes path and display name, so a simple
         * path change does not automatically invalidate embedded data.
         */
        val signature =
            EmbeddedMetadataSignature.from(track)

        val embeddedMetadataIsValid =
            existing?.embeddedMetadataSignature ==
                signature

        return MusicTrackEntity(
            trackId = track.id,
            uri = track.uri,
            title = track.title,
            artist = track.artist,
            album = track.album,
            genre = track.genre,
            displayName = track.displayName,
            albumArtist = track.albumArtist,
            composer = track.composer,
            author = track.author,
            writer = track.writer,
            year = track.year,

            releaseDate =
                if (embeddedMetadataIsValid) {
                    existing?.releaseDate
                } else {
                    null
                },

            trackNumber = track.trackNumber,
            discNumber = track.discNumber,
            cdTrackNumber = track.cdTrackNumber,
            compilation = track.compilation,

            label =
                if (embeddedMetadataIsValid) {
                    existing?.label
                } else {
                    null
                },

            copyright =
                if (embeddedMetadataIsValid) {
                    existing?.copyright
                } else {
                    null
                },

            releaseType =
                if (embeddedMetadataIsValid) {
                    existing?.releaseType
                } else {
                    null
                },

            durationMs = track.durationMs,
            dateAddedEpochSeconds =
                track.dateAddedEpochSeconds,
            dateModifiedEpochSeconds =
                track.dateModifiedEpochSeconds,
            metadataDateEpochMillis =
                track.metadataDateEpochMillis,
            inferredDateEpochMillis =
                track.inferredDateEpochMillis,
            mimeType = track.mimeType,
            sampleRateHz = track.sampleRateHz,
            bitsPerSample = track.bitsPerSample,
            bitrateBps = track.bitrateBps,
            fileSizeBytes = track.fileSizeBytes,
            relativePath = track.relativePath,
            volumeName = volumeName,
            artworkUri = track.artworkUri,

            generationModified =
                generationModified,

            embeddedMetadataSignature =
                if (embeddedMetadataIsValid) {
                    existing?.embeddedMetadataSignature
                } else {
                    null
                },

            embeddedMetadataStatus =
                if (embeddedMetadataIsValid) {
                    existing?.embeddedMetadataStatus
                        ?: MusicTrackEntity
                            .EMBEDDED_METADATA_PENDING
                } else {
                    MusicTrackEntity
                        .EMBEDDED_METADATA_PENDING
                },

            embeddedMetadataUpdatedAtEpochMillis =
                if (embeddedMetadataIsValid) {
                    existing?.embeddedMetadataUpdatedAtEpochMillis
                } else {
                    null
                },
        )
    }

    private enum class VolumeSyncOutcome {
        FULL_SCAN,
        INCREMENTAL,
        UNCHANGED,
    }

    private companion object {
        const val TAG =
            "MediaCatalogSync"

        const val DATABASE_BATCH_SIZE =
            400
    }
}