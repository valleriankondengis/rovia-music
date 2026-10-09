package com.rovia.music.data.media.store

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.rovia.music.core.model.Track
import com.rovia.music.data.database.dao.MusicTrackDao
import com.rovia.music.data.database.entity.MusicTrackEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * Result of one bounded embedded-metadata processing batch.
 */
data class EmbeddedMetadataProcessingReport(
    val queued: Int,
    val completed: Int,
    val failed: Int,
)

/**
 * Extracts selected embedded metadata independently from catalog scanning.
 *
 * Only tracks marked as pending are processed. A bounded batch prevents
 * one invocation from opening an unbounded number of audio files.
 *
 * This processor must be scheduled outside the UI thread.
 */
class EmbeddedMetadataEnrichmentProcessor(
    context: Context,
    private val musicTrackDao: MusicTrackDao,
    private val currentTimeMillis: () -> Long =
        System::currentTimeMillis,
) {

    private val applicationContext =
        context.applicationContext

    /**
     * Processes at most [limit] pending tracks.
     *
     * A track is considered successfully processed when the media source
     * could be opened and queried, even if it contains no release date
     * or copyright metadata.
     *
     * Failed attempts are recorded separately so unchanged files are
     * not automatically reopened on every application startup.
     */
    suspend fun processPendingBatch(
        limit: Int = DEFAULT_BATCH_SIZE,
    ): EmbeddedMetadataProcessingReport =
        withContext(Dispatchers.IO) {
            require(limit > 0) {
                "Batch limit must be greater than zero."
            }

            val pendingTracks =
                musicTrackDao.getPendingEmbeddedMetadata(
                    limit = limit,
                )

            var completed = 0
            var failed = 0

            for (entity in pendingTracks) {
                currentCoroutineContext().ensureActive()

                val track =
                    entity.toTrack()

                val signature =
                    EmbeddedMetadataSignature.from(track)

                val result =
                    try {
                        readEmbeddedMetadata(
                            uri = Uri.parse(entity.uri),
                        )
                    } catch (
                        cancellation: CancellationException,
                    ) {
                        throw cancellation
                    } catch (
                        _: Exception,
                    ) {
                        null
                    }

                /*
                 * Store the signature even for a failed attempt.
                 * This prevents the unchanged source from being retried
                 * automatically on every startup.
                 *
                 * If MediaStore later reports a relevant source change,
                 * the synchronization coordinator can invalidate the
                 * signature and mark the track pending again.
                 */
                musicTrackDao.updateEmbeddedMetadata(
                    volumeName = entity.volumeName,
                    trackId = entity.trackId,
                    releaseDate = result?.releaseDate,
                    copyright = result?.copyright,
                    signature = signature,
                    status =
                        if (result != null) {
                            MusicTrackEntity
                                .EMBEDDED_METADATA_COMPLETE
                        } else {
                            MusicTrackEntity
                                .EMBEDDED_METADATA_FAILED
                        },
                    updatedAtEpochMillis =
                        currentTimeMillis(),
                )

                if (result != null) {
                    /*
                     * Keep the Recent Play snapshot synchronized with
                     * the embedded metadata discovered for this URI.
                     *
                     * Do not overwrite existing values when extraction
                     * fails.
                     */
                    musicTrackDao.updateRecentPlayEmbeddedMetadata(
                        uri = entity.uri,
                        releaseDate = result.releaseDate,
                        copyright = result.copyright,
                    )

                    completed++
                } else {
                    failed++
                }
            }

            EmbeddedMetadataProcessingReport(
                queued = pendingTracks.size,
                completed = completed,
                failed = failed,
            )
        }

    private fun readEmbeddedMetadata(
        uri: Uri,
    ): EmbeddedMetadata {
        return MediaMetadataRetriever().use { retriever ->
            retriever.setDataSource(
                applicationContext,
                uri,
            )

            EmbeddedMetadata(
                releaseDate =
                    retriever.extractMetadata(
                        MediaMetadataRetriever.METADATA_KEY_DATE,
                    )?.takeUnless {
                        it.isBlank()
                    },
                copyright =
                    retriever.extractMetadata(
                        COPYRIGHT_METADATA_KEY,
                    )?.takeUnless {
                        it.isBlank()
                    },
            )
        }
    }

    private data class EmbeddedMetadata(
        val releaseDate: String?,
        val copyright: String?,
    )

    private companion object {
        const val DEFAULT_BATCH_SIZE = 24

        /*
         * Android's native metadata key used by the existing Rovia
         * implementation for copyright extraction.
         */
        const val COPYRIGHT_METADATA_KEY = 15
    }
}

private fun MusicTrackEntity.toTrack(): Track {
    return Track(
        id = trackId,
        uri = uri,
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
        releaseDate = releaseDate,
        trackNumber = trackNumber,
        discNumber = discNumber,
        cdTrackNumber = cdTrackNumber,
        compilation = compilation,
        label = label,
        copyright = copyright,
        releaseType = releaseType,
        durationMs = durationMs,
        dateAddedEpochSeconds = dateAddedEpochSeconds,
        dateModifiedEpochSeconds = dateModifiedEpochSeconds,
        metadataDateEpochMillis = metadataDateEpochMillis,
        inferredDateEpochMillis = inferredDateEpochMillis,
        mimeType = mimeType,
        sampleRateHz = sampleRateHz,
        bitsPerSample = bitsPerSample,
        bitrateBps = bitrateBps,
        fileSizeBytes = fileSizeBytes,
        relativePath = relativePath,
        volumeName = volumeName,
        artworkUri = artworkUri,
    )
}