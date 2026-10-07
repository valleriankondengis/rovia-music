@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
)

package com.rovia.music.feature.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rovia.music.core.model.Track
import java.util.Locale

@Composable
internal fun TrackTechnicalInfo(
    track: Track?,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
        horizontalArrangement =
            Arrangement.Center,
        verticalAlignment =
            Alignment.CenterVertically,
    ) {
        Text(
            text =
                formatSampleRate(
                    track?.sampleRateHz,
                ),
            style =
                MaterialTheme.typography.labelMedium,
            color =
                MaterialTheme.colorScheme
                    .onSurfaceVariant,
        )

        Text(
            text = "•",
            style =
                MaterialTheme.typography.labelMedium,
            color =
                MaterialTheme.colorScheme
                    .onSurfaceVariant,
            modifier =
                Modifier.padding(
                    horizontal = 6.dp,
                ),
        )

        Text(
            text =
                formatBitrate(
                    track?.bitrateBps,
                ),
            style =
                MaterialTheme.typography.labelMedium,
            color =
                MaterialTheme.colorScheme
                    .onSurfaceVariant,
        )

        Text(
            text = "•",
            style =
                MaterialTheme.typography.labelMedium,
            color =
                MaterialTheme.colorScheme
                    .onSurfaceVariant,
            modifier =
                Modifier.padding(
                    horizontal = 6.dp,
                ),
        )

        Text(
            text =
                formatAudioFormat(
                    track?.mimeType,
                ),
            style =
                MaterialTheme.typography.labelMedium,
            color =
                MaterialTheme.colorScheme
                    .onSurfaceVariant,
        )
    }
}

@Composable
internal fun SeekTimeRow(
    positionMs: Float,
    durationMs: Long,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
        horizontalArrangement =
            Arrangement.SpaceBetween,
    ) {
        Text(
            text =
                formatDuration(
                    positionMs.toLong(),
                ),
            style =
                MaterialTheme.typography.labelMedium,
            color =
                MaterialTheme.colorScheme
                    .onSurfaceVariant,
        )

        Text(
            text =
                formatDuration(durationMs),
            style =
                MaterialTheme.typography.labelMedium,
            color =
                MaterialTheme.colorScheme
                    .onSurfaceVariant,
        )
    }
}

private fun formatSampleRate(
    sampleRateHz: Int?,
): String {
    if (
        sampleRateHz == null ||
        sampleRateHz <= 0
    ) {
        return "— kHz"
    }

    val sampleRateKhz =
        sampleRateHz / 1_000.0

    val formatted =
        if (
            sampleRateKhz ==
                sampleRateKhz
                    .toInt()
                    .toDouble()
        ) {
            sampleRateKhz
                .toInt()
                .toString()
        } else {
            String.format(
                Locale.US,
                "%.1f",
                sampleRateKhz,
            )
        }

    return "$formatted kHz"
}

private fun formatBitrate(
    bitrateBps: Int?,
): String {
    if (
        bitrateBps == null ||
        bitrateBps <= 0
    ) {
        return "— kbps"
    }

    return "${bitrateBps / 1_000} kbps"
}

private fun formatAudioFormat(
    mimeType: String?,
): String {
    return when (
        mimeType?.lowercase(Locale.US)
    ) {
        "audio/mpeg" ->
            "MP3"

        "audio/flac",
        "audio/x-flac",
        ->
            "FLAC"

        "audio/wav",
        "audio/x-wav",
        "audio/vnd.wave",
        ->
            "WAV"

        "audio/aac" ->
            "AAC"

        "audio/ogg" ->
            "OGG"

        "audio/opus" ->
            "OPUS"

        "audio/mp4",
        "audio/x-m4a",
        ->
            "M4A"

        "audio/x-ms-wma" ->
            "WMA"

        "audio/3gpp" ->
            "3GP"

        else ->
            mimeType
                ?.substringAfter(
                    '/',
                    missingDelimiterValue = "",
                )
                ?.takeUnless {
                    it.isBlank()
                }
                ?.uppercase(Locale.US)
                ?: "—"
    }
}

private fun formatDuration(
    durationMs: Long,
): String {
    if (durationMs <= 0L) {
        return "0:00"
    }

    val totalSeconds =
        durationMs / 1_000

    val minutes =
        totalSeconds / 60

    val seconds =
        totalSeconds % 60

    return "%d:%02d".format(
        minutes,
        seconds,
    )
}