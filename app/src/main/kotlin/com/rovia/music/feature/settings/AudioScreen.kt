@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
)

package com.rovia.music.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rovia.music.R
import com.rovia.music.playback.bitperfect.AudioOutputMode
import com.rovia.music.playback.bitperfect.AudioOutputModeStoreProvider
import com.rovia.music.playback.bitperfect.UsbAudioOutputPresence
import com.rovia.music.playback.bitperfect.a14.NativeA14Check
import com.rovia.music.playback.bitperfect.a14.NativeA14Support
import com.rovia.music.playback.bitperfect.a14.NativeA14UnavailableReason
import com.rovia.music.uniffi.dapOutputSupported
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val AudioScreenHorizontalPadding = 18.dp
private val AudioScreenVerticalSpacing = 1.dp
private val AudioScreenContentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)

private data class BackendOption(
    val backend: AudioOutputMode,
    val title: Int,
    val supporting: Int,
    val icon: Int,
)

@Composable
internal fun AudioScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current.applicationContext
    val modeStore = remember(context) { AudioOutputModeStoreProvider.get(context) }
    val selectedBackend by modeStore.mode.collectAsStateWithLifecycle()
    val allowFixedVolume by modeStore.allowFixedVolume.collectAsStateWithLifecycle()
    val coroutineScope = rememberCoroutineScope()
    var statusText by remember { mutableStateOf<String?>(null) }
    var pendingSelection by remember { mutableStateOf<AudioOutputMode?>(null) }
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    val backendOptions = listOf(
        BackendOption(
            backend = AudioOutputMode.OFF,
            title = R.string.audio_output_off,
            supporting = R.string.audio_output_off_supporting,
            icon = R.drawable.ic_smartphone,
        ),
        BackendOption(
            backend = AudioOutputMode.NATIVE_A14,
            title = R.string.audio_output_native_a14,
            supporting = R.string.audio_output_native_a14_supporting,
            icon = R.drawable.ic_smartphone,
        ),
        BackendOption(
            backend = AudioOutputMode.DIRECT_USB,
            title = R.string.audio_output_direct_usb,
            supporting = R.string.audio_output_direct_usb_supporting,
            icon = R.drawable.ic_usb,
        ),
        BackendOption(
            backend = AudioOutputMode.DAP,
            title = R.string.audio_output_dap,
            supporting = R.string.audio_output_dap_supporting,
            icon = R.drawable.ic_play,
        ),
    )

    fun selectBackend(backend: AudioOutputMode) {
        if (backend == selectedBackend) return
        when (backend) {
            AudioOutputMode.OFF -> {
                pendingSelection = null
                statusText = null
                modeStore.setMode(AudioOutputMode.OFF)
            }
            AudioOutputMode.DIRECT_USB -> {
                pendingSelection = null
                if (!UsbAudioOutputPresence.hasUsbAudioOutput(context)) {
                    statusText = context.getString(R.string.audio_output_connect_dac)
                    modeStore.setMode(AudioOutputMode.OFF)
                } else {
                    statusText = null
                    modeStore.setMode(AudioOutputMode.DIRECT_USB)
                }
            }
            AudioOutputMode.NATIVE_A14 -> {
                pendingSelection = backend
                coroutineScope.launch {
                    val result = withContext(Dispatchers.IO) { NativeA14Support.check(context) }
                    if (pendingSelection != backend) return@launch
                    pendingSelection = null
                    when (result) {
                        is NativeA14Check.Supported -> {
                            statusText = null
                            modeStore.setMode(AudioOutputMode.NATIVE_A14)
                        }
                        is NativeA14Check.Unavailable -> {
                            statusText = context.getString(
                                if (result.reason == NativeA14UnavailableReason.NO_USB_DEVICE) {
                                    R.string.audio_output_connect_dac
                                } else {
                                    R.string.audio_output_a14_unsupported
                                },
                            )
                            modeStore.setMode(AudioOutputMode.OFF)
                        }
                    }
                }
            }
            AudioOutputMode.DAP -> {
                if (!UsbAudioOutputPresence.hasUsbAudioOutput(context)) {
                    pendingSelection = null
                    statusText = context.getString(R.string.audio_output_connect_dac)
                    modeStore.setMode(AudioOutputMode.OFF)
                } else {
                    pendingSelection = backend
                    coroutineScope.launch {
                        val supported = withContext(Dispatchers.IO) {
                            dapOutputSupported(
                                UsbAudioOutputPresence.usbAudioOutputDeviceId(context),
                            )
                        }
                        if (pendingSelection != backend) return@launch
                        pendingSelection = null
                        if (supported) {
                            statusText = null
                            modeStore.setMode(AudioOutputMode.DAP)
                        } else {
                            statusText = context.getString(R.string.audio_output_dap_unsupported)
                            modeStore.setMode(AudioOutputMode.OFF)
                        }
                    }
                }
            }
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            modifier = Modifier.fillMaxWidth(),
            title = { Text(stringResource(R.string.audio_settings_title)) },
            navigationIcon = {
                FilledIconButton(
                    onClick = onBack,
                    shapes = IconButtonDefaults.shapes(),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_arrow_back),
                        contentDescription = stringResource(R.string.audio_settings_back),
                    )
                }
            },
            contentPadding = PaddingValues(horizontal = 10.dp),
        )

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(
                start = AudioScreenHorizontalPadding,
                top = 8.dp,
                end = AudioScreenHorizontalPadding,
                bottom = maxOf(36.dp, bottomInset + 16.dp),
            ),
            verticalArrangement = Arrangement.spacedBy(AudioScreenVerticalSpacing),
        ) {
            item(key = "audio_output_section") {
                SectionTitle(R.string.audio_output_section)
            }

            item(key = "audio_output_options") {
                Column(verticalArrangement = Arrangement.spacedBy(AudioScreenVerticalSpacing)) {
                    backendOptions.forEachIndexed { index, option ->
                        SegmentedListItem(
                            selected = selectedBackend == option.backend,
                            onClick = { selectBackend(option.backend) },
                            modifier = Modifier.fillMaxWidth(),
                            shapes = ListItemDefaults.segmentedShapes(
                                index = index,
                                count = backendOptions.size,
                            ),
                            colors = ListItemDefaults.segmentedColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                contentColor = MaterialTheme.colorScheme.onSurface,
                            ),
                            contentPadding = AudioScreenContentPadding,
                            leadingContent = { AudioLeadingIcon(option.icon) },
                            supportingContent = { Text(stringResource(option.supporting)) },
                            trailingContent = {
                                RadioButton(
                                    selected = selectedBackend == option.backend,
                                    onClick = null,
                                )
                            },
                            content = {
                                Text(
                                    stringResource(option.title),
                                    style = MaterialTheme.typography.titleSmall,
                                )
                            },
                        )
                    }
                }
            }

            item(key = "audio_output_fixed_volume") {
                SegmentedListItem(
                    selected = allowFixedVolume,
                    onClick = { modeStore.setAllowFixedVolume(!allowFixedVolume) },
                    modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
                    shapes = ListItemDefaults.segmentedShapes(index = 0, count = 1),
                    colors = ListItemDefaults.segmentedColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                    contentPadding = AudioScreenContentPadding,
                    leadingContent = { AudioLeadingIcon(R.drawable.ic_usb) },
                    supportingContent = {
                        Text(stringResource(R.string.audio_settings_allow_fixed_volume_supporting))
                    },
                    trailingContent = {
                        Switch(
                            checked = allowFixedVolume,
                            onCheckedChange = null,
                        )
                    },
                    content = {
                        Text(
                            stringResource(R.string.audio_settings_allow_fixed_volume),
                            style = MaterialTheme.typography.titleSmall,
                        )
                    },
                )
            }

            statusText?.let { message ->
                item(key = "audio_output_status") {
                    Text(
                        text = message,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(
    resourceId: Int,
    topPadding: androidx.compose.ui.unit.Dp = 12.dp,
) {
    Text(
        text = stringResource(resourceId),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(start = 4.dp, top = topPadding, bottom = 8.dp),
    )
}

@Composable
private fun AudioLeadingIcon(drawable: Int) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .background(MaterialTheme.colorScheme.primary, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(drawable),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(23.dp),
        )
    }
}
