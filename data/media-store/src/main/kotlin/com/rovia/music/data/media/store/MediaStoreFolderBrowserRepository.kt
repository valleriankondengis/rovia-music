package com.rovia.music.data.media.store

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import com.rovia.music.core.library.FolderBrowserRepository
import com.rovia.music.core.library.FolderFilterRepository
import com.rovia.music.core.model.MusicFolder
import com.rovia.music.core.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MediaStoreFolderBrowserRepository(
    context: Context,
    private val folderFilterRepository: FolderFilterRepository,
) : FolderBrowserRepository {

    private val contentResolver =
        context.applicationContext.contentResolver

    override suspend fun getRootFolders(): List<MusicFolder> =
        withContext(Dispatchers.IO) {
            folderFilterRepository.awaitInitialized()

            val excludedFolders =
                folderFilterRepository.excludedFolders.value

            queryRelativePaths()
                .mapNotNull { relativePath ->
                    val normalizedPath =
                        normalizePath(relativePath)

                    if (
                        isExcluded(
                            relativePath = normalizedPath,
                            excludedFolders = excludedFolders,
                        )
                    ) {
                        return@mapNotNull null
                    }

                    normalizedPath
                        .trimEnd('/')
                        .substringBefore('/')
                        .takeIf { it.isNotBlank() }
                        ?.let { rootName ->
                            MusicFolder(
                                relativePath = "$rootName/",
                            )
                        }
                }
                .distinctBy { it.relativePath }
                .sortedWith(
                    compareBy(
                        String.CASE_INSENSITIVE_ORDER,
                    ) { it.relativePath },
                )
        }

    override suspend fun getSubFolders(
        parentRelativePath: String,
    ): List<MusicFolder> =
        withContext(Dispatchers.IO) {
            folderFilterRepository.awaitInitialized()

            val normalizedParent =
                normalizePath(parentRelativePath)

            val excludedFolders =
                folderFilterRepository.excludedFolders.value

            if (
                isExcluded(
                    relativePath = normalizedParent,
                    excludedFolders = excludedFolders,
                )
            ) {
                return@withContext emptyList()
            }

            queryRelativePaths()
                .mapNotNull { relativePath ->
                    val normalizedPath =
                        normalizePath(relativePath)

                    if (
                        !normalizedPath.startsWith(
                            normalizedParent,
                        )
                    ) {
                        return@mapNotNull null
                    }

                    if (
                        normalizedPath ==
                        normalizedParent
                    ) {
                        return@mapNotNull null
                    }

                    val remainder =
                        normalizedPath.removePrefix(
                            normalizedParent,
                        )

                    val childName =
                        remainder
                            .substringBefore('/')
                            .takeIf { it.isNotBlank() }
                            ?: return@mapNotNull null

                    val childPath =
                        normalizedParent +
                            childName +
                            "/"

                    if (
                        isExcluded(
                            relativePath = childPath,
                            excludedFolders = excludedFolders,
                        )
                    ) {
                        return@mapNotNull null
                    }

                    MusicFolder(
                        relativePath = childPath,
                    )
                }
                .distinctBy { it.relativePath }
                .sortedWith(
                    compareBy(
                        String.CASE_INSENSITIVE_ORDER,
                    ) { it.relativePath },
                )
        }

    override suspend fun getSongs(
        relativePath: String,
    ): List<Track> =
        withContext(Dispatchers.IO) {
            folderFilterRepository.awaitInitialized()

            val normalizedPath =
                normalizePath(relativePath)

            val excludedFolders =
                folderFilterRepository.excludedFolders.value

            if (
                isExcluded(
                    relativePath = normalizedPath,
                    excludedFolders = excludedFolders,
                )
            ) {
                return@withContext emptyList()
            }

            /*
             * Keep the folder query aligned with
             * MediaStoreMusicRepository.getAllTracks().
             *
             * This ensures Track objects created from folders
             * contain the same metadata available in All Songs.
             */
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

            val tracks =
                mutableListOf<Track>()

            contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                "${MediaStore.Audio.Media.RELATIVE_PATH} = ?",
                arrayOf(normalizedPath),
                "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC",
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

                while (cursor.moveToNext()) {
                    val songRelativePath =
                        normalizePath(
                            cursor.getString(
                                relativePathIndex,
                            ),
                        )

                    if (
                        isExcluded(
                            relativePath = songRelativePath,
                            excludedFolders = excludedFolders,
                        )
                    ) {
                        continue
                    }

                    val id =
                        cursor.getLong(
                            idIndex,
                        )

                    val title =
                        cursor
                            .getString(
                                titleIndex,
                            )
                            ?.takeIf {
                                it.isNotBlank()
                            }
                            ?: cursor
                                .getString(
                                    displayNameIndex,
                                )
                                ?.takeIf {
                                    it.isNotBlank()
                                }
                            ?: "Unknown"

                    val displayName =
                        cursor
                            .getString(
                                displayNameIndex,
                            )
                            ?.takeUnless {
                                it.isBlank()
                            }

                    val artist =
                        cursor
                            .getString(
                                artistIndex,
                            )
                            ?.takeUnless {
                                it.isBlank() ||
                                    it == "<unknown>"
                            }

                    val album =
                        cursor
                            .getString(
                                albumIndex,
                            )
                            ?.takeUnless {
                                it.isBlank() ||
                                    it == "<unknown>"
                            }

                    val albumArtist =
                        cursor
                            .getString(
                                albumArtistIndex,
                            )
                            ?.takeUnless {
                                it.isBlank() ||
                                    it == "<unknown>"
                            }

                    val genre =
                        cursor
                            .getString(
                                genreIndex,
                            )
                            ?.takeUnless {
                                it.isBlank() ||
                                    it == "<unknown>"
                            }

                    val author =
                        cursor
                            .getString(
                                authorIndex,
                            )
                            ?.takeUnless {
                                it.isBlank()
                            }

                    val composer =
                        cursor
                            .getString(
                                composerIndex,
                            )
                            ?.takeUnless {
                                it.isBlank()
                            }

                    val writer =
                        cursor
                            .getString(
                                writerIndex,
                            )
                            ?.takeUnless {
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
                            cursor
                                .getInt(
                                    yearIndex,
                                )
                                .takeIf {
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
                            cursor
                                .getInt(
                                    trackIndex,
                                )
                                .takeIf {
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
                        cursor
                            .getString(
                                discNumberIndex,
                            )
                            ?.takeUnless {
                                it.isBlank()
                            }
                            ?: encodedTrackNumber?.let { encoded ->
                                if (encoded >= 1_000) {
                                    (
                                        encoded / 1_000
                                    ).toString()
                                } else {
                                    null
                                }
                            }

                    val cdTrackNumber =
                        cursor
                            .getString(
                                cdTrackNumberIndex,
                            )
                            ?.takeUnless {
                                it.isBlank()
                            }

                    val compilation =
                        cursor
                            .getString(
                                compilationIndex,
                            )
                            ?.takeUnless {
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
                            cursor
                                .getLong(
                                    dateModifiedIndex,
                                )
                                .takeIf {
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
                            cursor
                                .getLong(
                                    metadataDateIndex,
                                )
                                .takeIf {
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
                            cursor
                                .getLong(
                                    inferredDateIndex,
                                )
                                .takeIf {
                                    it > 0L
                                }
                    }

                    val mimeType =
                        cursor
                            .getString(
                                mimeTypeIndex,
                            )
                            ?.takeUnless {
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
                            cursor
                                .getInt(
                                    sampleRateIndex,
                                )
                                .takeIf {
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
                            cursor
                                .getInt(
                                    bitsPerSampleIndex,
                                )
                                .takeIf {
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
                            cursor
                                .getInt(
                                    bitrateIndex,
                                )
                                .takeIf {
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
                            cursor
                                .getLong(
                                    fileSizeIndex,
                                )
                                .takeIf {
                                    it > 0L
                                }
                        }

                    val volumeName =
                        cursor
                            .getString(
                                volumeNameIndex,
                            )
                            ?.takeUnless {
                                it.isBlank()
                            }

                    val contentUri =
                        ContentUris.withAppendedId(
                            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                            id,
                        )

                    tracks +=
                        Track(
                            id = id,
                            uri = contentUri.toString(),
                            title = title,
                            artist = artist,
                            album = album,
                            genre = genre,
                            displayName = displayName,
                            albumArtist = albumArtist,
                            composer = composer,
                            author = author,
                            writer = writer,
                            year = year,
                            trackNumber = trackNumber,
                            discNumber = discNumber,
                            cdTrackNumber = cdTrackNumber,
                            compilation = compilation,
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
                            sampleRateHz = sampleRateHz,
                            bitsPerSample = bitsPerSample,
                            bitrateBps = bitrateBps,
                            fileSizeBytes = fileSizeBytes,
                            relativePath =
                                songRelativePath,
                            volumeName = volumeName,
                            artworkUri =
                                contentUri.toString(),
                        )
                }
            }

            tracks
        }

    private fun queryRelativePaths(): List<String> {
        val projection =
            arrayOf(
                MediaStore.Audio.Media.RELATIVE_PATH,
            )

        val paths =
            mutableListOf<String>()

        contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            null,
        )?.use { cursor ->

            val relativePathIndex =
                cursor.getColumnIndexOrThrow(
                    MediaStore.Audio.Media.RELATIVE_PATH,
                )

            while (cursor.moveToNext()) {
                val relativePath =
                    cursor.getString(
                        relativePathIndex,
                    )

                if (!relativePath.isNullOrBlank()) {
                    paths += relativePath
                }
            }
        }

        return paths
    }

    private fun normalizePath(
        path: String?,
    ): String {
        if (path.isNullOrBlank()) {
            return ""
        }

        return path.trimEnd('/') + "/"
    }

    private fun isExcluded(
        relativePath: String,
        excludedFolders: Set<String>,
    ): Boolean {
        val normalizedPath =
            normalizePath(relativePath)

        return excludedFolders.any { excludedFolder ->
            val normalizedExcludedFolder =
                normalizePath(excludedFolder)

            normalizedPath ==
                normalizedExcludedFolder ||
                normalizedPath.startsWith(
                    normalizedExcludedFolder,
                )
        }
    }
}