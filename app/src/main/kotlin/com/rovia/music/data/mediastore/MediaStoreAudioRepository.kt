package com.rovia.music.data.mediastore

import android.content.ContentResolver
import android.content.ContentUris
import android.database.ContentObserver
import android.net.Uri
import android.media.MediaMetadataRetriever
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
import java.time.LocalDate
import java.time.ZoneOffset
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

    private data class EmbeddedTrackMetadata(
        val releaseDate: String? = null,
        val metadataDateEpochMillis: Long? = null,
        val inferredDateEpochMillis: Long? = null,
        val releaseYear: Int? = null,
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
    )

    private data class ParsedMetadataDate(
        val year: Int,
        val metadataDateEpochMillis: Long?,
        val inferredDateEpochMillis: Long?,
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

    suspend fun loadTrackMetadata(track: LocalAudioTrack): LocalAudioTrack =
        withContext(Dispatchers.IO) {
            applyEmbeddedMetadata(
                track = track,
                metadata = readEmbeddedMetadata(track.uri),
            )
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

    private fun applyEmbeddedMetadata(
        track: LocalAudioTrack,
        metadata: EmbeddedTrackMetadata,
    ): LocalAudioTrack {
        val releaseYear = track.releaseYear ?: metadata.releaseYear
        val metadataGenres = metadata.genre
            ?.split(',', ';')
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            ?.distinctBy { it.lowercase(Locale.ROOT) }
            .orEmpty()

        return track.copy(
            albumArtist = track.albumArtist ?: metadata.albumArtist,
            releaseYear = releaseYear,
            releaseDate = metadata.releaseDate ?: track.releaseDate,
            metadataDateEpochMillis = metadata.metadataDateEpochMillis
                ?: track.metadataDateEpochMillis,
            inferredDateEpochMillis = metadata.inferredDateEpochMillis
                ?: track.inferredDateEpochMillis
                ?: yearStartEpochMillis(releaseYear),
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
        val match = Regex("""^(\d{4})(?:[-/.](\d{1,2}))?(?:[-/.](\d{1,2}))?.*$""")
            .matchEntire(text) ?: return null
        val year = match.groupValues[1].toIntOrNull()
            ?.takeIf { it in 1000..9999 } ?: return null
        val month = match.groupValues[2].takeIf(String::isNotEmpty)?.toIntOrNull()
        val day = match.groupValues[3].takeIf(String::isNotEmpty)?.toIntOrNull()

        return try {
            val normalizedDate = when {
                month == null -> LocalDate.of(year, 1, 1)
                day == null -> LocalDate.of(year, month, 1)
                else -> LocalDate.of(year, month, day)
            }
            val timestamp = normalizedDate
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant()
                .toEpochMilli()
            val yearStart = LocalDate.of(year, 1, 1)
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant()
                .toEpochMilli()

            ParsedMetadataDate(
                year = year,
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

    private fun yearStartEpochMillis(year: Int?): Long? {
        if (year == null || year !in 1000..9999) return null

        return try {
            LocalDate.of(year, 1, 1)
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant()
                .toEpochMilli()
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
private fun android.database.Cursor.getStringOrNull(columnIndex: Int): String? {
    if (columnIndex < 0 || isNull(columnIndex)) return null
    return getString(columnIndex)?.takeIf(String::isNotBlank)
}

private fun android.database.Cursor.getLongOrNull(columnIndex: Int): Long? {
    if (columnIndex < 0 || isNull(columnIndex)) return null
    return getLong(columnIndex)
}