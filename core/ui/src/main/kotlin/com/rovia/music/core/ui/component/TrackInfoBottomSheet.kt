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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rovia.music.core.model.Track
import com.rovia.music.core.ui.R
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
    val bitrateKbps =
        (bitrateBps + 500) / 1_000

    return "$bitrateKbps kbps"
}