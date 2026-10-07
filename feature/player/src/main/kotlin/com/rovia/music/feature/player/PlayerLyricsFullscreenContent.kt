@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
)

package com.rovia.music.feature.player

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rovia.music.core.model.PlaybackState
import com.rovia.music.core.model.SyncedLyrics
import com.rovia.music.core.model.Track

@Composable
internal fun PlayerLyricsFullscreenContent(
    track: Track?,
    playbackState: PlaybackState,
    lyrics: SyncedLyrics?,
    hasLyrics: Boolean,
    isLyricsLoading: Boolean,
    isLyricsVisible: Boolean,
    onToggleLyrics: () -> Unit,
    onClose: () -> Unit,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    seekPosition: Float,
    duration: Long,
    onSeekPositionChange: (Float) -> Unit,
    onSeekFinished: (Long) -> Unit,
    onLyricSeek: (Long) -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(
                    start = 24.dp,
                    top = 16.dp,
                    end = 24.dp,
                    bottom = 20.dp,
                ),
    ) {
        PlayerActionButtonGroup(
            track = track,
            hasLyrics = hasLyrics,
            isLyricsLoading = isLyricsLoading,
            isLyricsVisible = isLyricsVisible,
            onToggleLyrics = onToggleLyrics,
            onClose = onClose,
        )

        Spacer(
            modifier = Modifier.height(20.dp),
        )

        PlayerLyric(
            lyrics = lyrics,
            hasLyrics = hasLyrics,
            isLyricsLoading = isLyricsLoading,
            playbackPositionMs =
                playbackState.positionMs,
            onSeek = onLyricSeek,
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
        )

        Spacer(
            modifier = Modifier.height(16.dp),
        )

        PlayerLyricsMiniPlayer(
            playbackState = playbackState,
            positionMs = seekPosition,
            durationMs = duration,
            onPositionChange = onSeekPositionChange,
            onSeekFinished = onSeekFinished,
            onPrevious = onPrevious,
            onPlayPause = onPlayPause,
            onNext = onNext,
            modifier =
                Modifier.fillMaxWidth(),
        )
    }
}