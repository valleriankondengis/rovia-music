
package com.rovia.music.core.playback

import kotlinx.coroutines.flow.StateFlow

/**
 * Identifies the type of audio output presented by the Player UI.
 */
enum class AudioOutputType {
    LOCAL_DEVICE,
    BLUETOOTH,
}

/**
 * Represents the currently detected audio output and system media volume.
 *
 * [deviceName] contains the actual system-reported Bluetooth device name
 * when available. It remains null when the output is local or the name
 * is unavailable.
 *
 * [currentMediaVolume] and [maxMediaVolume] represent the Android
 * media stream volume, not an application-owned volume value.
 */
data class AudioOutputState(
    val outputType: AudioOutputType,
    val deviceName: String?,
    val currentMediaVolume: Int,
    val maxMediaVolume: Int,
    val isVolumeFixed: Boolean,
) {
    /**
     * Normalized media volume for Slider presentation.
     * Returns zero when the system does not expose a usable volume range.
     */
    val volumeFraction: Float
        get() =
            if (maxMediaVolume > 0) {
                (
                    currentMediaVolume.toFloat() /
                        maxMediaVolume.toFloat()
                ).coerceIn(0f, 1f)
            } else {
                0f
            }
}

/**
 * Provides observable audio-output information and system media-volume control.
 *
 * Implementations must use Android's actual audio state and must not
 * fabricate device names or volume values.
 */
interface AudioOutputController {

    /**
     * Emits the latest detected output and media-volume state.
     * Null means the initial state has not been resolved yet.
     */
    val state: StateFlow<AudioOutputState?>

    /**
     * Changes Android's system media volume.
     * Implementations must clamp the requested value to the supported range.
     */
    fun setMediaVolume(volume: Int)

    /**
     * Re-reads the current output and system media-volume state.
     */
    fun refresh()
}
