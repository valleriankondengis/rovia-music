package com.rovia.music.playback.bitperfect

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import androidx.core.content.ContextCompat
import com.rovia.music.playback.bitperfect.direct.DirectUsbControllerProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class UsbDetachModeReverter(
    context: Context,
    private val onReverted: (() -> Unit)? = null,
) {

    private val appContext = context.applicationContext

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != UsbManager.ACTION_USB_DEVICE_DETACHED) return
            val detached = intent.getParcelableExtra(
                UsbManager.EXTRA_DEVICE,
                UsbDevice::class.java,
            )
            if (detached != null && !detached.isUsbAudioStreamingDevice()) return
            val store = AudioOutputModeStoreProvider.get(appContext)
            val mode = store.mode.value
            if (mode == AudioOutputMode.OFF) return

            store.setMode(AudioOutputMode.OFF)
            if (mode == AudioOutputMode.DIRECT_USB) {
                CoroutineScope(Dispatchers.IO).launch {
                    runCatching { DirectUsbControllerProvider.get(appContext).disconnect() }.onFailure { }
                }
            }
            onReverted?.invoke()
        }
    }

    fun register() {
        ContextCompat.registerReceiver(
            appContext,
            receiver,
            IntentFilter(UsbManager.ACTION_USB_DEVICE_DETACHED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    fun unregister() {
        runCatching { appContext.unregisterReceiver(receiver) }.onFailure { }
    }

    companion object {
    }
}

private const val USB_AUDIO_STREAMING_SUBCLASS = 2

private fun UsbDevice.isUsbAudioStreamingDevice(): Boolean {
    return (0 until interfaceCount).any { index ->
        val usbInterface = getInterface(index)
        usbInterface.interfaceClass == UsbConstants.USB_CLASS_AUDIO &&
            usbInterface.interfaceSubclass == USB_AUDIO_STREAMING_SUBCLASS
    }
}
