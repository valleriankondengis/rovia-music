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
import com.rovia.music.playback.PlaybackPipelineStore
import com.rovia.music.playback.decoder.DecoderRegistry
import com.rovia.music.playback.PipelineInfo
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

private data class PipelineSection(
    val title: String,
    val stages: List<PipelineStage>,
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

    val decoderInfo by PlaybackDecoderStore.decoderInfo
        .collectAsStateWithLifecycle()
    val decoderFailure by PlaybackDecoderStore.lastFailure
        .collectAsStateWithLifecycle()
    val pipeline by PlaybackPipelineStore.pipeline
        .collectAsStateWithLifecycle()
    val decoderValue = when {
        decoderFailure != null ->
            stringResource(R.string.pipeline_decoder_unsupported)
        decoderInfo != null -> decoderInfo!!.backendDisplayName
        else -> DecoderRegistry.defaultBackendDisplayName()
    }

    val decodedBits = decoderInfo?.bitsPerSample?.takeIf { it > 0 }
        ?: track.bitsPerSample
    val decodedRate = decoderInfo?.sampleRateHz?.takeIf { it > 0 }
        ?: track.sampleRateHz
    val decodedChannels = decoderInfo?.channels?.takeIf { it > 0 }
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

    val sourceSection = PipelineSection(
        title = stringResource(R.string.pipeline_section_source),
        stages = listOf(
            PipelineStage(
                label = stringResource(R.string.pipeline_title),
                value = titleValue,
            ),
            PipelineStage(
                label = stringResource(R.string.pipeline_artist),
                value = artistValue,
            ),
            PipelineStage(
                label = stringResource(R.string.pipeline_size),
                value = track.fileSizeBytes?.takeIf { it > 0 }?.let(::formatPipelineFileSize)
                    ?: stringResource(R.string.pipeline_unknown),
            ),
            PipelineStage(
                label = stringResource(R.string.pipeline_format),
                value = sourceValue.takeIf { it.isNotBlank() }
                    ?: stringResource(R.string.pipeline_unknown),
            ),
        ),
    )

    val decoderSection = PipelineSection(
        title = stringResource(R.string.pipeline_section_decoder),
        stages = buildList {
            add(
                PipelineStage(
                    label = stringResource(R.string.pipeline_decoder),
                    value = decoderValue,
                ),
            )
            if (decoderFailure != null) {
                add(
                    PipelineStage(
                        label = stringResource(R.string.pipeline_decoder_failure),
                        value = decoderFailure!!,
                    ),
                )
            } else if (decoderInfo != null) {
                val info = decoderInfo!!
                add(
                    PipelineStage(
                        label = stringResource(R.string.pipeline_codec),
                        value = info.codec.takeIf(String::isNotBlank)
                            ?: stringResource(R.string.pipeline_unknown),
                    ),
                )
                add(
                    PipelineStage(
                        label = stringResource(R.string.pipeline_decoded_pcm),
                        value = listOfNotNull(
                            info.sampleRateHz.takeIf { it > 0 }?.let(::formatPipelineSampleRate),
                            info.bitsPerSample.takeIf { it > 0 }?.let { "$it-bit" },
                            info.channels.takeIf { it > 0 }?.let { "$it ch" },
                            info.durationUs.takeIf { it > 0 }?.let(::formatPipelineDuration),
                            if (info.lossless) stringResource(R.string.pipeline_lossless) else null,
                        ).joinToString(" · ").takeIf { it.isNotBlank() }
                            ?: stringResource(R.string.pipeline_unknown),
                    ),
                )
            }
        },
    )

    val outputSection = PipelineSection(
        title = stringResource(R.string.pipeline_section_output),
        stages = buildOutputStages(
            outputMode = outputMode,
            pipeline = pipeline,
            decodedBits = decodedBits,
            decodedChannels = decodedChannels,
        ),
    )

    val receivedValue = when (outputMode) {
        AudioOutputMode.OFF -> {
            val rate = pipeline?.offOutputSampleRateHz
            if (rate != null) {
                "${formatPipelineSampleRate(rate)} (${stringResource(R.string.pipeline_mixer_output)})"
            } else {
                stringResource(R.string.pipeline_received_mixer)
            }
        }
        AudioOutputMode.NATIVE_A14 -> {
            pipeline?.a14SelectedFormat
                ?: stringResource(R.string.pipeline_output_a14)
        }
        AudioOutputMode.DIRECT_USB -> {
            val rate = pipeline?.usbNegotiatedRateHz
            val bits = pipeline?.usbNegotiatedBits
            val channels = pipeline?.usbNegotiatedChannels
            if (rate != null && bits != null && channels != null) {
                "${formatPipelineSampleRate(rate)} · $bits-bit · $channels ch"
            } else {
                sourceSummary.takeIf { it.isNotBlank() }
                    ?: stringResource(R.string.pipeline_unknown)
            }
        }
        AudioOutputMode.DAP -> {
            val rate = pipeline?.dapVerifiedRateHz
            val bits = pipeline?.dapVerifiedBits
            val channels = pipeline?.dapVerifiedChannels
            if (rate != null && bits != null && channels != null) {
                "${formatPipelineSampleRate(rate)} · $bits-bit · $channels ch"
            } else {
                sourceSummary.takeIf { it.isNotBlank() }
                    ?: stringResource(R.string.pipeline_unknown)
            }
        }
    }

    val dacSection = PipelineSection(
        title = stringResource(R.string.pipeline_section_dac),
        stages = listOf(
            PipelineStage(
                label = stringResource(R.string.pipeline_received),
                value = if (bitPerfect) receivedValue
                else stringResource(R.string.pipeline_received_mixer),
            ),
            PipelineStage(
                label = stringResource(R.string.pipeline_device),
                value = dacName ?: stringResource(R.string.pipeline_device_phone),
            ),
            PipelineStage(
                label = stringResource(R.string.pipeline_status),
                value = stringResource(
                    if (bitPerfect) R.string.pipeline_bit_perfect
                    else R.string.pipeline_system_mixer,
                ),
            ),
        ),
    )

    val sections = listOf(sourceSection, decoderSection, outputSection, dacSection)

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
            sections.forEach { section ->
                Text(
                    text = section.title,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            start = PIPELINE_HORIZONTAL_PADDING,
                            end = PIPELINE_HORIZONTAL_PADDING,
                            top = 12.dp,
                            bottom = 6.dp,
                        ),
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = PIPELINE_HORIZONTAL_PADDING)
                        .clip(MaterialTheme.shapes.extraLarge),
                    verticalArrangement = Arrangement.spacedBy(PIPELINE_VERTICAL_SPACING),
                ) {
                    section.stages.forEachIndexed { index, stage ->
                        SegmentedListItem(
                            modifier = Modifier.fillMaxWidth(),
                            shapes = ListItemDefaults.segmentedShapes(
                                index = index,
                                count = section.stages.size,
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
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun buildOutputStages(
    outputMode: AudioOutputMode,
    pipeline: PipelineInfo?,
    decodedBits: Int?,
    decodedChannels: Int?,
): List<PipelineStage> {
    val unknown = stringResource(R.string.pipeline_unknown)
    val p = pipeline
    return when (outputMode) {
        AudioOutputMode.OFF -> buildList {
            add(
                PipelineStage(
                    label = stringResource(R.string.pipeline_output),
                    value = stringResource(R.string.pipeline_output_mixer),
                ),
            )
            val downsampling = p?.offDownsamplerActive == true
            add(
                PipelineStage(
                    label = stringResource(R.string.pipeline_resample),
                    value = if (downsampling) {
                        val from = p.offDownsampledFromHz
                        val to = p.offOutputSampleRateHz
                        if (from != null && to != null) {
                            "${formatPipelineSampleRate(from)} → ${formatPipelineSampleRate(to)} (PcmDownsampler)"
                        } else {
                            stringResource(R.string.pipeline_resample_active)
                        }
                    } else {
                        stringResource(R.string.pipeline_resample_none)
                    },
                ),
            )
            add(
                PipelineStage(
                    label = stringResource(R.string.pipeline_audiosink),
                    value = stringResource(R.string.pipeline_audiosink_unused),
                ),
            )
            val offRate = p?.offOutputSampleRateHz
            add(
                PipelineStage(
                    label = stringResource(R.string.pipeline_audiotrack),
                    value = if (offRate != null) {
                        listOfNotNull(
                            formatPipelineSampleRate(offRate),
                            decodedBits?.takeIf { it > 0 }?.let { "$it-bit" },
                            decodedChannels?.takeIf { it > 0 }?.let { "$it ch" },
                            p.offBufferBytes?.let { "buffer ${formatPipelineFileSize(it.toLong())}" },
                            p.offFrameCount?.let { "$it frames" },
                        ).joinToString(" · ")
                    } else {
                        unknown
                    },
                ),
            )
            add(
                PipelineStage(
                    label = stringResource(R.string.pipeline_audioflinger),
                    value = stringResource(R.string.pipeline_mixer_deep_buffer),
                ),
            )
        }
        AudioOutputMode.NATIVE_A14 -> buildList {
            add(
                PipelineStage(
                    label = stringResource(R.string.pipeline_output),
                    value = stringResource(R.string.pipeline_output_a14),
                ),
            )
            add(
                PipelineStage(
                    label = stringResource(R.string.pipeline_mixer_attribute),
                    value = p?.a14SelectedFormat ?: unknown,
                ),
            )
            add(
                PipelineStage(
                    label = stringResource(R.string.pipeline_audioflinger),
                    value = stringResource(R.string.pipeline_audioflinger_a14),
                ),
            )
            add(
                PipelineStage(
                    label = stringResource(R.string.pipeline_device),
                    value = p?.a14DeviceName?.takeIf(String::isNotBlank) ?: unknown,
                ),
            )
            add(
                PipelineStage(
                    label = stringResource(R.string.pipeline_route),
                    value = stringResource(
                        if (p?.a14RouteVerified == true) R.string.pipeline_route_verified
                        else R.string.pipeline_route_selected,
                    ),
                ),
            )
            add(
                PipelineStage(
                    label = stringResource(R.string.pipeline_audiosink),
                    value = stringResource(R.string.pipeline_audiosink_unused),
                ),
            )
            add(
                PipelineStage(
                    label = stringResource(R.string.pipeline_audiotrack),
                    value = p?.a14SelectedFormat ?: unknown,
                ),
            )
        }
        AudioOutputMode.DIRECT_USB -> buildList {
            add(
                PipelineStage(
                    label = stringResource(R.string.pipeline_output),
                    value = stringResource(R.string.pipeline_output_direct_usb),
                ),
            )
            add(
                PipelineStage(
                    label = stringResource(R.string.pipeline_usb_device),
                    value = listOfNotNull(
                        p?.usbDeviceName?.takeIf(String::isNotBlank),
                        p?.usbManufacturer?.takeIf(String::isNotBlank),
                        p?.usbVendorId?.let { "VID $it" },
                        p?.usbProductId?.let { "PID $it" },
                    ).joinToString(" · ").takeIf { it.isNotBlank() } ?: unknown,
                ),
            )
            add(
                PipelineStage(
                    label = stringResource(R.string.pipeline_uac),
                    value = p?.usbUacVersion ?: unknown,
                ),
            )
            val usbRate = p?.usbNegotiatedRateHz
            add(
                PipelineStage(
                    label = stringResource(R.string.pipeline_clock),
                    value = if (usbRate != null) {
                        "${formatPipelineSampleRate(usbRate)} (SET_CUR, ${stringResource(R.string.pipeline_verified)})"
                    } else {
                        unknown
                    },
                ),
            )
            add(
                PipelineStage(
                    label = stringResource(R.string.pipeline_isochronous),
                    value = stringResource(R.string.pipeline_isochronous_detail),
                ),
            )
            add(
                PipelineStage(
                    label = stringResource(R.string.pipeline_audiosink),
                    value = stringResource(R.string.pipeline_audioflinger_bypassed),
                ),
            )
            add(
                PipelineStage(
                    label = stringResource(R.string.pipeline_audioflinger),
                    value = stringResource(R.string.pipeline_audioflinger_bypassed),
                ),
            )
            add(
                PipelineStage(
                    label = stringResource(R.string.pipeline_negotiated_usb),
                    value = if (usbRate != null) {
                        listOfNotNull(
                            formatPipelineSampleRate(usbRate),
                            p.usbNegotiatedBits?.let { "$it-bit" },
                            p.usbNegotiatedChannels?.let { "$it ch" },
                        ).joinToString(" · ")
                    } else {
                        unknown
                    },
                ),
            )
            add(
                PipelineStage(
                    label = stringResource(R.string.pipeline_dac_volume),
                    value = if (p != null && p.dacVolumeAvailable) {
                        val min = p.dacVolumeMinDb
                        val max = p.dacVolumeMaxDb
                        val current = p.dacVolumeCurrentDb
                        listOfNotNull(
                            if (min != null && max != null) {
                                stringResource(
                                    R.string.pipeline_dac_volume_range,
                                    formatPipelineDb(min),
                                    formatPipelineDb(max),
                                )
                            } else {
                                null
                            },
                            current?.let {
                                stringResource(
                                    R.string.pipeline_dac_volume_current,
                                    formatPipelineDb(it),
                                )
                            },
                            stringResource(
                                if (p.fixedVolumeAllowed) R.string.pipeline_fixed_volume_on
                                else R.string.pipeline_fixed_volume_off,
                            ),
                        ).joinToString(" · ").takeIf { it.isNotBlank() } ?: unknown
                    } else {
                        stringResource(R.string.pipeline_dac_volume_none)
                    },
                ),
            )
        }
        AudioOutputMode.DAP -> buildList {
            add(
                PipelineStage(
                    label = stringResource(R.string.pipeline_output),
                    value = stringResource(R.string.pipeline_output_dap),
                ),
            )
            val dapRate = p?.dapVerifiedRateHz
            add(
                PipelineStage(
                    label = stringResource(R.string.pipeline_aaudio),
                    value = if (dapRate != null) {
                        listOfNotNull(
                            formatPipelineSampleRate(dapRate),
                            p.dapVerifiedBits?.let { "$it-bit" },
                            p.dapVerifiedChannels?.let { "$it ch" },
                        ).joinToString(" · ")
                    } else {
                        unknown
                    },
                ),
            )
            add(
                PipelineStage(
                    label = stringResource(R.string.pipeline_audiosink),
                    value = stringResource(R.string.pipeline_audiosink_unused),
                ),
            )
            add(
                PipelineStage(
                    label = stringResource(R.string.pipeline_audioflinger),
                    value = stringResource(R.string.pipeline_audioflinger_dap),
                ),
            )
            add(
                PipelineStage(
                    label = stringResource(R.string.pipeline_sharing),
                    value = stringResource(R.string.pipeline_sharing_detail),
                ),
            )
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

private fun formatPipelineFileSize(bytes: Long): String = when {
    bytes >= 1_000_000 -> "${formatPipelineDecimal(bytes / 1_000_000.0, 1)} MB"
    bytes >= 1_000 -> "${formatPipelineDecimal(bytes / 1_000.0, 1)} KB"
    bytes > 0 -> "$bytes B"
    else -> ""
}

private fun formatPipelineDuration(durationUs: Long): String {
    val totalSeconds = durationUs / 1_000_000L
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "%d:%02d".format(minutes, seconds)
}

private fun formatPipelineDb(value: Double): String =
    formatPipelineDecimal(value, 1)
