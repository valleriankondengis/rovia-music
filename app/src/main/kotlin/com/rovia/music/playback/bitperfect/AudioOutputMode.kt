package com.rovia.music.playback.bitperfect

import android.content.Context
import android.content.SharedPreferences
import android.media.AudioDeviceInfo
import android.media.AudioManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AudioOutputMode {
    OFF,
    NATIVE_A14,
    DIRECT_USB,
    DAP,
}

class AudioOutputModeStore(context: Context) {

    private val preferences: SharedPreferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    private val _mode = MutableStateFlow(readMode())
    val mode: StateFlow<AudioOutputMode> = _mode.asStateFlow()

    private val _allowFixedVolume = MutableStateFlow(readAllowFixedVolume())
    val allowFixedVolume: StateFlow<Boolean> = _allowFixedVolume.asStateFlow()

    private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == MODE_KEY) {
            _mode.value = readMode()
        }
        if (key == ALLOW_FIXED_VOLUME_KEY) {
            _allowFixedVolume.value = readAllowFixedVolume()
        }
    }

    init {
        preferences.registerOnSharedPreferenceChangeListener(preferenceListener)
    }

    fun setMode(mode: AudioOutputMode) {
        if (readMode() == mode) {
            _mode.value = mode
            return
        }
        preferences.edit().putString(MODE_KEY, mode.name).apply()
        _mode.value = mode
    }

    fun setAllowFixedVolume(allowed: Boolean) {
        preferences.edit().putBoolean(ALLOW_FIXED_VOLUME_KEY, allowed).apply()
        _allowFixedVolume.value = allowed
    }

    private fun readAllowFixedVolume(): Boolean =
        preferences.getBoolean(ALLOW_FIXED_VOLUME_KEY, false)

    private fun readMode(): AudioOutputMode = runCatching {
        AudioOutputMode.valueOf(
            preferences.getString(MODE_KEY, AudioOutputMode.OFF.name)
                ?: AudioOutputMode.OFF.name,
        )
    }.getOrDefault(AudioOutputMode.OFF)

    companion object {
        private const val PREFERENCES_NAME = "audio_output_preferences"
        private const val MODE_KEY = "selected_backend_v2"
        private const val ALLOW_FIXED_VOLUME_KEY = "allow_fixed_volume_v1"
    }
}

internal object UsbAudioOutputPresence {

    fun hasUsbAudioOutput(context: Context): Boolean {
        val manager = context.getSystemService(AudioManager::class.java) ?: return false
        return manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any { device ->
            device.type == AudioDeviceInfo.TYPE_USB_DEVICE ||
                device.type == AudioDeviceInfo.TYPE_USB_HEADSET
        }
    }

    fun usbAudioOutputDeviceId(context: Context): Int {
        val manager = context.getSystemService(AudioManager::class.java) ?: return 0
        return manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            .firstOrNull { device ->
                device.type == AudioDeviceInfo.TYPE_USB_DEVICE ||
                    device.type == AudioDeviceInfo.TYPE_USB_HEADSET
            }
            ?.id ?: 0
    }

    fun usbAudioOutputDeviceName(context: Context): String? {
        val manager = context.getSystemService(AudioManager::class.java) ?: return null
        return manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            .firstOrNull { device ->
                device.type == AudioDeviceInfo.TYPE_USB_DEVICE ||
                    device.type == AudioDeviceInfo.TYPE_USB_HEADSET
            }
            ?.productName?.toString()?.takeIf { it.isNotBlank() }
    }
}

object AudioOutputModeStoreProvider {

    @Volatile
    private var instance: AudioOutputModeStore? = null

    fun get(context: Context): AudioOutputModeStore {
        return instance ?: synchronized(this) {
            instance ?: AudioOutputModeStore(context.applicationContext).also { instance = it }
        }
    }
}
