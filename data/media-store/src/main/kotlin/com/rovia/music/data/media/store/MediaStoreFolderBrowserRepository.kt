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

            val projection =
                arrayOf(
                    MediaStore.Audio.Media._ID,
                    MediaStore.Audio.Media.TITLE,
                    MediaStore.Audio.Media.ARTIST,
                    MediaStore.Audio.Media.ALBUM,
                    MediaStore.Audio.Media.DURATION,
                    MediaStore.Audio.Media.DATE_ADDED,
                    MediaStore.Audio.Media.DISPLAY_NAME,
                    MediaStore.Audio.Media.MIME_TYPE,
                    MediaStore.Audio.Media.SAMPLERATE,
                    MediaStore.Audio.Media.BITRATE,
                    MediaStore.Audio.Media.RELATIVE_PATH,
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

                val durationIndex =
                    cursor.getColumnIndexOrThrow(
                        MediaStore.Audio.Media.DURATION,
                    )

                val dateAddedIndex =
                    cursor.getColumnIndexOrThrow(
                        MediaStore.Audio.Media.DATE_ADDED,
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

                val bitrateIndex =
                    cursor.getColumnIndexOrThrow(
                        MediaStore.Audio.Media.BITRATE,
                    )

                val relativePathIndex =
                    cursor.getColumnIndexOrThrow(
                        MediaStore.Audio.Media.RELATIVE_PATH,
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
                        cursor.getLong(idIndex)

                    val title =
                        cursor
                            .getString(titleIndex)
                            ?.takeIf { it.isNotBlank() }
                            ?: cursor
                                .getString(displayNameIndex)
                                ?.takeIf { it.isNotBlank() }
                            ?: "Unknown"

                    val artist =
                        cursor
                            .getString(artistIndex)
                            ?.takeUnless {
                                it.isBlank() ||
                                    it == "<unknown>"
                            }

                    val album =
                        cursor
                            .getString(albumIndex)
                            ?.takeUnless {
                                it.isBlank() ||
                                    it == "<unknown>"
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
                            durationMs =
                                cursor.getLong(
                                    durationIndex,
                                ),
                            dateAddedEpochSeconds =
                                cursor.getLong(
                                    dateAddedIndex,
                                ),
                            mimeType =
                                cursor.getString(
                                    mimeTypeIndex,
                                ),
                            sampleRateHz =
                                if (
                                    cursor.isNull(
                                        sampleRateIndex,
                                    )
                                ) {
                                    null
                                } else {
                                    cursor.getInt(
                                        sampleRateIndex,
                                    )
                                },
                            bitrateBps =
                                if (
                                    cursor.isNull(
                                        bitrateIndex,
                                    )
                                ) {
                                    null
                                } else {
                                    cursor.getInt(
                                        bitrateIndex,
                                    )
                                },
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