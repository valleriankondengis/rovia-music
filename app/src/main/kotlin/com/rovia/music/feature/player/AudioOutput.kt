package com.rovia.music.feature.player

import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.rememberSliderState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rovia.music.R
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private const val MEDIA_STREAM = AudioManager.STREAM_MUSIC
private val AUDIO_OUTPUT_BUTTON_WIDTH = 128.dp

private data class AudioOutputSnapshot(
    val isBluetoothConnected: Boolean,
    val bluetoothDeviceName: String?,
    val volume: Int,
    val maxVolume: Int,
    val isVolumeFixed: Boolean,
) {
    val volumeFraction: Float
        get() = if (maxVolume > 0) volume.toFloat() / maxVolume else 0f
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AudioOutputIndicator(
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current.applicationContext
    val audioManager = remember(context) {
        context.getSystemService(AudioManager::class.java)
    }
    var outputState by remember(audioManager) {
        mutableStateOf(readAudioOutputSnapshot(audioManager))
    }
    var isBottomSheetVisible by remember {
        mutableStateOf(false)
    }

    val colorScheme = MaterialTheme.colorScheme
    val buttonBluetoothIcon = painterResource(R.drawable.ic_bluetooth)
    val buttonLocalDeviceIcon = painterResource(R.drawable.ic_smartphone)
    val buttonContentDescription = stringResource(R.string.audio_output_button_content_description)
    val localDeviceLabel = stringResource(R.string.audio_output_local_device)
    val bluetoothAudioLabel = stringResource(R.string.audio_output_bluetooth_audio)
    val sheetTitle = stringResource(R.string.audio_output_sheet_title)
    val mediaVolumeLabel = stringResource(R.string.audio_output_media_volume)
    val sliderDescription = stringResource(R.string.audio_output_volume_slider_description)
    val fixedVolumeMessage = stringResource(R.string.audio_output_volume_fixed)

    val outputLabel = if (!outputState.isBluetoothConnected) {
        localDeviceLabel
    } else {
        outputState.bluetoothDeviceName
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?: bluetoothAudioLabel
    }
    val buttonDescription = "$buttonContentDescription, $outputLabel"
    val buttonContainerColor = colorScheme.surfaceContainerHigh.copy(alpha = 0.92f)
    val buttonContentColor = colorScheme.onSurface
    val sheetIconColor = colorScheme.onSurfaceVariant
    val volumeAccentColor = colorScheme.primary

    DisposableEffect(audioManager) {
        val callback = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
                outputState = readAudioOutputSnapshot(audioManager)
            }

            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
                outputState = readAudioOutputSnapshot(audioManager)
            }
        }
        audioManager.registerAudioDeviceCallback(callback, Handler(Looper.getMainLooper()))
        onDispose {
            audioManager.unregisterAudioDeviceCallback(callback)
        }
    }

    LaunchedEffect(isBottomSheetVisible, audioManager) {
        outputState = readAudioOutputSnapshot(audioManager)
        if (isBottomSheetVisible) {
            while (true) {
                delay(200)
                outputState = readAudioOutputSnapshot(audioManager)
            }
        }
    }

    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(
            SheetValue.Hidden,
            SheetValue.PartiallyExpanded,
            SheetValue.Expanded,
        ),
    )

    if (!enabled) return

    Box(modifier = modifier) {
        FilledTonalButton(
            onClick = { isBottomSheetVisible = true },
            modifier = Modifier
                .width(AUDIO_OUTPUT_BUTTON_WIDTH)
                .height(40.dp)
                .semantics {
                    contentDescription = buttonDescription
                },
            enabled = enabled,
            shape = CircleShape,
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = buttonContainerColor,
                contentColor = buttonContentColor,
            ),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    painter = if (outputState.isBluetoothConnected) buttonBluetoothIcon else buttonLocalDeviceIcon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = outputLabel,
                    modifier = Modifier
                        .weight(1f)
                        .basicMarquee(
                            iterations = Int.MAX_VALUE,
                            initialDelayMillis = 900,
                            repeatDelayMillis = 900,
                            velocity = 35.dp,
                        ),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Clip,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }

    if (isBottomSheetVisible) {
        val sheetBluetoothIcon = painterResource(R.drawable.ic_bluetooth)
        val sheetLocalDeviceIcon = painterResource(R.drawable.ic_smartphone)

        ModalBottomSheet(
            modifier = Modifier.fillMaxHeight(),
            onDismissRequest = { isBottomSheetVisible = false },
            sheetState = sheetState,
            scrimColor = Color.Transparent,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(horizontal = 18.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                Text(
                    text = sheetTitle,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = colorScheme.onSurface,
                )

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.extraLarge,
                    color = colorScheme.surfaceContainerHigh,
                    contentColor = colorScheme.onSurface,
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Icon(
                            painter = if (outputState.isBluetoothConnected) sheetBluetoothIcon else sheetLocalDeviceIcon,
                            contentDescription = null,
                            modifier = Modifier.size(32.dp),
                            tint = sheetIconColor,
                        )
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = outputLabel,
                                style = MaterialTheme.typography.titleMedium,
                                color = colorScheme.onSurface,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (outputState.isBluetoothConnected) {
                                Text(
                                    text = bluetoothAudioLabel,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = mediaVolumeLabel,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = colorScheme.onSurface,
                    )

                    val sliderSteps = (outputState.maxVolume - 1).coerceAtLeast(0)
                    val sliderState = key(outputState.maxVolume) {
                        rememberSliderState(
                            value = outputState.volumeFraction.coerceIn(0f, 1f),
                            steps = sliderSteps,
                            trackRange = 0f..1f,
                        )
                    }

                    LaunchedEffect(outputState.volume, outputState.maxVolume, sliderState.isDragging) {
                        if (!sliderState.isDragging) {
                            sliderState.value = outputState.volumeFraction.coerceIn(0f, 1f)
                        }
                    }

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.extraLarge,
                        color = colorScheme.surfaceContainerHigh,
                        contentColor = colorScheme.onSurface,
                        tonalElevation = 0.dp,
                        shadowElevation = 0.dp,
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            AudioVolumeIcon(
                                isLoud = false,
                                tint = sheetIconColor,
                                modifier = Modifier.size(24.dp),
                            )
                            Slider(
                                state = sliderState,
                                onValueChange = { value ->
                                    sliderState.value = value
                                    if (outputState.maxVolume > 0 && !outputState.isVolumeFixed) {
                                        val volume = (value * outputState.maxVolume)
                                            .roundToInt()
                                            .coerceIn(0, outputState.maxVolume)
                                        runCatching {
                                            audioManager.setStreamVolume(MEDIA_STREAM, volume, 0)
                                        }
                                        outputState = outputState.copy(
                                            volume = runCatching {
                                                audioManager.getStreamVolume(MEDIA_STREAM)
                                            }.getOrDefault(volume).coerceIn(0, outputState.maxVolume),
                                        )
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .semantics {
                                        contentDescription = sliderDescription
                                    },
                                enabled = outputState.maxVolume > 0 && !outputState.isVolumeFixed,
                                colors = SliderDefaults.colors(
                                    thumbColor = volumeAccentColor,
                                    activeTrackColor = volumeAccentColor,
                                ),
                            )
                            AudioVolumeIcon(
                                isLoud = true,
                                tint = sheetIconColor,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }

                    if (outputState.isVolumeFixed) {
                        Text(
                            text = fixedVolumeMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            color = colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Text(
                            text = stringResource(
                                R.string.audio_output_volume_percent,
                                (outputState.volumeFraction * 100f).roundToInt(),
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            color = colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.End,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

private fun readAudioOutputSnapshot(audioManager: AudioManager): AudioOutputSnapshot {
    val bluetoothOutputs = runCatching {
        audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            .filter { it.isBluetoothAudioOutput() }
    }.getOrDefault(emptyList())

    val bluetoothName = bluetoothOutputs.firstNotNullOfOrNull { device ->
        runCatching {
            device.productName?.toString()?.trim()?.takeIf(String::isNotEmpty)
        }.getOrNull()
    }
    val maxVolume = runCatching {
        audioManager.getStreamMaxVolume(MEDIA_STREAM).coerceAtLeast(0)
    }.getOrDefault(0)
    val volume = runCatching {
        audioManager.getStreamVolume(MEDIA_STREAM).coerceIn(0, maxVolume.coerceAtLeast(0))
    }.getOrDefault(0)

    return AudioOutputSnapshot(
        isBluetoothConnected = bluetoothOutputs.isNotEmpty(),
        bluetoothDeviceName = bluetoothName,
        volume = volume,
        maxVolume = maxVolume,
        isVolumeFixed = runCatching { audioManager.isVolumeFixed }.getOrDefault(false),
    )
}

private fun AudioDeviceInfo.isBluetoothAudioOutput(): Boolean =
    when (type) {
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
        AudioDeviceInfo.TYPE_BLE_HEADSET,
        AudioDeviceInfo.TYPE_BLE_SPEAKER,
        AudioDeviceInfo.TYPE_BLE_BROADCAST,
        AudioDeviceInfo.TYPE_HEARING_AID -> isSink
        else -> false
    }

@Composable
private fun AudioVolumeIcon(
    isLoud: Boolean,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val unit = size.minDimension / 24f
        val offsetX = (size.width - 24f * unit) / 2f
        val offsetY = (size.height - 24f * unit) / 2f
        val speaker = Path().apply {
            moveTo(offsetX + 3f * unit, offsetY + 9f * unit)
            lineTo(offsetX + 7f * unit, offsetY + 9f * unit)
            lineTo(offsetX + 12f * unit, offsetY + 5f * unit)
            lineTo(offsetX + 12f * unit, offsetY + 19f * unit)
            lineTo(offsetX + 7f * unit, offsetY + 15f * unit)
            lineTo(offsetX + 3f * unit, offsetY + 15f * unit)
            close()
        }
        drawPath(speaker, tint)
        val stroke = Stroke(width = 1.7f * unit, cap = StrokeCap.Round)
        drawArc(
            color = tint,
            startAngle = -48f,
            sweepAngle = 96f,
            useCenter = false,
            topLeft = Offset(offsetX + 10f * unit, offsetY + 7f * unit),
            size = Size(7f * unit, 10f * unit),
            style = stroke,
        )
        if (isLoud) {
            drawArc(
                color = tint,
                startAngle = -48f,
                sweepAngle = 96f,
                useCenter = false,
                topLeft = Offset(offsetX + 8f * unit, offsetY + 4f * unit),
                size = Size(13f * unit, 16f * unit),
                style = stroke,
            )
        }
    }
}