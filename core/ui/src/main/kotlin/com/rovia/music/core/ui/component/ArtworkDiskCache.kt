
package com.rovia.music.core.ui.component

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.LinkedHashMap

internal sealed class ArtworkDiskCacheResult {

    data class Hit(
        val bitmap: Bitmap,
    ) : ArtworkDiskCacheResult()

    object NoArtwork : ArtworkDiskCacheResult()

    object Miss : ArtworkDiskCacheResult()
}

/**
 * Bounded persistent thumbnail cache.
 *
 * A lazily initialized, access-ordered in-memory index tracks disk
 * entries and their sizes. Directory enumeration is performed once
 * per process instead of once for every thumbnail write.
 *
 * Cache hits update the in-memory LRU order without touching file
 * modification timestamps. Across application restarts, lastModified
 * provides the initial eviction order.
 *
 * All methods perform disk work and must be called off the UI thread.
 */
internal object ArtworkDiskCache {

    private const val DIRECTORY_NAME = "artwork-thumbnails"

    private const val BITMAP_EXTENSION = ".jpg"
    private const val NO_ARTWORK_EXTENSION = ".none"
    private const val TEMP_EXTENSION = ".tmp"

    private const val MAX_CACHE_BYTES = 100L * 1024L * 1024L
    private const val JPEG_QUALITY = 90

    private const val BITMAP_PREFIX = "art-"
    private const val TEMP_PREFIX = "tmp-"

    private const val HEX_CHARS = "0123456789abcdef"

    private val fileLock = Any()

    private data class CacheEntry(
        val file: File,
        val sizeBytes: Long,
    )

    /**
     * Access-ordered map: least recently used entry comes first.
     *
     * The key is the SHA-256 digest without the file extension.
     */
    private val cacheEntries =
        LinkedHashMap<String, CacheEntry>(
            16,
            0.75f,
            true,
        )

    private var cacheIndexInitialized = false
    private var totalCachedBytes = 0L

    /**
     * Reads a cached thumbnail or negative marker.
     *
     * Bitmap decoding occurs outside the lock. A cache hit updates
     * in-memory LRU order but does not write file metadata.
     */
    fun read(
        context: Context,
        key: String,
    ): ArtworkDiskCacheResult {
        val directory = cacheDirectory(context)
        val hashedKey = hashKey(key)

        val entry =
            synchronized(fileLock) {
                initializeCacheIndexLocked(directory)

                val current = cacheEntries[hashedKey]
                    ?: return@synchronized null

                val isBitmap =
                    current.file.name.endsWith(BITMAP_EXTENSION)

                if (
                    !current.file.isFile ||
                    (isBitmap && current.sizeBytes <= 0L)
                ) {
                    removeEntryLocked(
                        key = hashedKey,
                        deleteFile = false,
                    )
                    return@synchronized null
                }

                current
            } ?: return ArtworkDiskCacheResult.Miss

        if (entry.file.name.endsWith(NO_ARTWORK_EXTENSION)) {
            return ArtworkDiskCacheResult.NoArtwork
        }

        val bitmap =
            runCatching {
                BitmapFactory.decodeFile(
                    entry.file.absolutePath,
                )
            }.getOrNull()

        if (
            bitmap != null &&
            !bitmap.isRecycled
        ) {
            return ArtworkDiskCacheResult.Hit(bitmap)
        }

        synchronized(fileLock) {
            val current = cacheEntries[hashedKey]

            if (current?.file == entry.file) {
                removeEntryLocked(
                    key = hashedKey,
                    deleteFile = true,
                )
            } else {
                // The entry may have been replaced while decoding.
                entry.file.delete()
            }
        }

        return ArtworkDiskCacheResult.Miss
    }

    /**
     * Writes a processed thumbnail.
     *
     * Compression takes place outside the lock. The finished
     * temporary file is then installed and indexed under the lock.
     * The input bitmap remains owned by the caller.
     */
    fun writeBitmap(
        context: Context,
        key: String,
        bitmap: Bitmap,
    ) {
        if (
            bitmap.isRecycled ||
            bitmap.width <= 0 ||
            bitmap.height <= 0
        ) {
            return
        }

        val directory = cacheDirectory(context)

        if (
            !directory.isDirectory &&
            !directory.mkdirs() &&
            !directory.isDirectory
        ) {
            return
        }

        val hashedKey = hashKey(key)

        val bitmapFile =
            File(
                directory,
                "$BITMAP_PREFIX$hashedKey$BITMAP_EXTENSION",
            )

        val noArtworkFile =
            File(
                directory,
                "$BITMAP_PREFIX$hashedKey$NO_ARTWORK_EXTENSION",
            )

        /*
         * Avoid compression if a valid bitmap is already cached.
         */
        val alreadyCached =
            synchronized(fileLock) {
                initializeCacheIndexLocked(directory)

                val current = cacheEntries[hashedKey]

                if (
                    current != null &&
                    !current.file.isFile
                ) {
                    removeEntryLocked(
                        key = hashedKey,
                        deleteFile = false,
                    )
                } else if (
                    current != null &&
                    current.file.name.endsWith(BITMAP_EXTENSION) &&
                    current.sizeBytes > 0L
                ) {
                    return@synchronized true
                }

                false
            }

        if (alreadyCached) {
            return
        }

        var bitmapToCompress = bitmap
        var copiedBitmap: Bitmap? = null
        var temporaryFile: File? = null

        try {
            if (bitmap.config == Bitmap.Config.HARDWARE) {
                copiedBitmap =
                    bitmap.copy(
                        Bitmap.Config.ARGB_8888,
                        false,
                    ) ?: return

                bitmapToCompress = copiedBitmap
            }

            temporaryFile =
                File.createTempFile(
                    TEMP_PREFIX,
                    TEMP_EXTENSION,
                    directory,
                )

            val compressed =
                FileOutputStream(temporaryFile).use { output ->
                    val success =
                        bitmapToCompress.compress(
                            Bitmap.CompressFormat.JPEG,
                            JPEG_QUALITY,
                            output,
                        )

                    output.flush()
                    success
                }

            if (
                !compressed ||
                temporaryFile.length() <= 0L
            ) {
                return
            }

            synchronized(fileLock) {
                initializeCacheIndexLocked(directory)

                /*
                 * Another writer might have completed the same entry
                 * while this bitmap was being compressed.
                 */
                val current = cacheEntries[hashedKey]

                if (
                    current != null &&
                    current.file.isFile &&
                    current.file.name.endsWith(BITMAP_EXTENSION) &&
                    current.sizeBytes > 0L
                ) {
                    return@synchronized
                }

                if (current != null) {
                    val removed =
                        removeEntryLocked(
                            key = hashedKey,
                            deleteFile = true,
                        )

                    if (removed == null) {
                        return@synchronized
                    }
                }

                if (
                    noArtworkFile.exists() &&
                    !noArtworkFile.delete()
                ) {
                    return@synchronized
                }

                if (
                    bitmapFile.exists() &&
                    !bitmapFile.delete()
                ) {
                    return@synchronized
                }

                val renamed =
                    temporaryFile.renameTo(bitmapFile)

                if (!renamed) {
                    val copied =
                        runCatching {
                            temporaryFile.copyTo(
                                target = bitmapFile,
                                overwrite = true,
                            )
                        }.getOrNull()

                    if (copied == null) {
                        bitmapFile.delete()
                        return@synchronized
                    }

                    temporaryFile.delete()
                }

                if (
                    !bitmapFile.isFile ||
                    bitmapFile.length() <= 0L
                ) {
                    bitmapFile.delete()
                    return@synchronized
                }

                bitmapFile.setLastModified(
                    System.currentTimeMillis(),
                )

                val size = bitmapFile.length()

                cacheEntries[hashedKey] =
                    CacheEntry(
                        file = bitmapFile,
                        sizeBytes = size,
                    )

                totalCachedBytes += size

                /*
                 * This only walks the in-memory LRU index if the
                 * tracked size has exceeded the storage limit.
                 */
                trimCacheLocked()
            }
        } catch (_: Exception) {
            // Cache writes must not prevent artwork from being displayed.
        } finally {
            temporaryFile?.let { file ->
                if (file.exists()) {
                    runCatching {
                        file.delete()
                    }
                }
            }

            copiedBitmap?.let { copy ->
                if (!copy.isRecycled) {
                    copy.recycle()
                }
            }
        }
    }

    /**
     * Writes a negative marker only when absence of artwork has been
     * confirmed reliably by the caller.
     */
    fun writeNoArtwork(
        context: Context,
        key: String,
    ) {
        val directory = cacheDirectory(context)

        if (
            !directory.isDirectory &&
            !directory.mkdirs() &&
            !directory.isDirectory
        ) {
            return
        }

        val hashedKey = hashKey(key)

        val bitmapFile =
            File(
                directory,
                "$BITMAP_PREFIX$hashedKey$BITMAP_EXTENSION",
            )

        val noArtworkFile =
            File(
                directory,
                "$BITMAP_PREFIX$hashedKey$NO_ARTWORK_EXTENSION",
            )

        synchronized(fileLock) {
            initializeCacheIndexLocked(directory)

            val current = cacheEntries[hashedKey]

            if (
                current != null &&
                current.file.isFile
            ) {
                if (
                    current.file.name.endsWith(BITMAP_EXTENSION) &&
                    current.sizeBytes > 0L
                ) {
                    return
                }

                if (
                    current.file.name.endsWith(NO_ARTWORK_EXTENSION)
                ) {
                    // A marker already exists; the map access above
                    // has updated the in-memory LRU order.
                    return
                }
            }

            if (current != null) {
                val removed =
                    removeEntryLocked(
                        key = hashedKey,
                        deleteFile = true,
                    )

                if (removed == null) {
                    return
                }
            }

            if (
                bitmapFile.exists() &&
                !bitmapFile.delete()
            ) {
                return
            }

            val created =
                runCatching {
                    if (!noArtworkFile.exists()) {
                        noArtworkFile.createNewFile()
                    } else {
                        true
                    }
                }.getOrDefault(false)

            if (
                !created ||
                !noArtworkFile.isFile
            ) {
                return
            }

            noArtworkFile.setLastModified(
                System.currentTimeMillis(),
            )

            val size = noArtworkFile.length()

            cacheEntries[hashedKey] =
                CacheEntry(
                    file = noArtworkFile,
                    sizeBytes = size,
                )

            totalCachedBytes += size

            trimCacheLocked()
        }
    }

    /**
     * Invalidates the bitmap or negative marker associated with a key.
     */
    fun invalidate(
        context: Context,
        key: String,
    ) {
        val directory = cacheDirectory(context)
        val hashedKey = hashKey(key)

        synchronized(fileLock) {
            initializeCacheIndexLocked(directory)

            val current = cacheEntries[hashedKey]

            if (current != null) {
                removeEntryLocked(
                    key = hashedKey,
                    deleteFile = true,
                )
            }

            /*
             * Also remove either representation if an orphan file
             * exists outside the in-memory index.
             */
            File(
                directory,
                "$BITMAP_PREFIX$hashedKey$BITMAP_EXTENSION",
            ).delete()

            File(
                directory,
                "$BITMAP_PREFIX$hashedKey$NO_ARTWORK_EXTENSION",
            ).delete()
        }
    }

    /**
     * Clears cache entries. This is for explicit invalidation,
     * not ordinary scrolling.
     */
    fun clear(
        context: Context,
    ) {
        val directory = cacheDirectory(context)

        synchronized(fileLock) {
            initializeCacheIndexLocked(directory)

            cacheEntries.keys.toList().forEach { key ->
                removeEntryLocked(
                    key = key,
                    deleteFile = true,
                )
            }

            directory.listFiles()
                ?.filter { file ->
                    file.isFile &&
                        file.name.startsWith(TEMP_PREFIX) &&
                        file.name.endsWith(TEMP_EXTENSION)
                }
                ?.forEach { file ->
                    file.delete()
                }
        }
    }

    private fun cacheDirectory(
        context: Context,
    ): File {
        return File(
            context.applicationContext.cacheDir,
            DIRECTORY_NAME,
        )
    }

    /**
     * Builds the index only once per process.
     *
     * Existing entries are ordered by modification time on startup.
     * After initialization, access order is maintained in memory.
     *
     * Caller must hold fileLock.
     */
    private fun initializeCacheIndexLocked(
        directory: File,
    ) {
        if (cacheIndexInitialized) {
            return
        }

        cacheIndexInitialized = true
        cacheEntries.clear()
        totalCachedBytes = 0L

        if (!directory.isDirectory) {
            return
        }

        val files =
            directory.listFiles()
                ?.filter(::isCacheEntryFile)
                .orEmpty()

        val groupedFiles =
            files.groupBy(::baseKeyFromFile)

        val selectedEntries =
            mutableListOf<Pair<String, File>>()

        for ((key, matchingFiles) in groupedFiles) {
            val bitmapFile =
                matchingFiles.firstOrNull { file ->
                    file.name.endsWith(BITMAP_EXTENSION) &&
                        file.length() > 0L
                }

            val markerFile =
                matchingFiles.firstOrNull { file ->
                    file.name.endsWith(NO_ARTWORK_EXTENSION)
                }

            /*
             * Prefer a valid bitmap if an interrupted or older version
             * left both a bitmap and negative marker for the same key.
             */
            val selectedFile = bitmapFile ?: markerFile

            matchingFiles
                .filter { file ->
                    file != selectedFile
                }
                .forEach { redundantFile ->
                    runCatching {
                        redundantFile.delete()
                    }
                }

            if (selectedFile != null) {
                selectedEntries += key to selectedFile
            }
        }

        /*
         * Initial order is oldest first. Later reads update map order
         * without filesystem metadata writes.
         */
        selectedEntries
            .sortedBy { (_, file) ->
                file.lastModified()
            }
            .forEach { (key, file) ->
                val size = file.length().coerceAtLeast(0L)

                cacheEntries[key] =
                    CacheEntry(
                        file = file,
                        sizeBytes = size,
                    )

                totalCachedBytes += size
            }

        trimCacheLocked()
    }

    private fun isCacheEntryFile(
        file: File,
    ): Boolean {
        if (
            !file.isFile ||
            !file.name.startsWith(BITMAP_PREFIX)
        ) {
            return false
        }

        return file.name.endsWith(BITMAP_EXTENSION) ||
            file.name.endsWith(NO_ARTWORK_EXTENSION)
    }

    private fun baseKeyFromFile(
        file: File,
    ): String {
        val withoutPrefix =
            file.name.removePrefix(BITMAP_PREFIX)

        return when {
            withoutPrefix.endsWith(BITMAP_EXTENSION) ->
                withoutPrefix.removeSuffix(BITMAP_EXTENSION)

            withoutPrefix.endsWith(NO_ARTWORK_EXTENSION) ->
                withoutPrefix.removeSuffix(NO_ARTWORK_EXTENSION)

            else -> withoutPrefix
        }
    }

    /**
     * Removes one indexed entry and updates the tracked disk usage.
     *
     * If physical deletion fails, the entry remains indexed so the
     * tracked size does not falsely decrease.
     *
     * Caller must hold fileLock.
     */
    private fun removeEntryLocked(
        key: String,
        deleteFile: Boolean,
    ): CacheEntry? {
        val entry = cacheEntries[key] ?: return null

        if (
            deleteFile &&
            entry.file.exists() &&
            !entry.file.delete()
        ) {
            return null
        }

        cacheEntries.remove(key)

        totalCachedBytes =
            (totalCachedBytes - entry.sizeBytes)
                .coerceAtLeast(0L)

        return entry
    }

    /**
     * Evicts least-recently-used entries when the indexed size exceeds
     * the limit. No directory scan or sort occurs here.
     *
     * Caller must hold fileLock.
     */
    private fun trimCacheLocked() {
        if (totalCachedBytes <= MAX_CACHE_BYTES) {
            return
        }

        val iterator = cacheEntries.entries.iterator()

        while (
            totalCachedBytes > MAX_CACHE_BYTES &&
            iterator.hasNext()
        ) {
            val entry = iterator.next()
            val file = entry.value.file

            val deleted =
                !file.exists() ||
                    runCatching {
                        file.delete()
                    }.getOrDefault(false)

            if (deleted) {
                totalCachedBytes =
                    (
                        totalCachedBytes -
                            entry.value.sizeBytes
                        ).coerceAtLeast(0L)

                iterator.remove()
            }
        }
    }

    private fun hashKey(
        key: String,
    ): String {
        val digest =
            MessageDigest
                .getInstance("SHA-256")
                .digest(
                    key.toByteArray(Charsets.UTF_8),
                )

        return buildString(digest.size * 2) {
            digest.forEach { byte ->
                val value = byte.toInt() and 0xff

                append(HEX_CHARS[value ushr 4])
                append(HEX_CHARS[value and 0x0f])
            }
        }
    }
}
