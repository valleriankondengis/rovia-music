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
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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

            track.album
                ?.takeIf(String::isNotBlank)
                ?.let { album ->
                    TrackInfoRow(
                        label =
                            stringResource(
                                R.string.track_info_album,
                            ),
                        value = album,
                    )
                }

            track.albumArtist
                ?.takeIf(String::isNotBlank)
                ?.let { albumArtist ->
                    TrackInfoRow(
                        label =
                            stringResource(
                                R.string.track_info_album_artist,
                            ),
                        value = albumArtist,
                    )
                }

            track.genre
                ?.takeIf(String::isNotBlank)
                ?.let { genre ->
                    TrackInfoRow(
                        label =
                            stringResource(
                                R.string.track_info_genre,
                            ),
                        value = genre,
                    )
                }

            track.year
                ?.takeIf { it > 0 }
                ?.let { year ->
                    TrackInfoRow(
                        label =
                            stringResource(
                                R.string.track_info_year,
                            ),
                        value = year.toString(),
                    )
                }

            track.trackNumber
                ?.takeIf { it > 0 }
                ?.let { trackNumber ->
                    TrackInfoRow(
                        label =
                            stringResource(
                                R.string.track_info_track,
                            ),
                        value = trackNumber.toString(),
                    )
                }

            track.discNumber
                ?.takeIf(String::isNotBlank)
                ?.let { discNumber ->
                    TrackInfoRow(
                        label =
                            stringResource(
                                R.string.track_info_disc,
                            ),
                        value = discNumber,
                    )
                }

            track.cdTrackNumber
                ?.takeIf(String::isNotBlank)
                ?.takeIf { it != track.trackNumber?.toString() }
                ?.let { cdTrackNumber ->
                    TrackInfoRow(
                        label =
                            stringResource(
                                R.string.track_info_cd_track,
                            ),
                        value = cdTrackNumber,
                    )
                }

            track.compilation
                ?.takeIf(String::isNotBlank)
                ?.let { compilation ->
                    TrackInfoRow(
                        label =
                            stringResource(
                                R.string.track_info_compilation,
                            ),
                        value = compilation,
                    )
                }

            track.composer
                ?.takeIf(String::isNotBlank)
                ?.let { composer ->
                    TrackInfoRow(
                        label =
                            stringResource(
                                R.string.track_info_composer,
                            ),
                        value = composer,
                    )
                }

            track.author
                ?.takeIf(String::isNotBlank)
                ?.let { author ->
                    TrackInfoRow(
                        label =
                            stringResource(
                                R.string.track_info_author,
                            ),
                        value = author,
                    )
                }

            track.writer
                ?.takeIf(String::isNotBlank)
                ?.let { writer ->
                    TrackInfoRow(
                        label =
                            stringResource(
                                R.string.track_info_writer,
                            ),
                        value = writer,
                    )
                }

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

            track.mimeType
                ?.takeIf(String::isNotBlank)
                ?.let { mimeType ->
                    TrackInfoRow(
                        label =
                            stringResource(
                                R.string.track_info_format,
                            ),
                        value =
                            formatAudioType(
                                mimeType,
                            ),
                    )
                }

            track.sampleRateHz
                ?.takeIf { it > 0 }
                ?.let { sampleRateHz ->
                    TrackInfoRow(
                        label =
                            stringResource(
                                R.string.track_info_sample_rate,
                            ),
                        value =
                            formatSampleRate(
                                sampleRateHz,
                            ),
                    )
                }

            track.bitsPerSample
                ?.takeIf { it > 0 }
                ?.let { bitsPerSample ->
                    TrackInfoRow(
                        label =
                            stringResource(
                                R.string.track_info_bit_depth,
                            ),
                        value =
                            "$bitsPerSample-bit",
                    )
                }

            track.bitrateBps
                ?.takeIf { it > 0 }
                ?.let { bitrateBps ->
                    TrackInfoRow(
                        label =
                            stringResource(
                                R.string.track_info_bitrate,
                            ),
                        value =
                            formatBitrate(
                                bitrateBps,
                            ),
                    )
                }

            track.displayName
                ?.takeIf(String::isNotBlank)
                ?.let { displayName ->
                    TrackInfoRow(
                        label =
                            stringResource(
                                R.string.track_info_file_name,
                            ),
                        value = displayName,
                    )
                }

            track.fileSizeBytes
                ?.takeIf { it > 0L }
                ?.let { fileSizeBytes ->
                    TrackInfoRow(
                        label =
                            stringResource(
                                R.string.track_info_file_size,
                            ),
                        value =
                            formatFileSize(
                                fileSizeBytes,
                            ),
                    )
                }

            track.relativePath
                ?.takeIf(String::isNotBlank)
                ?.let { relativePath ->
                    TrackInfoRow(
                        label =
                            stringResource(
                                R.string.track_info_path,
                            ),
                        value = relativePath,
                    )
                }

            track.volumeName
                ?.takeIf(String::isNotBlank)
                ?.let { volumeName ->
                    TrackInfoRow(
                        label =
                            stringResource(
                                R.string.track_info_volume,
                            ),
                        value = volumeName,
                    )
                }

            track.dateAddedEpochSeconds
                .takeIf { it > 0L }
                ?.let { dateAddedEpochSeconds ->
                    TrackInfoRow(
                        label =
                            stringResource(
                                R.string.track_info_added,
                            ),
                        value =
                            formatEpochSeconds(
                                dateAddedEpochSeconds,
                            ),
                    )
                }

            track.dateModifiedEpochSeconds
                ?.takeIf { it > 0L }
                ?.let { dateModifiedEpochSeconds ->
                    TrackInfoRow(
                        label =
                            stringResource(
                                R.string.track_info_modified,
                            ),
                        value =
                            formatEpochSeconds(
                                dateModifiedEpochSeconds,
                            ),
                    )
                }

            track.inferredDateEpochMillis
                ?.takeIf { it > 0L }
                ?.let { inferredDateEpochMillis ->
                    TrackInfoRow(
                        label =
                            stringResource(
                                R.string.track_info_created_inferred,
                            ),
                        value =
                            formatEpochMillis(
                                inferredDateEpochMillis,
                            ),
                    )
                }

            track.metadataDateEpochMillis
                ?.takeIf { it > 0L }
                ?.let { metadataDateEpochMillis ->
                    TrackInfoRow(
                        label =
                            stringResource(
                                R.string.track_info_metadata_date,
                            ),
                        value =
                            formatEpochMillis(
                                metadataDateEpochMillis,
                            ),
                    )
                }

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
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
        return "--:--"
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

        else ->
            "$bitrateBps bps"
    }
}

private fun formatFileSize(
    fileSizeBytes: Long,
): String {
    if (fileSizeBytes <= 0L) {
        return "—"
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
    return formatEpochMillis(
        epochSeconds * 1_000L,
    )
}

private fun formatEpochMillis(
    epochMillis: Long,
): String {
    if (epochMillis <= 0L) {
        return "—"
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