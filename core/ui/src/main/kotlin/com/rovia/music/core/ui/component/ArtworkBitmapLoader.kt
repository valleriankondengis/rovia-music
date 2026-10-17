
package com.rovia.music.core.ui.component

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.MediaStore
import android.util.LruCache
import android.util.Size
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream

/**
 * Loads artwork from Android-supported URIs.
 *
 * Features:
 * - Bounded in-memory bitmap cache.
 * - Persistent disk cache for sources with a reliable version.
 * - Shared in-flight requests for identical URI/size combinations.
 * - Bounded number of concurrent artwork loads.
 * - Cancellable requests when no caller is waiting.
 * - Android thumbnail API with scaled BitmapFactory fallback.
 *
 * The caller's coroutine lifecycle does not own the shared loading
 * operation. A shared request is cancelled only after its last waiter
 * has left.
 *
 * This loader does not modify the original audio files or playback state.
 */
object ArtworkBitmapLoader {

    private const val DEFAULT_MAX_DIMENSION = 1024
    private const val FILE_SCHEME = "file"
    private const val CONTENT_SCHEME = "content"
    private const val MEDIA_AUTHORITY = "media"
    private const val KILOBYTE = 1024L

    private const val MIN_CACHE_SIZE_KB = 8 * 1024
    private const val MAX_CACHE_SIZE_KB = 40 * 1024

    private const val MAX_CONCURRENT_ARTWORK_LOADS = 3

    private val cacheSizeKb: Int =
        (
            Runtime.getRuntime().maxMemory() /
                KILOBYTE /
                8L
            ).coerceIn(
                MIN_CACHE_SIZE_KB.toLong(),
                MAX_CACHE_SIZE_KB.toLong(),
            ).toInt()

    private val bitmapCache =
        object : LruCache<String, Bitmap>(cacheSizeKb) {
            override fun sizeOf(
                key: String,
                value: Bitmap,
            ): Int {
                return (value.byteCount / KILOBYTE)
                    .toInt()
                    .coerceAtLeast(1)
            }
        }

    private data class InFlightRequest(
        val deferred: Deferred<Bitmap?>,
        var waiterCount: Int,
    )

    private sealed interface RequestLookup {

        data class Cached(
            val bitmap: Bitmap,
        ) : RequestLookup

        data class InFlight(
            val request: InFlightRequest,
        ) : RequestLookup
    }

    private val requestMutex = Mutex()

    /**
     * All accesses to this map and waiterCount are protected
     * by requestMutex.
     */
    private val inFlightRequests =
        mutableMapOf<String, InFlightRequest>()

    /**
     * Independent of individual UI lifecycles. Requests are cancelled
     * when their last waiter leaves.
     */
    private val loaderScope =
        CoroutineScope(
            SupervisorJob() + Dispatchers.IO,
        )

    /**
     * Limits concurrent thumbnail reads and bitmap decoding.
     */
    private val artworkLoadSemaphore =
        Semaphore(
            permits = MAX_CONCURRENT_ARTWORK_LOADS,
        )

    /**
     * Loads and scales an image directly from its source if necessary.
     */
    suspend fun load(
        context: Context,
        artworkUri: String?,
        maxDimension: Int = DEFAULT_MAX_DIMENSION,
    ): Bitmap? {
        return loadShared(
            context = context,
            artworkUri = artworkUri,
            maxDimension = maxDimension,
            preferThumbnailApi = false,
        )
    }

    /**
     * Loads artwork through Android's thumbnail API first, falling
     * back to scaled BitmapFactory decoding when required.
     */
    suspend fun loadArtwork(
        context: Context,
        artworkUri: String?,
        maxDimension: Int,
    ): Bitmap? {
        return loadShared(
            context = context,
            artworkUri = artworkUri,
            maxDimension = maxDimension,
            preferThumbnailApi = true,
        )
    }

    private suspend fun loadShared(
        context: Context,
        artworkUri: String?,
        maxDimension: Int,
        preferThumbnailApi: Boolean,
    ): Bitmap? {
        if (artworkUri.isNullOrBlank()) {
            return null
        }

        val uri =
            runCatching {
                Uri.parse(artworkUri)
            }.getOrNull() ?: return null

        val applicationContext = context.applicationContext
        val safeMaxDimension = maxDimension.coerceAtLeast(1)

        /*
         * The memory/in-flight key is intentionally independent of
         * the disk source version. This lets all visible instances of
         * the same URI and requested size share the same bitmap load.
         */
        val key = memoryCacheKey(
            uri = uri,
            maxDimension = safeMaxDimension,
        )

        getCachedBitmap(key)?.let { cached ->
            return cached
        }

        val lookup =
            requestMutex.withLock {
                // Recheck after acquiring the lock.
                getCachedBitmap(key)?.let { cached ->
                    return@withLock RequestLookup.Cached(cached)
                }

                val existingRequest = inFlightRequests[key]

                if (existingRequest != null) {
                    existingRequest.waiterCount++

                    RequestLookup.InFlight(existingRequest)
                } else {
                    val deferred =
                        loaderScope.async(
                            start = CoroutineStart.LAZY,
                        ) {
                            artworkLoadSemaphore.withPermit {
                                val bitmap =
                                    loadFromDiskOrSource(
                                        context = applicationContext,
                                        uri = uri,
                                        maxDimension = safeMaxDimension,
                                        preferThumbnailApi = preferThumbnailApi,
                                    )

                                if (
                                    bitmap != null &&
                                    !bitmap.isRecycled
                                ) {
                                    bitmapCache.put(key, bitmap)
                                }

                                bitmap
                            }
                        }

                    val request =
                        InFlightRequest(
                            deferred = deferred,
                            waiterCount = 1,
                        )

                    inFlightRequests[key] = request

                    // Register the request before starting its work.
                    deferred.start()

                    RequestLookup.InFlight(request)
                }
            }

        return when (lookup) {
            is RequestLookup.Cached -> {
                lookup.bitmap
            }

            is RequestLookup.InFlight -> {
                val request = lookup.request

                try {
                    request.deferred
                        .await()
                        ?.takeUnless(Bitmap::isRecycled)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } finally {
                    /*
                     * Cancellation must not prevent waiter cleanup.
                     * Otherwise an abandoned request could remain in
                     * the in-flight map and interfere with later loads.
                     */
                    withContext(NonCancellable) {
                        requestMutex.withLock {
                            if (request.waiterCount > 0) {
                                request.waiterCount--
                            }

                            if (
                                request.waiterCount == 0 &&
                                inFlightRequests[key] === request
                            ) {
                                inFlightRequests.remove(key)

                                if (request.deferred.isActive) {
                                    request.deferred.cancel()
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun getCachedBitmap(
        key: String,
    ): Bitmap? {
        val bitmap = bitmapCache.get(key) ?: return null

        if (bitmap.isRecycled) {
            bitmapCache.remove(key)
            return null
        }

        return bitmap
    }

    private fun memoryCacheKey(
        uri: Uri,
        maxDimension: Int,
    ): String {
        return "${uri.normalizeScheme()}|$maxDimension"
    }

    /**
     * Reads the disk cache first when the source can be versioned
     * reliably. Otherwise, loads the source without persistent caching.
     *
     * This method runs on Dispatchers.IO, under the concurrency limit.
     */
    private fun loadFromDiskOrSource(
        context: Context,
        uri: Uri,
        maxDimension: Int,
        preferThumbnailApi: Boolean,
    ): Bitmap? {
        val diskKey =
            createPersistentCacheKey(
                context = context,
                uri = uri,
                maxDimension = maxDimension,
            )

        if (diskKey != null) {
            when (
                val cached =
                    ArtworkDiskCache.read(
                        context = context,
                        key = diskKey,
                    )
            ) {
                is ArtworkDiskCacheResult.Hit -> {
                    return cached.bitmap
                }

                ArtworkDiskCacheResult.NoArtwork -> {
                    return null
                }

                ArtworkDiskCacheResult.Miss -> {
                    // Read the source below.
                }
            }
        }

        val bitmap =
            loadBitmapFromSource(
                context = context,
                uri = uri,
                maxDimension = maxDimension,
                preferThumbnailApi = preferThumbnailApi,
            )

        if (
            bitmap != null &&
            !bitmap.isRecycled &&
            diskKey != null
        ) {
            ArtworkDiskCache.writeBitmap(
                context = context,
                key = diskKey,
                bitmap = bitmap,
            )
        }

        /*
         * Do not write a negative cache entry here. A null result may
         * represent a transient provider or permission failure rather
         * than a reliable finding that the track has no artwork.
         */
        return bitmap
    }

    /**
     * Creates a persistent cache key only when the source identity can
     * be paired with useful version metadata.
     *
     * For MediaStore audio, the version includes file size,
     * modification time, and generation when available.
     *
     * For file:// sources, the key includes file path, size, and
     * modification time. Temporary external artwork files therefore
     * use their own identity instead of sharing the original audio key.
     *
     * Unknown content providers intentionally bypass the disk cache.
     */
    private fun createPersistentCacheKey(
        context: Context,
        uri: Uri,
        maxDimension: Int,
    ): String? {
        val normalizedUri = uri.normalizeScheme()

        val sourceVersion =
            when (uri.scheme) {
                FILE_SCHEME -> {
                    fileSourceVersion(uri)
                }

                CONTENT_SCHEME -> {
                    if (
                        uri.authority.equals(
                            MEDIA_AUTHORITY,
                            ignoreCase = true,
                        )
                    ) {
                        mediaStoreSourceVersion(
                            context = context,
                            uri = uri,
                        )
                    } else {
                        null
                    }
                }

                else -> null
            } ?: return null

        return buildString {
            append(normalizedUri)
            append("|version=")
            append(sourceVersion)
            append("|dimension=")
            append(maxDimension)
        }
    }

    private fun fileSourceVersion(
        uri: Uri,
    ): String? {
        val path = uri.path ?: return null
        val file = File(path)

        if (!file.isFile || !file.canRead()) {
            return null
        }

        val size = file.length()
        val lastModified = file.lastModified()

        if (size < 0L || lastModified <= 0L) {
            return null
        }

        return "file:size=$size:modified=$lastModified"
    }

    private fun mediaStoreSourceVersion(
        context: Context,
        uri: Uri,
    ): String? {
        val projection =
            arrayOf(
                MediaStore.MediaColumns.SIZE,
                MediaStore.MediaColumns.DATE_MODIFIED,
                MediaStore.MediaColumns.GENERATION_MODIFIED,
            )

        val cursor =
            runCatching {
                context.contentResolver.query(
                    uri,
                    projection,
                    null,
                    null,
                    null,
                )
            }.getOrNull() ?: return null

        return cursor.use { result ->
            if (!result.moveToFirst()) {
                return@use null
            }

            val sizeIndex =
                result.getColumnIndex(
                    MediaStore.MediaColumns.SIZE,
                )

            val modifiedIndex =
                result.getColumnIndex(
                    MediaStore.MediaColumns.DATE_MODIFIED,
                )

            if (
                sizeIndex < 0 ||
                modifiedIndex < 0 ||
                result.isNull(sizeIndex) ||
                result.isNull(modifiedIndex)
            ) {
                return@use null
            }

            val size = result.getLong(sizeIndex)
            val lastModifiedSeconds =
                result.getLong(modifiedIndex)

            if (
                size < 0L ||
                lastModifiedSeconds < 0L
            ) {
                return@use null
            }

            val generationIndex =
                result.getColumnIndex(
                    MediaStore.MediaColumns.GENERATION_MODIFIED,
                )

            val generation =
                if (
                    generationIndex >= 0 &&
                    !result.isNull(generationIndex)
                ) {
                    result.getLong(generationIndex)
                        .takeIf { it >= 0L }
                } else {
                    null
                }

            buildString {
                append("media:size=")
                append(size)
                append(":modified=")
                append(lastModifiedSeconds)
                append(":generation=")
                append(generation ?: "unknown")
            }
        }
    }

    /**
     * Runs on the loader's IO dispatcher.
     */
    private fun loadBitmapFromSource(
        context: Context,
        uri: Uri,
        maxDimension: Int,
        preferThumbnailApi: Boolean,
    ): Bitmap? {
        if (preferThumbnailApi) {
            val thumbnail =
                runCatching {
                    context.contentResolver.loadThumbnail(
                        uri,
                        Size(
                            maxDimension,
                            maxDimension,
                        ),
                        null,
                    )
                }.getOrNull()

            if (
                thumbnail != null &&
                !thumbnail.isRecycled
            ) {
                return thumbnail
            }
        }

        return decodeBitmap(
            context = context,
            uri = uri,
            maxDimension = maxDimension,
        )
    }

    /**
     * Reads image dimensions first, then decodes a scaled bitmap.
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
