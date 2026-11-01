package com.rovia.music.data.mediastore

import com.rovia.music.util.KotlinDateTime
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.TimeZone
import kotlinx.datetime.LocalDate
import android.content.ContentResolver
import android.content.ContentUris
import android.database.ContentObserver
import android.net.Uri
import android.media.MediaMetadataRetriever
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import androidx.room3.withWriteTransaction
import com.rovia.music.data.local.database.RoviaDatabase
import com.rovia.music.data.local.entity.TrackEntity
import com.rovia.music.data.repository.FolderFilterRepository
import com.rovia.music.data.repository.isExcludedFolderPath
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class MediaStoreAudioRepository(
    private val contentResolver: ContentResolver,
    private val folderFilterRepository: FolderFilterRepository,
    private val catalogSynchronizer: MediaStoreCatalogSynchronizer? = null,
    private val database: RoviaDatabase? = null,
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

    private data class CatalogSyncRequest(
        val tracks: List<LocalAudioTrack>,
        val initialSnapshot: MediaStoreGenerationSnapshot,
    )

    private data class EmbeddedTrackMetadata(
        val releaseDate: String? = null,
        val metadataDateEpochMillis: Long? = null,
        val inferredDateEpochMillis: Long? = null,
        val releaseYear: Int? = null,
        val releaseMonth: Int? = null,
        val releaseDay: Int? = null,
        val releaseDatePrecision: String? = null,
        val releaseDateSource: String? = null,
        val albumArtist: String? = null,
        val cdTrackNumber: String? = null,
        val trackNumber: Int? = null,
        val discNumber: Int? = null,
        val compilation: String? = null,
        val genre: String? = null,
        val composer: String? = null,
        val author: String? = null,
        val writer: String? = null,
        val mimeType: String? = null,
        val sampleRateHz: Int? = null,
        val bitsPerSample: Int? = null,
        val bitrateBps: Int? = null,
        val extractionSucceeded: Boolean = false,
    )

    private data class ParsedMetadataDate(
        val year: Int,
        val month: Int?,
        val day: Int?,
        val precision: String,
        val metadataDateEpochMillis: Long?,
        val inferredDateEpochMillis: Long?,
    )

    private val collectionUri: Uri = MediaStore.Audio.Media.getContentUri(
        MediaStore.VOLUME_EXTERNAL,
    )

    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val refreshMutex = Mutex()
    private val catalogSyncRequests = Channel<CatalogSyncRequest>(Channel.CONFLATED)
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
    }

    init {
        val synchronizer = catalogSynchronizer
        if (synchronizer != null) {
            repositoryScope.launch {
                for (request in catalogSyncRequests) {
                    try {
                        val report = synchronizer.synchronize(
                            tracks = request.tracks,
                            initialSnapshot = request.initialSnapshot,
                        )
                        if (report.didWork) {
                            Log.d(
                                TAG,
                                "Room catalog sync: full=${report.fullSyncVolumeCount}, " +
                                    "incremental=${report.incrementalSyncVolumeCount}, " +
                                    "inserted=${report.insertedTrackCount}, " +
                                    "updated=${report.updatedTrackCount}, " +
                                    "unavailable=${report.unavailableTrackCount}",
                            )
                        }
                    } catch (exception: CancellationException) {
                        throw exception
                    } catch (exception: Exception) {
                        Log.w(TAG, "Unable to synchronize the Room catalog", exception)
                    }
                }
            }
        }

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
                val generationSnapshot = try {
                    catalogSynchronizer?.captureGenerationSnapshot()
                } catch (exception: CancellationException) {
                    throw exception
                } catch (exception: Exception) {
                    Log.w(TAG, "Unable to capture MediaStore generation snapshot", exception)
                    null
                }
                val freshRawTracks = queryRawTracks()
                val excludedFolders = folderFilterRepository.excludedFolders.value
                val previousRawTracks = cachedRawTracks
                val previousExcludedFolders = lastAppliedExcludedFolders

                cachedRawTracks = freshRawTracks

                val querySnapshotStable = synchronized(observerLock) {
                    val stable = mediaChangeSequence == queryStartSequence
                    cacheInvalidated = !stable
                    stable
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

                if (querySnapshotStable && generationSnapshot != null && catalogSynchronizer != null) {
                    val enqueueResult = catalogSyncRequests.trySend(
                        CatalogSyncRequest(
                            tracks = freshRawTracks,
                            initialSnapshot = generationSnapshot,
                        ),
                    )
                    if (enqueueResult.isFailure) {
                        Log.w(TAG, "Unable to enqueue Room catalog synchronization")
                    }
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

    suspend fun loadTrackMetadata(track: LocalAudioTrack): LocalAudioTrack =
        withContext(Dispatchers.IO) {
            applyEmbeddedMetadata(
                track = track,
                metadata = readEmbeddedMetadata(track.uri),
            )
        }

    suspend fun loadReleaseMetadata(track: LocalAudioTrack): LocalAudioTrack =
        withContext(Dispatchers.IO) {
            val roomDatabase = database
            val volumeName = track.volumeName?.takeIf(String::isNotBlank)
            val existingTrack = if (roomDatabase != null && volumeName != null) {
                roomDatabase.trackDao().getByMediaStoreIdentity(
                    mediaStoreId = track.id,
                    volumeName = volumeName,
                )
            } else {
                null
            }

            if (existingTrack != null && isEmbeddedMetadataCacheCurrent(existingTrack, track)) {
                return@withContext applyStoredMetadata(track, existingTrack)
            }

            val metadata = readEmbeddedMetadata(track.uri)
            val enrichedTrack = applyEmbeddedMetadata(track, metadata)
            if (roomDatabase == null || !metadata.extractionSucceeded || volumeName == null) {
                return@withContext enrichedTrack
            }

            val trackToUpdate = existingTrack
                ?: roomDatabase.trackDao().getByMediaStoreIdentity(
                    mediaStoreId = track.id,
                    volumeName = volumeName,
                )
                ?: return@withContext enrichedTrack

            persistEmbeddedMetadata(
                database = roomDatabase,
                trackEntity = trackToUpdate,
                metadata = metadata,
                enrichedTrack = enrichedTrack,
            )

            val updatedTrack = roomDatabase.trackDao().getById(trackToUpdate.trackId)
            if (updatedTrack != null) applyStoredMetadata(track, updatedTrack) else enrichedTrack
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
            MediaStore.Audio.AudioColumns.COMPOSER,
            MediaStore.Audio.AudioColumns.TRACK,
            MediaStore.MediaColumns.DATE_MODIFIED,
            MediaStore.MediaColumns.RELATIVE_PATH,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.VOLUME_NAME,
        )

        val fallbackProjection = arrayOf(
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
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.VOLUME_NAME,
        )

        val selection =
            "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND " +
                "${MediaStore.Audio.Media.DURATION} > 0"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"

        val extendedCursor = try {
            contentResolver.query(
                collectionUri,
                projection,
                selection,
                null,
                sortOrder,
            )
        } catch (exception: Exception) {
            Log.w(TAG, "Extended MediaStore projection failed; using basic columns", exception)
            null
        }

        val cursor = extendedCursor ?: contentResolver.query(
            collectionUri,
            fallbackProjection,
            selection,
            null,
            sortOrder,
        ) ?: throw IllegalStateException("MediaStore audio catalog query returned no cursor")

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
            val composerColumn = result.getColumnIndex(MediaStore.Audio.AudioColumns.COMPOSER)
            val trackNumberColumn = result.getColumnIndex(MediaStore.Audio.AudioColumns.TRACK)
            val displayNameColumn = result.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
            val fileSizeColumn = result.getColumnIndex(MediaStore.MediaColumns.SIZE)
            val mimeTypeColumn = result.getColumnIndex(MediaStore.MediaColumns.MIME_TYPE)
            val volumeNameColumn = result.getColumnIndex(MediaStore.MediaColumns.VOLUME_NAME)

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
                    val albumArtist = result.getStringOrNull(albumArtistColumn)
                    val releaseYear = if (
                        releaseYearColumn >= 0 && !result.isNull(releaseYearColumn)
                    ) {
                        result.getInt(releaseYearColumn).takeIf { it in 1000..9999 }
                    } else {
                        null
                    }
                    val mediaStoreTrackNumber = result.getLongOrNull(trackNumberColumn)
                        ?.takeIf { it > 0L && it <= Int.MAX_VALUE }
                        ?.toInt()
                    val trackNumber = mediaStoreTrackNumber
                        ?.let { if (it >= 1000) it % 1000 else it }
                        ?.takeIf { it > 0 }
                    val discNumber = mediaStoreTrackNumber
                        ?.takeIf { it >= 1000 }
                        ?.div(1000)
                        ?.takeIf { it > 0 }
                    val uri = ContentUris.withAppendedId(collectionUri, id)

                    add(
                        LocalAudioTrack(
                            id = id,
                            uri = uri,
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
                            releaseDatePrecision = if (releaseYear != null) "YEAR" else null,
                            releaseDateSource = if (releaseYear != null) "MEDIASTORE_YEAR" else null,
                            inferredDateEpochMillis = yearStartEpochMillis(releaseYear),
                            trackNumber = trackNumber,
                            discNumber = discNumber,
                            cdTrackNumber = mediaStoreTrackNumber?.toString(),
                            composer = result.getStringOrNull(composerColumn),
                            mimeType = result.getStringOrNull(mimeTypeColumn),
                            displayName = result.getStringOrNull(displayNameColumn),
                            fileSizeBytes = result.getLongOrNull(fileSizeColumn)
                                ?.takeIf { it > 0L },
                            volumeName = result.getStringOrNull(volumeNameColumn),
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

    private fun isEmbeddedMetadataCacheCurrent(
        cached: TrackEntity,
        track: LocalAudioTrack,
    ): Boolean {
        val title = track.title
            ?: track.displayName?.substringBeforeLast('.')?.takeIf(String::isNotBlank)
            ?: ""

        return cached.isAvailable &&
            cached.mediaStoreId == track.id &&
            cached.volumeName == track.volumeName &&
            cached.metadataExtractionVersion >= EMBEDDED_METADATA_EXTRACTION_VERSION &&
            cached.metadataExtractedAtEpochMillis != null &&
            cached.title == title &&
            cached.artistCreditRaw == track.artist &&
            cached.albumTitleSnapshot == track.album &&
            cached.durationMs == track.durationMs &&
            cached.mediaStoreYear == track.releaseYear &&
            cached.dateAddedSeconds == track.dateAddedSeconds &&
            cached.dateModifiedSeconds == track.dateModifiedSeconds &&
            cached.relativePath == track.relativePath &&
            cached.fileSizeBytes == track.fileSizeBytes &&
            cached.mimeType == track.mimeType
    }

    private fun applyStoredMetadata(
        track: LocalAudioTrack,
        cached: TrackEntity,
    ): LocalAudioTrack {
        val parsedDate = parseMetadataDate(cached.releaseDateRaw)
        val releaseYear = cached.releaseYear ?: cached.mediaStoreYear ?: track.releaseYear

        return track.copy(
            albumArtist = track.albumArtist ?: cached.albumArtistCreditRaw,
            releaseDate = cached.releaseDateRaw ?: track.releaseDate,
            releaseYear = releaseYear,
            releaseMonth = cached.releaseMonth ?: track.releaseMonth,
            releaseDay = cached.releaseDay ?: track.releaseDay,
            releaseDatePrecision = cached.releaseDatePrecision ?: track.releaseDatePrecision,
            releaseDateSource = cached.releaseDateSource ?: track.releaseDateSource,
            metadataDateEpochMillis = parsedDate?.metadataDateEpochMillis
                ?: track.metadataDateEpochMillis,
            inferredDateEpochMillis = parsedDate?.inferredDateEpochMillis
                ?: track.inferredDateEpochMillis
                ?: yearStartEpochMillis(releaseYear),
            trackNumber = cached.trackNumber ?: track.trackNumber,
            discNumber = cached.discNumber ?: track.discNumber,
            compilation = cached.isCompilation?.toString() ?: track.compilation,
        )
    }

    private suspend fun persistEmbeddedMetadata(
        database: RoviaDatabase,
        trackEntity: TrackEntity,
        metadata: EmbeddedTrackMetadata,
        enrichedTrack: LocalAudioTrack,
    ) {
        val now = KotlinDateTime.nowEpochMilliseconds()
        database.withWriteTransaction {
            val current = database.trackDao().getById(trackEntity.trackId)
                ?: return@withWriteTransaction
            if (!current.isAvailable) return@withWriteTransaction

            val hasEmbeddedReleaseDate = metadata.releaseDateSource == "EMBEDDED_DATE"
            val hasEmbeddedReleaseYear = metadata.releaseDateSource == "EMBEDDED_YEAR"
            val hasEmbeddedReleaseMetadata = hasEmbeddedReleaseDate || hasEmbeddedReleaseYear
            val fallbackYear = current.releaseYear ?: current.mediaStoreYear
            val releaseYear = if (hasEmbeddedReleaseMetadata) {
                metadata.releaseYear ?: fallbackYear
            } else {
                fallbackYear
            }
            val releaseDateRaw = when {
                hasEmbeddedReleaseDate -> metadata.releaseDate
                hasEmbeddedReleaseYear -> metadata.releaseYear?.toString()
                !current.releaseDateRaw.isNullOrBlank() -> current.releaseDateRaw
                current.mediaStoreYear != null -> current.mediaStoreYear.toString()
                else -> null
            }
            val releaseMonth = if (hasEmbeddedReleaseMetadata) metadata.releaseMonth else current.releaseMonth
            val releaseDay = if (hasEmbeddedReleaseMetadata) metadata.releaseDay else current.releaseDay
            val releaseDatePrecision = when {
                hasEmbeddedReleaseMetadata -> metadata.releaseDatePrecision ?: "YEAR"
                !current.releaseDatePrecision.isNullOrBlank() -> current.releaseDatePrecision
                current.mediaStoreYear != null -> "YEAR"
                else -> null
            }
            val releaseDateSource = when {
                hasEmbeddedReleaseMetadata -> metadata.releaseDateSource
                !current.releaseDateSource.isNullOrBlank() -> current.releaseDateSource
                current.mediaStoreYear != null -> "MEDIASTORE_YEAR"
                else -> null
            }
            val updatedTrack = current.copy(
                albumArtistCreditRaw = current.albumArtistCreditRaw
                    ?: enrichedTrack.albumArtist?.takeIf(String::isNotBlank),
                releaseDateRaw = releaseDateRaw,
                releaseYear = releaseYear,
                releaseMonth = releaseMonth,
                releaseDay = releaseDay,
                releaseDatePrecision = releaseDatePrecision,
                releaseDateSource = releaseDateSource,
                trackNumber = enrichedTrack.trackNumber ?: current.trackNumber,
                discNumber = enrichedTrack.discNumber ?: current.discNumber,
                isCompilation = enrichedTrack.compilation?.let(::parseBooleanMetadata)
                    ?: current.isCompilation,
                metadataExtractionVersion = EMBEDDED_METADATA_EXTRACTION_VERSION,
                metadataExtractedAtEpochMillis = now,
                updatedAtEpochMillis = now,
            )
            database.trackDao().update(updatedTrack)

            val albumId = updatedTrack.albumReleaseId ?: return@withWriteTransaction
            if (!hasEmbeddedReleaseMetadata) return@withWriteTransaction

            val albumDao = database.albumDao()
            val album = albumDao.getById(albumId) ?: return@withWriteTransaction
            val candidatePriority = releaseDatePriority(
                source = updatedTrack.releaseDateSource,
                precision = updatedTrack.releaseDatePrecision,
            )
            val existingPriority = releaseDatePriority(
                source = album.releaseDateSource,
                precision = album.releaseDatePrecision,
            )
            if (candidatePriority <= existingPriority) return@withWriteTransaction

            albumDao.update(
                album.copy(
                    releaseDateRaw = updatedTrack.releaseDateRaw,
                    releaseYear = updatedTrack.releaseYear,
                    releaseMonth = updatedTrack.releaseMonth,
                    releaseDay = updatedTrack.releaseDay,
                    releaseDatePrecision = updatedTrack.releaseDatePrecision,
                    releaseDateSource = updatedTrack.releaseDateSource,
                    updatedAtEpochMillis = now,
                ),
            )
        }
    }

    private fun releaseDatePriority(source: String?, precision: String?): Int = when (source) {
        "EMBEDDED_DATE" -> when (precision) {
            "DAY" -> 500
            "MONTH" -> 400
            else -> 300
        }
        "EMBEDDED_YEAR" -> 250
        "MEDIASTORE_ALBUM_FIRST_YEAR" -> 200
        "MEDIASTORE_YEAR" -> 180
        "MEDIASTORE_ALBUM_FIRST_LAST_YEAR", "MEDIASTORE_ALBUM_LAST_YEAR" -> 100
        else -> 0
    }

    private fun applyEmbeddedMetadata(
        track: LocalAudioTrack,
        metadata: EmbeddedTrackMetadata,
    ): LocalAudioTrack {
        val releaseYear = metadata.releaseYear ?: track.releaseYear
        val hasEmbeddedReleaseMetadata = metadata.releaseDateSource != null
        val metadataGenres = metadata.genre
            ?.split(',', ';')
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            ?.distinctBy { it.lowercase() }
            .orEmpty()

        return track.copy(
            albumArtist = track.albumArtist ?: metadata.albumArtist,
            releaseYear = releaseYear,
            releaseDate = if (metadata.releaseDateSource == "EMBEDDED_DATE") {
                metadata.releaseDate
            } else {
                track.releaseDate
            },
            releaseMonth = if (hasEmbeddedReleaseMetadata) metadata.releaseMonth else track.releaseMonth,
            releaseDay = if (hasEmbeddedReleaseMetadata) metadata.releaseDay else track.releaseDay,
            releaseDatePrecision = metadata.releaseDatePrecision ?: track.releaseDatePrecision,
            releaseDateSource = metadata.releaseDateSource ?: track.releaseDateSource,
            metadataDateEpochMillis = if (hasEmbeddedReleaseMetadata) {
                metadata.metadataDateEpochMillis
            } else {
                track.metadataDateEpochMillis
            },
            inferredDateEpochMillis = if (hasEmbeddedReleaseMetadata) {
                metadata.inferredDateEpochMillis
            } else {
                track.inferredDateEpochMillis ?: yearStartEpochMillis(releaseYear)
            },
            genres = track.genres.ifEmpty { metadataGenres },
            trackNumber = metadata.trackNumber ?: track.trackNumber,
            discNumber = metadata.discNumber ?: track.discNumber,
            cdTrackNumber = metadata.cdTrackNumber ?: track.cdTrackNumber,
            compilation = metadata.compilation ?: track.compilation,
            composer = track.composer ?: metadata.composer,
            author = metadata.author ?: track.author,
            writer = metadata.writer ?: track.writer,
            mimeType = track.mimeType ?: metadata.mimeType,
            sampleRateHz = metadata.sampleRateHz ?: track.sampleRateHz,
            bitsPerSample = metadata.bitsPerSample ?: track.bitsPerSample,
            bitrateBps = metadata.bitrateBps ?: track.bitrateBps,
        )
    }

    private fun readEmbeddedMetadata(uri: Uri): EmbeddedTrackMetadata {
        val retriever = MediaMetadataRetriever()

        return try {
            contentResolver.openFileDescriptor(uri, "r")?.use { descriptor ->
                retriever.setDataSource(descriptor.fileDescriptor)

                fun value(key: Int): String? = retriever.extractMetadata(key)
                    ?.trim()
                    ?.takeIf(String::isNotBlank)

                val releaseDate = value(MediaMetadataRetriever.METADATA_KEY_DATE)
                val parsedDate = parseMetadataDate(releaseDate)
                val metadataYear = value(MediaMetadataRetriever.METADATA_KEY_YEAR)
                    ?.toIntOrNull()
                    ?.takeIf { it in 1000..9999 }
                val releaseYear = parsedDate?.year ?: metadataYear
                val cdTrackNumber = value(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)
                val mediaTrackNumber = parsePositiveTrackNumber(cdTrackNumber)
                val discNumber = value(MediaMetadataRetriever.METADATA_KEY_DISC_NUMBER)
                    ?.substringBefore('/')
                    ?.toIntOrNull()
                    ?.takeIf { it > 0 }

                EmbeddedTrackMetadata(
                    releaseDate = releaseDate,
                    metadataDateEpochMillis = parsedDate?.metadataDateEpochMillis,
                    inferredDateEpochMillis = parsedDate?.inferredDateEpochMillis
                        ?: if (parsedDate == null) yearStartEpochMillis(releaseYear) else null,
                    releaseYear = releaseYear,
                    releaseMonth = parsedDate?.month,
                    releaseDay = parsedDate?.day,
                    releaseDatePrecision = parsedDate?.precision
                        ?: metadataYear?.let { "YEAR" },
                    releaseDateSource = when {
                        parsedDate != null -> "EMBEDDED_DATE"
                        metadataYear != null -> "EMBEDDED_YEAR"
                        else -> null
                    },
                    albumArtist = value(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST),
                    cdTrackNumber = cdTrackNumber,
                    trackNumber = mediaTrackNumber,
                    discNumber = discNumber,
                    compilation = value(MediaMetadataRetriever.METADATA_KEY_COMPILATION),
                    genre = value(MediaMetadataRetriever.METADATA_KEY_GENRE),
                    composer = value(MediaMetadataRetriever.METADATA_KEY_COMPOSER),
                    author = value(MediaMetadataRetriever.METADATA_KEY_AUTHOR),
                    writer = value(MediaMetadataRetriever.METADATA_KEY_WRITER),
                    mimeType = value(MediaMetadataRetriever.METADATA_KEY_MIMETYPE),
                    sampleRateHz = value(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE)
                        ?.toIntOrNull()
                        ?.takeIf { it > 0 },
                    bitsPerSample = value(MediaMetadataRetriever.METADATA_KEY_BITS_PER_SAMPLE)
                        ?.toIntOrNull()
                        ?.takeIf { it > 0 },
                    bitrateBps = value(MediaMetadataRetriever.METADATA_KEY_BITRATE)
                        ?.toIntOrNull()
                        ?.takeIf { it > 0 },
                    extractionSucceeded = true,
                )
            } ?: EmbeddedTrackMetadata()
        } catch (exception: Exception) {
            Log.w(TAG, "Unable to read embedded metadata for $uri", exception)
            EmbeddedTrackMetadata()
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
            }
        }
    }

    private fun parseMetadataDate(value: String?): ParsedMetadataDate? {
        val text = value?.trim()?.takeIf(String::isNotBlank) ?: return null
        val match = METADATA_DATE_PATTERN.matchEntire(text) ?: return null
        val year = match.groupValues[1].toIntOrNull()
            ?.takeIf { it in 1000..9999 } ?: return null
        val month = match.groupValues[2]
            .takeIf(String::isNotEmpty)
            ?.toIntOrNull()
        val day = match.groupValues[3]
            .takeIf(String::isNotEmpty)
            ?.toIntOrNull()

        if (day != null && month == null) return null

        return try {
            val normalizedDate = when {
                month == null -> LocalDate(year, 1, 1)
                day == null -> LocalDate(year, month, 1)
                else -> LocalDate(year, month, day)
            }
            val timestamp = normalizedDate.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()
            val yearStart = LocalDate(year, 1, 1).atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()
            val precision = when {
                day != null -> "DAY"
                month != null -> "MONTH"
                else -> "YEAR"
            }

            ParsedMetadataDate(
                year = year,
                month = month,
                day = day,
                precision = precision,
                metadataDateEpochMillis = timestamp.takeIf { month != null },
                inferredDateEpochMillis = yearStart.takeIf { month == null },
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun parsePositiveTrackNumber(value: String?): Int? = value
        ?.substringBefore('/')
        ?.trim()
        ?.toIntOrNull()
        ?.takeIf { it > 0 }

    private fun parseBooleanMetadata(value: String?): Boolean? = when (value?.trim()?.lowercase()) {
        "1", "true", "yes" -> true
        "0", "false", "no" -> false
        else -> null
    }

    private fun yearStartEpochMillis(year: Int?): Long? {
        if (year == null || year !in 1000..9999) return null

        return try {
            LocalDate(year, 1, 1).atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()
        } catch (_: Exception) {
            null
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

                        val normalizedGenreName = genre.name.lowercase()

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

    private suspend fun publishCatalog(
        rawTracks: List<LocalAudioTrack>,
        excludedFolders: Set<String>,
        isRefreshing: Boolean,
    ) {
        val previous = mutableCatalogState.value
        val (visibleTracks, recentlyAddedTracks, contentChanged) =
            withContext(Dispatchers.Default) {
                val visible = rawTracks.filter { track ->
                    !isExcludedFolderPath(
                        path = track.relativePath.orEmpty(),
                        excludedFolders = excludedFolders,
                    )
                }
                val recentlyAdded = visible.sortedWith(
                    compareByDescending<LocalAudioTrack> { it.dateAddedSeconds }
                        .thenBy { it.title.orEmpty().lowercase() },
                )
                val changed =
                    !previous.isLoaded ||
                        previous.tracks != visible ||
                        previous.recentlyAddedTracks != recentlyAdded
                Triple(visible, recentlyAdded, changed)
            }

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
        const val CONTENT_CHANGE_DEBOUNCE_MS = 600L
        const val EMBEDDED_METADATA_EXTRACTION_VERSION = 1
        val METADATA_DATE_PATTERN = Regex("""^(\d{4})(?:[-/.](\d{1,2}))?(?:[-/.](\d{1,2}))?.*$""")
    }
}
private fun android.database.Cursor.getStringOrNull(columnIndex: Int): String? {
    if (columnIndex < 0 || isNull(columnIndex)) return null
    return getString(columnIndex)?.takeIf(String::isNotBlank)
}

private fun android.database.Cursor.getLongOrNull(columnIndex: Int): Long? {
    if (columnIndex < 0 || isNull(columnIndex)) return null
    return getLong(columnIndex)
}
