package com.rovia.music.playback

import android.util.Log

object PlaybackDiagnostics {
    const val TAG = "RoviaPlayback"

    fun log(message: String) {
        Log.d(TAG, message)
    }
}
