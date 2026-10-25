package com.rovia.music.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
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

object ArtworkBitmapLoader {

    private const val DEFAULT_MAX_DIMENSION = 1024
    private const val FILE_SCHEME = "file"
    private const val CONTENT_SCHEME = "content"
    private const val MEDIA_AUTHORITY = "media"
    private const val KILOBYTE = 1024L
    private const val MIN_CACHE_SIZE_KB = 8 * 1024
    private const val MAX_CACHE_SIZE_KB = 40 * 1024
    private const val MAX_CONCURRENT_ARTWORK_LOADS = 3

    private val cacheSizeKb = (
        Runtime.getRuntime().maxMemory() / KILOBYTE / 8L
    ).coerceIn(
        MIN_CACHE_SIZE_KB.toLong(),
        MAX_CACHE_SIZE_KB.toLong()
    ).toInt()

    private val bitmapCache = object : LruCache<String, Bitmap>(
        cacheSizeKb
    ) {
        override fun sizeOf(
            key: String,
            value: Bitmap
        ): Int {
            return (value.byteCount / KILOBYTE)
                .toInt()
                .coerceAtLeast(1)
        }
    }

    private data class InFlightRequest(
        val deferred: Deferred<Bitmap?>,
        var waiterCount: Int
    )

    private sealed interface RequestLookup {
        data class Cached(val bitmap: Bitmap) : RequestLookup
        data class InFlight(val request: InFlightRequest) : RequestLookup
    }

    private val requestMutex = Mutex()
    private val inFlightRequests = mutableMapOf<String, InFlightRequest>()

    private val loaderScope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO
    )

    private val artworkLoadSemaphore = Semaphore(
        permits = MAX_CONCURRENT_ARTWORK_LOADS
    )

    suspend fun load(
        context: Context,
        artworkUri: String?,
        maxDimension: Int = DEFAULT_MAX_DIMENSION
    ): Bitmap? {
        return loadShared(
            context = context,
            artworkUri = artworkUri,
            maxDimension = maxDimension,
            preferThumbnailApi = false
        )
    }

    suspend fun loadArtwork(
        context: Context,
        artworkUri: String?,
        maxDimension: Int
    ): Bitmap? {
        return loadShared(
            context = context,
            artworkUri = artworkUri,
            maxDimension = maxDimension,
            preferThumbnailApi = true
        )
    }

    private suspend fun loadShared(
        context: Context,
        artworkUri: String?,
        maxDimension: Int,
        preferThumbnailApi: Boolean
    ): Bitmap? {
        if (artworkUri.isNullOrBlank()) {
            return null
        }

        val uri = runCatching {
            Uri.parse(artworkUri).normalizeScheme()
        }.getOrNull() ?: return null

        val applicationContext = context.applicationContext
        val safeMaxDimension = normalizeDimension(maxDimension)
        val key = memoryCacheKey(uri, safeMaxDimension)

        getCachedBitmap(key)?.let { return it }

        val lookup = requestMutex.withLock {
            getCachedBitmap(key)?.let {
                return@withLock RequestLookup.Cached(it)
            }

            val existingRequest = inFlightRequests[key]

            if (existingRequest != null) {
                existingRequest.waiterCount++
                RequestLookup.InFlight(existingRequest)
            } else {
                val deferred = loaderScope.async(
                    start = CoroutineStart.LAZY
                ) {
                    artworkLoadSemaphore.withPermit {
                        loadFromDiskOrSource(
                            context = applicationContext,
                            uri = uri,
                            maxDimension = safeMaxDimension,
                            preferThumbnailApi = preferThumbnailApi,
                            memoryKey = key
                        )
                    }
                }

                val request = InFlightRequest(
                    deferred = deferred,
                    waiterCount = 1
                )

                inFlightRequests[key] = request
                deferred.start()

                RequestLookup.InFlight(request)
            }
        }

        return when (lookup) {
            is RequestLookup.Cached -> lookup.bitmap

            is RequestLookup.InFlight -> {
                val request = lookup.request

                try {
                    request.deferred.await()
                        ?.takeUnless(Bitmap::isRecycled)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } finally {
                    withContext(NonCancellable) {
                        requestMutex.withLock {
                            request.waiterCount =
                                (request.waiterCount - 1).coerceAtLeast(0)

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

    private suspend fun loadFromDiskOrSource(
        context: Context,
        uri: Uri,
        maxDimension: Int,
        preferThumbnailApi: Boolean,
        memoryKey: String
    ): Bitmap? {
        val diskKey = createPersistentCacheKey(
            context = context,
            uri = uri,
            maxDimension = maxDimension
        )

        if (diskKey != null) {
            when (
                val result = ArtworkDiskCache.read(
                    context = context,
                    key = diskKey
                )
            ) {
                is ArtworkDiskCacheResult.Hit -> {
                    bitmapCache.put(memoryKey, result.bitmap)
                    return result.bitmap
                }

                ArtworkDiskCacheResult.NoArtwork -> return null
                ArtworkDiskCacheResult.Miss -> Unit
            }
        }

        val bitmap = loadBitmapFromSource(
            context = context,
            uri = uri,
            maxDimension = maxDimension,
            preferThumbnailApi = preferThumbnailApi
        )

        if (bitmap != null && !bitmap.isRecycled) {
            bitmapCache.put(memoryKey, bitmap)

            if (diskKey != null) {
                ArtworkDiskCache.writeBitmap(
                    context = context,
                    key = diskKey,
                    bitmap = bitmap
                )
            }
        } else if (diskKey != null) {
            ArtworkDiskCache.writeNoArtwork(
                context = context,
                key = diskKey
            )
        }

        return bitmap
    }

    private fun getCachedBitmap(key: String): Bitmap? {
        val bitmap = bitmapCache.get(key) ?: return null

        if (bitmap.isRecycled) {
            bitmapCache.remove(key)
            return null
        }

        return bitmap
    }

    private fun memoryCacheKey(
        uri: Uri,
        maxDimension: Int
    ): String {
        return "${uri.normalizeScheme()}|$maxDimension"
    }

    private fun normalizeDimension(
        maxDimension: Int
    ): Int {
        val safeDimension = maxDimension.coerceAtLeast(1)
        return (((safeDimension + 127) / 128) * 128)
            .coerceIn(128, DEFAULT_MAX_DIMENSION)
    }

    private fun loadBitmapFromSource(
        context: Context,
        uri: Uri,
        maxDimension: Int,
        preferThumbnailApi: Boolean
    ): Bitmap? {
        if (
            preferThumbnailApi &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
        ) {
            val thumbnail = runCatching {
                context.contentResolver.loadThumbnail(
                    uri,
                    Size(maxDimension, maxDimension),
                    null
                )
            }.getOrNull()

            if (thumbnail != null && !thumbnail.isRecycled) {
                return thumbnail
            }
        }

        loadEmbeddedArtwork(
            context = context,
            uri = uri,
            maxDimension = maxDimension
        )?.let { return it }

        return decodeBitmap(
            context = context,
            uri = uri,
            maxDimension = maxDimension
        )
    }

    private fun loadEmbeddedArtwork(
        context: Context,
        uri: Uri,
        maxDimension: Int
    ): Bitmap? {
        val retriever = MediaMetadataRetriever()

        val artworkBytes = try {
            retriever.setDataSource(context, uri)
            retriever.embeddedPicture
        } catch (_: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        } ?: return null

        return decodeBytes(
            artworkBytes = artworkBytes,
            maxDimension = maxDimension
        )
    }

    private fun decodeBytes(
        artworkBytes: ByteArray,
        maxDimension: Int
    ): Bitmap? {
        val boundsOptions = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }

        BitmapFactory.decodeByteArray(
            artworkBytes,
            0,
            artworkBytes.size,
            boundsOptions
        )

        if (
            boundsOptions.outWidth <= 0 ||
            boundsOptions.outHeight <= 0
        ) {
            return null
        }

        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = calculateInSampleSize(
                width = boundsOptions.outWidth,
                height = boundsOptions.outHeight,
                maxDimension = maxDimension
            )
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }

        return runCatching {
            BitmapFactory.decodeByteArray(
                artworkBytes,
                0,
                artworkBytes.size,
                decodeOptions
            )
        }.getOrNull()
    }

    private fun decodeBitmap(
        context: Context,
        uri: Uri,
        maxDimension: Int
    ): Bitmap? {
        val boundsOptions = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }

        val boundsSuccess = runCatching {
            openInputStream(context, uri)?.use { stream ->
                BitmapFactory.decodeStream(
                    stream,
                    null,
                    boundsOptions
                )
            }
        }.isSuccess

        if (
            !boundsSuccess ||
            boundsOptions.outWidth <= 0 ||
            boundsOptions.outHeight <= 0
        ) {
            return null
        }

        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = calculateInSampleSize(
                width = boundsOptions.outWidth,
                height = boundsOptions.outHeight,
                maxDimension = maxDimension
            )
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }

        return runCatching {
            openInputStream(context, uri)?.use { stream ->
                BitmapFactory.decodeStream(
                    stream,
                    null,
                    decodeOptions
                )
            }
        }.getOrNull()
    }

    private fun openInputStream(
        context: Context,
        uri: Uri
    ) = if (uri.scheme.equals(FILE_SCHEME, ignoreCase = true)) {
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

    private fun calculateInSampleSize(
        width: Int,
        height: Int,
        maxDimension: Int
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

    private fun createPersistentCacheKey(
        context: Context,
        uri: Uri,
        maxDimension: Int
    ): String? {
        val normalizedUri = uri.normalizeScheme()

        val sourceVersion = when (uri.scheme) {
            FILE_SCHEME -> {
                val path = uri.path ?: return null
                val file = File(path)

                if (!file.isFile || !file.canRead()) {
                    return null
                }

                val size = file.length()
                val modified = file.lastModified()

                if (size < 0L || modified <= 0L) {
                    return null
                }

                "file:$size:$modified"
            }

            CONTENT_SCHEME -> {
                if (
                    !uri.authority.equals(
                        MEDIA_AUTHORITY,
                        ignoreCase = true
                    )
                ) {
                    return null
                }

                val projection = arrayOf(
                    MediaStore.MediaColumns.SIZE,
                    MediaStore.MediaColumns.DATE_MODIFIED,
                    MediaStore.MediaColumns.GENERATION_MODIFIED
                )

                val cursor = runCatching {
                    context.contentResolver.query(
                        uri,
                        projection,
                        null,
                        null,
                        null
                    )
                }.getOrNull() ?: return null

                cursor.use { result ->
                    if (!result.moveToFirst()) {
                        return null
                    }

                    val sizeIndex = result.getColumnIndex(
                        MediaStore.MediaColumns.SIZE
                    )
                    val modifiedIndex = result.getColumnIndex(
                        MediaStore.MediaColumns.DATE_MODIFIED
                    )
                    val generationIndex = result.getColumnIndex(
                        MediaStore.MediaColumns.GENERATION_MODIFIED
                    )

                    if (
                        sizeIndex < 0 ||
                        modifiedIndex < 0 ||
                        result.isNull(sizeIndex) ||
                        result.isNull(modifiedIndex)
                    ) {
                        return null
                    }

                    val size = result.getLong(sizeIndex)
                    val modified = result.getLong(modifiedIndex)

                    if (size < 0L || modified < 0L) {
                        return null
                    }

                    val generation = if (
                        generationIndex >= 0 &&
                        !result.isNull(generationIndex)
                    ) {
                        result.getLong(generationIndex)
                    } else {
                        -1L
                    }

                    "media:$size:$modified:$generation"
                }
            }

            else -> return null
        }

        return "$normalizedUri|$sourceVersion|$maxDimension"
    }
}