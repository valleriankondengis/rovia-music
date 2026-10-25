package com.rovia.music.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.LinkedHashMap

internal sealed interface ArtworkDiskCacheResult {
    data class Hit(val bitmap: Bitmap) : ArtworkDiskCacheResult
    object NoArtwork : ArtworkDiskCacheResult
    object Miss : ArtworkDiskCacheResult
}

internal object ArtworkDiskCache {

    private const val DIRECTORY_NAME = "artwork-thumbnails"
    private const val BITMAP_EXTENSION = ".jpg"
    private const val NO_ARTWORK_EXTENSION = ".none"
    private const val MAX_CACHE_BYTES = 100L * 1024L * 1024L
    private const val JPEG_QUALITY = 90

    private val cacheLock = Any()

    private val cacheEntries = LinkedHashMap<String, File>(
        16,
        0.75f,
        true
    )

    private var initializedDirectory: String? = null
    private var totalCachedBytes = 0L

    fun read(
        context: Context,
        key: String
    ): ArtworkDiskCacheResult {
        val directory = cacheDirectory(context)
        val hashedKey = hashKey(key)

        val file = synchronized(cacheLock) {
            initializeCacheIndexLocked(directory)

            val current = cacheEntries[hashedKey]
                ?: return@synchronized null

            if (!current.isFile) {
                removeEntryLocked(hashedKey, false)
                return@synchronized null
            }

            current.setLastModified(System.currentTimeMillis())
            current
        } ?: return ArtworkDiskCacheResult.Miss

        if (file.name.endsWith(NO_ARTWORK_EXTENSION)) {
            return ArtworkDiskCacheResult.NoArtwork
        }

        val bitmap = runCatching {
            BitmapFactory.decodeFile(file.absolutePath)
        }.getOrNull()

        if (bitmap != null && !bitmap.isRecycled) {
            return ArtworkDiskCacheResult.Hit(bitmap)
        }

        synchronized(cacheLock) {
            if (cacheEntries[hashedKey] == file) {
                removeEntryLocked(hashedKey, true)
            }
        }

        return ArtworkDiskCacheResult.Miss
    }

    fun writeBitmap(
        context: Context,
        key: String,
        bitmap: Bitmap
    ) {
        if (
            bitmap.isRecycled ||
            bitmap.width <= 0 ||
            bitmap.height <= 0
        ) {
            return
        }

        val directory = cacheDirectory(context)

        if (!directory.isDirectory && !directory.mkdirs()) {
            return
        }

        val hashedKey = hashKey(key)
        val targetFile = File(
            directory,
            "art-$hashedKey$BITMAP_EXTENSION"
        )
        val markerFile = File(
            directory,
            "art-$hashedKey$NO_ARTWORK_EXTENSION"
        )

        var bitmapToCompress = bitmap
        var copiedBitmap: Bitmap? = null
        var temporaryFile: File? = null

        try {
            if (bitmap.config == Bitmap.Config.HARDWARE) {
                copiedBitmap = bitmap.copy(
                    Bitmap.Config.ARGB_8888,
                    false
                ) ?: return

                bitmapToCompress = copiedBitmap
            }

            temporaryFile = File.createTempFile(
                "tmp-",
                ".tmp",
                directory
            )

            val compressed = FileOutputStream(temporaryFile).use { output ->
                val success = bitmapToCompress.compress(
                    Bitmap.CompressFormat.JPEG,
                    JPEG_QUALITY,
                    output
                )
                output.flush()
                success
            }

            if (!compressed || temporaryFile.length() <= 0L) {
                return
            }

            synchronized(cacheLock) {
                initializeCacheIndexLocked(directory)

                val current = cacheEntries[hashedKey]

                if (
                    current != null &&
                    current.isFile &&
                    current.name.endsWith(BITMAP_EXTENSION) &&
                    current.length() > 0L
                ) {
                    return@synchronized
                }

                if (
                    current != null &&
                    !removeEntryLocked(hashedKey, true)
                ) {
                    return@synchronized
                }

                if (
                    markerFile.exists() &&
                    !markerFile.delete()
                ) {
                    return@synchronized
                }

                if (
                    targetFile.exists() &&
                    !targetFile.delete()
                ) {
                    return@synchronized
                }

                val renamed = temporaryFile.renameTo(targetFile)

                if (!renamed) {
                    val copied = runCatching {
                        temporaryFile.copyTo(
                            target = targetFile,
                            overwrite = true
                        )
                    }.getOrNull()

                    if (copied == null) {
                        targetFile.delete()
                        return@synchronized
                    }

                    temporaryFile.delete()
                }

                if (!targetFile.isFile || targetFile.length() <= 0L) {
                    targetFile.delete()
                    return@synchronized
                }

                targetFile.setLastModified(System.currentTimeMillis())
                cacheEntries[hashedKey] = targetFile
                totalCachedBytes += targetFile.length()

                trimCacheLocked()
            }
        } catch (_: Exception) {
            return
        } finally {
            temporaryFile?.let {
                if (it.exists()) {
                    runCatching { it.delete() }
                }
            }

            copiedBitmap?.let {
                if (!it.isRecycled) {
                    it.recycle()
                }
            }
        }
    }

    fun writeNoArtwork(
        context: Context,
        key: String
    ) {
        val directory = cacheDirectory(context)

        if (!directory.isDirectory && !directory.mkdirs()) {
            return
        }

        val hashedKey = hashKey(key)
        val bitmapFile = File(
            directory,
            "art-$hashedKey$BITMAP_EXTENSION"
        )
        val markerFile = File(
            directory,
            "art-$hashedKey$NO_ARTWORK_EXTENSION"
        )

        synchronized(cacheLock) {
            initializeCacheIndexLocked(directory)

            val current = cacheEntries[hashedKey]

            if (
                current != null &&
                current.isFile &&
                current.name.endsWith(BITMAP_EXTENSION) &&
                current.length() > 0L
            ) {
                return
            }

            if (
                current != null &&
                current.isFile &&
                current.name.endsWith(NO_ARTWORK_EXTENSION)
            ) {
                current.setLastModified(System.currentTimeMillis())
                return
            }

            if (
                current != null &&
                !removeEntryLocked(hashedKey, true)
            ) {
                return
            }

            if (
                bitmapFile.exists() &&
                !bitmapFile.delete()
            ) {
                return
            }

            val created = runCatching {
                if (!markerFile.exists()) {
                    markerFile.createNewFile()
                } else {
                    true
                }
            }.getOrDefault(false)

            if (!created || !markerFile.isFile) {
                return
            }

            markerFile.setLastModified(System.currentTimeMillis())
            cacheEntries[hashedKey] = markerFile
            totalCachedBytes += markerFile.length()

            trimCacheLocked()
        }
    }

    fun invalidate(
        context: Context,
        key: String
    ) {
        val directory = cacheDirectory(context)
        val hashedKey = hashKey(key)

        synchronized(cacheLock) {
            initializeCacheIndexLocked(directory)
            removeEntryLocked(hashedKey, true)

            File(
                directory,
                "art-$hashedKey$BITMAP_EXTENSION"
            ).delete()

            File(
                directory,
                "art-$hashedKey$NO_ARTWORK_EXTENSION"
            ).delete()
        }
    }

    private fun cacheDirectory(context: Context): File {
        return File(
            context.applicationContext.cacheDir,
            DIRECTORY_NAME
        )
    }

    private fun initializeCacheIndexLocked(directory: File) {
        if (initializedDirectory == directory.absolutePath) {
            return
        }

        initializedDirectory = directory.absolutePath
        cacheEntries.clear()
        totalCachedBytes = 0L

        if (!directory.isDirectory) {
            return
        }

        val files = directory.listFiles()
            ?.filter { file ->
                file.isFile &&
                    file.name.startsWith("art-") &&
                    (
                        file.name.endsWith(BITMAP_EXTENSION) ||
                            file.name.endsWith(NO_ARTWORK_EXTENSION)
                        )
            }
            .orEmpty()

        val groupedFiles = files.groupBy(::baseKeyFromFile)

        val selectedEntries = mutableListOf<Pair<String, File>>()

        for ((key, matchingFiles) in groupedFiles) {
            val bitmapFile = matchingFiles.firstOrNull { file ->
                file.name.endsWith(BITMAP_EXTENSION) &&
                    file.length() > 0L
            }

            val markerFile = matchingFiles.firstOrNull { file ->
                file.name.endsWith(NO_ARTWORK_EXTENSION)
            }

            val selectedFile = bitmapFile ?: markerFile

            matchingFiles
                .filter { it != selectedFile }
                .forEach { it.delete() }

            if (selectedFile != null) {
                selectedEntries += key to selectedFile
            }
        }

        selectedEntries
            .sortedBy { (_, file) -> file.lastModified() }
            .forEach { (key, file) ->
                cacheEntries[key] = file
                totalCachedBytes += file.length()
            }

        trimCacheLocked()
    }

    private fun baseKeyFromFile(file: File): String {
        return file.name
            .removePrefix("art-")
            .removeSuffix(BITMAP_EXTENSION)
            .removeSuffix(NO_ARTWORK_EXTENSION)
    }

    private fun removeEntryLocked(
        key: String,
        deleteFile: Boolean
    ): Boolean {
        val file = cacheEntries[key] ?: return true

        if (
            deleteFile &&
            file.exists() &&
            !file.delete()
        ) {
            return false
        }

        cacheEntries.remove(key)
        totalCachedBytes =
            (totalCachedBytes - file.length()).coerceAtLeast(0L)

        return true
    }

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
            val file = entry.value

            val deleted = !file.exists() || runCatching {
                file.delete()
            }.getOrDefault(false)

            if (deleted) {
                totalCachedBytes =
                    (totalCachedBytes - file.length()).coerceAtLeast(0L)
                iterator.remove()
            }
        }
    }

    private fun hashKey(key: String): String {
        val digest = MessageDigest
            .getInstance("SHA-256")
            .digest(key.toByteArray(Charsets.UTF_8))

        return buildString(digest.size * 2) {
            digest.forEach { byte ->
                val value = byte.toInt() and 0xff
                append("0123456789abcdef"[value ushr 4])
                append("0123456789abcdef"[value and 0x0f])
            }
        }
    }
}