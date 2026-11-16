package com.rovia.music.playback.bitperfect.a14

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioMixerAttributes
import android.os.Build
import androidx.annotation.RequiresApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class NativeA14UnavailableReason {
    ANDROID_VERSION,
    NO_USB_DEVICE,
    NO_BIT_PERFECT_SUPPORT,
}

sealed interface NativeA14Check {
    data class Supported(
        val deviceName: String,
        val formats: List<String>,
    ) : NativeA14Check

    data class Unavailable(
        val reason: NativeA14UnavailableReason,
    ) : NativeA14Check
}

object NativeA14Status {
    private val _status = MutableStateFlow<String?>(null)
    val status: StateFlow<String?> = _status.asStateFlow()

    internal fun set(value: String?) {
        _status.value = value
    }
}

object NativeA14Support {

    fun check(context: Context): NativeA14Check {
        if (Build.VERSION.SDK_INT < 34) {
            return NativeA14Check.Unavailable(NativeA14UnavailableReason.ANDROID_VERSION)
        }
        return NativeA14Probe.check(context.applicationContext)
    }

    internal fun usbOutputDevice(manager: AudioManager): AudioDeviceInfo? =
        manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).firstOrNull { device ->
            device.type == AudioDeviceInfo.TYPE_USB_DEVICE ||
                device.type == AudioDeviceInfo.TYPE_USB_HEADSET
        }

    internal fun describe(format: AudioFormat): String {
        val depth = when (format.encoding) {
            AudioFormat.ENCODING_PCM_16BIT -> "16-bit"
            AudioFormat.ENCODING_PCM_24BIT_PACKED -> "24-bit"
            AudioFormat.ENCODING_PCM_32BIT -> "32-bit"
            AudioFormat.ENCODING_PCM_FLOAT -> "float"
            else -> "encoding ${format.encoding}"
        }
        val channels = when (format.channelMask) {
            AudioFormat.CHANNEL_OUT_MONO -> "mono"
            AudioFormat.CHANNEL_OUT_STEREO -> "stereo"
            else -> "${Integer.bitCount(format.channelMask)} ch"
        }
        return "${format.sampleRate} Hz · $depth · $channels"
    }
}

@RequiresApi(34)
private object NativeA14Probe {

    fun check(context: Context): NativeA14Check {
        val manager = context.getSystemService(AudioManager::class.java)
            ?: return NativeA14Check.Unavailable(NativeA14UnavailableReason.NO_USB_DEVICE)
        val usb = NativeA14Support.usbOutputDevice(manager)
            ?: return NativeA14Check.Unavailable(NativeA14UnavailableReason.NO_USB_DEVICE)
        val bitPerfect = manager.getSupportedMixerAttributes(usb).filter { attributes ->
            attributes.mixerBehavior == AudioMixerAttributes.MIXER_BEHAVIOR_BIT_PERFECT
        }
        if (bitPerfect.isEmpty()) {
            return NativeA14Check.Unavailable(NativeA14UnavailableReason.NO_BIT_PERFECT_SUPPORT)
        }
        val formats = bitPerfect
            .map { attributes -> NativeA14Support.describe(attributes.format) }
            .distinct()
            .sorted()
        return NativeA14Check.Supported(
            deviceName = usb.productName?.toString().orEmpty(),
            formats = formats,
        )
    }
}

@RequiresApi(34)
internal class NativeA14MixerController(context: Context) {

    data class Selection(
        val device: AudioDeviceInfo,
        val attributes: AudioMixerAttributes,
        val platformAttributes: AudioAttributes,
    )

    private val manager = context.getSystemService(AudioManager::class.java)

    private var current: Selection? = null

    fun select(sampleRate: Int, channelMask: Int, encoding: Int): Selection? {
        val audioManager = manager ?: return null
        val usb = NativeA14Support.usbOutputDevice(audioManager)
        if (usb == null) {
            clearSelection()
            NativeA14Status.set("Native A14: no USB audio device is connected.")
            return null
        }
        val match = audioManager.getSupportedMixerAttributes(usb).firstOrNull { attributes ->
            attributes.mixerBehavior == AudioMixerAttributes.MIXER_BEHAVIOR_BIT_PERFECT &&
                attributes.format.sampleRate == sampleRate &&
                attributes.format.channelMask == channelMask &&
                attributes.format.encoding == encoding
        }
        if (match == null) {
            clearSelection()
            NativeA14Status.set(
                "Native A14: no BIT_PERFECT attribute matches " +
                    "$sampleRate Hz for this track; playback cannot start in this mode.",
            )

            return null
        }
        current?.let { existing ->
            if (existing.attributes == match && existing.device.id == usb.id) {
                return existing
            }
        }
        clearSelection()
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
        if (!audioManager.setPreferredMixerAttributes(attributes, usb, match)) {
            NativeA14Status.set(
                "Native A14: the audio HAL rejected the BIT_PERFECT request for " +
                    "${NativeA14Support.describe(match.format)}.",
            )

            return null
        }
        val selection = Selection(
            device = usb,
            attributes = match,
            platformAttributes = attributes,
        )
        current = selection
        NativeA14Status.set(
            "Native A14: BIT_PERFECT requested for " +
                "${NativeA14Support.describe(match.format)} on ${usb.productName}.",
        )

        return selection
    }

    fun verify(selection: Selection, routedDevice: AudioDeviceInfo?): Boolean {
        val audioManager = manager ?: return false
        val preferred = audioManager.getPreferredMixerAttributes(
            selection.platformAttributes,
            selection.device,
        )
        val verified = routedDevice?.id == selection.device.id && preferred == selection.attributes
        NativeA14Status.set(
            if (verified) {
                "Native A14: bit-perfect route verified for " +
                    "${NativeA14Support.describe(selection.attributes.format)} " +
                    "on ${selection.device.productName}."
            } else {
                "Native A14: bit-perfect route not verified " +
                    "(routed device: ${routedDevice?.productName ?: "unknown"})."
            },
        )

        return verified
    }

    fun isSelectionIntact(selection: Selection): Boolean {
        val audioManager = manager ?: return false
        val preferred = audioManager.getPreferredMixerAttributes(
            selection.platformAttributes,
            selection.device,
        )
        return preferred == selection.attributes
    }

    fun clear() {
        clearSelection()
        NativeA14Status.set(null)
    }

    private fun clearSelection() {
        val selection = current
        if (selection != null) {
            runCatching {
                manager?.clearPreferredMixerAttributes(
                    selection.platformAttributes,
                    selection.device,
                )
            }.onFailure { }
        }
        current = null
    }

    companion object {
    }
}
