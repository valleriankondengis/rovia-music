package com.rovia.music.data.media.store

import com.rovia.music.core.model.Track
import java.security.MessageDigest

/**
 * Creates a lightweight fingerprint for the source metadata used
 * when extracting embedded metadata.
 *
 * This does not read the audio file.
 *
 * File path and display name are intentionally excluded so that a
 * path-only rename does not automatically invalidate embedded metadata.
 *
 * File size, modification time, MIME type, duration, and indexed
 * metadata fields are included so relevant source changes can
 * invalidate previously extracted metadata.
 */
object EmbeddedMetadataSignature {

    fun from(
        track: Track,
    ): String {
        val payload =
            buildString {
                appendPart(track.volumeName)
                appendPart(track.id)

                appendPart(track.fileSizeBytes)
                appendPart(track.dateModifiedEpochSeconds)
                appendPart(track.mimeType)
                appendPart(track.durationMs)

                appendPart(track.title)
                appendPart(track.artist)
                appendPart(track.albumArtist)
                appendPart(track.album)
                appendPart(track.genre)

                appendPart(track.author)
                appendPart(track.composer)
                appendPart(track.writer)

                appendPart(track.year)
                appendPart(track.trackNumber)
                appendPart(track.discNumber)
                appendPart(track.cdTrackNumber)
                appendPart(track.compilation)

                appendPart(track.metadataDateEpochMillis)
                appendPart(track.inferredDateEpochMillis)

                appendPart(track.sampleRateHz)
                appendPart(track.bitsPerSample)
                appendPart(track.bitrateBps)
            }

        val digest =
            MessageDigest
                .getInstance("SHA-256")
                .digest(
                    payload.toByteArray(Charsets.UTF_8),
                )

        val hexCharacters =
            "0123456789abcdef"

        return buildString(digest.size * 2) {
            digest.forEach { byte ->
                val value =
                    byte.toInt() and 0xff

                append(
                    hexCharacters[value ushr 4],
                )

                append(
                    hexCharacters[value and 0x0f],
                )
            }
        }
    }

    private fun StringBuilder.appendPart(
        value: Any?,
    ) {
        if (value == null) {
            append("-1:")
            return
        }

        val text =
            value.toString()

        /*
         * Length-prefixing prevents ambiguous concatenations
         * when metadata values contain punctuation or separators.
         */
        append(text.length)
        append(':')
        append(text)
    }
}