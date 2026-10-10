@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
)

package com.rovia.music.feature.player

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rovia.music.core.model.PlaybackState
import com.rovia.music.core.model.RepeatMode
import com.rovia.music.core.model.SyncedLyrics
import com.rovia.music.core.model.Track
import kotlinx.coroutines.delay

@Composable
internal fun PortraitPlayerContent(
    track: Track?,
    playbackState: PlaybackState,
    lyrics: SyncedLyrics?,
    hasLyrics: Boolean,
    isLyricsLoading: Boolean,
    isLyricsVisible: Boolean,
    onToggleLyrics: () -> Unit,
    onClose: () -> Unit,
    seekPosition: Float,
    duration: Long,
    displayedIsPlaying: Boolean,
    motionEffectsSpec:
        FiniteAnimationSpec<Float>,
    onSeekPositionChange: (Float) -> Unit,
    onSeekFinished: () -> Unit,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onRepeatModeChange: (RepeatMode) -> Unit,
    onShuffleEnabledChange: (Boolean) -> Unit,
    onLyricSeek: (Long) -> Unit,
) {
    val showInlineLyrics =
        hasLyrics &&
            !isLyricsLoading

    var resolvedHasLyrics by remember {
        mutableStateOf(hasLyrics)
    }

    LaunchedEffect(
        track?.id,
        isLyricsLoading,
        hasLyrics,
    ) {
        if (!isLyricsLoading) {
            delay(180)

            resolvedHasLyrics =
                hasLyrics
        }
    }

    val targetArtworkSize =
        if (resolvedHasLyrics) {
            300.dp
        } else {
            320.dp
        }

    val artworkSize by animateDpAsState(
        targetValue = targetArtworkSize,
        animationSpec =
            tween(
                durationMillis = 300,
            ),
        label = "playerArtworkSize",
    )

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(
                    horizontal = 24.dp,
                    vertical = 16.dp,
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

        /*
         * Artwork memiliki slot dengan tinggi tetap.
         *
         * Ukuran artwork boleh berubah 300dp <-> 320dp,
         * tetapi slot tetap 320dp sehingga seluruh komponen
         * di bawahnya tidak ikut naik/turun.
         */
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(320.dp),
            contentAlignment =
                Alignment.Center,
        ) {
            PlayerArtwork(
                track = track,
                size = artworkSize,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(artworkSize),
            )
        }

        if (showInlineLyrics) {
            PlayerCurrentLyricLine(
                lyrics = lyrics,
                playbackPositionMs =
                    playbackState.positionMs,
                modifier =
                    Modifier.fillMaxWidth(),
            )
        } else {
            Spacer(
                modifier =
                    Modifier.height(48.dp),
            )
        }

        Text(
            text =
                track?.title
                    ?: stringResource(
                        R.string.player_no_track,
                    ),
            style =
                MaterialTheme.typography.headlineSmall,
            maxLines = 1,
            modifier =
                Modifier
                    .padding(top = 8.dp)
                    .basicMarquee(
                        iterations = Int.MAX_VALUE,
                        initialDelayMillis = 900,
                        repeatDelayMillis = 900,
                        velocity = 35.dp,
                    ),
        )

        Text(
            text =
                track?.artist
                    ?: stringResource(
                        R.string.player_unknown_artist,
                    ),
            style =
                MaterialTheme.typography.bodyLarge,
            color =
                MaterialTheme.colorScheme
                    .onSurfaceVariant,
            maxLines = 1,
            modifier =
                Modifier
                    .padding(top = 4.dp)
                    .basicMarquee(
                        iterations = Int.MAX_VALUE,
                        initialDelayMillis = 900,
                        repeatDelayMillis = 900,
                        velocity = 35.dp,
                    ),
        )

        WavySeekBar(
            positionMs = seekPosition,
            durationMs = duration,
            isPlaying = playbackState.isPlaying,
            onPositionChange =
                onSeekPositionChange,
            onSeekFinished =
                onSeekFinished,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = 28.dp),
        )

        TrackTechnicalInfo(
            track = track,
        )

        SeekTimeRow(
            positionMs = seekPosition,
            durationMs = duration,
        )

        Spacer(
            modifier = Modifier.height(32.dp),
        )

        PlaybackButtonGroup(
            isPlaying = displayedIsPlaying,
            onPrevious = onPrevious,
            onPlayPause = onPlayPause,
            onNext = onNext,
        )

        Spacer(
            modifier = Modifier.height(16.dp),
        )

        PlaybackOptionsButtonGroup(
            repeatMode = playbackState.repeatMode,
            shuffleEnabled = playbackState.shuffleEnabled,
            onRepeatModeChange = onRepeatModeChange,
            onShuffleEnabledChange = onShuffleEnabledChange,
        )
    }
}

@Composable
private fun PlayerCurrentLyricLine(
    lyrics: SyncedLyrics?,
    playbackPositionMs: Long,
    modifier: Modifier = Modifier,
) {
    val activeLineIndex =
        lyrics
            ?.lines
            ?.indexOfLast {
                it.startTimeMs <= playbackPositionMs
            }
            ?: -1

    val activeLine =
        lyrics
            ?.lines
            ?.getOrNull(activeLineIndex)

    if (
        activeLine == null ||
            activeLine.words.isEmpty()
    ) {
        Spacer(
            modifier =
                modifier.height(
                    48.dp,
                ),
        )

        return
    }

    val activeWordIndex =
        activeLine.words.indexOfLast {
            it.startTimeMs <= playbackPositionMs
        }

    val inactiveColor =
        MaterialTheme.colorScheme
            .onSurfaceVariant
            .copy(alpha = 0.55f)

    val activeColor =
        MaterialTheme.colorScheme.onSurface

    val lyricText =
        buildAnnotatedString {
            activeLine.words.forEachIndexed {
                index,
                word,
                ->
                val wordColor =
                    when {
                        activeWordIndex < 0 ->
                            activeColor

                        index == activeWordIndex ->
                            MaterialTheme
                                .colorScheme
                                .primary

                        index < activeWordIndex ->
                            activeColor

                        else ->
                            inactiveColor
                    }

                val wordFontWeight =
                    when {
                        index == activeWordIndex ->
                            FontWeight.Bold

                        index < activeWordIndex ->
                            FontWeight.Medium

                        else ->
                            FontWeight.Normal
                    }

                pushStyle(
                    SpanStyle(
                        color = wordColor,
                        fontWeight = wordFontWeight,
                    ),
                )

                append(word.text)

                if (
                    index < activeLine.words.lastIndex
                ) {
                    append(" ")
                }

                pop()
            }
        }

    Box(
        modifier =
            modifier.height(48.dp),
        contentAlignment =
            Alignment.CenterStart,
    ) {
        BasicText(
            text = lyricText,
            modifier =
                Modifier.fillMaxWidth(),
            maxLines = 2,
            overflow = TextOverflow.Clip,
            style =
                MaterialTheme.typography.titleMedium.copy(
                    textAlign = TextAlign.Start,
                    lineHeight = 22.sp,
                ),
        )
    }
}