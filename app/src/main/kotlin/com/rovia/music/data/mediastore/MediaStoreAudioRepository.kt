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
import java.util.Locale

class MediaStoreAudioRepository(
    private val contentResolver: ContentResolver,
    private val folderFilterRepository: FolderFilterRepository,
) {
    data class CatalogState(
        val tracks: List<LocalAudioTrack> = emptyList(),
        val recentlyAddedTracks: List<LocalAudioTrack> = emptyList(),
        val isLoaded: Boolean = false,
        val isRefreshing: Boolean = false,
        val loadFailed: Boolean = false,
        val revision: Long = 0L,
    )

    private data class GenreRecord(
        val id: Long,
        val name: String,
    )

    private val collectionUri: Uri = MediaStore.Audio.Media.getContentUri(
        MediaStore.VOLUME_EXTERNAL,
    )

    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val refreshMutex = Mutex()
    private val observerLock = Any()

    private val mutableCatalogState = MutableStateFlow(CatalogState())
    val catalogState: StateFlow<CatalogState> = mutableCatalogState.asStateFlow()

    private var cachedRawTracks: List<LocalAudioTrack>? = null
    private var lastAppliedExcludedFolders: Set<String>? = null

    @Volatile
    private var cacheInvalidated = true

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
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.ALBUM_ARTIST,
            MediaStore.Audio.Media.YEAR,
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

        val rawTracks = cursor.use { result ->
            val idColumn = result.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleColumn = result.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistColumn = result.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumColumn = result.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val durationColumn = result.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val dateAddedColumn = result.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
            val dateModifiedColumn =
                result.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)
            val relativePathColumn =
                result.getColumnIndexOrThrow(MediaStore.MediaColumns.RELATIVE_PATH)

            val albumIdColumn = result.getColumnIndex(MediaStore.Audio.Media.ALBUM_ID)
            val albumArtistColumn = result.getColumnIndex(MediaStore.Audio.Media.ALBUM_ARTIST)
            val releaseYearColumn = result.getColumnIndex(MediaStore.Audio.Media.YEAR)

            buildList {
                while (result.moveToNext()) {
                    val id = result.getLong(idColumn)

                    val albumId = if (
                        albumIdColumn >= 0 && !result.isNull(albumIdColumn)
                    ) {
                        result.getLong(albumIdColumn).takeIf { it > 0L }
                    } else {
                        null
                    }

                    val albumArtist = if (albumArtistColumn >= 0) {
                        result.getString(albumArtistColumn)?.takeIf(String::isNotBlank)
                    } else {
                        null
                    }

                    val releaseYear = if (
                        releaseYearColumn >= 0 && !result.isNull(releaseYearColumn)
                    ) {
                        result.getInt(releaseYearColumn).takeIf { it in 1000..9999 }
                    } else {
                        null
                    }

                    add(
                        LocalAudioTrack(
                            id = id,
                            uri = ContentUris.withAppendedId(collectionUri, id),
                            title = result.getString(titleColumn)
                                ?.takeIf(String::isNotBlank),
                            artist = result.getString(artistColumn)
                                ?.takeIf(String::isNotBlank),
                            album = result.getString(albumColumn)
                                ?.takeIf(String::isNotBlank),
                            durationMs = result.getLong(durationColumn).coerceAtLeast(0L),
                            dateAddedSeconds = result.getLong(dateAddedColumn)
                                .coerceAtLeast(0L),
                            relativePath = result.getString(relativePathColumn),
                            dateModifiedSeconds = result.getLong(dateModifiedColumn)
                                .coerceAtLeast(0L),
                            albumId = albumId,
                            albumArtist = albumArtist,
                            releaseYear = releaseYear,
                        ),
                    )
                }
            }
        }

        val audioIds = rawTracks.mapTo(mutableSetOf()) { it.id }
        val genresByAudioId = queryGenresByAudioId(audioIds)

        rawTracks.map { track ->
            track.copy(
                genres = genresByAudioId[track.id].orEmpty(),
            )
        }
    }

    private fun queryGenresByAudioId(
        audioIds: Set<Long>,
    ): Map<Long, List<String>> {
        if (audioIds.isEmpty()) {
            return emptyMap()
        }

        val genreRecords = try {
            queryGenreRecords()
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            Log.w(TAG, "Unable to query MediaStore genres", exception)
            return emptyMap()
        }

        if (genreRecords.isEmpty()) {
            return emptyMap()
        }

        val genresByAudioId = mutableMapOf<Long, LinkedHashMap<String, String>>()

        genreRecords.forEach { genre ->
            try {
                val membersUri = MediaStore.Audio.Genres.Members.getContentUri(
                    MediaStore.VOLUME_EXTERNAL,
                    genre.id,
                )

                val cursor = contentResolver.query(
                    membersUri,
                    arrayOf(MediaStore.Audio.Genres.Members.AUDIO_ID),
                    null,
                    null,
                    null,
                ) ?: return@forEach

                cursor.use { result ->
                    val audioIdColumn = result.getColumnIndex(
                        MediaStore.Audio.Genres.Members.AUDIO_ID,
                    )

                    if (audioIdColumn < 0) {
                        return@use
                    }

                    while (result.moveToNext()) {
                        val audioId = result.getLong(audioIdColumn)

                        if (audioId !in audioIds) {
                            continue
                        }

                        val normalizedGenreName = genre.name.lowercase(Locale.ROOT)

                        genresByAudioId
                            .getOrPut(audioId) { linkedMapOf() }
                            .putIfAbsent(normalizedGenreName, genre.name)
                    }
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                Log.w(
                    TAG,
                    "Unable to query members for genre ${genre.name}",
                    exception,
                )
            }
        }

        return genresByAudioId.mapValues { (_, genreNames) ->
            genreNames.values.toList()
        }
    }

    private fun queryGenreRecords(): List<GenreRecord> {
        val genresUri = MediaStore.Audio.Genres.getContentUri(
            MediaStore.VOLUME_EXTERNAL,
        )

        val cursor = contentResolver.query(
            genresUri,
            arrayOf(
                MediaStore.Audio.Genres._ID,
                MediaStore.Audio.Genres.NAME,
            ),
            null,
            null,
            "${MediaStore.Audio.Genres.NAME} COLLATE NOCASE ASC",
        ) ?: return emptyList()

        return cursor.use { result ->
            val idColumn = result.getColumnIndex(MediaStore.Audio.Genres._ID)
            val nameColumn = result.getColumnIndex(MediaStore.Audio.Genres.NAME)

            if (idColumn < 0 || nameColumn < 0) {
                return@use emptyList()
            }

            buildList {
                while (result.moveToNext()) {
                    val genreId = result.getLong(idColumn)
                    val genreName = result.getString(nameColumn)
                        ?.trim()
                        ?.takeIf(String::isNotEmpty)

                    if (genreId > 0L && genreName != null) {
                        add(
                            GenreRecord(
                                id = genreId,
                                name = genreName,
                            ),
                        )
                    }
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