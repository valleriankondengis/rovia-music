
package com.rovia.music.data.media.store

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import com.rovia.music.core.model.Track
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Resolves metadata directly from an externally supplied content URI.
 *
 * This class intentionally does not access:
 * - the Room music catalog;
 * - MusicRepository;
 * - FolderBrowserRepository;
 * - FolderFilterRepository;
 * - MediaStore catalog synchronization;
 * - Recent Play persistence.
 *
 * The audio remains at its original URI. Only embedded artwork,
 * when available, is copied into an app-private temporary cache.
 */
class ExternalAudioMetadataResolver(
    context: Context,
) {

    private val applicationContext =
        context.applicationContext

    private val contentResolver =
        applicationContext.contentResolver

    private val artworkFiles =
        ConcurrentHashMap<String, File>()

    /**
     * Reads metadata off the UI thread.
     */
    suspend fun resolve(
        uri: Uri,
    ): Track =
        withContext(Dispatchers.IO) {
            resolveBlocking(uri)
        }

    /**
     * Deletes the temporary artwork associated with a track.
     * The original audio file is never deleted.
     */
    fun releaseArtwork(
        track: Track,
    ) {
        val artworkUri =
            track.artworkUri ?: return

        artworkFiles.remove(artworkUri)?.let { file ->
            runCatching {
                file.delete()
            }
        }
    }

    /**
     * Cleans up temporary artwork owned by this resolver.
     */
    fun releaseAllArtwork() {
        artworkFiles.forEach { (uri, file) ->
            if (artworkFiles.remove(uri, file)) {
                runCatching {
                    file.delete()
                }
            }
        }

        val cacheDirectory =
            File(
                applicationContext.cacheDir,
                EXTERNAL_ARTWORK_DIRECTORY,
            )

        if (
            artworkFiles.isEmpty() &&
            cacheDirectory.exists()
        ) {
            runCatching {
                cacheDirectory.listFiles()
                    ?.filter { file ->
                        file.isFile &&
                            file.name.startsWith(
                                EXTERNAL_ARTWORK_PREFIX,
                            )
                    }
                    ?.forEach { file ->
                        file.delete()
                    }
            }
        }
    }

    private fun resolveBlocking(
        uri: Uri,
    ): Track {
        if (uri.scheme != CONTENT_SCHEME) {
            throw ExternalAudioMetadataException(
                "The external audio URI must use the content scheme.",
            )
        }

        val sourceDetails =
            querySourceDetails(uri)

        val retriever =
            MediaMetadataRetriever()

        try {
            retriever.setDataSource(
                applicationContext,
                uri,
            )

            fun metadata(
                key: Int,
            ): String? =
                retriever.extractMetadata(key)
                    ?.trim()
                    ?.takeIf(String::isNotEmpty)

            val embeddedTitle =
                metadata(
                    MediaMetadataRetriever.METADATA_KEY_TITLE,
                )

            val title =
                embeddedTitle
                    ?: titleFromDisplayName(
                        sourceDetails.displayName,
                    )
                    ?: uri.lastPathSegment
                        ?.takeIf(String::isNotBlank)
                    ?: uri.toString()

            val artist =
                metadata(
                    MediaMetadataRetriever.METADATA_KEY_ARTIST,
                )

            val album =
                metadata(
                    MediaMetadataRetriever.METADATA_KEY_ALBUM,
                )

            val albumArtist =
                metadata(
                    MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST,
                )

            val releaseDate =
                metadata(
                    MediaMetadataRetriever.METADATA_KEY_DATE,
                )

            val year =
                metadata(
                    MediaMetadataRetriever.METADATA_KEY_YEAR,
                )?.toIntOrNull()
                    ?: releaseDate
                        ?.take(4)
                        ?.toIntOrNull()

            val cdTrackNumber =
                metadata(
                    MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER,
                )

            val discNumber =
                metadata(
                    MediaMetadataRetriever.METADATA_KEY_DISC_NUMBER,
                )

            val durationMs =
                metadata(
                    MediaMetadataRetriever.METADATA_KEY_DURATION,
                )?.toLongOrNull()
                    ?.coerceAtLeast(0L)
                    ?: 0L

            val mimeType =
                runCatching {
                    contentResolver.getType(uri)
                }.getOrNull()
                    ?: metadata(
                        MediaMetadataRetriever.METADATA_KEY_MIMETYPE,
                    )

            /*
             * Track.bitrateBps is Int?, so parse it as Int?.
             * Invalid, missing, or out-of-range values remain null.
             */
            val bitrateBps =
                metadata(
                    MediaMetadataRetriever.METADATA_KEY_BITRATE,
                )?.toIntOrNull()
                    ?.takeIf { it > 0 }

            val artworkUri =
                retriever.embeddedPicture
                    ?.let(::writeArtworkToCache)

            /*
             * Catalog identity and catalog timestamps are deliberately
             * not inferred or persisted for an external URI.
             */
            return Track(
                id = uri.toString().hashCode().toLong(),
                uri = uri.toString(),
                title = title,
                artist = artist,
                album = album,
                genre =
                    metadata(
                        MediaMetadataRetriever.METADATA_KEY_GENRE,
                    ),
                displayName = sourceDetails.displayName,
                albumArtist = albumArtist,
                composer =
                    metadata(
                        MediaMetadataRetriever.METADATA_KEY_COMPOSER,
                    ),
                author =
                    metadata(
                        MediaMetadataRetriever.METADATA_KEY_AUTHOR,
                    ),
                writer =
                    metadata(
                        MediaMetadataRetriever.METADATA_KEY_WRITER,
                    ),
                year = year,
                releaseDate = releaseDate,
                trackNumber =
                    cdTrackNumber
                        ?.substringBefore('/')
                        ?.toIntOrNull(),
                discNumber = discNumber,
                cdTrackNumber = cdTrackNumber,
                compilation =
                    metadata(
                        MediaMetadataRetriever.METADATA_KEY_COMPILATION,
                    ),
                label = null,
                copyright = null,
                releaseType = null,
                durationMs = durationMs,
                dateAddedEpochSeconds = 0L,
                dateModifiedEpochSeconds = null,
                metadataDateEpochMillis = null,
                inferredDateEpochMillis = null,
                mimeType = mimeType,
                sampleRateHz = null,
                bitsPerSample = null,
                bitrateBps = bitrateBps,
                fileSizeBytes = sourceDetails.sizeBytes,
                relativePath = null,
                volumeName = null,
                artworkUri = artworkUri,
            )
        } catch (
            exception: ExternalAudioMetadataException,
        ) {
            throw exception
        } catch (
            exception: Exception,
        ) {
            throw ExternalAudioMetadataException(
                "Unable to read metadata from the external audio URI.",
                exception,
            )
        } finally {
            runCatching {
                retriever.release()
            }
        }
    }

    private fun querySourceDetails(
        uri: Uri,
    ): SourceDetails {
        return try {
            contentResolver.query(
                uri,
                arrayOf(
                    OpenableColumns.DISPLAY_NAME,
                    OpenableColumns.SIZE,
                ),
                null,
                null,
                null,
            )?.use { cursor ->

                if (!cursor.moveToFirst()) {
                    return@use SourceDetails(
                        displayName = null,
                        sizeBytes = null,
                    )
                }

                val nameIndex =
                    cursor.getColumnIndex(
                        OpenableColumns.DISPLAY_NAME,
                    )

                val sizeIndex =
                    cursor.getColumnIndex(
                        OpenableColumns.SIZE,
                    )

                val displayName =
                    if (nameIndex >= 0) {
                        cursor.getString(nameIndex)
                            ?.takeIf(String::isNotBlank)
                    } else {
                        null
                    }

                val sizeBytes =
                    if (
                        sizeIndex >= 0 &&
                        !cursor.isNull(sizeIndex)
                    ) {
                        cursor.getLong(sizeIndex)
                            .takeIf { it >= 0L }
                    } else {
                        null
                    }

                SourceDetails(
                    displayName = displayName,
                    sizeBytes = sizeBytes,
                )
            } ?: SourceDetails(
                displayName = null,
                sizeBytes = null,
            )
        } catch (
            exception: SecurityException,
        ) {
            throw ExternalAudioMetadataException(
                "Read access to the external audio URI was denied.",
                exception,
            )
        } catch (
            _: Exception,
        ) {
            /*
             * Optional name and size metadata may be unavailable
             * from some content providers.
             */
            SourceDetails(
                displayName = null,
                sizeBytes = null,
            )
        }
    }

    private fun titleFromDisplayName(
        displayName: String?,
    ): String? {
        val name =
            displayName
                ?.substringAfterLast('/')
                ?.takeIf(String::isNotBlank)
                ?: return null

        val title =
            name.substringBeforeLast(
                delimiter = '.',
                missingDelimiterValue = name,
            )

        return title.takeIf(String::isNotBlank)
            ?: name
    }

    private fun writeArtworkToCache(
        bytes: ByteArray,
    ): String? {
        if (bytes.isEmpty()) {
            return null
        }

        val cacheDirectory =
            File(
                applicationContext.cacheDir,
                EXTERNAL_ARTWORK_DIRECTORY,
            )

        if (
            !cacheDirectory.exists() &&
            !cacheDirectory.mkdirs()
        ) {
            return null
        }

        var file: File? = null

        return try {
            file =
                File.createTempFile(
                    EXTERNAL_ARTWORK_PREFIX,
                    EXTERNAL_ARTWORK_SUFFIX,
                    cacheDirectory,
                )

            file.writeBytes(bytes)

            val uri =
                Uri.fromFile(file)
                    .toString()

            artworkFiles[uri] = file

            uri
        } catch (
            _: Exception,
        ) {
            file?.let { partialFile ->
                runCatching {
                    partialFile.delete()
                }
            }

            /*
             * Artwork is optional. Failure to cache the image
             * must not prevent audio playback.
             */
            null
        }
    }

    private data class SourceDetails(
        val displayName: String?,
        val sizeBytes: Long?,
    )

    companion object {
        private const val CONTENT_SCHEME = "content"

        private const val EXTERNAL_ARTWORK_DIRECTORY =
            "external-artwork"

        private const val EXTERNAL_ARTWORK_PREFIX =
            "rovia-artwork-"

        private const val EXTERNAL_ARTWORK_SUFFIX =
            ".bin"
    }
}

/**
 * A recoverable failure while resolving metadata from an
 * externally supplied content URI.
 */
class ExternalAudioMetadataException(
    message: String,
    cause: Throwable? = null,
) : Exception(
    message,
    cause,
)
