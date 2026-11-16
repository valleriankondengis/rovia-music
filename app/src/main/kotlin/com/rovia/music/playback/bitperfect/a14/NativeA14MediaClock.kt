package com.rovia.music.playback.bitperfect.a14

import androidx.annotation.OptIn
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.MediaClock

@OptIn(UnstableApi::class)
internal class NativeA14MediaClock(
    private val coordinator: NativeA14PlaybackCoordinator,
) : MediaClock {

    @Volatile
    private var playbackParameters: PlaybackParameters = PlaybackParameters.DEFAULT

    override fun getPositionUs(): Long = coordinator.clockPositionUs()

    override fun setPlaybackParameters(playbackParameters: PlaybackParameters) {
        this.playbackParameters = playbackParameters
    }

    override fun getPlaybackParameters(): PlaybackParameters = playbackParameters
}
