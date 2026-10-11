
package com.rovia.music.playback.media3

import android.content.Context
import android.database.ContentObserver
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.net.Uri
import com.rovia.music.core.playback.AudioOutputController
import com.rovia.music.core.playback.AudioOutputState
import com.rovia.music.core.playback.AudioOutputType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Native Android implementation of audio-output detection and media-volume control.
 *
 * Intended to be retained by the application-scoped AppContainer.
 * Device and volume changes are observed rather than continuously polled.
 */
class AndroidAudioOutputController(
    context: Context,
) : AudioOutputController {

    private val applicationContext =
        context.applicationContext

    private val audioManager =
        applicationContext.getSystemService(
            Context.AUDIO_SERVICE,
        ) as AudioManager

    private val mainHandler =
        Handler(Looper.getMainLooper())

    private val mutableState =
        MutableStateFlow<AudioOutputState?>(null)

    override val state: StateFlow<AudioOutputState?> =
        mutableState.asStateFlow()

    private val audioDeviceCallback =
        object : AudioDeviceCallback() {

            override fun onAudioDevicesAdded(
                addedDevices: Array<out AudioDeviceInfo>,
            ) {
                refresh()
            }

            override fun onAudioDevicesRemoved(
                removedDevices: Array<out AudioDeviceInfo>,
            ) {
                refresh()
            }
        }

    private val mediaVolumeObserver =
        object : ContentObserver(mainHandler) {

            override fun onChange(
                selfChange: Boolean,
                uri: Uri?,
            ) {
                refresh()
            }
        }

    init {
        audioManager.registerAudioDeviceCallback(
            audioDeviceCallback,
            mainHandler,
        )

        applicationContext.contentResolver
            .registerContentObserver(
                Settings.System.getUriFor("volume_music"),
                false,
                mediaVolumeObserver,
            )

        refresh()
    }

    override fun refresh() {
        val bluetoothDevice =
            audioManager
                .getDevices(
                    AudioManager.GET_DEVICES_OUTPUTS,
                )
                .firstOrNull { device ->
                    device.type in bluetoothOutputTypes
                }

        val maxVolume =
            audioManager
                .getStreamMaxVolume(
                    AudioManager.STREAM_MUSIC,
                )
                .coerceAtLeast(0)

        val currentVolume =
            audioManager
                .getStreamVolume(
                    AudioManager.STREAM_MUSIC,
                )
                .coerceIn(
                    0,
                    maxVolume,
                )

        val deviceName =
            bluetoothDevice
                ?.productName
                ?.toString()
                ?.trim()
                ?.takeIf { name ->
                    name.isNotEmpty()
                }

        val newState =
            AudioOutputState(
                outputType =
                    if (bluetoothDevice != null) {
                        AudioOutputType.BLUETOOTH
                    } else {
                        AudioOutputType.LOCAL_DEVICE
                    },
                deviceName = deviceName,
                currentMediaVolume = currentVolume,
                maxMediaVolume = maxVolume,
                isVolumeFixed =
                    audioManager.isVolumeFixed,
            )

        if (mutableState.value != newState) {
            mutableState.value = newState
        }
    }

    override fun setMediaVolume(volume: Int) {
        val maxVolume =
            audioManager
                .getStreamMaxVolume(
                    AudioManager.STREAM_MUSIC,
                )
                .coerceAtLeast(0)

        if (audioManager.isVolumeFixed) {
            refresh()
            return
        }

        val clampedVolume =
            volume.coerceIn(
                0,
                maxVolume,
            )

        audioManager.setStreamVolume(
            AudioManager.STREAM_MUSIC,
            clampedVolume,
            0,
        )

        refresh()
    }

    private companion object {

        val bluetoothOutputTypes =
            setOf(
                AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
                AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
                AudioDeviceInfo.TYPE_BLE_HEADSET,
                AudioDeviceInfo.TYPE_BLE_SPEAKER,
            )
    }
}
