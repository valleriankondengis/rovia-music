package com.rovia.music.data.mediastore

import android.content.ContentResolver
import android.content.ContentUris
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import com.rovia.music.data.repository.FolderFilterRepository
import com.rovia.music.data.repository.isExcludedFolderPath
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.Comparator

/**
 * In-memory MediaStore catalog shared by Home, Library and Search.
 *
 * The cache is deliberately process-local (no database). A ContentObserver marks it stale when
 * MediaStore changes, then refreshes it after a short debounce so bulk file operations do not
 * trigger a query for every individual notification.
 */
class MediaStoreAudioRepository(
    private val contentResolver: ContentResolver,
    private val folderFilterRepository: FolderFilterRepository,
) {
    data class CatalogState(
        /** Alphabetical, folder-filtered track list. */
        val tracks: List<LocalAudioTrack> = emptyList(),
        /** Same catalog sorted newest-first, used by Home and Recently Added. */
        val recentlyAddedTracks: List<LocalAudioTrack> = emptyList(),
        val isLoaded: Boolean = false,
        val isRefreshing: Boolean = false,
        /** True only when there is no usable cached catalog because loading failed. */
        val loadFailed: Boolean = false,
        /** Increased when the visible catalog contents change. */
        val revision: Long = 0L,
    )

    private val collectionUri: Uri = MediaStore.Audio.Media.getContentUri(
        MediaStore.VOLUME_EXTERNAL,
    )

    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val refreshMutex = Mutex()
    private val observerLock = Any()

    private val mutableCatalogState = MutableStateFlow(CatalogState())
    val catalogState: StateFlow<CatalogState> = mutableCatalogState.asStateFlow()

    /** Raw MediaStore results, including folder paths, so filter changes do not require a rescan. */
    private var cachedRawTracks: List<LocalAudioTrack>? = null
    private var lastAppliedExcludedFolders: Set<String>? = null

    @Volatile
    private var cacheInvalidated = true

    /** Sequence number prevents a MediaStore change during a query from being lost. */
    private var mediaChangeSequence = 0L

    private var observerRegistered = false
    private var pendingRefreshJob: Job? = null

    private val mediaStoreObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            scheduleMediaStoreRefresh()
        }

        override fun onChange(selfChange: Boolean, uri: Uri?) {
            scheduleMediaStoreRefresh()
        }
    }

    init {
        // Folder filtering changes can be reflected immediately from the cached raw catalog.
        repositoryScope.launch {
            folderFilterRepository.excludedFolders.collect { excludedFolders ->
                refreshMutex.withLock {
                    val rawTracks = cachedRawTracks ?: return@withLock
                    if (lastAppliedExcludedFolders == excludedFolders) {
                        return@withLock
                    }

                    publishCatalog(
                        rawTracks = rawTracks,
                        excludedFolders = excludedFolders,
                        isRefreshing = mutableCatalogState.value.isRefreshing,
                    )
                }
            }
        }
    }

    /**
     * Load the catalog if it has not been loaded yet or MediaStore has invalidated the cache.
     * A cached catalog is returned immediately on subsequent calls.
     *
     * Pass [forceRefresh] for an explicit user retry/refresh. MediaStore notifications trigger
     * this automatically; callers normally do not need to force a refresh.
     */
    suspend fun refresh(forceRefresh: Boolean = false) {
        ensureContentObserverRegistered()

        refreshMutex.withLock {
            if (!forceRefresh && cachedRawTracks != null && !cacheInvalidated) {
                return@withLock
            }

            val previousState = mutableCatalogState.value
            mutableCatalogState.value = previousState.copy(
                isRefreshing = true,
                loadFailed = false,
            )

            val queryStartSequence = synchronized(observerLock) {
                mediaChangeSequence
            }

            try {
                val freshRawTracks = queryRawTracks()
                val excludedFolders = folderFilterRepository.excludedFolders.value
                val previousRawTracks = cachedRawTracks
                val previousExcludedFolders = lastAppliedExcludedFolders

                cachedRawTracks = freshRawTracks
                synchronized(observerLock) {
                    // If MediaStore notified us during this query, keep the cache invalidated so
                    // the debounced job performs one more pass after the provider settles.
                    cacheInvalidated = mediaChangeSequence != queryStartSequence
                }

                val currentVisible = mutableCatalogState.value
                val shouldRepublish =
                    previousRawTracks != freshRawTracks ||
                        previousExcludedFolders != excludedFolders ||
                        !currentVisible.isLoaded

                if (shouldRepublish) {
                    publishCatalog(
                        rawTracks = freshRawTracks,
                        excludedFolders = excludedFolders,
                        isRefreshing = false,
                    )
                } else {
                    mutableCatalogState.value = currentVisible.copy(
                        isRefreshing = false,
                        loadFailed = false,
                    )
                }
            } catch (exception: CancellationException) {
                cacheInvalidated = true
                mutableCatalogState.value = mutableCatalogState.value.copy(
                    isRefreshing = false,
                    loadFailed = cachedRawTracks == null,
                )
                throw exception
            } catch (exception: Exception) {
                cacheInvalidated = true
                mutableCatalogState.value = mutableCatalogState.value.copy(
                    isRefreshing = false,
                    loadFailed = cachedRawTracks == null,
                )
                throw exception
            }
        }
    }

    /** Compatibility helpers for existing callers that want a one-shot list. */
    suspend fun loadTracks(forceRefresh: Boolean = false): List<LocalAudioTrack> {
        refresh(forceRefresh)
        return mutableCatalogState.value.tracks
    }

    suspend fun loadRecentlyAddedTracks(
        limit: Int? = null,
        forceRefresh: Boolean = false,
    ): List<LocalAudioTrack> {
        refresh(forceRefresh)
        val tracks = mutableCatalogState.value.recentlyAddedTracks
        return if (limit == null || limit < 0) tracks else tracks.take(limit)
    }

    private fun ensureContentObserverRegistered() {
        synchronized(observerLock) {
            if (observerRegistered) return

            try {
                contentResolver.registerContentObserver(
                    collectionUri,
                    true,
                    mediaStoreObserver,
                )
                observerRegistered = true
            } catch (exception: Exception) {
                // The query still works. A later refresh will retry registration, and screens
                // refresh on re-entry, so a transient registration failure is not fatal.
                Log.w(TAG, "Unable to register MediaStore observer", exception)
            }
        }
    }

    private fun scheduleMediaStoreRefresh() {
        synchronized(observerLock) {
            mediaChangeSequence += 1L
            cacheInvalidated = true
            pendingRefreshJob?.cancel()
            pendingRefreshJob = repositoryScope.launch {
                delay(CONTENT_CHANGE_DEBOUNCE_MS)
                try {
                    refresh(forceRefresh = false)
                } catch (exception: CancellationException) {
                    throw exception
                } catch (exception: Exception) {
                    Log.w(TAG, "Unable to refresh MediaStore catalog", exception)
                }
            }
        }
    }

    private suspend fun queryRawTracks(): List<LocalAudioTrack> = withContext(Dispatchers.IO) {
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.MediaColumns.DATE_MODIFIED,
            MediaStore.MediaColumns.RELATIVE_PATH,
        )

        val selection =
            "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND " +
                "${MediaStore.Audio.Media.DURATION} > 0"

        val cursor = contentResolver.query(
            collectionUri,
            projection,
            selection,
            null,
            "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC",
        ) ?: return@withContext emptyList()

        cursor.use { result ->
            val idColumn = result.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleColumn = result.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistColumn = result.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumColumn = result.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val durationColumn = result.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val dateAddedColumn = result.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
            val dateModifiedColumn = result.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)
            val relativePathColumn = result.getColumnIndexOrThrow(MediaStore.MediaColumns.RELATIVE_PATH)

            buildList {
                while (result.moveToNext()) {
                    val id = result.getLong(idColumn)

                    add(
                        LocalAudioTrack(
                            id = id,
                            uri = ContentUris.withAppendedId(collectionUri, id),
                            title = result.getString(titleColumn)?.takeIf(String::isNotBlank),
                            artist = result.getString(artistColumn)?.takeIf(String::isNotBlank),
                            album = result.getString(albumColumn)?.takeIf(String::isNotBlank),
                            durationMs = result.getLong(durationColumn).coerceAtLeast(0L),
                            dateAddedSeconds = result.getLong(dateAddedColumn).coerceAtLeast(0L),
                            relativePath = result.getString(relativePathColumn),
                            dateModifiedSeconds = result.getLong(dateModifiedColumn).coerceAtLeast(0L),
                        ),
                    )
                }
            }
        }
    }

    private fun publishCatalog(
        rawTracks: List<LocalAudioTrack>,
        excludedFolders: Set<String>,
        isRefreshing: Boolean,
    ) {
        val visibleTracks = rawTracks.filter { track ->
            !isExcludedFolderPath(
                path = track.relativePath.orEmpty(),
                excludedFolders = excludedFolders,
            )
        }

        // queryRawTracks already sorts alphabetically, and filtering preserves that order.
        val recentlyAddedTracks = visibleTracks.sortedWith(
            Comparator { left, right ->
                val dateOrder = right.dateAddedSeconds.compareTo(left.dateAddedSeconds)
                if (dateOrder != 0) {
                    dateOrder
                } else {
                    String.CASE_INSENSITIVE_ORDER.compare(
                        left.title.orEmpty(),
                        right.title.orEmpty(),
                    )
                }
            },
        )

        val previous = mutableCatalogState.value
        val contentChanged =
            !previous.isLoaded ||
                previous.tracks != visibleTracks ||
                previous.recentlyAddedTracks != recentlyAddedTracks

        lastAppliedExcludedFolders = excludedFolders
        mutableCatalogState.value = CatalogState(
            tracks = visibleTracks,
            recentlyAddedTracks = recentlyAddedTracks,
            isLoaded = true,
            isRefreshing = isRefreshing,
            loadFailed = false,
            revision = previous.revision + if (contentChanged) 1L else 0L,
        )
    }

    private companion object {
        const val TAG = "MediaStoreAudioRepo"
        const val CONTENT_CHANGE_DEBOUNCE_MS = 350L
    }
}