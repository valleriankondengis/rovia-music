package com.rovia.music.data.media.store

import android.content.ContentResolver
import android.content.Context
import android.provider.MediaStore
import com.rovia.music.core.library.FolderScannerRepository
import com.rovia.music.core.model.MusicFolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MediaStoreFolderScannerRepository(
    context: Context,
) : FolderScannerRepository {

    private val contentResolver: ContentResolver =
        context.applicationContext.contentResolver

    override suspend fun getScannedFolders(): List<MusicFolder> =
        withContext(Dispatchers.IO) {
            val collection =
                MediaStore.Audio.Media.getContentUri(
                    MediaStore.VOLUME_EXTERNAL,
                )

            val projection =
                arrayOf(
                    MediaStore.Audio.Media.RELATIVE_PATH,
                )

            val folders =
                mutableSetOf<String>()

            contentResolver.query(
                collection,
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
                        )?.trim()

                    if (!relativePath.isNullOrEmpty()) {
                        folders +=
                            normalizeFolderPath(
                                relativePath,
                            )
                    }
                }
            }

            folders
                .sortedWith(
                    compareBy(
                        String.CASE_INSENSITIVE_ORDER,
                    ) { it },
                )
                .map { relativePath ->
                    MusicFolder(
                        relativePath = relativePath,
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