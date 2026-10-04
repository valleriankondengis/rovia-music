package com.rovia.music.data.media.store

import android.content.ContentResolver
import android.content.Context
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

    private val contentResolver: ContentResolver =
        context.applicationContext.contentResolver

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
                    MediaStore.Audio.Media.DURATION,
                    MediaStore.Audio.Media.DATE_ADDED,
                    MediaStore.Audio.Media.DISPLAY_NAME,
                    MediaStore.Audio.Media.MIME_TYPE,
                    MediaStore.Audio.Media.SAMPLERATE,
                    MediaStore.Audio.Media.BITRATE,
                    MediaStore.Audio.Media.RELATIVE_PATH,
                )

            val queryArgs =
                android.os.Bundle().apply {
                    putString(
                        ContentResolver
                            .QUERY_ARG_SQL_SORT_ORDER,
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
                        )

                    if (
                        isExcludedFolder(
                            relativePath =
                                relativePath,
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

                    val durationMs =
                        cursor.getLong(
                            durationIndex,
                        )

                    val dateAddedEpochSeconds =
                        cursor.getLong(
                            dateAddedIndex,
                        )

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

                    val uri =
                        MediaStore.Audio.Media.getContentUri(
                            MediaStore.VOLUME_EXTERNAL,
                            id,
                        )

                    tracks +=
                        Track(
                            id = id,
                            uri = uri.toString(),
                            title =
                                title ?: "Unknown",
                            artist = artist,
                            album = album,
                            durationMs = durationMs,
                            dateAddedEpochSeconds =
                                dateAddedEpochSeconds,
                            mimeType = mimeType,
                            sampleRateHz =
                                sampleRateHz,
                            bitrateBps =
                                bitrateBps,
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
}