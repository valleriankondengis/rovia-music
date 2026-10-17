
@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
)

package com.rovia.music.feature.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.rovia.music.core.model.PlaybackState
import com.rovia.music.core.model.SyncedLyrics
import com.rovia.music.core.model.Track
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val FULLSCREEN_LYRICS_ENTER_EASING =
    CubicBezierEasing(
        0.22f,
        1f,
        0.36f,
        1f,
    )

private const val FULLSCREEN_LYRICS_FADE_DURATION_MS = 320

private const val FULLSCREEN_MINIPLAYER_ENTER_DURATION_MS = 440
private const val FULLSCREEN_MINIPLAYER_FADE_DURATION_MS = 300
private const val FULLSCREEN_MINIPLAYER_DELAY_MS = 100L

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
    artworkColor: Color? = null,
) {
    /*
     * Lyrics use fade-in only.
     *
     * PlayerLyric owns its internal auto-scroll and centering animation.
     * Translating the entire lyric viewport during entry can visually
     * compete with those animations, making the lyrics appear to jump.
     * Keeping the viewport stationary avoids that second vertical motion.
     */
    val lyricAlpha =
        remember {
            Animatable(0f)
        }

    /*
     * MiniPlayer and seekbar share one animated layer so they move
     * upward together without changing their layout measurements.
     */
    val miniPlayerOffsetY =
        remember {
            Animatable(1f)
        }

    val miniPlayerAlpha =
        remember {
            Animatable(0f)
        }

    /*
     * Each time the fullscreen lyrics content enters composition,
     * fade in the lyric viewport, then slide in the MiniPlayer.
     */
    LaunchedEffect(Unit) {
        launch {
            lyricAlpha.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis =
                        FULLSCREEN_LYRICS_FADE_DURATION_MS,
                    easing = FULLSCREEN_LYRICS_ENTER_EASING,
                ),
            )
        }

        delay(FULLSCREEN_MINIPLAYER_DELAY_MS)

        launch {
            miniPlayerOffsetY.animateTo(
                targetValue = 0f,
                animationSpec = tween(
                    durationMillis =
                        FULLSCREEN_MINIPLAYER_ENTER_DURATION_MS,
                    easing = FULLSCREEN_LYRICS_ENTER_EASING,
                ),
            )
        }

        launch {
            miniPlayerAlpha.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis =
                        FULLSCREEN_MINIPLAYER_FADE_DURATION_MS,
                    easing = FULLSCREEN_LYRICS_ENTER_EASING,
                ),
            )
        }
    }

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
        /*
         * Header remains stationary.
         */
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

        /*
         * Keep the lyric viewport at its normal layout position.
         * Only its opacity changes; PlayerLyric retains control of
         * the actual lyric scrolling and centering.
         */
        androidx.compose.foundation.layout.Box(
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .graphicsLayer {
                        alpha = lyricAlpha.value
                    },
        ) {
            PlayerLyric(
                lyrics = lyrics,
                hasLyrics = hasLyrics,
                isLyricsLoading = isLyricsLoading,
                playbackPositionMs =
                    playbackState.positionMs,
                onSeek = onLyricSeek,
                modifier = Modifier.fillMaxSize(),
                isPlaying = playbackState.isPlaying,
            )
        }

        Spacer(
            modifier = Modifier.height(16.dp),
        )

        /*
         * MiniPlayer and seekbar slide upward together.
         * Their layout space remains stable throughout the animation.
         */
        androidx.compose.foundation.layout.Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        translationY =
                            size.height * miniPlayerOffsetY.value

                        alpha = miniPlayerAlpha.value
                    },
        ) {
            PlayerLyricsMiniPlayer(
                playbackState = playbackState,
                positionMs = seekPosition,
                durationMs = duration,
                onPositionChange = onSeekPositionChange,
                onSeekFinished = onSeekFinished,
                onPrevious = onPrevious,
                onPlayPause = onPlayPause,
                onNext = onNext,
                modifier = Modifier.fillMaxWidth(),
                artworkColor = artworkColor,
            )
        }
    }
}
