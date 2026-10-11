
package com.rovia.music.core.ui.component

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream

/**
 * Loads artwork from Android-supported URIs.
 *
 * Supports artwork stored in temporary file:// URIs as well as
 * content:// and other URIs supported by ContentResolver.
 *
 * Decoding takes place on Dispatchers.IO. Image dimensions are
 * inspected before the actual decode to reduce memory usage.
 *
 * This loader does not create a cache, copy artwork, or modify
 * any playback or database state.
 */
object ArtworkBitmapLoader {

    private const val DEFAULT_MAX_DIMENSION = 1024
    private const val FILE_SCHEME = "file"

    /**
     * Loads an image from the supplied artwork URI.
     *
     * Returns null when the URI is absent, inaccessible, or does
     * not contain a decodable image.
     *
     * maxDimension is the maximum intended width or height in
     * decoded pixels. The image aspect ratio is preserved.
     */
    suspend fun load(
        context: Context,
        artworkUri: String?,
        maxDimension: Int = DEFAULT_MAX_DIMENSION,
    ): Bitmap? =
        withContext(Dispatchers.IO) {
            if (artworkUri.isNullOrBlank()) {
                return@withContext null
            }

            val uri =
                runCatching {
                    Uri.parse(artworkUri)
                }.getOrNull()
                    ?: return@withContext null

            val applicationContext =
                context.applicationContext

            val safeMaxDimension =
                maxDimension.coerceAtLeast(1)

            /*
             * Read dimensions first so a large embedded cover
             * does not have to be decoded at full resolution.
             */
            val boundsOptions =
                BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }

            val boundsReadSucceeded =
                runCatching {
                    openInputStream(
                        context = applicationContext,
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
                return@withContext null
            }

            val decodeOptions =
                BitmapFactory.Options().apply {
                    inSampleSize =
                        calculateInSampleSize(
                            width = boundsOptions.outWidth,
                            height = boundsOptions.outHeight,
                            maxDimension = safeMaxDimension,
                        )

                    inPreferredConfig =
                        Bitmap.Config.ARGB_8888

                    inJustDecodeBounds = false
                }

            /*
             * Reopen the source for the actual bitmap decode.
             * Providers that can supply only one-shot streams
             * may fail here; such failure is treated as absent
             * artwork instead of crashing playback.
             */
            runCatching {
                openInputStream(
                    context = applicationContext,
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
            val path =
                uri.path
                    ?: return null

            val file =
                File(path)

            if (file.isFile && file.canRead()) {
                file.inputStream()
            } else {
                null
            }
        } else {
            context.contentResolver
                .openInputStream(uri)
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
