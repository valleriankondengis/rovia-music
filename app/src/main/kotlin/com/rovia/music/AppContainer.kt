
package com.rovia.music

import android.content.Context
import android.util.Log
import com.rovia.music.core.library.FolderBrowserRepository
import com.rovia.music.core.library.FolderFilterRepository
import com.rovia.music.core.library.FolderScannerRepository
import com.rovia.music.core.library.LyricsRepository
import com.rovia.music.core.library.MusicRepository
import com.rovia.music.core.playback.PlaybackController
import com.rovia.music.core.playback.PlaybackSettingsRepository
import com.rovia.music.core.playback.RecentPlayRepository
import com.rovia.music.data.database.RoviaDatabase
import com.rovia.music.data.database.RoviaDatabaseProvider
import com.rovia.music.data.database.repository.RoomMusicCatalogRepository
import com.rovia.music.data.media.store.EmbeddedLyricsRepository
import com.rovia.music.data.media.store.EmbeddedMetadataEnrichmentProcessor
import com.rovia.music.data.media.store.MediaCatalogSyncCoordinator
import com.rovia.music.data.media.store.MediaStoreCatalogDataSource
import com.rovia.music.data.media.store.MediaStoreFolderBrowserRepository
import com.rovia.music.data.media.store.MediaStoreFolderScannerRepository
import com.rovia.music.playback.media3.Media3PlaybackController
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class AppContainer(
    context: Context,
    private val applicationScope: CoroutineScope,
) {

    private val applicationContext =
        context.applicationContext

    private val database =
        RoviaDatabase.getInstance(
            applicationContext,
        )

    private val recentPlayRepository:
        RecentPlayRepository =
        RoviaDatabaseProvider
            .createRecentPlayRepository(
                context = applicationContext,
                scope = applicationScope,
            )

    private val playbackSettingsRepository:
        PlaybackSettingsRepository =
        RoviaDatabaseProvider
            .createPlaybackSettingsRepository(
                context = applicationContext,
                scope = applicationScope,
            )

    val folderFilterRepository:
        FolderFilterRepository =
        RoviaDatabaseProvider
            .createFolderFilterRepository(
                context = applicationContext,
                scope = applicationScope,
            )

    val folderScannerRepository:
        FolderScannerRepository =
        MediaStoreFolderScannerRepository(
            context = applicationContext,
        )

    val folderBrowserRepository:
        FolderBrowserRepository =
        MediaStoreFolderBrowserRepository(
            context = applicationContext,
            folderFilterRepository =
                folderFilterRepository,
        )

    /*
     * The UI reads the persistent Room catalog.
     *
     * MediaStore is accessed separately by the synchronization
     * pipeline below.
     */
    val musicRepository:
        MusicRepository =
        RoomMusicCatalogRepository(
            dao = database.musicTrackDao(),
        )

    private val mediaStoreCatalogDataSource =
        MediaStoreCatalogDataSource(
            context = applicationContext,
        )

    private val catalogSyncCoordinator =
        MediaCatalogSyncCoordinator(
            context = applicationContext,
            dataSource = mediaStoreCatalogDataSource,
            musicTrackDao = database.musicTrackDao(),
            syncStateDao = database.mediaStoreSyncStateDao(),
        )

    private val embeddedMetadataProcessor =
        EmbeddedMetadataEnrichmentProcessor(
            context = applicationContext,
            musicTrackDao = database.musicTrackDao(),
        )

    private val catalogSyncLock =
        Any()

    @Volatile
    private var catalogSyncJob: Job? = null

    val lyricsRepository:
        LyricsRepository =
        EmbeddedLyricsRepository(
            applicationContext,
        )

    val playbackController:
        PlaybackController =
        Media3PlaybackController(
            context = applicationContext,
            recentPlayRepository =
                recentPlayRepository,
            playbackSettingsRepository =
                playbackSettingsRepository,
        )

    /**
     * Starts catalog synchronization without blocking the caller.
     *
     * Concurrent requests share the active synchronization job.
     * The Room catalog remains available to readers while synchronization
     * and embedded-metadata enrichment run in the application scope.
     *
     * This method must only be invoked after audio permission is granted.
     */
    fun synchronizeMusicCatalog() {
        synchronized(catalogSyncLock) {
            if (catalogSyncJob?.isActive == true) {
                return
            }

            catalogSyncJob =
                applicationScope.launch {
                    try {
                        val report =
                            catalogSyncCoordinator
                                .synchronize()

                        if (
                            !report.permissionGranted ||
                            report.volumeDiscoveryFailed ||
                            report.failedVolumes.isNotEmpty()
                        ) {
                            Log.w(
                                TAG,
                                "Catalog synchronization was incomplete. " +
                                    "Pending embedded metadata will be retained.",
                            )
                            return@launch
                        }

                        /*
                         * Process pending metadata in small batches.
                         * A short pause between batches allows other
                         * application work to proceed.
                         */
                        while (true) {
                            val batchReport =
                                embeddedMetadataProcessor
                                    .processPendingBatch()

                            if (batchReport.queued == 0) {
                                break
                            }

                            delay(
                                EMBEDDED_METADATA_BATCH_DELAY_MS,
                            )
                        }
                    } catch (
                        cancellation: CancellationException,
                    ) {
                        throw cancellation
                    } catch (
                        exception: Exception,
                    ) {
                        Log.e(
                            TAG,
                            "Music catalog synchronization failed.",
                            exception,
                        )
                    } finally {
                        synchronized(catalogSyncLock) {
                            catalogSyncJob = null
                        }
                    }
                }
        }
    }

    private companion object {
        const val TAG =
            "RoviaAppContainer"

        const val EMBEDDED_METADATA_BATCH_DELAY_MS =
            250L
    }
}
