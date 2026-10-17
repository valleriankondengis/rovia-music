@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
)

package com.rovia.music.feature.player

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableLongState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.ResolvedTextDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rovia.music.core.model.LyricLine
import com.rovia.music.core.model.PlaybackState
import com.rovia.music.core.model.RepeatMode
import com.rovia.music.core.model.SyncedLyrics
import com.rovia.music.core.model.Track
import kotlinx.coroutines.yield
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

private const val LANDSCAPE_INLINE_WORD_RISE_DP = 2f
private const val LANDSCAPE_INLINE_WORD_RISE_MIN_MS = 250f
private const val LANDSCAPE_INLINE_WORD_RISE_MAX_MS = 700f
private const val LANDSCAPE_INLINE_WORD_SETTLE_MS = 700f
private const val LANDSCAPE_INLINE_WORD_LIFT_QUICK_MS = 150f
private const val LANDSCAPE_INLINE_WORD_LIFT_HELD_MS = 900f
private const val LANDSCAPE_INLINE_WORD_LIFT_FLOOR = 0.25f

private val LANDSCAPE_INLINE_LYRIC_EASING =
    CubicBezierEasing(
        0.41f,
        0f,
        0.12f,
        0.99f,
    )

@Composable
internal fun LandscapePlayerContent(
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
    motionEffectsSpec: FiniteAnimationSpec<Float>,
    onSeekPositionChange: (Float) -> Unit,
    onSeekFinished: () -> Unit,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onRepeatModeChange: (RepeatMode) -> Unit,
    onShuffleEnabledChange: (Boolean) -> Unit,
    onLyricSeek: (Long) -> Unit,
    audioOutputIndicator: (@Composable () -> Unit)? = null,
    artworkColor: Color? = null,
) {
    val showInlineLyrics =
        hasLyrics &&
            !isLyricsLoading

    Row(
        modifier =
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(
                    horizontal = 24.dp,
                    vertical = 16.dp,
                ),
        horizontalArrangement =
            Arrangement.spacedBy(24.dp),
    ) {
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxHeight(),
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
                modifier =
                    Modifier.height(12.dp),
            )

            BoxWithConstraints(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(1f),
                contentAlignment =
                    Alignment.Center,
            ) {
                val artworkTargetSize =
                    minOf(
                        maxWidth * 0.88f,
                        maxHeight * 0.88f,
                    )

                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(artworkTargetSize),
                    contentAlignment =
                        Alignment.Center,
                ) {
                    PlayerArtwork(
                        track = track,
                        size = artworkTargetSize,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(artworkTargetSize),
                    )
                }
            }
        }

        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(
                        horizontal = 8.dp,
                    ),
        ) {
            BoxWithConstraints(
                modifier =
                    Modifier.fillMaxSize(),
            ) {
                val compactLandscape =
                    maxHeight < 520.dp

                Column(
                    modifier =
                        Modifier.fillMaxSize(),
                    verticalArrangement =
                        if (compactLandscape) {
                            Arrangement.Top
                        } else {
                            Arrangement.Center
                        },
                ) {
                    if (showInlineLyrics) {
                        PlayerCurrentLyricLine(
                            lyrics = lyrics,
                            playbackPositionMs =
                                playbackState.positionMs,
                            isPlaying =
                                playbackState.isPlaying,
                            modifier =
                                Modifier.fillMaxWidth(),
                        )

                        Spacer(
                            modifier =
                                Modifier.height(8.dp),
                        )
                    } else {
                        Spacer(
                            modifier =
                                Modifier.height(48.dp),
                        )

                        Spacer(
                            modifier =
                                Modifier.height(8.dp),
                        )
                    }

                    androidx.compose.material3.Text(
                        text =
                            track?.title
                                ?: androidx.compose.ui.res.stringResource(
                                    R.string.player_no_track,
                                ),
                        style =
                            MaterialTheme
                                .typography
                                .headlineSmall,
                        maxLines = 1,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .basicMarquee(
                                    iterations =
                                        Int.MAX_VALUE,
                                    initialDelayMillis =
                                        900,
                                    repeatDelayMillis =
                                        900,
                                    velocity = 35.dp,
                                ),
                    )

                    androidx.compose.material3.Text(
                        text =
                            track?.artist
                                ?: androidx.compose.ui.res.stringResource(
                                    R.string
                                        .player_unknown_artist,
                                ),
                        style =
                            MaterialTheme
                                .typography
                                .bodyLarge,
                        color =
                            MaterialTheme
                                .colorScheme
                                .onSurfaceVariant,
                        maxLines = 1,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp)
                                .basicMarquee(
                                    iterations =
                                        Int.MAX_VALUE,
                                    initialDelayMillis =
                                        900,
                                    repeatDelayMillis =
                                        900,
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
                                .padding(
                                    top =
                                        if (compactLandscape) {
                                            16.dp
                                        } else {
                                            28.dp
                                        },
                                ),
                        artworkColor = artworkColor,
                    )

                    TrackTechnicalInfo(
                        track = track,
                    )

                    SeekTimeRow(
                        positionMs = seekPosition,
                        durationMs = duration,
                    )

                    Spacer(
                        modifier =
                            Modifier.height(
                                if (compactLandscape) {
                                    8.dp
                                } else {
                                    24.dp
                                },
                            ),
                    )

                    PlaybackButtonGroup(
                        isPlaying = displayedIsPlaying,
                        onPrevious = onPrevious,
                        onPlayPause = onPlayPause,
                        onNext = onNext,
                    )

                    Spacer(
                        modifier =
                            Modifier.height(
                                if (compactLandscape) {
                                    8.dp
                                } else {
                                    16.dp
                                },
                            ),
                    )

                    if (audioOutputIndicator != null) {
                        Row(
                            modifier =
                                Modifier.fillMaxWidth(),
                            horizontalArrangement =
                                Arrangement.spacedBy(8.dp),
                            verticalAlignment =
                                Alignment.CenterVertically,
                        ) {
                            audioOutputIndicator()

                            PlaybackOptionsButtonGroup(
                                repeatMode =
                                    playbackState.repeatMode,
                                shuffleEnabled =
                                    playbackState.shuffleEnabled,
                                onRepeatModeChange =
                                    onRepeatModeChange,
                                onShuffleEnabledChange =
                                    onShuffleEnabledChange,
                                compact = true,
                            )
                        }
                    } else {
                        PlaybackOptionsButtonGroup(
                            repeatMode =
                                playbackState.repeatMode,
                            shuffleEnabled =
                                playbackState.shuffleEnabled,
                            onRepeatModeChange =
                                onRepeatModeChange,
                            onShuffleEnabledChange =
                                onShuffleEnabledChange,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerCurrentLyricLine(
    lyrics: SyncedLyrics?,
    playbackPositionMs: Long,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
) {
    /*
     * Use the frame clock to keep word highlighting and floating
     * motion smooth between playback position updates.
     */
    val lyricClock =
        remember(lyrics) {
            mutableLongStateOf(
                playbackPositionMs.coerceAtLeast(0L),
            )
        }

    var clockAnchorPositionMs by remember(lyrics) {
        mutableLongStateOf(
            playbackPositionMs.coerceAtLeast(0L),
        )
    }

    var clockAnchorFrameTimeNanos by remember(lyrics) {
        mutableLongStateOf(0L)
    }

    var hasClockAnchor by remember(lyrics) {
        mutableStateOf(false)
    }

    LaunchedEffect(
        lyrics,
        playbackPositionMs,
        isPlaying,
    ) {
        val frameTimeNanos =
            withFrameNanos { it }

        clockAnchorPositionMs =
            playbackPositionMs.coerceAtLeast(0L)

        clockAnchorFrameTimeNanos =
            frameTimeNanos

        hasClockAnchor = true

        if (!isPlaying) {
            lyricClock.longValue =
                playbackPositionMs.coerceAtLeast(0L)
        }
    }

    LaunchedEffect(
        lyrics,
        isPlaying,
    ) {
        if (!isPlaying) {
            lyricClock.longValue =
                playbackPositionMs.coerceAtLeast(0L)

            return@LaunchedEffect
        }

        while (true) {
            withFrameNanos { frameTimeNanos ->
                if (!hasClockAnchor) {
                    lyricClock.longValue =
                        playbackPositionMs.coerceAtLeast(0L)
                } else {
                    val elapsedNanos =
                        (
                            frameTimeNanos -
                                clockAnchorFrameTimeNanos
                            ).coerceAtLeast(0L)

                    val elapsedMs =
                        elapsedNanos / 1_000_000L

                    lyricClock.longValue =
                        (
                            clockAnchorPositionMs +
                                elapsedMs
                            ).coerceAtLeast(0L)
                }
            }
        }
    }

    val activeLineIndex by remember(
        lyrics,
        lyricClock,
    ) {
        derivedStateOf {
            lyrics
                ?.lines
                ?.indexOfLast {
                    it.startTimeMs <= lyricClock.longValue
                }
                ?: -1
        }
    }

    val activeLine =
        lyrics
            ?.lines
            ?.getOrNull(activeLineIndex)

    if (
        activeLine == null ||
        activeLine.words.isEmpty()
    ) {
        Spacer(
            modifier = modifier.height(48.dp),
        )
        return
    }

    val positionMs = lyricClock.longValue

    val activeWordIndex =
        activeLine.words.indexOfLast {
            it.startTimeMs <= positionMs
        }

    LandscapeInlineLyricContent(
        line = activeLine,
        positionMs = positionMs,
        activeWordIndex = activeWordIndex,
        modifier = modifier,
    )
}

@Composable
private fun LandscapeInlineLyricContent(
    line: LyricLine,
    positionMs: Long,
    activeWordIndex: Int,
    modifier: Modifier = Modifier,
) {
    val inactiveColor =
        MaterialTheme.colorScheme
            .onSurfaceVariant
            .copy(alpha = 0.55f)

    val activeColor =
        MaterialTheme.colorScheme.onSurface

    val activeWordColor =
        playerArtworkAccentColor()
            ?: MaterialTheme.colorScheme.primary

    val isWordSynced =
        remember(line) {
            isLandscapeInlineWordSyncedLine(line)
        }

    val wordOffsets =
        remember(line) {
            buildLandscapeInlineWordStartOffsets(line)
        }

    var textLayoutResult by remember(line) {
        mutableStateOf<TextLayoutResult?>(null)
    }

    val onTextLayout: (TextLayoutResult) -> Unit = { result ->
        val previous = textLayoutResult

        if (
            previous == null ||
            previous.layoutInput != result.layoutInput ||
            previous.size != result.size ||
            previous.lineCount != result.lineCount
        ) {
            textLayoutResult = result
        }
    }

    val regularText =
        remember(
            line,
            activeWordIndex,
            inactiveColor,
            activeColor,
            activeWordColor,
        ) {
            buildLandscapeInlineLyricText(
                line = line,
                activeWordIndex = activeWordIndex,
                inactiveColor = inactiveColor,
                activeColor = activeColor,
                activeWordColor = activeWordColor,
            )
        }

    val dimText =
        remember(
            line,
            activeWordIndex,
            inactiveColor,
            activeColor,
            activeWordColor,
        ) {
            buildLandscapeInlineLyricText(
                line = line,
                activeWordIndex = activeWordIndex,
                inactiveColor = inactiveColor,
                activeColor = activeColor,
                activeWordColor = activeWordColor,
                forceInactive = true,
            )
        }

    val sweptText = regularText

    Box(
        modifier = modifier.height(48.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (isWordSynced) {
            val floatingModifier =
                Modifier.drawWithContent {
                    val layout = textLayoutResult

                    if (layout == null) {
                        drawContent()
                    } else {
                        drawLandscapeInlineFloatingWords(
                            layout = layout,
                            line = line,
                            wordOffsets = wordOffsets,
                            positionMs = positionMs,
                        )
                    }
                }

            BasicText(
                text = dimText,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .then(floatingModifier),
                maxLines = 1,
                overflow = TextOverflow.Clip,
                style =
                    MaterialTheme.typography.titleMedium.copy(
                        textAlign = TextAlign.Start,
                        lineHeight = 22.sp,
                    ),
                onTextLayout = onTextLayout,
            )

            BasicText(
                text = sweptText,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .then(floatingModifier)
                        .drawWithContent {
                            val layout = textLayoutResult

                            if (layout == null) {
                                drawContent()
                            } else {
                                drawLandscapeInlineRevealedText(
                                    layout = layout,
                                    revealedCharacters =
                                        calculateLandscapeInlineRevealedCharacters(
                                            line = line,
                                            wordOffsets = wordOffsets,
                                            positionMs = positionMs,
                                        ),
                                )
                            }
                        },
                maxLines = 1,
                overflow = TextOverflow.Clip,
                style =
                    MaterialTheme.typography.titleMedium.copy(
                        textAlign = TextAlign.Start,
                        lineHeight = 22.sp,
                    ),
            )
        } else {
            BasicText(
                text = regularText,
                modifier = Modifier.fillMaxWidth(),
                maxLines = 1,
                overflow = TextOverflow.Clip,
                style =
                    MaterialTheme.typography.titleMedium.copy(
                        textAlign = TextAlign.Start,
                        lineHeight = 22.sp,
                    ),
                onTextLayout = onTextLayout,
            )
        }
    }
}

private fun buildLandscapeInlineLyricText(
    line: LyricLine,
    activeWordIndex: Int,
    inactiveColor: Color,
    activeColor: Color,
    activeWordColor: Color,
    forceInactive: Boolean = false,
): AnnotatedString {
    return buildAnnotatedString {
        line.words.forEachIndexed { index, word ->
            val wordColor =
                when {
                    forceInactive ->
                        inactiveColor

                    activeWordIndex < 0 ->
                        activeColor

                    index == activeWordIndex ->
                        activeWordColor

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

            if (index < line.words.lastIndex) {
                append(" ")
            }

            pop()
        }
    }
}

private fun isLandscapeInlineWordSyncedLine(
    line: LyricLine,
): Boolean {
    if (line.words.size < 2) {
        return false
    }

    for (index in 1 until line.words.size) {
        if (
            line.words[index].startTimeMs >
            line.words[index - 1].startTimeMs
        ) {
            return true
        }
    }

    return false
}

private fun buildLandscapeInlineWordStartOffsets(
    line: LyricLine,
): List<Int> {
    val offsets = ArrayList<Int>(line.words.size)
    var offset = 0

    line.words.forEachIndexed { index, word ->
        offsets += offset
        offset += word.text.length

        if (index < line.words.lastIndex) {
            offset += 1
        }
    }

    return offsets
}

private fun calculateLandscapeInlineRevealedCharacters(
    line: LyricLine,
    wordOffsets: List<Int>,
    positionMs: Long,
): Float {
    if (line.words.isEmpty()) {
        return 0f
    }

    val lastIndex = line.words.lastIndex

    val totalCharacters =
        wordOffsets[lastIndex] +
            line.words[lastIndex].text.length

    for (index in line.words.indices) {
        val word = line.words[index]
        val wordOffset = wordOffsets[index]

        if (positionMs < word.startTimeMs) {
            return wordOffset.toFloat()
        }

        val nextWord =
            line.words.getOrNull(index + 1)

        if (nextWord != null) {
            val wordDuration =
                nextWord.startTimeMs -
                    word.startTimeMs

            if (wordDuration > 0L) {
                if (positionMs < nextWord.startTimeMs) {
                    val progress =
                        (
                            (
                                positionMs -
                                    word.startTimeMs
                                ).toFloat() /
                                wordDuration.toFloat()
                            ).coerceIn(0f, 1f)

                    return wordOffset +
                        word.text.length * progress
                }

                continue
            }

            continue
        }

        val finalWordDuration =
            estimateLandscapeInlineWordDurationMs(
                line = line,
                wordIndex = index,
            )

        val progress =
            (
                (
                    positionMs -
                        word.startTimeMs
                    ).toFloat() /
                    finalWordDuration.toFloat()
                ).coerceIn(0f, 1f)

        return wordOffset +
            word.text.length * progress
    }

    return totalCharacters.toFloat()
}

private fun estimateLandscapeInlineWordDurationMs(
    line: LyricLine,
    wordIndex: Int,
): Long {
    val word =
        line.words.getOrNull(wordIndex)
            ?: return 500L

    val previousWord =
        line.words.getOrNull(wordIndex - 1)
            ?: return 500L

    val interval =
        word.startTimeMs -
            previousWord.startTimeMs

    return if (interval > 0L) {
        interval.coerceIn(250L, 900L)
    } else {
        500L
    }
}

private fun calculateLandscapeInlineWordLift(
    line: LyricLine,
    wordIndex: Int,
    positionMs: Long,
): Float {
    val word =
        line.words.getOrNull(wordIndex)
            ?: return 0f

    val nextWord =
        line.words.getOrNull(wordIndex + 1)

    val inferredEnd =
        if (
            nextWord != null &&
            nextWord.startTimeMs > word.startTimeMs
        ) {
            nextWord.startTimeMs
        } else {
            word.startTimeMs +
                estimateLandscapeInlineWordDurationMs(
                    line = line,
                    wordIndex = wordIndex,
                )
        }

    val wordDuration =
        (
            inferredEnd -
                word.startTimeMs
            ).coerceAtLeast(1L)

    val riseDuration =
        wordDuration.toFloat().coerceIn(
            LANDSCAPE_INLINE_WORD_RISE_MIN_MS,
            LANDSCAPE_INLINE_WORD_RISE_MAX_MS,
        )

    val rising =
        (
            (positionMs - word.startTimeMs).toFloat() /
                riseDuration
            ).coerceIn(0f, 1f)

    val falling =
        (
            1f -
                (positionMs - inferredEnd).toFloat() /
                    LANDSCAPE_INLINE_WORD_SETTLE_MS
            ).coerceIn(0f, 1f)

    val heldThrough =
        (
            (
                wordDuration.toFloat() -
                    LANDSCAPE_INLINE_WORD_LIFT_QUICK_MS
                ) /
                (
                    LANDSCAPE_INLINE_WORD_LIFT_HELD_MS -
                        LANDSCAPE_INLINE_WORD_LIFT_QUICK_MS
                    )
            ).coerceIn(0f, 1f)

    val liftHeight =
        LANDSCAPE_INLINE_WORD_LIFT_FLOOR +
            (1f - LANDSCAPE_INLINE_WORD_LIFT_FLOOR) *
            landscapeInlineSmoothStep(heldThrough)

    return liftHeight *
        landscapeInlineSmoothStep(
            min(rising, falling),
        )
}

private fun landscapeInlineSmoothStep(
    fraction: Float,
): Float {
    val value = fraction.coerceIn(0f, 1f)
    return value * value * (3f - 2f * value)
}

private fun ContentDrawScope.drawLandscapeInlineFloatingWords(
    layout: TextLayoutResult,
    line: LyricLine,
    wordOffsets: List<Int>,
    positionMs: Long,
) {
    if (
        line.words.isEmpty() ||
        !isLandscapeInlineWordSyncedLine(line) ||
        layout.lineCount <= 0
    ) {
        drawContent()
        return
    }

    val textLength =
        layout.layoutInput.text.length

    if (textLength <= 0) {
        drawContent()
        return
    }

    val visualLine = 0
    val lineStart = layout.getLineStart(visualLine)
    val lineEnd =
        layout.getLineEnd(
            visualLine,
            visibleEnd = true,
        )

    val left =
        min(
            layout.getLineLeft(visualLine),
            layout.getLineRight(visualLine),
        )

    val right =
        max(
            layout.getLineLeft(visualLine),
            layout.getLineRight(visualLine),
        )

    val top = layout.getLineTop(visualLine)
    val bottom = layout.getLineBottom(visualLine)
    val peak = LANDSCAPE_INLINE_WORD_RISE_DP.dp.toPx()

    val segments =
        ArrayList<LandscapeInlineFloatingWordSegment>()

    for (wordIndex in line.words.indices) {
        if (wordIndex >= wordOffsets.size) {
            continue
        }

        val word = line.words[wordIndex]
        val start = max(wordOffsets[wordIndex], lineStart)
        val end =
            min(
                wordOffsets[wordIndex] + word.text.length,
                lineEnd,
            )

        if (start >= end) {
            continue
        }

        val lift =
            calculateLandscapeInlineWordLift(
                line = line,
                wordIndex = wordIndex,
                positionMs = positionMs,
            )

        if (lift <= 0.01f) {
            continue
        }

        val from =
            layout.landscapeInlineHorizontalPosition(
                offset = start,
                visualLine = visualLine,
            )

        val to =
            layout.landscapeInlineHorizontalPosition(
                offset = end,
                visualLine = visualLine,
            )

        val segmentLeft =
            min(from, to).coerceIn(left, right)

        val segmentRight =
            max(from, to).coerceIn(left, right)

        if (segmentRight <= segmentLeft) {
            continue
        }

        segments += LandscapeInlineFloatingWordSegment(
            left = segmentLeft,
            right = segmentRight,
            lift = lift,
        )
    }

    if (segments.isEmpty()) {
        drawContent()
        return
    }

    val flatPath = Path()
    var edge = left
    var hasFlatContent = false

    for (segment in segments.sortedBy { it.left }) {
        if (segment.left > edge) {
            flatPath.addRect(
                Rect(
                    left = edge,
                    top = top,
                    right = segment.left,
                    bottom = bottom,
                ),
            )

            hasFlatContent = true
        }

        edge = max(edge, segment.right)
    }

    if (edge < right) {
        flatPath.addRect(
            Rect(
                left = edge,
                top = top,
                right = right,
                bottom = bottom,
            ),
        )

        hasFlatContent = true
    }

    if (hasFlatContent) {
        clipPath(flatPath) {
            this@drawLandscapeInlineFloatingWords.drawContent()
        }
    }

    for (segment in segments) {
        clipRect(
            left = segment.left - 0.5f,
            top = top - peak - 1f,
            right = segment.right + 0.5f,
            bottom = bottom + 1f,
        ) {
            translate(
                top = -segment.lift * peak,
            ) {
                this@drawLandscapeInlineFloatingWords.drawContent()
            }
        }
    }
}

private data class LandscapeInlineFloatingWordSegment(
    val left: Float,
    val right: Float,
    val lift: Float,
)

private fun TextLayoutResult.landscapeInlineHorizontalPosition(
    offset: Int,
    visualLine: Int,
): Float {
    val lineStart = getLineStart(visualLine)
    val lineEnd =
        getLineEnd(
            visualLine,
            visibleEnd = true,
        )

    val left =
        min(
            getLineLeft(visualLine),
            getLineRight(visualLine),
        )

    val right =
        max(
            getLineLeft(visualLine),
            getLineRight(visualLine),
        )

    val textLength =
        layoutInput.text.length

    val directionIndex =
        lineStart.coerceIn(
            0,
            (textLength - 1).coerceAtLeast(0),
        )

    val isRtl =
        getParagraphDirection(directionIndex) ==
            ResolvedTextDirection.Rtl

    return when {
        offset <= lineStart ->
            if (isRtl) right else left

        offset >= lineEnd ->
            if (isRtl) left else right

        else ->
            getHorizontalPosition(
                offset,
                usePrimaryDirection = true,
            )
    }
}

private fun ContentDrawScope.drawLandscapeInlineRevealedText(
    layout: TextLayoutResult,
    revealedCharacters: Float,
) {
    val textLength =
        layout.layoutInput.text.length

    if (
        textLength <= 0 ||
        revealedCharacters <= 0f
    ) {
        return
    }

    if (revealedCharacters >= textLength) {
        drawContent()
        return
    }

    if (layout.lineCount <= 0) {
        return
    }

    val visualLine = 0
    val lineStart = layout.getLineStart(visualLine)
    val lineEnd =
        layout.getLineEnd(
            visualLine,
            visibleEnd = false,
        )

    if (revealedCharacters <= lineStart) {
        return
    }

    val lineLeft =
        min(
            layout.getLineLeft(visualLine),
            layout.getLineRight(visualLine),
        )

    val lineRight =
        max(
            layout.getLineLeft(visualLine),
            layout.getLineRight(visualLine),
        )

    val lineTop = layout.getLineTop(visualLine)
    val lineBottom = layout.getLineBottom(visualLine)

    if (
        lineRight <= lineLeft ||
        lineBottom <= lineTop
    ) {
        return
    }

    if (revealedCharacters >= lineEnd) {
        clipRect(
            left = lineLeft,
            top = lineTop,
            right = lineRight,
            bottom = lineBottom,
        ) {
            this@drawLandscapeInlineRevealedText.drawContent()
        }
        return
    }

    val offset =
        floor(revealedCharacters)
            .toInt()
            .coerceIn(lineStart, lineEnd)

    val characterProgress =
        (
            revealedCharacters -
                offset
            ).coerceIn(0f, 1f)

    val directionIndex =
        lineStart.coerceIn(
            0,
            (textLength - 1).coerceAtLeast(0),
        )

    val isRtl =
        layout.getParagraphDirection(directionIndex) ==
            ResolvedTextDirection.Rtl

    val currentX =
        layout.getHorizontalPosition(
            offset,
            usePrimaryDirection = true,
        )

    val nextOffset =
        (offset + 1).coerceAtMost(textLength)

    val nextX =
        if (
            nextOffset >= textLength ||
            layout.getLineForOffset(nextOffset) != visualLine
        ) {
            if (isRtl) lineLeft else lineRight
        } else {
            layout.getHorizontalPosition(
                nextOffset,
                usePrimaryDirection = true,
            )
        }

    val revealX =
        (
            currentX +
                (nextX - currentX) *
                characterProgress
            ).coerceIn(
                lineLeft,
                lineRight,
            )

    val clipLeft =
        if (isRtl) revealX else lineLeft

    val clipRight =
        if (isRtl) lineRight else revealX

    if (clipRight > clipLeft) {
        clipRect(
            left = clipLeft,
            top = lineTop,
            right = clipRight,
            bottom = lineBottom,
        ) {
            this@drawLandscapeInlineRevealedText.drawContent()
        }
    }
}