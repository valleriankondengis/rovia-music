package com.rovia.music.data.media.store

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.provider.MediaStore
import com.rovia.music.core.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * One audio item read from a specific MediaStore volume.
 *
 * Embedded metadata is intentionally not extracted here.
 */
data class MediaStoreCatalogItem(
    val track: Track,
    val generationModified: Long?,
)

/**
 * A snapshot of MediaStore synchronization markers for one volume.
 *
 * The generation captured here must be treated as the synchronization
 * boundary for the query that follows it. A coordinator must not advance
 * its checkpoint to a later generation without accounting for changes
 * that happened after this snapshot was captured.
 */
data class MediaStoreVolumeState(
    val volumeName: String,
    val version: String,
    val generation: Long,
)

/**
 * Reads audio catalog information and synchronization markers from
 * individual MediaStore volumes.
 *
 * Responsibilities:
 * - Discover currently mounted external MediaStore volumes.
 * - Read MediaStore version and generation.
 * - Query all audio tracks on a volume.
 * - Query audio tracks modified after a known generation.
 * - Query track IDs for catalog reconciliation.
 *
 * This class does not:
 * - Write to Room.
 * - Read embedded metadata with MediaMetadataRetriever.
 * - Delete catalog entries.
 * - Apply excluded-folder preferences.
 */
class MediaStoreCatalogDataSource(
    context: Context,
) {

    private val applicationContext =
        context.applicationContext

    private val contentResolver: ContentResolver =
        applicationContext.contentResolver

    private val trackProjection =
        arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ARTIST,
            MediaStore.Audio.Media.GENRE,
            MediaStore.Audio.Media.AUTHOR,
            MediaStore.Audio.Media.COMPOSER,
            MediaStore.Audio.Media.WRITER,
            MediaStore.Audio.Media.YEAR,
            MediaStore.Audio.Media.TRACK,
            MediaStore.Audio.Media.DISC_NUMBER,
            MediaStore.Audio.Media.CD_TRACK_NUMBER,
            MediaStore.Audio.Media.COMPILATION,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.DATE_MODIFIED,
            MediaStore.Audio.Media.DATE_TAKEN,
            MediaStore.Audio.Media.INFERRED_DATE,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.SAMPLERATE,
            MediaStore.Audio.Media.BITS_PER_SAMPLE,
            MediaStore.Audio.Media.BITRATE,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.RELATIVE_PATH,
            MediaStore.MediaColumns.GENERATION_MODIFIED,
        )

    /**
     * Returns the currently available external MediaStore volume names.
     *
     * A volume that is absent from this result must not automatically
     * be treated as an empty volume. It may simply be unavailable.
     */
    suspend fun getAvailableVolumeNames(): List<String> =
        withContext(Dispatchers.IO) {
            MediaStore
                .getExternalVolumeNames(applicationContext)
                .toList()
                .sorted()
        }

    /**
     * Reads MediaStore version and generation for a currently available
     * volume.
     *
     * A missing volume or unavailable MediaStore version is treated as
     * an error so callers cannot accidentally interpret it as an empty
     * catalog and delete valid cached entries.
     */
    suspend fun getVolumeState(
        volumeName: String,
    ): MediaStoreVolumeState =
        withContext(Dispatchers.IO) {
            val availableVolumes =
                MediaStore.getExternalVolumeNames(
                    applicationContext,
                )

            check(volumeName in availableVolumes) {
                "MediaStore volume is not currently available: $volumeName"
            }

            val version =
                MediaStore.getVersion(
                    applicationContext,
                    volumeName,
                )

            check(!version.isNullOrBlank()) {
                "MediaStore version is unavailable for volume: $volumeName"
            }

            val generation =
                MediaStore.getGeneration(
                    applicationContext,
                    volumeName,
                )

            MediaStoreVolumeState(
                volumeName = volumeName,
                version = version,
                generation = generation,
            )
        }

    /**
     * Queries all audio tracks from one specific volume.
     *
     * This is intended for initial indexing and full reconciliation,
     * not for every application startup.
     */
    suspend fun queryAllTracks(
        volumeName: String,
    ): List<MediaStoreCatalogItem> =
        queryTracks(
            volumeName = volumeName,
            generationExclusive = null,
        )

    /**
     * Queries tracks whose GENERATION_MODIFIED is greater than the
     * supplied checkpoint.
     *
     * The caller must validate the stored MediaStore version before
     * relying on generation-based incremental synchronization.
     */
    suspend fun queryTracksModifiedAfter(
        volumeName: String,
        generationExclusive: Long,
    ): List<MediaStoreCatalogItem> {
        require(generationExclusive >= 0L) {
            "Generation checkpoint must not be negative."
        }

        return queryTracks(
            volumeName = volumeName,
            generationExclusive = generationExclusive,
        )
    }

    /**
     * Queries IDs on one volume for deletion reconciliation.
     *
     * This intentionally avoids reading every metadata column and
     * does not open individual audio files.
     */
    suspend fun queryTrackIds(
        volumeName: String,
    ): List<Long> =
        withContext(Dispatchers.IO) {
            val collection =
                getAudioCollectionUri(volumeName)

            val cursor =
                contentResolver.query(
                    collection,
                    arrayOf(
                        MediaStore.Audio.Media._ID,
                    ),
                    null,
                    null,
                    "${MediaStore.Audio.Media._ID} ASC",
                ) ?: throw IllegalStateException(
                    "MediaStore returned a null cursor for volume: $volumeName",
                )

            cursor.use {
                val idIndex =
                    it.getColumnIndexOrThrow(
                        MediaStore.Audio.Media._ID,
                    )

                val trackIds =
                    ArrayList<Long>()

                while (it.moveToNext()) {
                    trackIds += it.getLong(idIndex)
                }

                trackIds
            }
        }

    private suspend fun queryTracks(
        volumeName: String,
        generationExclusive: Long?,
    ): List<MediaStoreCatalogItem> =
        withContext(Dispatchers.IO) {
            val collection =
                getAudioCollectionUri(volumeName)

            val selection =
                generationExclusive?.let {
                    "${MediaStore.MediaColumns.GENERATION_MODIFIED} > ?"
                }

            val selectionArgs =
                generationExclusive?.let {
                    arrayOf(it.toString())
                }

            val sortOrder =
                "${MediaStore.MediaColumns.GENERATION_MODIFIED} ASC, " +
                    "${MediaStore.Audio.Media._ID} ASC"

            val cursor =
                contentResolver.query(
                    collection,
                    trackProjection,
                    selection,
                    selectionArgs,
                    sortOrder,
                ) ?: throw IllegalStateException(
                    "MediaStore returned a null cursor for volume: $volumeName",
                )

            cursor.use {
                readCatalogItems(
                    cursor = it,
                    volumeName = volumeName,
                )
            }
        }

    private fun readCatalogItems(
        cursor: Cursor,
        volumeName: String,
    ): List<MediaStoreCatalogItem> {
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

        val albumArtistIndex =
            cursor.getColumnIndexOrThrow(
                MediaStore.Audio.Media.ALBUM_ARTIST,
            )

        val genreIndex =
            cursor.getColumnIndexOrThrow(
                MediaStore.Audio.Media.GENRE,
            )

        val authorIndex =
            cursor.getColumnIndexOrThrow(
                MediaStore.Audio.Media.AUTHOR,
            )

        val composerIndex =
            cursor.getColumnIndexOrThrow(
                MediaStore.Audio.Media.COMPOSER,
            )

        val writerIndex =
            cursor.getColumnIndexOrThrow(
                MediaStore.Audio.Media.WRITER,
            )

        val yearIndex =
            cursor.getColumnIndexOrThrow(
                MediaStore.Audio.Media.YEAR,
            )

        val trackIndex =
            cursor.getColumnIndexOrThrow(
                MediaStore.Audio.Media.TRACK,
            )

        val discNumberIndex =
            cursor.getColumnIndexOrThrow(
                MediaStore.Audio.Media.DISC_NUMBER,
            )

        val cdTrackNumberIndex =
            cursor.getColumnIndexOrThrow(
                MediaStore.Audio.Media.CD_TRACK_NUMBER,
            )

        val compilationIndex =
            cursor.getColumnIndexOrThrow(
                MediaStore.Audio.Media.COMPILATION,
            )

        val durationIndex =
            cursor.getColumnIndexOrThrow(
                MediaStore.Audio.Media.DURATION,
            )

        val dateAddedIndex =
            cursor.getColumnIndexOrThrow(
                MediaStore.Audio.Media.DATE_ADDED,
            )

        val dateModifiedIndex =
            cursor.getColumnIndexOrThrow(
                MediaStore.Audio.Media.DATE_MODIFIED,
            )

        val metadataDateIndex =
            cursor.getColumnIndexOrThrow(
                MediaStore.Audio.Media.DATE_TAKEN,
            )

        val inferredDateIndex =
            cursor.getColumnIndexOrThrow(
                MediaStore.Audio.Media.INFERRED_DATE,
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

        val bitsPerSampleIndex =
            cursor.getColumnIndexOrThrow(
                MediaStore.Audio.Media.BITS_PER_SAMPLE,
            )

        val bitrateIndex =
            cursor.getColumnIndexOrThrow(
                MediaStore.Audio.Media.BITRATE,
            )

        val fileSizeIndex =
            cursor.getColumnIndexOrThrow(
                MediaStore.Audio.Media.SIZE,
            )

        val relativePathIndex =
            cursor.getColumnIndexOrThrow(
                MediaStore.Audio.Media.RELATIVE_PATH,
            )

        val generationModifiedIndex =
            cursor.getColumnIndexOrThrow(
                MediaStore.MediaColumns.GENERATION_MODIFIED,
            )

        val items =
            ArrayList<MediaStoreCatalogItem>()

        while (cursor.moveToNext()) {
            val id =
                cursor.getLong(idIndex)

            val displayName =
                cursor.readString(displayNameIndex)

            val title =
                cursor.readString(titleIndex)
                    ?: displayName
                    ?: "Unknown"

            val artist =
                cursor.readString(
                    artistIndex,
                    ignoreUnknown = true,
                )

            val album =
                cursor.readString(
                    albumIndex,
                    ignoreUnknown = true,
                )

            val albumArtist =
                cursor.readString(
                    albumArtistIndex,
                    ignoreUnknown = true,
                )

            val genre =
                cursor.readString(
                    genreIndex,
                    ignoreUnknown = true,
                )

            val author =
                cursor.readString(authorIndex)

            val composer =
                cursor.readString(composerIndex)

            val writer =
                cursor.readString(writerIndex)

            val year =
                cursor.readInt(yearIndex)
                    ?.takeIf { it > 0 }

            val encodedTrackNumber =
                cursor.readInt(trackIndex)
                    ?.takeIf { it > 0 }

            val trackNumber =
                encodedTrackNumber?.let { encoded ->
                    if (encoded >= 1_000) {
                        encoded % 1_000
                    } else {
                        encoded
                    }
                }

            val discNumber =
                cursor.readString(discNumberIndex)
                    ?: encodedTrackNumber?.let { encoded ->
                        if (encoded >= 1_000) {
                            (encoded / 1_000).toString()
                        } else {
                            null
                        }
                    }

            val cdTrackNumber =
                cursor.readString(cdTrackNumberIndex)

            val compilation =
                cursor.readString(compilationIndex)

            val durationMs =
                cursor.getLong(durationIndex)

            val dateAddedEpochSeconds =
                cursor.getLong(dateAddedIndex)

            val dateModifiedEpochSeconds =
                cursor.readLong(dateModifiedIndex)
                    ?.takeIf { it > 0L }

            val metadataDateEpochMillis =
                cursor.readLong(metadataDateIndex)
                    ?.takeIf { it > 0L }

            val inferredDateEpochMillis =
                cursor.readLong(inferredDateIndex)
                    ?.takeIf { it > 0L }

            val mimeType =
                cursor.readString(mimeTypeIndex)

            val sampleRateHz =
                cursor.readInt(sampleRateIndex)
                    ?.takeIf { it > 0 }

            val bitsPerSample =
                cursor.readInt(bitsPerSampleIndex)
                    ?.takeIf { it > 0 }

            val bitrateBps =
                cursor.readInt(bitrateIndex)
                    ?.takeIf { it > 0 }

            val fileSizeBytes =
                cursor.readLong(fileSizeIndex)
                    ?.takeIf { it > 0L }

            val relativePath =
                cursor.readString(relativePathIndex)

            val generationModified =
                cursor.readLong(generationModifiedIndex)

            val uri =
                MediaStore.Audio.Media.getContentUri(
                    volumeName,
                    id,
                )

            val track =
                Track(
                    id = id,
                    uri = uri.toString(),
                    title = title,
                    artist = artist,
                    album = album,
                    genre = genre,
                    displayName = displayName,
                    albumArtist = albumArtist,
                    composer = composer,
                    author = author,
                    writer = writer,
                    year = year,
                    releaseDate = null,
                    trackNumber = trackNumber,
                    discNumber = discNumber,
                    cdTrackNumber = cdTrackNumber,
                    compilation = compilation,
                    label = null,
                    copyright = null,
                    releaseType = null,
                    durationMs = durationMs,
                    dateAddedEpochSeconds = dateAddedEpochSeconds,
                    dateModifiedEpochSeconds =
                        dateModifiedEpochSeconds,
                    metadataDateEpochMillis =
                        metadataDateEpochMillis,
                    inferredDateEpochMillis =
                        inferredDateEpochMillis,
                    mimeType = mimeType,
                    sampleRateHz = sampleRateHz,
                    bitsPerSample = bitsPerSample,
                    bitrateBps = bitrateBps,
                    fileSizeBytes = fileSizeBytes,
                    relativePath = relativePath,
                    volumeName = volumeName,
                    artworkUri = uri.toString(),
                )

            items +=
                MediaStoreCatalogItem(
                    track = track,
                    generationModified = generationModified,
                )
        }

        return items
    }

    private fun getAudioCollectionUri(
        volumeName: String,
    ) = MediaStore.Audio.Media.getContentUri(
        volumeName,
    )

    private fun Cursor.readString(
        index: Int,
        ignoreUnknown: Boolean = false,
    ): String? {
        if (isNull(index)) {
            return null
        }

        return getString(index)
            ?.takeUnless { value ->
                value.isBlank() ||
                    (
                        ignoreUnknown &&
                            value == "<unknown>"
                        )
            }
    }

    private fun Cursor.readInt(
        index: Int,
    ): Int? {
        return if (isNull(index)) {
            null
        } else {
            getInt(index)
        }
    }

    private fun Cursor.readLong(
        index: Int,
    ): Long? {
        return if (isNull(index)) {
            null
        } else {
            getLong(index)
        }
    }
}