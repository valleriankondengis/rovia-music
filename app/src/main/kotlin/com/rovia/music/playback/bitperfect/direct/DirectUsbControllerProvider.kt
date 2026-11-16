package com.rovia.music.playback.bitperfect.direct

import android.content.Context

internal object DirectUsbControllerProvider {

    @Volatile
    private var instance: DirectUsbController? = null

    fun get(context: Context): DirectUsbController {
        return instance ?: synchronized(this) {
            instance ?: DirectUsbController(context.applicationContext).also { instance = it }
        }
    }
}
