
@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
)

package com.rovia.music.feature.player

import android.content.res.Configuration
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rovia.music.core.playback.AudioOutputState
import com.rovia.music.core.playback.AudioOutputType
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@Composable
internal fun AudioOutputIndicator(
    state: AudioOutputState?,
    bluetoothIcon: Painter,
    localDeviceIcon: Painter,
    volumeDownIcon: Painter,
    volumeUpIcon: Painter,
    outputButtonContentDescription: String,
    sheetTitle: String,
    localDeviceLabel: String,
    bluetoothAudioLabel: String,
    mediaVolumeLabel: String,
    volumeSliderContentDescription: String,
    fixedVolumeMessage: String,
    onVolumeChange: (Int) -> Unit,
    onRefresh: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    if (state == null) {
        return
    }

    var isBottomSheetVisible by remember {
        mutableStateOf(false)
    }

    val configuration =
        LocalConfiguration.current

    val isLandscape =
        configuration.orientation ==
            Configuration.ORIENTATION_LANDSCAPE

    val isBluetoothConnected =
        state.outputType == AudioOutputType.BLUETOOTH

    val bluetoothName =
        state.deviceName
            ?.trim()
            ?.takeIf { name ->
                name.isNotEmpty()
            }

    val outputLabel =
        if (isBluetoothConnected) {
            bluetoothName ?: bluetoothAudioLabel
        } else {
            localDeviceLabel
        }

    val buttonDescription =
        "$outputButtonContentDescription, $outputLabel"

    /*
     * Refresh the actual system media volume while the sheet
     * is visible, including changes made using physical buttons.
     */
    LaunchedEffect(
        isBottomSheetVisible,
        onRefresh,
    ) {
        if (isBottomSheetVisible) {
            onRefresh()

            while (true) {
                delay(200)
                onRefresh()
            }
        }
    }

    /*
     * Portrait:
     * Keep the output button at a maximum width of 128.dp.
     *
     * Landscape:
     * Use the remaining width after reserving 224.dp for
     * Repeat and Shuffle and 8.dp for spacing between groups.
     *
     * This makes the complete landscape control row fill its
     * available width without pushing Shuffle into overflow.
     */
    BoxWithConstraints(
        modifier = modifier,
    ) {
        val availableOutputWidth =
            (maxWidth - 232.dp)
                .coerceAtLeast(0.dp)

        val outputButtonWidth =
            if (isLandscape) {
                availableOutputWidth
            } else {
                minOf(
                    128.dp,
                    availableOutputWidth,
                )
            }

        val outputLabelMaxWidth =
            (outputButtonWidth - 36.dp)
                .coerceAtLeast(0.dp)

        Surface(
            onClick = {
                isBottomSheetVisible = true
            },
            modifier =
                Modifier
                    .width(outputButtonWidth)
                    .height(64.dp)
                    .semantics {
                        contentDescription =
                            buttonDescription
                    },
            shape =
                RoundedCornerShape(18.dp),
            color =
                MaterialTheme
                    .colorScheme
                    .surfaceContainerHigh,
            contentColor =
                MaterialTheme
                    .colorScheme
                    .onSurface,
            tonalElevation = 1.dp,
            shadowElevation = 0.dp,
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = 6.dp),
                contentAlignment =
                    Alignment.Center,
            ) {
                Row(
                    verticalAlignment =
                        Alignment.CenterVertically,
                    horizontalArrangement =
                        Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        painter =
                            if (isBluetoothConnected) {
                                bluetoothIcon
                            } else {
                                localDeviceIcon
                            },
                        contentDescription = null,
                        modifier =
                            Modifier.size(20.dp),
                        tint =
                            MaterialTheme
                                .colorScheme
                                .onSurfaceVariant,
                    )

                    Text(
                        text = outputLabel,
                        modifier =
                            Modifier
                                .widthIn(
                                    max = outputLabelMaxWidth,
                                )
                                .basicMarquee(
                                    iterations = Int.MAX_VALUE,
                                    initialDelayMillis = 900,
                                    repeatDelayMillis = 900,
                                    velocity = 35.dp,
                                ),
                        style =
                            MaterialTheme
                                .typography
                                .labelMedium,
                        color =
                            MaterialTheme
                                .colorScheme
                                .onSurface,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Clip,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }

    if (isBottomSheetVisible) {
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
                Modifier.fillMaxSize(),
            onDismissRequest = {
                isBottomSheetVisible = false
            },
            sheetState = sheetState,
            scrimColor = Color.Transparent,
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(
                            horizontal = 24.dp,
                            vertical = 8.dp,
                        ),
                verticalArrangement =
                    Arrangement.spacedBy(24.dp),
            ) {
                Text(
                    text = sheetTitle,
                    style =
                        MaterialTheme
                            .typography
                            .headlineSmall,
                    color =
                        MaterialTheme
                            .colorScheme
                            .onSurface,
                )

                Row(
                    modifier =
                        Modifier.fillMaxWidth(),
                    verticalAlignment =
                        Alignment.CenterVertically,
                    horizontalArrangement =
                        Arrangement.spacedBy(16.dp),
                ) {
                    Icon(
                        painter =
                            if (isBluetoothConnected) {
                                bluetoothIcon
                            } else {
                                localDeviceIcon
                            },
                        contentDescription = null,
                        modifier =
                            Modifier.size(32.dp),
                        tint =
                            MaterialTheme
                                .colorScheme
                                .onSurfaceVariant,
                    )

                    Column(
                        modifier =
                            Modifier.weight(1f),
                        verticalArrangement =
                            Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = outputLabel,
                            style =
                                MaterialTheme
                                    .typography
                                    .titleMedium,
                            color =
                                MaterialTheme
                                    .colorScheme
                                    .onSurface,
                            maxLines = 2,
                            overflow =
                                TextOverflow.Ellipsis,
                        )

                        if (isBluetoothConnected) {
                            Text(
                                text = bluetoothAudioLabel,
                                style =
                                    MaterialTheme
                                        .typography
                                        .bodyMedium,
                                color =
                                    MaterialTheme
                                        .colorScheme
                                        .onSurfaceVariant,
                            )
                        }
                    }
                }

                Column(
                    verticalArrangement =
                        Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = mediaVolumeLabel,
                        style =
                            MaterialTheme
                                .typography
                                .titleMedium,
                        color =
                            MaterialTheme
                                .colorScheme
                                .onSurface,
                    )

                    Row(
                        modifier =
                            Modifier.fillMaxWidth(),
                        verticalAlignment =
                            Alignment.CenterVertically,
                        horizontalArrangement =
                            Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            painter = volumeDownIcon,
                            contentDescription = null,
                            tint =
                                MaterialTheme
                                    .colorScheme
                                    .onSurfaceVariant,
                            modifier =
                                Modifier.size(24.dp),
                        )

                        Slider(
                            value = state.volumeFraction,
                            onValueChange = { value ->
                                val maxVolume =
                                    state.maxMediaVolume

                                if (
                                    maxVolume > 0 &&
                                    !state.isVolumeFixed
                                ) {
                                    onVolumeChange(
                                        (
                                            value * maxVolume
                                        ).roundToInt().coerceIn(
                                            0,
                                            maxVolume,
                                        ),
                                    )
                                }
                            },
                            modifier =
                                Modifier
                                    .weight(1f)
                                    .semantics {
                                        contentDescription =
                                            volumeSliderContentDescription
                                    },
                            enabled =
                                state.maxMediaVolume > 0 &&
                                    !state.isVolumeFixed,
                            valueRange = 0f..1f,
                            steps =
                                (
                                    state.maxMediaVolume - 1
                                ).coerceAtLeast(0),
                        )

                        Icon(
                            painter = volumeUpIcon,
                            contentDescription = null,
                            tint =
                                MaterialTheme
                                    .colorScheme
                                    .onSurfaceVariant,
                            modifier =
                                Modifier.size(24.dp),
                        )
                    }

                    if (state.isVolumeFixed) {
                        Text(
                            text = fixedVolumeMessage,
                            style =
                                MaterialTheme
                                    .typography
                                    .bodyMedium,
                            color =
                                MaterialTheme
                                    .colorScheme
                                    .onSurfaceVariant,
                        )
                    } else {
                        Text(
                            text =
                                "${(state.volumeFraction * 100f).roundToInt()}%",
                            style =
                                MaterialTheme
                                    .typography
                                    .labelMedium,
                            color =
                                MaterialTheme
                                    .colorScheme
                                    .onSurfaceVariant,
                            textAlign = TextAlign.End,
                            modifier =
                                Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}
