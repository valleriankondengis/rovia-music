package com.rovia.music.feature.player

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableLongState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.structuralEqualityPolicy
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.ResolvedTextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rovia.music.core.model.LyricLine
import com.rovia.music.core.model.SyncedLyrics
import kotlinx.coroutines.yield
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

private const val LYRIC_SETTLE_MS = 400
private const val LYRIC_LINE_SPACING_DP = 14

private const val LINE_SCROLL_MIN_MS = 350L
private const val LINE_SCROLL_MAX_MS = 500L
private const val LINE_STAGGER_STEPS = 3
private const val LINE_STAGGER_FRACTION = 0.06f

private const val LYRIC_FONT_SIZE_SP = 34
private const val LYRIC_LINE_HEIGHT_SP = 41

private const val WORD_RISE_DP = 2f
private const val WORD_RISE_MIN_MS = 250f
private const val WORD_RISE_MAX_MS = 700f
private const val WORD_SETTLE_MS = 700f
private const val WORD_LIFT_QUICK_MS = 150f
private const val WORD_LIFT_HELD_MS = 900f
private const val WORD_LIFT_FLOOR = 0.25f

private const val GAP_DOT_COUNT = 3
private const val GAP_ROW_HEIGHT_DP = 36

private const val GAP_MIN_DURATION_MS = 5_000L
private const val INITIAL_GAP_MIN_DURATION_MS = 5_000L
private const val GAP_DOT_REST_ALPHA = 0.25f
private const val GAP_REST_SCALE = 0.80f

/*
 * Small safety interval after the estimated lyric end.
 *
 * The five-second gap threshold is evaluated BEFORE this safety
 * interval is applied, so a genuine five-second gap can still
 * display the dots.
 */
private const val GAP_LINE_END_SAFETY_MS = 500L

/*
 * Estimate unsynchronized lyric duration from text length.
 *
 * This replaces the old percentage-based estimate that treated
 * almost the entire interval before the next lyric as sung content.
 * Long timestamp intervals can now contain a detectable instrumental gap.
 */
private const val UNSYNCED_BASE_DURATION_MS = 800L
private const val UNSYNCED_WORD_DURATION_MS = 700L
private const val UNSYNCED_CHARACTER_DURATION_MS = 18L
private const val UNSYNCED_MIN_LINE_DURATION_MS = 2_000L
private const val UNSYNCED_MAX_LINE_DURATION_MS = 10_000L

/*
 * Word-synchronized lines still need an estimated ending time for
 * their last word because that word has no following timestamp.
 */
private const val FINAL_WORD_BASE_DURATION_MS = 1_200L
private const val FINAL_WORD_CHARACTER_DURATION_MS = 65L
private const val FINAL_WORD_MAX_DURATION_MS = 2_500L

private val LYRIC_EASING =
    CubicBezierEasing(
        0.41f,
        0f,
        0.12f,
        0.99f,
    )

private val LYRIC_LINE_ALPHA =
    floatArrayOf(
        1f,
        0.80f,
        0.70f,
        0.58f,
        0.46f,
    )

/*
 * Gaussian blur levels adapted from BitChord.
 * The active lyric stays sharp while inactive lyrics become
 * progressively softer with their distance from the active row.
 */
private val LYRIC_LINE_BLUR =
    arrayOf(
        0.dp,
        1.dp,
        1.dp,
        1.7.dp,
        2.4.dp,
    )

private sealed interface LyricDisplayItem {
    val key: String

    data class LineItem(
        val sourceIndex: Int,
        val line: LyricLine,
    ) : LyricDisplayItem {
        override val key: String
            get() = "line-$sourceIndex-${line.startTimeMs}"
    }

    data class GapItem(
        val startTimeMs: Long,
        val endTimeMs: Long,
    ) : LyricDisplayItem {
        override val key: String
            get() = "gap-$startTimeMs-$endTimeMs"
    }
}

private data class LyricScrollPlan(
    val targetDisplayIndex: Int,
    val durationMs: Long,
)

private data class LyricScrollRun(
    val id: Int,
    val deltaPx: Float,
    val durationMs: Long,
    val focusDisplayIndex: Int,
)

private data class FloatingWordSegment(
    val visualLine: Int,
    val left: Float,
    val right: Float,
    val top: Float,
    val bottom: Float,
    val lift: Float,
)

@Composable
fun LyricViewer(
    lyrics: SyncedLyrics,
    positionMs: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    isPlaying: Boolean = true,
) {
    val listState = rememberLazyListState()

    val displayItems =
        remember(lyrics) {
            buildLyricDisplayItems(lyrics)
        }

    val lyricClock =
        remember(lyrics) {
            mutableLongStateOf(
                positionMs.coerceAtLeast(0L),
            )
        }

    var clockAnchorPositionMs by remember(lyrics) {
        mutableLongStateOf(positionMs.coerceAtLeast(0L))
    }

    var clockAnchorFrameTimeNanos by remember(lyrics) {
        mutableLongStateOf(0L)
    }

    var hasClockAnchor by remember(lyrics) {
        mutableStateOf(false)
    }

    LaunchedEffect(
        lyrics,
        positionMs,
        isPlaying,
    ) {
        val frameTimeNanos =
            withFrameNanos { it }

        clockAnchorPositionMs =
            positionMs.coerceAtLeast(0L)

        clockAnchorFrameTimeNanos =
            frameTimeNanos

        hasClockAnchor = true

        if (!isPlaying) {
            lyricClock.longValue =
                positionMs.coerceAtLeast(0L)
        }
    }

    /*
     * Drive word reveal, floating words and gap dots from the Compose
     * frame clock rather than relying only on periodic player updates.
     */
    LaunchedEffect(
        lyrics,
        isPlaying,
    ) {
        if (!isPlaying) {
            lyricClock.longValue =
                positionMs.coerceAtLeast(0L)

            return@LaunchedEffect
        }

        while (true) {
            withFrameNanos { frameTimeNanos ->
                if (!hasClockAnchor) {
                    lyricClock.longValue =
                        positionMs.coerceAtLeast(0L)
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

    val activeDisplayIndex by remember(
        displayItems,
        lyricClock,
    ) {
        derivedStateOf(structuralEqualityPolicy()) {
            findActiveDisplayIndex(
                items = displayItems,
                positionMs = lyricClock.longValue,
            )
        }
    }

    val scrollPlan by remember(
        displayItems,
        lyricClock,
    ) {
        derivedStateOf(structuralEqualityPolicy()) {
            buildScrollPlan(
                items = displayItems,
                positionMs = lyricClock.longValue,
            )
        }
    }

    var centeredMode by remember(lyrics) {
        mutableStateOf(false)
    }

    var scrollRun by remember(lyrics) {
        mutableStateOf(
            LyricScrollRun(
                id = 0,
                deltaPx = 0f,
                durationMs = LYRIC_SETTLE_MS.toLong(),
                focusDisplayIndex = -1,
            ),
        )
    }

    val scrollElapsedMs =
        remember(lyrics) {
            mutableFloatStateOf(0f)
        }

    LaunchedEffect(scrollRun.id) {
        if (scrollRun.id == 0) {
            scrollElapsedMs.floatValue = 0f
            return@LaunchedEffect
        }

        scrollElapsedMs.floatValue = 0f

        val startedAt =
            withFrameNanos { it }

        while (true) {
            val now =
                withFrameNanos { it }

            val elapsed =
                (
                    now - startedAt
                    ).coerceAtLeast(0L) / 1_000_000f

            scrollElapsedMs.floatValue =
                elapsed.coerceAtMost(
                    scrollRun.durationMs.toFloat(),
                )

            if (
                elapsed >= scrollRun.durationMs.toFloat()
            ) {
                break
            }
        }
    }

    BoxWithConstraints(
        modifier = modifier,
    ) {
        val centeredPadding = maxHeight / 2
        val density = LocalDensity.current

        LaunchedEffect(
            activeDisplayIndex,
            centeredMode,
        ) {
            if (
                centeredMode ||
                activeDisplayIndex < 0 ||
                activeDisplayIndex >= displayItems.size
            ) {
                return@LaunchedEffect
            }

            val layoutInfo = listState.layoutInfo

            val viewportHeight =
                (
                    layoutInfo.viewportEndOffset -
                        layoutInfo.viewportStartOffset
                    ).coerceAtLeast(1)

            val viewportCenter =
                layoutInfo.viewportStartOffset +
                    viewportHeight / 2

            val activeItem =
                layoutInfo.visibleItemsInfo
                    .firstOrNull {
                        it.index == activeDisplayIndex
                    }

            if (activeItem != null) {
                val activeItemCenter =
                    activeItem.offset +
                        activeItem.size / 2

                if (activeItemCenter >= viewportCenter) {
                    centeredMode = true
                }
            } else if (activeDisplayIndex > 0) {
                centeredMode = true
            }
        }

        /*
         * Keep the top padding at zero when entering centered mode.
         * Bottom padding is sufficient to let the final lyrics reach
         * the center without changing the position of visible rows.
         */
        LaunchedEffect(
            scrollPlan.targetDisplayIndex,
            centeredMode,
        ) {
            val targetIndex =
                scrollPlan.targetDisplayIndex

            if (
                !centeredMode ||
                targetIndex < 0 ||
                targetIndex >= displayItems.size
            ) {
                return@LaunchedEffect
            }

            val durationMs =
                scrollPlan.durationMs
                    .coerceIn(
                        LINE_SCROLL_MIN_MS,
                        LINE_SCROLL_MAX_MS,
                    )
                    .toInt()

            yield()

            var layoutInfo = listState.layoutInfo

            var targetItem =
                layoutInfo.visibleItemsInfo
                    .firstOrNull {
                        it.index == targetIndex
                    }

            var usedOffscreenFallback = false

            if (targetItem == null) {
                /*
                 * Estimate the off-screen target's position so the first
                 * scroll aims near the center rather than the top.
                 */
                val initialViewportHeight =
                    (
                        layoutInfo.viewportEndOffset -
                            layoutInfo.viewportStartOffset
                        ).coerceAtLeast(1)

                val estimatedHalfItemHeightPx =
                    with(density) {
                        when (displayItems[targetIndex]) {
                            is LyricDisplayItem.GapItem ->
                                18.dp.toPx()

                            is LyricDisplayItem.LineItem ->
                                LYRIC_LINE_HEIGHT_SP.sp.toPx() +
                                    7.dp.toPx()
                        }
                    }

                val desiredTopOffset =
                    (
                        initialViewportHeight / 2f -
                            estimatedHalfItemHeightPx
                        ).coerceAtLeast(0f)

                listState.animateScrollToItem(
                    index = targetIndex,
                    scrollOffset = -desiredTopOffset.toInt(),
                )

                usedOffscreenFallback = true

                yield()

                layoutInfo = listState.layoutInfo

                targetItem =
                    layoutInfo.visibleItemsInfo
                        .firstOrNull {
                            it.index == targetIndex
                        }
            }

            if (targetItem == null) {
                return@LaunchedEffect
            }

            val viewportHeight =
                (
                    layoutInfo.viewportEndOffset -
                        layoutInfo.viewportStartOffset
                    ).coerceAtLeast(1)

            val viewportCenter =
                layoutInfo.viewportStartOffset +
                    viewportHeight / 2f

            val targetCenter =
                targetItem.offset +
                    targetItem.size / 2f

            val deltaPx =
                targetCenter - viewportCenter

            val minimumDeltaPx =
                if (usedOffscreenFallback) {
                    12f
                } else {
                    1f
                }

            if (abs(deltaPx) <= minimumDeltaPx) {
                return@LaunchedEffect
            }

            val animationDurationMs =
                if (usedOffscreenFallback) {
                    val halfViewport =
                        (viewportHeight / 2f)
                            .coerceAtLeast(1f)

                    val correctionFraction =
                        (abs(deltaPx) / halfViewport)
                            .coerceIn(0.15f, 0.50f)

                    (durationMs * correctionFraction)
                        .toInt()
                        .coerceIn(100, durationMs)
                } else {
                    durationMs
                }

            scrollRun =
                LyricScrollRun(
                    id = scrollRun.id + 1,
                    deltaPx = deltaPx,
                    durationMs = animationDurationMs.toLong(),
                    focusDisplayIndex = targetIndex,
                )

            listState.animateScrollBy(
                value = deltaPx,
                animationSpec = tween(
                    durationMillis = animationDurationMs,
                    easing = LYRIC_EASING,
                ),
            )
        }

        val focusDisplayIndex =
            scrollPlan.targetDisplayIndex
                .takeIf {
                    it in displayItems.indices
                }
                ?: activeDisplayIndex

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(0.dp),
            contentPadding =
                if (centeredMode) {
                    PaddingValues(
                        bottom = centeredPadding,
                    )
                } else {
                    PaddingValues(0.dp)
                },
        ) {
            itemsIndexed(
                items = displayItems,
                key = { _, item -> item.key },
            ) { displayIndex, item ->

                val isActive =
                    displayIndex == activeDisplayIndex

                val distanceFromFocus =
                    if (focusDisplayIndex >= 0) {
                        abs(displayIndex - focusDisplayIndex)
                    } else {
                        LYRIC_LINE_ALPHA.lastIndex
                    }

                val alphaIndex =
                    distanceFromFocus.coerceAtMost(
                        LYRIC_LINE_ALPHA.lastIndex,
                    )

                val rowAlpha by animateFloatAsState(
                    targetValue = LYRIC_LINE_ALPHA[alphaIndex],
                    animationSpec = tween(
                        durationMillis = LYRIC_SETTLE_MS,
                        easing = LYRIC_EASING,
                    ),
                    label = "lyric-row-alpha",
                )

                val rowScale by animateFloatAsState(
                    targetValue =
                        if (isActive) 1f else 0.98f,
                    animationSpec = tween(
                        durationMillis = LYRIC_SETTLE_MS,
                        easing = LYRIC_EASING,
                    ),
                    label = "lyric-row-scale",
                )

                /*
                 * The active line stays sharp. Inactive lines become
                 * progressively softer according to the active row.
                 */
                val blurDistance =
                    if (activeDisplayIndex >= 0) {
                        abs(displayIndex - activeDisplayIndex)
                    } else {
                        0
                    }

                val rowBlur by animateDpAsState(
                    targetValue =
                        when {
                            item is LyricDisplayItem.GapItem ->
                                0.dp

                            isActive ->
                                0.dp

                            else ->
                                LYRIC_LINE_BLUR[
                                    blurDistance.coerceAtMost(
                                        LYRIC_LINE_BLUR.lastIndex,
                                    )
                                ]
                        },
                    animationSpec = tween(
                        durationMillis = LYRIC_SETTLE_MS,
                        easing = LYRIC_EASING,
                    ),
                    label = "lyric-row-blur",
                )

                val run = scrollRun

                val behind =
                    if (run.deltaPx >= 0f) {
                        displayIndex - run.focusDisplayIndex
                    } else {
                        run.focusDisplayIndex - displayIndex
                    }

                val staggerDelayMs =
                    behind
                        .coerceIn(0, LINE_STAGGER_STEPS)
                        .toFloat() *
                        LINE_STAGGER_FRACTION *
                        run.durationMs

                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .graphicsLayer {
                                alpha = rowAlpha
                                scaleX = rowScale
                                scaleY = rowScale

                                transformOrigin =
                                    TransformOrigin(
                                        pivotFractionX = 0f,
                                        pivotFractionY = 0.5f,
                                    )

                                translationY =
                                    if (
                                        staggerDelayMs <= 0f ||
                                        run.id == 0
                                    ) {
                                        0f
                                    } else {
                                        val elapsed =
                                            scrollElapsedMs.floatValue

                                        val wholeProgress =
                                            (
                                                elapsed /
                                                    run.durationMs
                                                        .coerceAtLeast(1L)
                                                        .toFloat()
                                                ).coerceIn(0f, 1f)

                                        val delayedProgress =
                                            (
                                                (elapsed - staggerDelayMs) /
                                                    run.durationMs
                                                        .coerceAtLeast(1L)
                                                        .toFloat()
                                                ).coerceIn(0f, 1f)

                                        run.deltaPx * (
                                            LYRIC_EASING.transform(wholeProgress) -
                                                LYRIC_EASING.transform(delayedProgress)
                                            )
                                    }
                            }
                            .blur(
                                radius = rowBlur,
                                edgeTreatment = BlurredEdgeTreatment.Unbounded,
                            ),
                ) {
                    when (item) {
                        is LyricDisplayItem.LineItem -> {
                            val activeWordIndex =
                                if (isActive) {
                                    findActiveWordIndex(
                                        line = item.line,
                                        positionMs = positionMs,
                                    )
                                } else {
                                    -1
                                }

                            LyricLineContent(
                                line = item.line,
                                activeWordIndex = activeWordIndex,
                                isActive = isActive,
                                lyricClock = lyricClock,
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            onSeek(item.line.startTimeMs)
                                        }
                                        .padding(
                                            vertical =
                                                LYRIC_LINE_SPACING_DP.dp / 2,
                                        ),
                            )
                        }

                        is LyricDisplayItem.GapItem -> {
                            LyricGapContent(
                                gap = item,
                                lyricClock = lyricClock,
                                isActive = isActive,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LyricLineContent(
    line: LyricLine,
    activeWordIndex: Int,
    isActive: Boolean,
    lyricClock: MutableLongState,
    modifier: Modifier = Modifier,
) {
    val inactiveColor =
        MaterialTheme.colorScheme.onSurfaceVariant
            .copy(alpha = 0.45f)

    val activeColor =
        MaterialTheme.colorScheme.onSurface

    val activeWordColor =
        playerArtworkAccentColor()
            ?: MaterialTheme.colorScheme.primary

    val lineColor by animateColorAsState(
        targetValue =
            if (isActive) activeColor else inactiveColor,
        animationSpec = tween(
            durationMillis = LYRIC_SETTLE_MS,
            easing = LYRIC_EASING,
        ),
        label = "lyric-line-color",
    )

    val style =
        MaterialTheme.typography.titleLarge.copy(
            fontSize = LYRIC_FONT_SIZE_SP.sp,
            lineHeight = LYRIC_LINE_HEIGHT_SP.sp,
            fontWeight = FontWeight.ExtraBold,
        )

    val wordOffsets =
        remember(line) {
            buildWordStartOffsets(line)
        }

    val isWordSynced =
        remember(line) {
            isWordSyncedLine(line)
        }

    var textWidthPx by remember(line) {
        mutableIntStateOf(0)
    }

    var textLayoutResult by remember(line) {
        mutableStateOf<TextLayoutResult?>(null)
    }

    val onTextLayout: (TextLayoutResult) -> Unit = { result ->
        if (textWidthPx != result.size.width) {
            textWidthPx = result.size.width
        }

        val previousLayout = textLayoutResult

        if (
            previousLayout == null ||
            previousLayout.layoutInput != result.layoutInput ||
            previousLayout.size != result.size ||
            previousLayout.lineCount != result.lineCount
        ) {
            textLayoutResult = result
        }
    }

    val regularText =
        remember(
            line,
            isActive,
            activeWordIndex,
            inactiveColor,
            activeColor,
            activeWordColor,
            lineColor,
        ) {
            buildLyricAnnotatedString(
                line = line,
                isActive = isActive,
                activeWordIndex = activeWordIndex,
                inactiveColor = inactiveColor,
                activeColor = activeColor,
                activeWordColor = activeWordColor,
                lineColor = lineColor,
            )
        }

    val dimText =
        remember(
            line,
            inactiveColor,
        ) {
            buildLyricAnnotatedString(
                line = line,
                isActive = false,
                activeWordIndex = -1,
                inactiveColor = inactiveColor,
                activeColor = activeColor,
                activeWordColor = activeWordColor,
                lineColor = inactiveColor,
                forceInactive = true,
            )
        }

    val sweptText =
        remember(
            line,
            activeWordIndex,
            inactiveColor,
            activeColor,
            activeWordColor,
        ) {
            buildLyricAnnotatedString(
                line = line,
                isActive = true,
                activeWordIndex = activeWordIndex,
                inactiveColor = inactiveColor,
                activeColor = activeColor,
                activeWordColor = activeWordColor,
                lineColor = activeColor,
            )
        }

    val density = LocalDensity.current

    BoxWithConstraints(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.CenterStart,
    ) {
        val availableWidthPx =
            with(density) {
                maxWidth.toPx()
            }

        val safeWidthPx =
            (
                availableWidthPx -
                    with(density) {
                        12.dp.toPx()
                    }
                ).coerceAtLeast(1f)

        val adaptiveScale =
            if (isActive && textWidthPx > 0) {
                min(
                    1.10f,
                    safeWidthPx / textWidthPx.toFloat(),
                ).coerceAtLeast(1f)
            } else {
                1f
            }

        val textScale by animateFloatAsState(
            targetValue = adaptiveScale,
            animationSpec = tween(
                durationMillis = LYRIC_SETTLE_MS,
                easing = LYRIC_EASING,
            ),
            label = "lyric-text-scale",
        )

        val textModifier =
            Modifier.graphicsLayer {
                scaleX = textScale
                scaleY = textScale
                transformOrigin =
                    TransformOrigin(
                        pivotFractionX = 0f,
                        pivotFractionY = 0.5f,
                    )
            }

        /*
         * Apply matching floating geometry to each text layer while
         * retaining timestamp-driven reveal on the foreground.
         */
        val floatingModifier =
            Modifier.drawWithContent {
                val layout = textLayoutResult

                if (layout == null || line.words.isEmpty()) {
                    drawContent()
                } else {
                    drawFloatingWords(
                        layout = layout,
                        line = line,
                        wordOffsets = wordOffsets,
                        positionMs = lyricClock.longValue,
                    )
                }
            }

        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.CenterStart,
        ) {
            when {
                isWordSynced && isActive -> {
                    Text(
                        text = dimText,
                        modifier = textModifier.then(floatingModifier),
                        color = lineColor,
                        style = style,
                        maxLines = 3,
                        onTextLayout = onTextLayout,
                    )

                    Text(
                        text = sweptText,
                        modifier =
                            textModifier
                                .then(floatingModifier)
                                .drawWithContent {
                                    val layout = textLayoutResult

                                    if (layout == null) {
                                        drawContent()
                                    } else {
                                        drawRevealedText(
                                            layout = layout,
                                            revealedCharacters =
                                                calculateRevealedCharacters(
                                                    line = line,
                                                    wordOffsets = wordOffsets,
                                                    positionMs = lyricClock.longValue,
                                                ),
                                        )
                                    }
                                },
                        color = lineColor,
                        style = style,
                        maxLines = 3,
                    )
                }

                isWordSynced -> {
                    Text(
                        text = dimText,
                        modifier = textModifier.then(floatingModifier),
                        color = lineColor,
                        style = style,
                        maxLines = 3,
                        onTextLayout = onTextLayout,
                    )
                }

                else -> {
                    Text(
                        text = regularText,
                        modifier = textModifier,
                        color = lineColor,
                        style = style,
                        maxLines = 3,
                        onTextLayout = onTextLayout,
                    )
                }
            }
        }
    }
}

@Composable
private fun LyricGapContent(
    gap: LyricDisplayItem.GapItem,
    lyricClock: MutableLongState,
    isActive: Boolean,
    modifier: Modifier = Modifier,
) {
    val rowHeight by animateFloatAsState(
        targetValue = if (isActive) 1f else 0f,
        animationSpec = tween(
            durationMillis = if (isActive) 400 else 350,
            easing = LYRIC_EASING,
        ),
        label = "lyric-gap-height-factor",
    )

    val accentColor =
        playerArtworkAccentColor()
            ?: MaterialTheme.colorScheme.primary

    val dotAlpha by animateFloatAsState(
        targetValue = if (isActive) 1f else 0f,
        animationSpec = tween(
            durationMillis = 300,
            easing = LYRIC_EASING,
        ),
        label = "lyric-gap-alpha",
    )

    Box(
        modifier = modifier.height(
            GAP_ROW_HEIGHT_DP.dp * rowHeight,
        ),
        contentAlignment = Alignment.CenterStart,
    ) {
        Canvas(
            modifier =
                Modifier
                    .padding(start = 8.dp)
                    .size(
                        width = 48.dp,
                        height = 12.dp,
                    )
                    .graphicsLayer {
                        alpha = dotAlpha
                    },
        ) {
            val now = lyricClock.longValue

            val progress =
                (
                    (now - gap.startTimeMs).toFloat() /
                        (
                            gap.endTimeMs -
                                gap.startTimeMs
                            ).coerceAtLeast(1L).toFloat()
                    ).coerceIn(0f, 1f)

            val radius = 4.dp.toPx()
            val dotSpacing = 6.dp.toPx()
            val dotStride = radius * 2f + dotSpacing

            repeat(GAP_DOT_COUNT) { dotIndex ->
                val lit =
                    (
                        progress * GAP_DOT_COUNT -
                            dotIndex
                        ).coerceIn(0f, 1f)

                val alpha =
                    GAP_DOT_REST_ALPHA +
                        (1f - GAP_DOT_REST_ALPHA) * lit

                val dotScale =
                    GAP_REST_SCALE +
                        (1f - GAP_REST_SCALE) * lit

                drawCircle(
                    color = accentColor.copy(alpha = alpha),
                    radius = radius * dotScale,
                    center = Offset(
                        x = radius + dotIndex * dotStride,
                        y = size.height / 2f,
                    ),
                )
            }
        }
    }
}

private fun buildLyricDisplayItems(
    lyrics: SyncedLyrics,
): List<LyricDisplayItem> {
    if (lyrics.lines.isEmpty()) {
        return emptyList()
    }

    val result = mutableListOf<LyricDisplayItem>()
    val firstLine = lyrics.lines.first()

    /*
     * Initial silence is based on a known timestamp, so no line-duration
     * estimate is necessary.
     */
    if (
        firstLine.startTimeMs >=
        INITIAL_GAP_MIN_DURATION_MS
    ) {
        result += LyricDisplayItem.GapItem(
            startTimeMs = 0L,
            endTimeMs = firstLine.startTimeMs,
        )
    }

    lyrics.lines.forEachIndexed { index, line ->
        result += LyricDisplayItem.LineItem(
            sourceIndex = index,
            line = line,
        )

        val nextLine =
            lyrics.lines.getOrNull(index + 1)
                ?: return@forEachIndexed

        if (nextLine.startTimeMs <= line.startTimeMs) {
            return@forEachIndexed
        }

        val estimatedLineEnd =
            estimateLineEndTimeMs(
                line = line,
                nextLineStartMs = nextLine.startTimeMs,
            )

        /*
         * IMPORTANT:
         *
         * Evaluate the full estimated gap before adding the safety delay.
         * Otherwise, a five-second instrumental break could fail the
         * five-second condition simply because of the extra safety margin.
         */
        val estimatedGapDuration =
            nextLine.startTimeMs - estimatedLineEnd

        if (estimatedGapDuration >= GAP_MIN_DURATION_MS) {
            val gapStartTimeMs =
                (
                    estimatedLineEnd +
                        GAP_LINE_END_SAFETY_MS
                    ).coerceAtMost(nextLine.startTimeMs)

            if (gapStartTimeMs < nextLine.startTimeMs) {
                result += LyricDisplayItem.GapItem(
                    startTimeMs = gapStartTimeMs,
                    endTimeMs = nextLine.startTimeMs,
                )
            }
        }
    }

    return result
}

private fun findActiveDisplayIndex(
    items: List<LyricDisplayItem>,
    positionMs: Long,
): Int {
    for (index in items.indices) {
        val item = items[index]

        if (
            item is LyricDisplayItem.GapItem &&
            positionMs >= item.startTimeMs &&
            positionMs < item.endTimeMs
        ) {
            return index
        }
    }

    var activeIndex = -1

    for (index in items.indices) {
        val item = items[index]

        if (item is LyricDisplayItem.LineItem) {
            if (item.line.startTimeMs <= positionMs) {
                activeIndex = index
            } else {
                break
            }
        }
    }

    return activeIndex
}

private fun buildScrollPlan(
    items: List<LyricDisplayItem>,
    positionMs: Long,
): LyricScrollPlan {
    val activeIndex =
        findActiveDisplayIndex(
            items = items,
            positionMs = positionMs,
        )

    val activeItem =
        items.getOrNull(activeIndex)

    if (activeItem is LyricDisplayItem.GapItem) {
        val nextLineIndex =
            (activeIndex + 1 until items.size)
                .firstOrNull {
                    items[it] is LyricDisplayItem.LineItem
                }

        if (nextLineIndex == null) {
            return LyricScrollPlan(
                targetDisplayIndex = activeIndex,
                durationMs = LINE_SCROLL_MIN_MS,
            )
        }

        val nextLine =
            items[nextLineIndex] as LyricDisplayItem.LineItem

        val leadMs =
            (
                activeItem.endTimeMs -
                    activeItem.startTimeMs
                ).coerceAtLeast(0L)
                .coerceIn(
                    LINE_SCROLL_MIN_MS,
                    LINE_SCROLL_MAX_MS,
                )

        val targetIndex =
            if (
                nextLine.line.startTimeMs <=
                positionMs + leadMs
            ) {
                nextLineIndex
            } else {
                activeIndex
            }

        return LyricScrollPlan(
            targetDisplayIndex = targetIndex,
            durationMs = leadMs,
        )
    }

    var currentLineIndex = -1
    var currentLine: LyricDisplayItem.LineItem? = null

    for (index in items.indices) {
        val item = items[index]

        if (
            item is LyricDisplayItem.LineItem &&
            item.line.startTimeMs <= positionMs
        ) {
            currentLineIndex = index
            currentLine = item
        }
    }

    if (currentLine == null) {
        val firstLineIndex =
            items.indexOfFirst {
                it is LyricDisplayItem.LineItem
            }

        if (firstLineIndex < 0) {
            return LyricScrollPlan(
                targetDisplayIndex = -1,
                durationMs = LINE_SCROLL_MIN_MS,
            )
        }

        val firstLine =
            items[firstLineIndex] as LyricDisplayItem.LineItem

        val targetIndex =
            if (
                firstLine.line.startTimeMs <=
                positionMs + LINE_SCROLL_MAX_MS
            ) {
                firstLineIndex
            } else {
                -1
            }

        return LyricScrollPlan(
            targetDisplayIndex = targetIndex,
            durationMs = LINE_SCROLL_MAX_MS,
        )
    }

    var nextLine: LyricDisplayItem.LineItem? = null

    for (index in currentLineIndex + 1 until items.size) {
        val item = items[index]

        if (item is LyricDisplayItem.LineItem) {
            nextLine = item
            break
        }
    }

    if (nextLine == null) {
        return LyricScrollPlan(
            targetDisplayIndex = currentLineIndex,
            durationMs = LINE_SCROLL_MIN_MS,
        )
    }

    val estimatedLineEnd =
        estimateLineEndTimeMs(
            line = currentLine.line,
            nextLineStartMs = nextLine.line.startTimeMs,
        )

    val interLineGap =
        (
            nextLine.line.startTimeMs -
                estimatedLineEnd
            ).coerceAtLeast(0L)

    val leadMs =
        interLineGap.coerceIn(
            LINE_SCROLL_MIN_MS,
            LINE_SCROLL_MAX_MS,
        )

    val targetIndex =
        if (
            nextLine.line.startTimeMs <=
            positionMs + leadMs
        ) {
            items.indexOf(nextLine)
        } else {
            currentLineIndex
        }

    return LyricScrollPlan(
        targetDisplayIndex = targetIndex,
        durationMs = leadMs,
    )
}

private fun isWordSyncedLine(
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

private fun buildWordStartOffsets(
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

private fun inferredWordEndTimeMs(
    line: LyricLine,
    wordIndex: Int,
): Long {
    val word =
        line.words.getOrNull(wordIndex)
            ?: return 0L

    val nextWord =
        line.words.getOrNull(wordIndex + 1)

    if (
        nextWord != null &&
        nextWord.startTimeMs > word.startTimeMs
    ) {
        return nextWord.startTimeMs
    }

    return word.startTimeMs +
        estimateWordDurationMs(
            line = line,
            wordIndex = wordIndex,
        )
}

private fun calculateWordLift(
    line: LyricLine,
    wordIndex: Int,
    positionMs: Long,
): Float {
    val word =
        line.words.getOrNull(wordIndex)
            ?: return 0f

    val inferredEnd =
        inferredWordEndTimeMs(
            line = line,
            wordIndex = wordIndex,
        )

    val wordDuration =
        (
            inferredEnd -
                word.startTimeMs
            ).coerceAtLeast(1L)

    val riseDuration =
        wordDuration.toFloat().coerceIn(
            WORD_RISE_MIN_MS,
            WORD_RISE_MAX_MS,
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
                    WORD_SETTLE_MS
            ).coerceIn(0f, 1f)

    val heldThrough =
        (
            (wordDuration.toFloat() - WORD_LIFT_QUICK_MS) /
                (WORD_LIFT_HELD_MS - WORD_LIFT_QUICK_MS)
            ).coerceIn(0f, 1f)

    val liftHeight =
        WORD_LIFT_FLOOR +
            (1f - WORD_LIFT_FLOOR) *
            smoothStep(heldThrough)

    return liftHeight *
        smoothStep(
            min(rising, falling),
        )
}

private fun smoothStep(
    fraction: Float,
): Float {
    val value = fraction.coerceIn(0f, 1f)
    return value * value * (3f - 2f * value)
}

private fun ContentDrawScope.drawFloatingWords(
    layout: TextLayoutResult,
    line: LyricLine,
    wordOffsets: List<Int>,
    positionMs: Long,
) {
    if (line.words.isEmpty()) {
        drawContent()
        return
    }

    val peak =
        WORD_RISE_DP.dp.toPx()

    val textLength =
        layout.layoutInput.text.length

    if (textLength == 0) {
        drawContent()
        return
    }

    val segments =
        ArrayList<FloatingWordSegment>()

    for (visualLine in 0 until layout.lineCount) {
        val lineStart =
            layout.getLineStart(visualLine)

        val lineEnd =
            layout.getLineEnd(
                visualLine,
                visibleEnd = true,
            )

        if (lineStart >= lineEnd) {
            continue
        }

        val physicalLeft =
            min(
                layout.getLineLeft(visualLine),
                layout.getLineRight(visualLine),
            )

        val physicalRight =
            max(
                layout.getLineLeft(visualLine),
                layout.getLineRight(visualLine),
            )

        val top =
            layout.getLineTop(visualLine)

        val bottom =
            layout.getLineBottom(visualLine)

        for (wordIndex in line.words.indices) {
            val word = line.words[wordIndex]

            if (wordIndex >= wordOffsets.size) {
                continue
            }

            val wordStart = wordOffsets[wordIndex]
            val wordEnd = wordStart + word.text.length

            val start = max(wordStart, lineStart)
            val end = min(wordEnd, lineEnd)

            if (start >= end) {
                continue
            }

            val lift =
                calculateWordLift(
                    line = line,
                    wordIndex = wordIndex,
                    positionMs = positionMs,
                )

            if (lift <= 0.01f) {
                continue
            }

            val from =
                layout.horizontalPositionOnLine(
                    offset = start,
                    visualLine = visualLine,
                )

            val to =
                layout.horizontalPositionOnLine(
                    offset = end,
                    visualLine = visualLine,
                )

            val left =
                min(from, to).coerceIn(
                    physicalLeft,
                    physicalRight,
                )

            val right =
                max(from, to).coerceIn(
                    physicalLeft,
                    physicalRight,
                )

            if (right <= left) {
                continue
            }

            segments += FloatingWordSegment(
                visualLine = visualLine,
                left = left,
                right = right,
                top = top,
                bottom = bottom,
                lift = lift,
            )
        }
    }

    if (segments.isEmpty()) {
        drawContent()
        return
    }

    val flatPath = Path()
    var hasFlatContent = false

    for (visualLine in 0 until layout.lineCount) {
        val lineStart =
            layout.getLineStart(visualLine)

        val lineEnd =
            layout.getLineEnd(
                visualLine,
                visibleEnd = true,
            )

        if (lineStart >= lineEnd) {
            continue
        }

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

        val top =
            layout.getLineTop(visualLine)

        val bottom =
            layout.getLineBottom(visualLine)

        val rowSegments =
            segments
                .filter { it.visualLine == visualLine }
                .sortedBy { it.left }

        if (rowSegments.isEmpty()) {
            flatPath.addRect(
                Rect(
                    left = left,
                    top = top,
                    right = right,
                    bottom = bottom,
                ),
            )

            hasFlatContent = true
            continue
        }

        var edge = left

        for (segment in rowSegments) {
            val segmentLeft =
                segment.left.coerceIn(left, right)

            val segmentRight =
                segment.right.coerceIn(left, right)

            if (segmentLeft > edge) {
                flatPath.addRect(
                    Rect(
                        left = edge,
                        top = top,
                        right = segmentLeft,
                        bottom = bottom,
                    ),
                )

                hasFlatContent = true
            }

            edge = max(edge, segmentRight)
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
    }

    if (hasFlatContent) {
        clipPath(flatPath) {
            this@drawFloatingWords.drawContent()
        }
    }

    for (segment in segments) {
        clipRect(
            left = segment.left - 0.5f,
            top = segment.top - peak - 1f,
            right = segment.right + 0.5f,
            bottom = segment.bottom + 1f,
        ) {
            translate(
                top = -segment.lift * peak,
            ) {
                this@drawFloatingWords.drawContent()
            }
        }
    }
}

private fun TextLayoutResult.horizontalPositionOnLine(
    offset: Int,
    visualLine: Int,
): Float {
    val lineStart =
        getLineStart(visualLine)

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

private fun estimateWordDurationMs(
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

/*
 * Estimate the end of a lyric line without assuming that the singer
 * occupies most of the entire interval before the next lyric.
 *
 * For unsynchronized lyrics, the estimate is derived from word count
 * and character count, with conservative lower and upper bounds.
 *
 * For word-synchronized lyrics, known timestamps are used for the words,
 * while the final word gets a length-based duration estimate.
 */
private fun estimateLineEndTimeMs(
    line: LyricLine,
    nextLineStartMs: Long,
): Long {
    val safeNextLineStartMs =
        nextLineStartMs.coerceAtLeast(line.startTimeMs)

    if (safeNextLineStartMs <= line.startTimeMs) {
        return line.startTimeMs
    }

    if (isWordSyncedLine(line)) {
        val lastWordIndex = line.words.lastIndex

        val lastWord =
            line.words.getOrNull(lastWordIndex)
                ?: return line.startTimeMs

        val estimatedDurationFromText =
            (
                FINAL_WORD_BASE_DURATION_MS +
                    lastWord.text.count { !it.isWhitespace() } *
                    FINAL_WORD_CHARACTER_DURATION_MS
                ).coerceAtMost(FINAL_WORD_MAX_DURATION_MS)

        val estimatedDurationFromPreviousWord =
            estimateWordDurationMs(
                line = line,
                wordIndex = lastWordIndex,
            )

        val finalWordDuration =
            max(
                estimatedDurationFromText,
                estimatedDurationFromPreviousWord,
            )

        val estimatedFinalWordEnd =
            (
                lastWord.startTimeMs +
                    finalWordDuration
                ).coerceAtMost(safeNextLineStartMs)

        return estimatedFinalWordEnd.coerceIn(
            minimumValue = line.startTimeMs,
            maximumValue = safeNextLineStartMs,
        )
    }

    /*
     * Join the text first because a line may be represented as either
     * one text block or multiple word objects without word-level timing.
     * Both forms should produce a comparable duration estimate.
     */
    val lineText =
        line.words
            .joinToString(separator = " ") {
                it.text.trim()
            }
            .trim()

    val wordCount =
        if (lineText.isBlank()) {
            line.words.size.coerceAtLeast(1)
        } else {
            lineText
                .split(Regex("\\s+"))
                .count { it.isNotBlank() }
                .coerceAtLeast(1)
        }

    val characterCount =
        lineText.count {
            !it.isWhitespace()
        }

    val estimatedDurationMs =
        (
            UNSYNCED_BASE_DURATION_MS +
                wordCount * UNSYNCED_WORD_DURATION_MS +
                characterCount * UNSYNCED_CHARACTER_DURATION_MS
            ).coerceIn(
                minimumValue = UNSYNCED_MIN_LINE_DURATION_MS,
                maximumValue = UNSYNCED_MAX_LINE_DURATION_MS,
            )

    return (
        line.startTimeMs +
            estimatedDurationMs
        ).coerceAtMost(safeNextLineStartMs)
}

private fun buildLyricAnnotatedString(
    line: LyricLine,
    isActive: Boolean,
    activeWordIndex: Int,
    inactiveColor: Color,
    activeColor: Color,
    activeWordColor: Color,
    lineColor: Color,
    forceInactive: Boolean = false,
): AnnotatedString {
    return buildAnnotatedString {
        line.words.forEachIndexed { index, word ->
            val wordColor =
                when {
                    forceInactive ->
                        inactiveColor

                    !isActive ->
                        lineColor

                    activeWordIndex < 0 ->
                        inactiveColor

                    index == activeWordIndex ->
                        activeWordColor

                    index < activeWordIndex ->
                        activeColor

                    else ->
                        inactiveColor
                }

            pushStyle(
                SpanStyle(
                    color = wordColor,
                    fontWeight = FontWeight.ExtraBold,
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

/*
 * There is deliberately only one implementation of this function.
 * It is shared by the active line's progressive reveal.
 */
private fun calculateRevealedCharacters(
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
            estimateWordDurationMs(
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

private fun ContentDrawScope.drawRevealedText(
    layout: TextLayoutResult,
    revealedCharacters: Float,
) {
    val textLength =
        layout.layoutInput.text.length

    if (
        textLength == 0 ||
        revealedCharacters <= 0f
    ) {
        return
    }

    if (revealedCharacters >= textLength) {
        this.drawContent()
        return
    }

    val revealPath = Path()
    var hasReveal = false

    for (visualLine in 0 until layout.lineCount) {
        val lineStart =
            layout.getLineStart(visualLine)

        val lineEnd =
            layout.getLineEnd(
                visualLine,
                visibleEnd = false,
            )

        if (revealedCharacters <= lineStart) {
            continue
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

        val lineTop =
            layout.getLineTop(visualLine)

        val lineBottom =
            layout.getLineBottom(visualLine)

        if (
            lineRight <= lineLeft ||
            lineBottom <= lineTop
        ) {
            continue
        }

        if (revealedCharacters >= lineEnd) {
            revealPath.addRect(
                Rect(
                    left = lineLeft,
                    top = lineTop,
                    right = lineRight,
                    bottom = lineBottom,
                ),
            )

            hasReveal = true
            continue
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
            revealPath.addRect(
                Rect(
                    left = clipLeft,
                    top = lineTop,
                    right = clipRight,
                    bottom = lineBottom,
                ),
            )

            hasReveal = true
        }
    }

    if (hasReveal) {
        clipPath(revealPath) {
            this@drawRevealedText.drawContent()
        }
    }
}

private fun findActiveWordIndex(
    line: LyricLine,
    positionMs: Long,
): Int {
    if (line.words.isEmpty()) {
        return -1
    }

    var activeIndex = -1

    for (index in line.words.indices) {
        val word = line.words[index]

        if (word.startTimeMs <= positionMs) {
            activeIndex = index
        } else {
            break
        }
    }

    return activeIndex
}