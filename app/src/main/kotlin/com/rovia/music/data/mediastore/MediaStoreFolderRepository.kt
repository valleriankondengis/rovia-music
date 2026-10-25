package com.rovia.music.data.mediastore

import android.content.ContentResolver
import android.provider.MediaStore
import com.rovia.music.data.repository.normalizeFolderPath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MediaStoreFolderRepository(
    private val contentResolver: ContentResolver,
) {
    suspend fun loadFolders(): List<MusicFolder> = withContext(Dispatchers.IO) {
        val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        val projection = arrayOf(MediaStore.MediaColumns.RELATIVE_PATH)
        val selection =
            "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND " +
                "${MediaStore.Audio.Media.DURATION} > 0"

        // Do not sort every audio row just to produce a unique folder list.
        val cursor = contentResolver.query(
            collection,
            projection,
            selection,
            null,
            null,
        ) ?: return@withContext emptyList()

        val paths = linkedSetOf<String>()
        cursor.use { result ->
            val relativePathColumn = result.getColumnIndexOrThrow(
                MediaStore.MediaColumns.RELATIVE_PATH,
            )

            while (result.moveToNext()) {
                val relativePath = result.getString(relativePathColumn)
                    ?.let(::normalizeFolderPath)
                    ?.takeIf(String::isNotBlank)
                    ?: continue

                val segments = relativePath.split('/')
                for (index in segments.indices) {
                    paths.add(segments.take(index + 1).joinToString("/"))
                }
            }
        }

        paths.sortedWith(String.CASE_INSENSITIVE_ORDER).map(::MusicFolder)
    }
}