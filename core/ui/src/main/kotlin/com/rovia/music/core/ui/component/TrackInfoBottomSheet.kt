@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
)

package com.rovia.music.core.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rovia.music.core.model.Track
import com.rovia.music.core.ui.R
import java.text.DateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TrackInfoBottomSheet(
    track: Track,
    onDismissRequest: () -> Unit,
) {
    val sheetState =
        rememberBottomSheetState(
            initialValue = SheetValue.PartiallyExpanded,
            enabledValues =
                setOf(
                    SheetValue.Hidden,
                    SheetValue.PartiallyExpanded,
                    SheetValue.Expanded,
                ),
        )

    ModalBottomSheet(
        modifier =
            Modifier.fillMaxHeight(),
        onDismissRequest =
            onDismissRequest,
        sheetState =
            sheetState,
        scrimColor =
            Color.Transparent,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(
                        rememberScrollState(),
                    )
                    .padding(
                        horizontal = 24.dp,
                        vertical = 8.dp,
                    ),
        ) {
            AlbumArtwork(
                artworkUri = track.artworkUri,
                fallbackText = track.title,
                contentDescription = null,
                modifier =
                    Modifier
                        .size(96.dp)
                        .align(Alignment.CenterHorizontally),
                size = 96.dp,
                shape = MaterialTheme.shapes.large,
            )

            Spacer(
                modifier =
                    Modifier.height(
                        16.dp,
                    ),
            )

            Text(
                text = track.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )

            track.artist
                ?.takeIf(String::isNotBlank)
                ?.let { artist ->
                    Spacer(
                        modifier =
                            Modifier.height(
                                4.dp,
                            ),
                    )

                    Text(
                        text = artist,
                        style = MaterialTheme.typography.bodyLarge,
                        color =
                            MaterialTheme
                                .colorScheme
                                .onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

            Spacer(
                modifier =
                    Modifier.height(
                        20.dp,
                    ),
            )

            TrackInfoRow(
                label =
                    stringResource(
                        R.string.track_info_album,
                    ),
                value =
                    track.album
                        ?.takeIf(String::isNotBlank)
                        .orEmpty(),
            )

            TrackInfoRow(
                label =
                    stringResource(
                        R.string.track_info_album_artist,
                    ),
                value =
                    track.albumArtist
                        ?.takeIf(String::isNotBlank)
                        .orEmpty(),
            )

            TrackInfoRow(
                label =
                    stringResource(
                        R.string.track_info_genre,
                    ),
                value =
                    track.genre
                        ?.takeIf(String::isNotBlank)
                        .orEmpty(),
            )

            TrackInfoRow(
                label =
                    stringResource(
                        R.string.track_info_year,
                    ),
                value =
                    track.year
                        ?.takeIf { it > 0 }
                        ?.toString()
                        .orEmpty(),
            )

            TrackInfoRow(
                label =
                    stringResource(
                        R.string.track_info_release_date,
                    ),
                value =
                    track.releaseDate
                        ?.takeIf(String::isNotBlank)
                        .orEmpty(),
            )

            TrackInfoRow(
                label =
                    stringResource(
                        R.string.track_info_track,
                    ),
                value =
                    track.trackNumber
                        ?.takeIf { it > 0 }
                        ?.toString()
                        .orEmpty(),
            )

            TrackInfoRow(
                label =
                    stringResource(
                        R.string.track_info_disc,
                    ),
                value =
                    track.discNumber
                        ?.takeIf(String::isNotBlank)
                        .orEmpty(),
            )

            TrackInfoRow(
                label =
                    stringResource(
                        R.string.track_info_cd_track,
                    ),
                value =
                    track.cdTrackNumber
                        ?.takeIf(String::isNotBlank)
                        .orEmpty(),
            )

            TrackInfoRow(
                label =
                    stringResource(
                        R.string.track_info_compilation,
                    ),
                value =
                    track.compilation
                        ?.takeIf(String::isNotBlank)
                        .orEmpty(),
            )

            TrackInfoRow(
                label =
                    stringResource(
                        R.string.track_info_release_type,
                    ),
                value =
                    track.releaseType
                        ?.takeIf(String::isNotBlank)
                        .orEmpty(),
            )

            TrackInfoRow(
                label =
                    stringResource(
                        R.string.track_info_label,
                    ),
                value =
                    track.label
                        ?.takeIf(String::isNotBlank)
                        .orEmpty(),
            )

            TrackInfoRow(
                label =
                    stringResource(
                        R.string.track_info_copyright,
                    ),
                value =
                    track.copyright
                        ?.takeIf(String::isNotBlank)
                        .orEmpty(),
            )

            TrackInfoRow(
                label =
                    stringResource(
                        R.string.track_info_composer,
                    ),
                value =
                    track.composer
                        ?.takeIf(String::isNotBlank)
                        .orEmpty(),
            )

            TrackInfoRow(
                label =
                    stringResource(
                        R.string.track_info_author,
                    ),
                value =
                    track.author
                        ?.takeIf(String::isNotBlank)
                        .orEmpty(),
            )

            TrackInfoRow(
                label =
                    stringResource(
                        R.string.track_info_writer,
                    ),
                value =
                    track.writer
                        ?.takeIf(String::isNotBlank)
                        .orEmpty(),
            )

            TrackInfoRow(
                label =
                    stringResource(
                        R.string.track_info_duration,
                    ),
                value =
                    formatTrackDuration(
                        track.durationMs,
                    ),
            )

            TrackInfoRow(
                label =
                    stringResource(
                        R.string.track_info_format,
                    ),
                value =
                    track.mimeType
                        ?.takeIf(String::isNotBlank)
                        ?.let { mimeType ->
                            formatAudioType(
                                mimeType,
                            )
                        }
                        .orEmpty(),
            )

            TrackInfoRow(
                label =
                    stringResource(
                        R.string.track_info_sample_rate,
                    ),
                value =
                    track.sampleRateHz
                        ?.takeIf { it > 0 }
                        ?.let { sampleRateHz ->
                            formatSampleRate(
                                sampleRateHz,
                            )
                        }
                        .orEmpty(),
            )

            TrackInfoRow(
                label =
                    stringResource(
                        R.string.track_info_bit_depth,
                    ),
                value =
                    track.bitsPerSample
                        ?.takeIf { it > 0 }
                        ?.let { bitsPerSample ->
                            "$bitsPerSample-bit"
                        }
                        .orEmpty(),
            )

            TrackInfoRow(
                label =
                    stringResource(
                        R.string.track_info_bitrate,
                    ),
                value =
                    track.bitrateBps
                        ?.takeIf { it > 0 }
                        ?.let { bitrateBps ->
                            formatBitrate(
                                bitrateBps,
                            )
                        }
                        .orEmpty(),
            )

            TrackInfoRow(
                label =
                    stringResource(
                        R.string.track_info_file_name,
                    ),
                value =
                    track.displayName
                        ?.takeIf(String::isNotBlank)
                        .orEmpty(),
            )

            TrackInfoRow(
                label =
                    stringResource(
                        R.string.track_info_file_size,
                    ),
                value =
                    track.fileSizeBytes
                        ?.takeIf { it > 0L }
                        ?.let { fileSizeBytes ->
                            formatFileSize(
                                fileSizeBytes,
                            )
                        }
                        .orEmpty(),
            )

            TrackInfoRow(
                label =
                    stringResource(
                        R.string.track_info_path,
                    ),
                value =
                    track.relativePath
                        ?.takeIf(String::isNotBlank)
                        .orEmpty(),
            )

            TrackInfoRow(
                label =
                    stringResource(
                        R.string.track_info_volume,
                    ),
                value =
                    track.volumeName
                        ?.takeIf(String::isNotBlank)
                        .orEmpty(),
            )

            TrackInfoRow(
                label =
                    stringResource(
                        R.string.track_info_added,
                    ),
                value =
                    track.dateAddedEpochSeconds
                        .takeIf { it > 0L }
                        ?.let { dateAddedEpochSeconds ->
                            formatEpochSeconds(
                                dateAddedEpochSeconds,
                            )
                        }
                        .orEmpty(),
            )

            TrackInfoRow(
                label =
                    stringResource(
                        R.string.track_info_modified,
                    ),
                value =
                    track.dateModifiedEpochSeconds
                        ?.takeIf { it > 0L }
                        ?.let { dateModifiedEpochSeconds ->
                            formatEpochSeconds(
                                dateModifiedEpochSeconds,
                            )
                        }
                        .orEmpty(),
            )

            TrackInfoRow(
                label =
                    stringResource(
                        R.string.track_info_created_inferred,
                    ),
                value =
                    track.inferredDateEpochMillis
                        ?.takeIf { it > 0L }
                        ?.let { inferredDateEpochMillis ->
                            formatEpochMillis(
                                inferredDateEpochMillis,
                            )
                        }
                        .orEmpty(),
            )

            TrackInfoRow(
                label =
                    stringResource(
                        R.string.track_info_metadata_date,
                    ),
                value =
                    track.metadataDateEpochMillis
                        ?.takeIf { it > 0L }
                        ?.let { metadataDateEpochMillis ->
                            formatEpochMillis(
                                metadataDateEpochMillis,
                            )
                        }
                        .orEmpty(),
            )

            Spacer(
                modifier =
                    Modifier.height(
                        16.dp,
                    ),
            )
        }
    }
}

@Composable
private fun TrackInfoRow(
    label: String,
    value: String,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(
                    vertical = 8.dp,
                ),
        horizontalArrangement =
            Arrangement.SpaceBetween,
        verticalAlignment =
            Alignment.Top,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color =
                MaterialTheme
                    .colorScheme
                    .onSurfaceVariant,
            modifier =
                Modifier.weight(
                    0.38f,
                ),
        )

        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            modifier =
                Modifier.weight(
                    0.62f,
                ),
        )
    }
}

private fun formatTrackDuration(
    durationMs: Long,
): String {
    if (durationMs <= 0L) {
        return ""
    }

    val totalSeconds =
        durationMs / 1_000

    val minutes =
        totalSeconds / 60

    val seconds =
        totalSeconds % 60

    return "%d:%02d".format(
        Locale.ROOT,
        minutes,
        seconds,
    )
}

private fun formatAudioType(
    mimeType: String,
): String {
    return mimeType
        .substringAfter(
            delimiter = "/",
            missingDelimiterValue = mimeType,
        )
        .removePrefix("x-")
        .uppercase(
            Locale.ROOT,
        )
}

private fun formatSampleRate(
    sampleRateHz: Int,
): String {
    return if (sampleRateHz % 1_000 == 0) {
        "${sampleRateHz / 1_000} kHz"
    } else {
        "%.1f kHz".format(
            Locale.ROOT,
            sampleRateHz / 1_000f,
        )
    }
}

private fun formatBitrate(
    bitrateBps: Int,
): String {
    return when {
        bitrateBps >= 1_000_000 ->
            "%.2f Mbps".format(
                Locale.ROOT,
                bitrateBps / 1_000_000.0,
            )

        bitrateBps >= 1_000 ->
            "%.0f kbps".format(
                Locale.ROOT,
                bitrateBps / 1_000.0,
            )

        bitrateBps > 0 ->
            "$bitrateBps bps"

        else ->
            ""
    }
}

private fun formatFileSize(
    fileSizeBytes: Long,
): String {
    if (fileSizeBytes <= 0L) {
        return ""
    }

    val units =
        arrayOf(
            "B",
            "KiB",
            "MiB",
            "GiB",
            "TiB",
        )

    var value =
        fileSizeBytes.toDouble()

    var unitIndex = 0

    while (
        value >= 1024.0 &&
        unitIndex < units.lastIndex
    ) {
        value /= 1024.0
        unitIndex++
    }

    return if (unitIndex == 0) {
        "$fileSizeBytes ${units[unitIndex]}"
    } else {
        "%.2f %s".format(
            Locale.ROOT,
            value,
            units[unitIndex],
        )
    }
}

private fun formatEpochSeconds(
    epochSeconds: Long,
): String {
    if (epochSeconds <= 0L) {
        return ""
    }

    return formatEpochMillis(
        epochSeconds * 1_000L,
    )
}

private fun formatEpochMillis(
    epochMillis: Long,
): String {
    if (epochMillis <= 0L) {
        return ""
    }

    return DateFormat
        .getDateTimeInstance(
            DateFormat.MEDIUM,
            DateFormat.SHORT,
        )
        .format(
            Date(epochMillis),
        )
}