package com.rovia.music.feature.player

import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbManager
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import androidx.activity.compose.PredictiveBackHandler
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TextButton
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rovia.music.playback.bitperfect.AudioOutputMode
import com.rovia.music.playback.bitperfect.AudioOutputModeStoreProvider
import com.rovia.music.playback.bitperfect.UsbAudioClass
import com.rovia.music.playback.bitperfect.direct.DirectUsbControllerProvider
import com.rovia.music.uniffi.DacVolumeDto
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private const val MEDIA_STREAM = AudioManager.STREAM_MUSIC
private val AUDIO_OUTPUT_BUTTON_MAX_WIDTH = 280.dp

private data class AudioOutputSnapshot(
    val usbDacName: String?,
    val usbDacUacLabel: String?,
    val isUsbHeadsetConnected: Boolean,
    val usbHeadsetName: String?,
    val isBluetoothConnected: Boolean,
    val bluetoothDeviceName: String?,
    val isWiredConnected: Boolean,
    val wiredDeviceName: String?,
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
    externalMode: Boolean = false,
) {
    val context = LocalContext.current.applicationContext
    val audioManager = remember(context) {
        context.getSystemService(AudioManager::class.java)
    }
    val usbManager = remember(context) {
        context.getSystemService(UsbManager::class.java)
    }
    var outputState by remember(audioManager, usbManager) {
        mutableStateOf(readAudioOutputSnapshot(audioManager, usbManager))
    }
    var isBottomSheetVisible by remember {
        mutableStateOf(false)
    }
    val outputModeStore = remember(context) { AudioOutputModeStoreProvider.get(context) }
    val storedOutputMode by outputModeStore.mode.collectAsStateWithLifecycle()
    val outputMode = if (externalMode) AudioOutputMode.OFF else storedOutputMode
    val directUsbController = remember(context) { DirectUsbControllerProvider.get(context) }
    var dacVolume by remember { mutableStateOf<DacVolumeDto?>(null) }
    val coroutineScope = rememberCoroutineScope()

    val colorScheme = MaterialTheme.colorScheme
    val buttonBluetoothIcon = painterResource(R.drawable.ic_bluetooth)
    val buttonUsbIcon = painterResource(R.drawable.ic_usb)
    val buttonLocalDeviceIcon = painterResource(R.drawable.ic_smartphone)
    val buttonContentDescription = stringResource(R.string.audio_output_button_content_description)
    val localDeviceLabel = stringResource(R.string.audio_output_local_device)
    val bluetoothAudioLabel = stringResource(R.string.audio_output_bluetooth_audio)
    val sheetTitle = stringResource(R.string.audio_output_sheet_title)
    val mediaVolumeLabel = stringResource(R.string.audio_output_media_volume)
    val sliderDescription = stringResource(R.string.audio_output_volume_slider_description)
    val fixedVolumeMessage = stringResource(R.string.audio_output_volume_fixed)
    val dacVolumeLabel = stringResource(R.string.audio_output_dac_volume)
    val dacVolumeUnavailableMessage = stringResource(R.string.audio_output_dac_volume_unavailable)

    val wiredAudioLabel = stringResource(R.string.audio_output_wired_audio)
    val wiredHeadsetLabel = stringResource(R.string.audio_output_wired_headset)
    val outputLabel = outputState.usbDacName
        ?: if (outputState.isUsbHeadsetConnected) {
            outputState.usbHeadsetName
                ?.trim()
                ?.takeIf(String::isNotEmpty)
                ?: wiredHeadsetLabel
        } else if (outputState.isBluetoothConnected) {
            outputState.bluetoothDeviceName
                ?.trim()
                ?.takeIf(String::isNotEmpty)
                ?: bluetoothAudioLabel
        } else if (outputState.isWiredConnected) {
            outputState.wiredDeviceName
                ?.trim()
                ?.takeIf(String::isNotEmpty)
                ?: wiredAudioLabel
        } else {
            localDeviceLabel
        }
    val outputIcon = when {
        outputState.usbDacName != null || outputState.isUsbHeadsetConnected ||
            outputState.isWiredConnected -> buttonUsbIcon
        outputState.isBluetoothConnected -> buttonBluetoothIcon
        else -> buttonLocalDeviceIcon
    }
    val buttonDescription = "$buttonContentDescription, $outputLabel"
    val buttonContentColor = Color.White
    val sheetIconColor = colorScheme.onSurfaceVariant
    val volumeAccentColor = colorScheme.primary

    DisposableEffect(audioManager, usbManager) {
        val callback = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
                outputState = readAudioOutputSnapshot(audioManager, usbManager)
            }

            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
                outputState = readAudioOutputSnapshot(audioManager, usbManager)
            }
        }
        audioManager.registerAudioDeviceCallback(callback, Handler(Looper.getMainLooper()))
        onDispose {
            audioManager.unregisterAudioDeviceCallback(callback)
        }
    }

    LaunchedEffect(isBottomSheetVisible, audioManager, usbManager, outputMode) {
        outputState = readAudioOutputSnapshot(audioManager, usbManager)
        if (outputMode == AudioOutputMode.DIRECT_USB) {
            dacVolume = directUsbController.playbackDacVolume()
        }
        if (isBottomSheetVisible) {
            while (true) {
                delay(200)
                outputState = readAudioOutputSnapshot(audioManager, usbManager)
                if (outputMode == AudioOutputMode.DIRECT_USB) {
                    dacVolume = directUsbController.playbackDacVolume()
                }
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
        TextButton(
            onClick = { isBottomSheetVisible = true },
            modifier = Modifier
                .widthIn(max = AUDIO_OUTPUT_BUTTON_MAX_WIDTH)
                .height(40.dp)
                .semantics {
                    contentDescription = buttonDescription
                },
            enabled = enabled,
            shape = RectangleShape,
            colors = ButtonDefaults.textButtonColors(
                contentColor = buttonContentColor,
            ),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    painter = outputIcon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = outputLabel,
                    modifier = Modifier
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
        val sheetUsbIcon = painterResource(R.drawable.ic_usb)
        val sheetLocalDeviceIcon = painterResource(R.drawable.ic_smartphone)
        val sheetDeviceIcon = when {
            outputState.usbDacName != null || outputState.isUsbHeadsetConnected ||
                outputState.isWiredConnected -> sheetUsbIcon
            outputState.isBluetoothConnected -> sheetBluetoothIcon
            else -> sheetLocalDeviceIcon
        }
        val sheetDeviceSubtitle = when {
            outputState.usbDacName != null -> {
                // In Direct USB mode the live session knows the
                // configuration the DAC is operating in; the snapshot
                // label is the fallback when no session is retained.
                if (outputMode == AudioOutputMode.DIRECT_USB) {
                    directUsbController.activeDeviceUacVersion()
                        ?: outputState.usbDacUacLabel
                } else {
                    outputState.usbDacUacLabel
                }
            }
            outputState.isUsbHeadsetConnected -> wiredHeadsetLabel
            outputState.isBluetoothConnected -> bluetoothAudioLabel
            outputState.isWiredConnected -> wiredAudioLabel
            else -> null
        }

        ModalBottomSheet(
            modifier = Modifier.fillMaxHeight(),
            onDismissRequest = { isBottomSheetVisible = false },
            sheetState = sheetState,
            scrimColor = Color.Transparent,
        ) {
            val sheetBackScope = rememberCoroutineScope()
            PredictiveBackHandler(enabled = sheetState.currentValue == SheetValue.Expanded) { progress ->
                progress.collect { }
                sheetBackScope.launch { sheetState.hide() }.invokeOnCompletion {
                    if (!sheetState.isVisible) isBottomSheetVisible = false
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(horizontal = 18.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Text(
                    text = sheetTitle,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = colorScheme.onSurface,
                )

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.extraLarge,
                    color = colorScheme.surfaceBright,
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
                            painter = sheetDeviceIcon,
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
                            sheetDeviceSubtitle?.let { subtitle ->
                                Text(
                                    text = subtitle,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = if (outputMode == AudioOutputMode.DIRECT_USB) {
                            dacVolumeLabel
                        } else {
                            mediaVolumeLabel
                        },
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = colorScheme.onSurface,
                    )

                    if (outputMode == AudioOutputMode.DIRECT_USB) {
                        val volume = dacVolume
                        if (volume != null && volume.available) {
                            val dacRangeDb = volume.maxDb - volume.minDb
                            val dacFraction = if (dacRangeDb > 0.0) {
                                ((volume.currentDb - volume.minDb) / dacRangeDb)
                                    .toFloat()
                                    .coerceIn(0f, 1f)
                            } else {
                                0f
                            }
                            val dacSliderState = key(volume.minDb, volume.maxDb) {
                                rememberSliderState(
                                    value = dacFraction,
                                    steps = (dacRangeDb.toInt() - 1).coerceAtLeast(0),
                                    trackRange = 0f..1f,
                                )
                            }
                            LaunchedEffect(volume.currentDb, dacSliderState.isDragging) {
                                if (!dacSliderState.isDragging) {
                                    dacSliderState.value = dacFraction
                                }
                            }
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = MaterialTheme.shapes.extraLarge,
                                color = colorScheme.surfaceBright,
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
                                        state = dacSliderState,
                                        onValueChange = { value ->
                                            dacSliderState.value = value
                                            val db = volume.minDb + value * dacRangeDb
                                            coroutineScope.launch {
                                                runCatching {
                                                    directUsbController.playbackSetDacVolume(db)
                                                }.onSuccess { updated -> dacVolume = updated }
                                                    .onFailure { }
                                            }
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .semantics {
                                                contentDescription = sliderDescription
                                            },
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
                            Text(
                                text = stringResource(R.string.audio_output_dac_db, volume.currentDb),
                                style = MaterialTheme.typography.labelMedium,
                                color = colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.End,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        } else {
                            Text(
                                text = dacVolumeUnavailableMessage,
                                style = MaterialTheme.typography.bodyMedium,
                                color = colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    if (outputMode != AudioOutputMode.DIRECT_USB) {
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
                        color = colorScheme.surfaceBright,
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
                                        }.onFailure { }
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
}

private fun readAudioOutputSnapshot(
    audioManager: AudioManager,
    usbManager: UsbManager?,
): AudioOutputSnapshot {
    val outputDevices = runCatching {
        audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
    }.getOrDefault(emptyArray())

    val bluetoothOutputs = outputDevices.filter { it.isBluetoothAudioOutput() }
    val bluetoothName = bluetoothOutputs.firstNotNullOfOrNull { device ->
        runCatching {
            device.productName?.toString()?.trim()?.takeIf(String::isNotEmpty)
        }.getOrNull()
    }

    val usbOutputs = outputDevices.filter { device ->
        device.isSink && device.type == AudioDeviceInfo.TYPE_USB_DEVICE
    }
    val usbHeadsetOutputs = outputDevices.filter { device ->
        device.isSink && device.type == AudioDeviceInfo.TYPE_USB_HEADSET
    }
    val usbHeadsetName = usbHeadsetOutputs.firstNotNullOfOrNull { device ->
        runCatching {
            device.productName?.toString()?.trim()?.takeIf(String::isNotEmpty)
        }.getOrNull()
    }
    val usbDacName = usbOutputs.firstNotNullOfOrNull { device ->
        runCatching {
            device.productName?.toString()?.trim()?.takeIf(String::isNotEmpty)
        }.getOrNull()
    } ?: detectUsbAudioDeviceName(usbManager)
    val usbDacUacLabel = if (usbOutputs.isNotEmpty() || usbDacName != null) {
        detectUsbAudioClassLabel(usbManager, usbDacName)
    } else {
        null
    }

    val wiredOutputs = outputDevices.filter { device ->
        device.isSink &&
            (device.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                device.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES)
    }
    val wiredName = wiredOutputs.firstNotNullOfOrNull { device ->
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
        usbDacName = usbDacName,
        usbDacUacLabel = usbDacUacLabel,
        isUsbHeadsetConnected = usbHeadsetOutputs.isNotEmpty(),
        usbHeadsetName = usbHeadsetName,
        isBluetoothConnected = bluetoothOutputs.isNotEmpty(),
        bluetoothDeviceName = bluetoothName,
        isWiredConnected = wiredOutputs.isNotEmpty(),
        wiredDeviceName = wiredName,
        volume = volume,
        maxVolume = maxVolume,
        isVolumeFixed = runCatching { audioManager.isVolumeFixed }.getOrDefault(false),
    )
}

private fun detectUsbAudioDeviceName(usbManager: UsbManager?): String? {
    val manager = usbManager ?: return null
    return runCatching {
        manager.deviceList.values.firstNotNullOfOrNull { device ->
            val hasAudioControl = (0 until device.configurationCount).any { configIndex ->
                val configuration = device.getConfiguration(configIndex)
                (0 until configuration.interfaceCount).any { interfaceIndex ->
                    val usbInterface = configuration.getInterface(interfaceIndex)
                    usbInterface.interfaceClass == UsbConstants.USB_CLASS_AUDIO &&
                        usbInterface.interfaceSubclass == 1
                }
            }
            if (hasAudioControl) {
                device.productName?.trim()?.takeIf(String::isNotEmpty)
            } else {
                null
            }
        }
    }.getOrNull()
}

private fun detectUsbAudioClassLabel(usbManager: UsbManager?, preferredName: String?): String? {
    val manager = usbManager ?: return null
    // The name the output snapshot already read carries an explicit
    // mode marker on devices that declare their mode in the product
    // string; that marker names the operating mode directly.
    UsbAudioClass.labelFromProductName(preferredName)?.let { return it }
    return runCatching {
        val devices = manager.deviceList.values.toList()
        val ordered = devices.sortedBy { device ->
            if (preferredName != null && device.productName == preferredName) 0 else 1
        }
        for (device in ordered) {
            if (!UsbAudioClass.hasAudioFunction(device)) continue
            // The first audio device in the ordered list is the DAC the
            // snapshot named; its label stands even when it is null
            // (its configurations disagree on the class version),
            // rather than borrowing another device's label.
            return@runCatching UsbAudioClass.labelForDevice(device)
        }
        null
    }.getOrNull()
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