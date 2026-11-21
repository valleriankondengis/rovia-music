package com.rovia.music.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rovia.music.R
import com.rovia.music.data.mediastore.LocalAudioTrack
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rovia.music.playback.PlaybackDecoderStore
import com.rovia.music.playback.bitperfect.AudioOutputMode
import kotlin.math.roundToLong

private val PIPELINE_HORIZONTAL_PADDING = 18.dp
private val PIPELINE_VERTICAL_SPACING = 2.dp
private val PIPELINE_CONTENT_PADDING = androidx.compose.foundation.layout.PaddingValues(
    horizontal = 16.dp,
    vertical = 14.dp,
)

private data class PipelineStage(
    val label: String,
    val value: String,
)

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
)
@Composable
fun AudioPipelineBottomSheet(
    track: LocalAudioTrack,
    outputMode: AudioOutputMode,
    dacName: String?,
    onDismissRequest: () -> Unit,
) {
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(
            SheetValue.Hidden,
            SheetValue.PartiallyExpanded,
            SheetValue.Expanded,
        ),
    )

    val bitPerfect = outputMode != AudioOutputMode.OFF

    // The decoder is always the registered backend (Symphonia,
    // id "symphonia-rust"); there is no platform-decoder branch.
    val decoderInfo by PlaybackDecoderStore.decoderInfo
        .collectAsStateWithLifecycle()
    val decoderFailure by PlaybackDecoderStore.lastFailure
        .collectAsStateWithLifecycle()
    val decoderValue = when {
        decoderFailure != null ->
            stringResource(R.string.pipeline_decoder_unsupported)
        decoderInfo != null -> decoderInfo!!.decoderLabel()
        else -> stringResource(R.string.pipeline_decoder_symphonia)
    }

    // Source format comes from the decoder probe when available; the
    // library track no longer carries sample rate / bit depth (see
    // MediaStoreAudioRepository.readEmbeddedMetadata).
    val decodedBits = decoderInfo?.bitsPerSample?.takeIf { it > 0 }
        ?: track.bitsPerSample
    val decodedRate = decoderInfo?.sampleRateHz?.takeIf { it > 0 }
        ?: track.sampleRateHz
    val sourceSummary = listOfNotNull(
        formatPipelineAudioType(track.mimeType),
        decodedBits?.takeIf { it > 0 }?.let { "$it-bit" },
        decodedRate?.takeIf { it > 0 }?.let(::formatPipelineSampleRate),
    ).joinToString(" · ")
    val sourceValue = listOfNotNull(
        sourceSummary.takeIf { it.isNotBlank() },
        track.bitrateBps?.takeIf { it > 0 }?.let(::formatPipelineBitrate),
    ).joinToString(" · ")

    val titleValue = track.title
        ?.takeIf(String::isNotBlank)
        ?: stringResource(R.string.player_unknown_track)
    val artistValue = track.artist
        ?.takeIf(String::isNotBlank)
        ?: stringResource(R.string.player_unknown_artist)
    val stages = listOf(
        PipelineStage(
            label = stringResource(R.string.pipeline_title),
            value = titleValue,
        ),
        PipelineStage(
            label = stringResource(R.string.pipeline_artist),
            value = artistValue,
        ),
        PipelineStage(
            label = stringResource(R.string.pipeline_status),
            value = stringResource(
                if (bitPerfect) R.string.pipeline_bit_perfect
                else R.string.pipeline_system_mixer,
            ),
        ),
        PipelineStage(
            label = stringResource(R.string.pipeline_source),
            value = sourceValue,
        ),
        PipelineStage(
            label = stringResource(R.string.pipeline_decoder),
            value = decoderValue,
        ),
        PipelineStage(
            label = stringResource(R.string.pipeline_output),
            value = stringResource(
                when (outputMode) {
                    AudioOutputMode.OFF -> R.string.pipeline_output_mixer
                    AudioOutputMode.NATIVE_A14 -> R.string.pipeline_output_a14
                    AudioOutputMode.DIRECT_USB -> R.string.pipeline_output_direct_usb
                    AudioOutputMode.DAP -> R.string.pipeline_output_dap
                },
            ),
        ),
        PipelineStage(
            label = stringResource(R.string.pipeline_device),
            value = dacName ?: stringResource(R.string.pipeline_device_phone),
        ),
        PipelineStage(
            label = stringResource(R.string.pipeline_received),
            value = if (bitPerfect) sourceSummary
            else stringResource(R.string.pipeline_received_mixer),
        ),
    )

    ModalBottomSheet(
        modifier = Modifier.fillMaxHeight(),
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        scrimColor = Color.Transparent,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(vertical = 8.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = PIPELINE_HORIZONTAL_PADDING)
                    .clip(MaterialTheme.shapes.extraLarge),
                verticalArrangement = Arrangement.spacedBy(PIPELINE_VERTICAL_SPACING),
            ) {
                stages.forEachIndexed { index, stage ->
                    SegmentedListItem(
                        modifier = Modifier.fillMaxWidth(),
                        shapes = ListItemDefaults.segmentedShapes(
                            index = index,
                            count = stages.size,
                        ),
                        colors = ListItemDefaults.segmentedColors(
                            containerColor = MaterialTheme.colorScheme.surfaceBright,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                        ),
                        contentPadding = PIPELINE_CONTENT_PADDING,
                        verticalAlignment = Alignment.CenterVertically,
                        content = {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text(
                                    text = stage.label,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = stage.value,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        },
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

private fun formatPipelineAudioType(mimeType: String?): String? = mimeType
    ?.takeIf(String::isNotBlank)
    ?.substringAfter(delimiter = "/", missingDelimiterValue = mimeType)
    ?.removePrefix("x-")
    ?.uppercase()
    ?.takeIf(String::isNotBlank)

private fun formatPipelineSampleRate(sampleRateHz: Int): String =
    if (sampleRateHz % 1_000 == 0) {
        "${sampleRateHz / 1_000} kHz"
    } else {
        "${formatPipelineDecimal(sampleRateHz / 1_000.0, 1)} kHz"
    }

private fun formatPipelineBitrate(bitrateBps: Int): String = when {
    bitrateBps >= 1_000_000 -> "${formatPipelineDecimal(bitrateBps / 1_000_000.0, 2)} Mbps"
    bitrateBps >= 1_000 -> "${(bitrateBps + 500) / 1_000} kbps"
    bitrateBps > 0 -> "$bitrateBps bps"
    else -> ""
}

private fun formatPipelineDecimal(value: Double, decimalPlaces: Int): String {
    val scale = when (decimalPlaces) {
        1 -> 10L
        else -> 100L
    }
    val rounded = (value * scale).roundToLong()
    val fraction = (rounded % scale).toString().padStart(decimalPlaces, '0')
    return "${rounded / scale}.$fraction"
}
