
package com.rovia.music.core.ui.component

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import android.util.Size
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream

/**
 * Loads artwork from Android-supported URIs.
 *
 * Supports temporary file:// artwork URIs as well as content://
 * and other URIs readable by ContentResolver.
 *
 * Decoding runs on Dispatchers.IO. Decoded bitmaps are kept in a
 * bounded in-memory LRU cache to avoid repeatedly reading and
 * decoding the same artwork while scrolling.
 *
 * This loader does not copy artwork or modify playback/database state.
 */
object ArtworkBitmapLoader {

    private const val DEFAULT_MAX_DIMENSION = 1024
    private const val FILE_SCHEME = "file"
    private const val KILOBYTE = 1024L
    private const val MIN_CACHE_SIZE_KB = 1024
    private const val MAX_CACHE_SIZE_KB = 16 * 1024

    private val cacheSizeKb: Int =
        (
            Runtime.getRuntime().maxMemory() /
                KILOBYTE /
                8L
            )
            .coerceIn(
                MIN_CACHE_SIZE_KB.toLong(),
                MAX_CACHE_SIZE_KB.toLong(),
            )
            .toInt()

    private val bitmapCache =
        object : LruCache<String, Bitmap>(cacheSizeKb) {
            override fun sizeOf(
                key: String,
                value: Bitmap,
            ): Int {
                return (value.byteCount / 1024).coerceAtLeast(1)
            }
        }

    /**
     * Loads an image from the supplied artwork URI.
     *
     * Returns a cached bitmap when available. Otherwise, the image
     * dimensions are inspected before decoding a scaled bitmap.
     */
    suspend fun load(
        context: Context,
        artworkUri: String?,
        maxDimension: Int = DEFAULT_MAX_DIMENSION,
    ): Bitmap? = withContext(Dispatchers.IO) {
        if (artworkUri.isNullOrBlank()) {
            return@withContext null
        }

        val uri =
            runCatching {
                Uri.parse(artworkUri)
            }.getOrNull()
                ?: return@withContext null

        val safeMaxDimension = maxDimension.coerceAtLeast(1)
        val key = cacheKey(uri, safeMaxDimension)

        bitmapCache.get(key)?.let {
            return@withContext it
        }

        val bitmap = decodeBitmap(
            context = context.applicationContext,
            uri = uri,
            maxDimension = safeMaxDimension,
        )

        if (bitmap != null && !bitmap.isRecycled) {
            bitmapCache.put(key, bitmap)
        }

        bitmap
    }

    /**
     * Loads artwork through Android's thumbnail API first, falling
     * back to scaled BitmapFactory decoding when needed.
     *
     * Both successful paths share the same bounded bitmap cache.
     * Calling this method from AlbumArtwork avoids repeatedly
     * requesting thumbnails for recently displayed artwork.
     */
    suspend fun loadArtwork(
        context: Context,
        artworkUri: String?,
        maxDimension: Int,
    ): Bitmap? = withContext(Dispatchers.IO) {
        if (artworkUri.isNullOrBlank()) {
            return@withContext null
        }

        val uri =
            runCatching {
                Uri.parse(artworkUri)
            }.getOrNull()
                ?: return@withContext null

        val applicationContext = context.applicationContext
        val safeMaxDimension = maxDimension.coerceAtLeast(1)
        val key = cacheKey(uri, safeMaxDimension)

        bitmapCache.get(key)?.let {
            return@withContext it
        }

        val thumbnail =
            runCatching {
                applicationContext.contentResolver.loadThumbnail(
                    uri,
                    Size(
                        safeMaxDimension,
                        safeMaxDimension,
                    ),
                    null,
                )
            }.getOrNull()

        val bitmap =
            thumbnail ?: decodeBitmap(
                context = applicationContext,
                uri = uri,
                maxDimension = safeMaxDimension,
            )

        if (bitmap != null && !bitmap.isRecycled) {
            bitmapCache.put(key, bitmap)
        }

        bitmap
    }

    private fun cacheKey(
        uri: Uri,
        maxDimension: Int,
    ): String {
        return "${uri.normalizeScheme()}|$maxDimension"
    }

    /**
     * Reads image dimensions first, then decodes a scaled bitmap.
     * The caller is responsible for invoking this on an I/O dispatcher.
     */
    private fun decodeBitmap(
        context: Context,
        uri: Uri,
        maxDimension: Int,
    ): Bitmap? {
        val boundsOptions =
            BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }

        val boundsReadSucceeded =
            runCatching {
                openInputStream(
                    context = context,
                    uri = uri,
                )?.use { stream ->
                    BitmapFactory.decodeStream(
                        stream,
                        null,
                        boundsOptions,
                    )
                }
            }.isSuccess

        if (
            !boundsReadSucceeded ||
            boundsOptions.outWidth <= 0 ||
            boundsOptions.outHeight <= 0
        ) {
            return null
        }

        val decodeOptions =
            BitmapFactory.Options().apply {
                inSampleSize =
                    calculateInSampleSize(
                        width = boundsOptions.outWidth,
                        height = boundsOptions.outHeight,
                        maxDimension = maxDimension,
                    )

                inPreferredConfig = Bitmap.Config.ARGB_8888
                inJustDecodeBounds = false
            }

        return runCatching {
            openInputStream(
                context = context,
                uri = uri,
            )?.use { stream ->
                BitmapFactory.decodeStream(
                    stream,
                    null,
                    decodeOptions,
                )
            }
        }.getOrNull()
    }

    private fun openInputStream(
        context: Context,
        uri: Uri,
    ): InputStream? {
        return if (uri.scheme == FILE_SCHEME) {
            val path = uri.path ?: return null
            val file = File(path)

            if (file.isFile && file.canRead()) {
                file.inputStream()
            } else {
                null
            }
        } else {
            context.contentResolver.openInputStream(uri)
        }
    }

    private fun calculateInSampleSize(
        width: Int,
        height: Int,
        maxDimension: Int,
    ): Int {
        var sampleSize = 1

        while (
            width / sampleSize > maxDimension ||
            height / sampleSize > maxDimension
        ) {
            sampleSize *= 2
        }

        return sampleSize
    }
}
