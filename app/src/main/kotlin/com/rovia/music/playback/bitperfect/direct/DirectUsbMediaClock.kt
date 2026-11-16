package com.rovia.music.playback.bitperfect.direct

import androidx.annotation.OptIn
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.MediaClock

@OptIn(UnstableApi::class)
internal class DirectUsbMediaClock(
    private val coordinator: DirectUsbPlaybackCoordinator,
) : MediaClock {

    @Volatile
    private var playbackParameters: PlaybackParameters = PlaybackParameters.DEFAULT

    override fun getPositionUs(): Long = coordinator.clockPositionUs()

    override fun setPlaybackParameters(playbackParameters: PlaybackParameters) {
        this.playbackParameters = playbackParameters
    }

    override fun getPlaybackParameters(): PlaybackParameters = playbackParameters
}
