package com.rovia.music.data.media.store

import android.content.ContentResolver
import android.content.Context
import android.media.MediaMetadataRetriever
import android.provider.MediaStore
import com.rovia.music.core.library.FolderFilterRepository
import com.rovia.music.core.library.MusicRepository
import com.rovia.music.core.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

class MediaStoreMusicRepository(
    context: Context,
    private val folderFilterRepository: FolderFilterRepository,
) : MusicRepository {

    private val applicationContext =
        context.applicationContext

    private val contentResolver: ContentResolver =
        applicationContext.contentResolver

    private val excludedFolders: StateFlow<Set<String>>
        get() =
            folderFilterRepository.excludedFolders

    override suspend fun getRecentlyAdded(
        limit: Int,
    ): List<Track> {
        return queryTracks(
            sortOrder =
                "${MediaStore.Audio.Media.DATE_ADDED} DESC",
            limit = limit,
        )
    }

    override suspend fun getAllTracks(): List<Track> {
        return queryTracks(
            sortOrder =
                "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC",
            limit = null,
        )
    }

    private suspend fun queryTracks(
        sortOrder: String,
        limit: Int?,
    ): List<Track> =
        withContext(Dispatchers.IO) {
            folderFilterRepository.awaitInitialized()

            val collection =
                MediaStore.Audio.Media.getContentUri(
                    MediaStore.VOLUME_EXTERNAL,
                )

            val projection =
                arrayOf(
                    MediaStore.Audio.Media._ID,
                    MediaStore.Audio.Media.TITLE,
                    MediaStore.Audio.Media.ARTIST,
                    MediaStore.Audio.Media.ALBUM,
                    MediaStore.Audio.Media.ALBUM_ARTIST,
                    MediaStore.Audio.Media.GENRE,
                    MediaStore.Audio.Media.AUTHOR,
                    MediaStore.Audio.Media.COMPOSER,
                    MediaStore.Audio.Media.WRITER,
                    MediaStore.Audio.Media.YEAR,
                    MediaStore.Audio.Media.TRACK,
                    MediaStore.Audio.Media.DISC_NUMBER,
                    MediaStore.Audio.Media.CD_TRACK_NUMBER,
                    MediaStore.Audio.Media.COMPILATION,
                    MediaStore.Audio.Media.DURATION,
                    MediaStore.Audio.Media.DATE_ADDED,
                    MediaStore.Audio.Media.DATE_MODIFIED,
                    MediaStore.Audio.Media.DATE_TAKEN,
                    MediaStore.Audio.Media.INFERRED_DATE,
                    MediaStore.Audio.Media.DISPLAY_NAME,
                    MediaStore.Audio.Media.MIME_TYPE,
                    MediaStore.Audio.Media.SAMPLERATE,
                    MediaStore.Audio.Media.BITS_PER_SAMPLE,
                    MediaStore.Audio.Media.BITRATE,
                    MediaStore.Audio.Media.SIZE,
                    MediaStore.Audio.Media.RELATIVE_PATH,
                    MediaStore.Audio.Media.VOLUME_NAME,
                )

            val queryArgs =
                android.os.Bundle().apply {
                    putString(
                        ContentResolver.QUERY_ARG_SQL_SORT_ORDER,
                        sortOrder,
                    )
                }

            val tracks =
                mutableListOf<Track>()

            contentResolver.query(
                collection,
                projection,
                queryArgs,
                null,
            )?.use { cursor ->

                val idIndex =
                    cursor.getColumnIndexOrThrow(
                        MediaStore.Audio.Media._ID,
                    )

                val titleIndex =
                    cursor.getColumnIndexOrThrow(
                        MediaStore.Audio.Media.TITLE,
                    )

                val artistIndex =
                    cursor.getColumnIndexOrThrow(
                        MediaStore.Audio.Media.ARTIST,
                    )

                val albumIndex =
                    cursor.getColumnIndexOrThrow(
                        MediaStore.Audio.Media.ALBUM,
                    )

                val albumArtistIndex =
                    cursor.getColumnIndexOrThrow(
                        MediaStore.Audio.Media.ALBUM_ARTIST,
                    )

                val genreIndex =
                    cursor.getColumnIndexOrThrow(
                        MediaStore.Audio.Media.GENRE,
                    )

                val authorIndex =
                    cursor.getColumnIndexOrThrow(
                        MediaStore.Audio.Media.AUTHOR,
                    )

                val composerIndex =
                    cursor.getColumnIndexOrThrow(
                        MediaStore.Audio.Media.COMPOSER,
                    )

                val writerIndex =
                    cursor.getColumnIndexOrThrow(
                        MediaStore.Audio.Media.WRITER,
                    )

                val yearIndex =
                    cursor.getColumnIndexOrThrow(
                        MediaStore.Audio.Media.YEAR,
                    )

                val trackIndex =
                    cursor.getColumnIndexOrThrow(
                        MediaStore.Audio.Media.TRACK,
                    )

                val discNumberIndex =
                    cursor.getColumnIndexOrThrow(
                        MediaStore.Audio.Media.DISC_NUMBER,
                    )

                val cdTrackNumberIndex =
                    cursor.getColumnIndexOrThrow(
                        MediaStore.Audio.Media.CD_TRACK_NUMBER,
                    )

                val compilationIndex =
                    cursor.getColumnIndexOrThrow(
                        MediaStore.Audio.Media.COMPILATION,
                    )

                val durationIndex =
                    cursor.getColumnIndexOrThrow(
                        MediaStore.Audio.Media.DURATION,
                    )

                val dateAddedIndex =
                    cursor.getColumnIndexOrThrow(
                        MediaStore.Audio.Media.DATE_ADDED,
                    )

                val dateModifiedIndex =
                    cursor.getColumnIndexOrThrow(
                        MediaStore.Audio.Media.DATE_MODIFIED,
                    )

                val metadataDateIndex =
                    cursor.getColumnIndexOrThrow(
                        MediaStore.Audio.Media.DATE_TAKEN,
                    )

                val inferredDateIndex =
                    cursor.getColumnIndexOrThrow(
                        MediaStore.Audio.Media.INFERRED_DATE,
                    )

                val displayNameIndex =
                    cursor.getColumnIndexOrThrow(
                        MediaStore.Audio.Media.DISPLAY_NAME,
                    )

                val mimeTypeIndex =
                    cursor.getColumnIndexOrThrow(
                        MediaStore.Audio.Media.MIME_TYPE,
                    )

                val sampleRateIndex =
                    cursor.getColumnIndexOrThrow(
                        MediaStore.Audio.Media.SAMPLERATE,
                    )

                val bitsPerSampleIndex =
                    cursor.getColumnIndexOrThrow(
                        MediaStore.Audio.Media.BITS_PER_SAMPLE,
                    )

                val bitrateIndex =
                    cursor.getColumnIndexOrThrow(
                        MediaStore.Audio.Media.BITRATE,
                    )

                val fileSizeIndex =
                    cursor.getColumnIndexOrThrow(
                        MediaStore.Audio.Media.SIZE,
                    )

                val relativePathIndex =
                    cursor.getColumnIndexOrThrow(
                        MediaStore.Audio.Media.RELATIVE_PATH,
                    )

                val volumeNameIndex =
                    cursor.getColumnIndexOrThrow(
                        MediaStore.Audio.Media.VOLUME_NAME,
                    )

                val currentExcludedFolders =
                    excludedFolders.value
                        .map(
                            ::normalizeFolderPath,
                        )
                        .filter(
                            String::isNotBlank,
                        )
                        .toSet()

                while (cursor.moveToNext()) {
                    val relativePath =
                        cursor.getString(
                            relativePathIndex,
                        )?.takeUnless {
                            it.isBlank()
                        }

                    if (
                        isExcludedFolder(
                            relativePath = relativePath,
                            excludedFolders =
                                currentExcludedFolders,
                        )
                    ) {
                        continue
                    }

                    val id =
                        cursor.getLong(
                            idIndex,
                        )

                    val title =
                        cursor.getString(
                            titleIndex,
                        )?.takeUnless {
                            it.isBlank()
                        }
                            ?: cursor.getString(
                                displayNameIndex,
                            )

                    val displayName =
                        cursor.getString(
                            displayNameIndex,
                        )?.takeUnless {
                            it.isBlank()
                        }

                    val artist =
                        cursor.getString(
                            artistIndex,
                        )?.takeUnless {
                            it.isBlank() ||
                                it == "<unknown>"
                        }

                    val album =
                        cursor.getString(
                            albumIndex,
                        )?.takeUnless {
                            it.isBlank() ||
                                it == "<unknown>"
                        }

                    val albumArtist =
                        cursor.getString(
                            albumArtistIndex,
                        )?.takeUnless {
                            it.isBlank() ||
                                it == "<unknown>"
                        }

                    val genre =
                        cursor.getString(
                            genreIndex,
                        )?.takeUnless {
                            it.isBlank() ||
                                it == "<unknown>"
                        }

                    val author =
                        cursor.getString(
                            authorIndex,
                        )?.takeUnless {
                            it.isBlank()
                        }

                    val composer =
                        cursor.getString(
                            composerIndex,
                        )?.takeUnless {
                            it.isBlank()
                        }

                    val writer =
                        cursor.getString(
                            writerIndex,
                        )?.takeUnless {
                            it.isBlank()
                        }

                    val year =
                        if (
                            cursor.isNull(
                                yearIndex,
                            )
                        ) {
                            null
                        } else {
                            cursor.getInt(
                                yearIndex,
                            ).takeIf {
                                it > 0
                            }
                        }

                    val encodedTrackNumber =
                        if (
                            cursor.isNull(
                                trackIndex,
                            )
                        ) {
                            null
                        } else {
                            cursor.getInt(
                                trackIndex,
                            ).takeIf {
                                it > 0
                            }
                        }

                    val trackNumber =
                        encodedTrackNumber?.let { encoded ->
                            if (encoded >= 1_000) {
                                encoded % 1_000
                            } else {
                                encoded
                            }
                        }

                    val discNumber =
                        cursor.getString(
                            discNumberIndex,
                        )?.takeUnless {
                            it.isBlank()
                        } ?: encodedTrackNumber?.let { encoded ->
                            if (encoded >= 1_000) {
                                (encoded / 1_000).toString()
                            } else {
                                null
                            }
                        }

                    val cdTrackNumber =
                        cursor.getString(
                            cdTrackNumberIndex,
                        )?.takeUnless {
                            it.isBlank()
                        }

                    val compilation =
                        cursor.getString(
                            compilationIndex,
                        )?.takeUnless {
                            it.isBlank()
                        }

                    val durationMs =
                        cursor.getLong(
                            durationIndex,
                        )

                    val dateAddedEpochSeconds =
                        cursor.getLong(
                            dateAddedIndex,
                        )

                    val dateModifiedEpochSeconds =
                        if (
                            cursor.isNull(
                                dateModifiedIndex,
                            )
                        ) {
                            null
                        } else {
                            cursor.getLong(
                                dateModifiedIndex,
                            ).takeIf {
                                it > 0L
                            }
                        }

                    val metadataDateEpochMillis =
                        if (
                            cursor.isNull(
                                metadataDateIndex,
                            )
                        ) {
                            null
                        } else {
                            cursor.getLong(
                                metadataDateIndex,
                            ).takeIf {
                                it > 0L
                            }
                        }

                    val inferredDateEpochMillis =
                        if (
                            cursor.isNull(
                                inferredDateIndex,
                            )
                        ) {
                            null
                        } else {
                            cursor.getLong(
                                inferredDateIndex,
                            ).takeIf {
                                it > 0L
                            }
                        }

                    val mimeType =
                        cursor.getString(
                            mimeTypeIndex,
                        )?.takeUnless {
                            it.isBlank()
                        }

                    val sampleRateHz =
                        if (
                            cursor.isNull(
                                sampleRateIndex,
                            )
                        ) {
                            null
                        } else {
                            cursor.getInt(
                                sampleRateIndex,
                            ).takeIf {
                                it > 0
                            }
                        }

                    val bitsPerSample =
                        if (
                            cursor.isNull(
                                bitsPerSampleIndex,
                            )
                        ) {
                            null
                        } else {
                            cursor.getInt(
                                bitsPerSampleIndex,
                            ).takeIf {
                                it > 0
                            }
                        }

                    val bitrateBps =
                        if (
                            cursor.isNull(
                                bitrateIndex,
                            )
                        ) {
                            null
                        } else {
                            cursor.getInt(
                                bitrateIndex,
                            ).takeIf {
                                it > 0
                            }
                        }

                    val fileSizeBytes =
                        if (
                            cursor.isNull(
                                fileSizeIndex,
                            )
                        ) {
                            null
                        } else {
                            cursor.getLong(
                                fileSizeIndex,
                            ).takeIf {
                                it > 0L
                            }
                        }

                    val volumeName =
                        cursor.getString(
                            volumeNameIndex,
                        )?.takeUnless {
                            it.isBlank()
                        }

                    val uri =
                        MediaStore.Audio.Media.getContentUri(
                            MediaStore.VOLUME_EXTERNAL,
                            id,
                        )

                    val embeddedMetadata =
                        readEmbeddedMetadata(
                            uri = uri,
                        )

                    tracks +=
                        Track(
                            id = id,
                            uri = uri.toString(),
                            title = title ?: "Unknown",
                            artist = artist,
                            album = album,
                            genre = genre,
                            displayName = displayName,
                            albumArtist = albumArtist,
                            composer = composer,
                            author = author,
                            writer = writer,
                            year = year,
                            releaseDate =
                                embeddedMetadata.releaseDate,
                            trackNumber = trackNumber,
                            discNumber = discNumber,
                            cdTrackNumber =
                                cdTrackNumber,
                            compilation =
                                compilation,
                            label = null,
                            copyright =
                                embeddedMetadata.copyright,
                            releaseType = null,
                            durationMs = durationMs,
                            dateAddedEpochSeconds =
                                dateAddedEpochSeconds,
                            dateModifiedEpochSeconds =
                                dateModifiedEpochSeconds,
                            metadataDateEpochMillis =
                                metadataDateEpochMillis,
                            inferredDateEpochMillis =
                                inferredDateEpochMillis,
                            mimeType = mimeType,
                            sampleRateHz =
                                sampleRateHz,
                            bitsPerSample =
                                bitsPerSample,
                            bitrateBps =
                                bitrateBps,
                            fileSizeBytes =
                                fileSizeBytes,
                            relativePath =
                                relativePath,
                            volumeName =
                                volumeName,
                            artworkUri =
                                uri.toString(),
                        )

                    /*
                     * Limit diterapkan setelah filtering.
                     *
                     * Lagu dari folder excluded tidak memakan
                     * slot Recently Added.
                     */
                    if (
                        limit != null &&
                        tracks.size >= limit
                    ) {
                        break
                    }
                }
            }

            tracks
        }

    private fun readEmbeddedMetadata(
        uri: android.net.Uri,
    ): EmbeddedMetadata {
        return try {
            MediaMetadataRetriever().use { retriever ->
                retriever.setDataSource(
                    applicationContext,
                    uri,
                )

                EmbeddedMetadata(
                    releaseDate =
                        retriever
                            .extractMetadata(
                                MediaMetadataRetriever
                                    .METADATA_KEY_DATE,
                            )
                            ?.takeUnless {
                                it.isBlank()
                            },
                    copyright =
                        extractCopyrightMetadata(
                            retriever,
                        ),
                )
            }
        } catch (
            _: Exception,
        ) {
            EmbeddedMetadata()
        }
    }

    private fun extractCopyrightMetadata(
        retriever: MediaMetadataRetriever,
    ): String? {
        /*
         * Android's platform metadata contract contains the
         * copyright key in the native MediaMetadataRetriever
         * implementation. The public SDK surface does not expose
         * a named constant consistently across API levels, so the
         * stable platform key value is kept locally.
         *
         * 15 = METADATA_KEY_COPYRIGHT
         */
        return retriever
            .extractMetadata(
                COPYRIGHT_METADATA_KEY,
            )
            ?.takeUnless {
                it.isBlank()
            }
    }

    private fun isExcludedFolder(
        relativePath: String?,
        excludedFolders: Set<String>,
    ): Boolean {
        if (
            relativePath.isNullOrBlank() ||
            excludedFolders.isEmpty()
        ) {
            return false
        }

        val normalizedPath =
            normalizeFolderPath(
                relativePath,
            )

        return excludedFolders.any { excludedFolder ->
            normalizedPath.startsWith(
                excludedFolder,
            )
        }
    }

    private fun normalizeFolderPath(
        path: String,
    ): String {
        val normalized =
            path.trim()

        if (
            normalized.isEmpty() ||
            normalized.endsWith("/")
        ) {
            return normalized
        }

        return "$normalized/"
    }

    private data class EmbeddedMetadata(
        val releaseDate: String? = null,
        val copyright: String? = null,
    )

    private companion object {
        const val COPYRIGHT_METADATA_KEY = 15
    }
}