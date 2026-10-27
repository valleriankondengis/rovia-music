package com.rovia.music.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.AbsoluteAlignment
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.yield
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

data class LyricWord(
    val text: String,
    val startTimeMs: Long,
)

data class LyricLine(
    val startTimeMs: Long,
    val words: List<LyricWord>,
    /** Stable identity such as v1/v2; it is metadata and is never rendered as lyric text. */
    val singerId: String? = null,
    /** True for an independent backing-vocal cue with its own timeline. */
    val isBackground: Boolean = false,
    /** Optional explicit cue/line end, when supplied by enhanced LRC. */
    val endTimeMs: Long? = null,
    /** Background cues are attached to the closest preceding main vocal line. */
    val backgroundVocals: List<LyricLine> = emptyList(),
)

data class SyncedLyrics(
    val lines: List<LyricLine>,
)

private const val LYRIC_SETTLE_MS = 400
private val SINGER_NUMBER_REGEX = Regex("""^v?(\d+)$""", RegexOption.IGNORE_CASE)
private const val BACKGROUND_VOCAL_LEAD_MS = 250L
private const val BACKGROUND_VOCAL_ENTER_MS = 400
private const val BACKGROUND_VOCAL_EXIT_MS = 450
private const val BACKGROUND_VOCAL_FONT_SIZE_SP = 23
private const val BACKGROUND_VOCAL_LINE_HEIGHT_SP = 29
private const val LYRIC_LINE_SPACING_DP = 14

private const val LINE_SCROLL_MIN_MS = 350L
private const val LINE_SCROLL_MAX_MS = 500L
private const val LINE_STAGGER_STEPS = 3
private const val LINE_STAGGER_FRACTION = 0.06f

private const val LYRIC_FONT_SIZE_SP = 34
private const val LYRIC_LINE_HEIGHT_SP = 41
private const val STATIC_LYRIC_FONT_SIZE_SP = 30
private const val STATIC_LYRIC_LINE_HEIGHT_SP = 38
private const val LYRIC_TOP_PADDING_DP = 32
private const val LYRIC_BOTTOM_PADDING_FRACTION = 0.8f

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

private const val GAP_LINE_END_SAFETY_MS = 500L

private const val UNSYNCED_BASE_DURATION_MS = 800L
private const val UNSYNCED_WORD_DURATION_MS = 700L
private const val UNSYNCED_CHARACTER_DURATION_MS = 18L
private const val UNSYNCED_MIN_LINE_DURATION_MS = 2_000L
private const val UNSYNCED_MAX_LINE_DURATION_MS = 10_000L

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
        val scrollLeadMs: Long = LINE_SCROLL_MIN_MS,
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

private data class LyricTimelineLine(
    val displayIndex: Int,
    val item: LyricDisplayItem.LineItem,
)

private data class LyricTimelineGap(
    val displayIndex: Int,
    val item: LyricDisplayItem.GapItem,
)

private data class LyricTimeline(
    val items: List<LyricDisplayItem>,
    val lines: List<LyricTimelineLine>,
    val gaps: List<LyricTimelineGap>,
    val lineTimestampsNondecreasing: Boolean,
    val gapStartsNondecreasing: Boolean,
)

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

private data class FloatingWordGeometry(
    val wordIndex: Int,
    val left: Float,
    val right: Float,
)

private data class LyricVisualLineGeometry(
    val visualLine: Int,
    val start: Int,
    val floatingEnd: Int,
    val revealEnd: Int,
    val left: Float,
    val right: Float,
    val top: Float,
    val bottom: Float,
    val isRtl: Boolean,
    val wordsByVisualOrder: List<FloatingWordGeometry>,
    val wordsByHorizontalPosition: List<FloatingWordGeometry>,
)

@Composable
fun LyricViewer(
    lyrics: SyncedLyrics,
    positionMs: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    isPlaying: Boolean = true,
) {
    val singerOrder =
        remember(lyrics) {
            lyrics.lines
                .mapNotNull { it.singerId?.trim()?.lowercase()?.takeIf(String::isNotEmpty) }
                .distinct()
        }

    val isStaticLyrics =
        remember(lyrics) {
            !hasMeaningfulLyricTimestamps(lyrics)
        }

    if (isStaticLyrics) {
        StaticLyricViewer(
            lyrics = lyrics,
            singerOrder = singerOrder,
            modifier = modifier,
        )
        return
    }

    val listState = rememberLazyListState()

    val timeline =
        remember(lyrics) {
            buildLyricTimeline(
                items = buildLyricDisplayItems(lyrics),
            )
        }

    val displayItems = timeline.items

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
        timeline,
        lyricClock,
    ) {
        derivedStateOf(structuralEqualityPolicy()) {
            findActiveDisplayIndex(
                timeline = timeline,
                positionMs = lyricClock.longValue,
            )
        }
    }

    val scrollPlan by remember(
        timeline,
        lyricClock,
    ) {
        derivedStateOf(structuralEqualityPolicy()) {
            buildScrollPlan(
                timeline = timeline,
                positionMs = lyricClock.longValue,
            )
        }
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
        val density = LocalDensity.current
        val activeTopOffsetPx = with(density) {
            LYRIC_TOP_PADDING_DP.dp.toPx()
        }

        LaunchedEffect(lyrics, scrollPlan.targetDisplayIndex) {
            val targetIndex = scrollPlan.targetDisplayIndex

            if (targetIndex !in displayItems.indices) {
                return@LaunchedEffect
            }

            val durationMs = scrollPlan.durationMs
                .coerceIn(
                    LINE_SCROLL_MIN_MS,
                    LINE_SCROLL_MAX_MS,
                )
                .toInt()

            yield()

            var layoutInfo = listState.layoutInfo
            var targetItem = layoutInfo.visibleItemsInfo
                .firstOrNull { it.index == targetIndex }
            var usedOffscreenFallback = false

            if (targetItem == null) {
                listState.animateScrollToItem(
                    index = targetIndex,
                )

                usedOffscreenFallback = true
                yield()

                layoutInfo = listState.layoutInfo
                targetItem = layoutInfo.visibleItemsInfo
                    .firstOrNull { it.index == targetIndex }
            }

            if (targetItem == null) {
                return@LaunchedEffect
            }

            val targetTop = layoutInfo.viewportStartOffset +
                activeTopOffsetPx
            val deltaPx = targetItem.offset - targetTop
            val minimumDeltaPx = if (usedOffscreenFallback) 12f else 1f

            if (abs(deltaPx) <= minimumDeltaPx) {
                return@LaunchedEffect
            }

            val viewportHeight = (
                layoutInfo.viewportEndOffset -
                    layoutInfo.viewportStartOffset
                ).coerceAtLeast(1)

            val animationDurationMs = if (usedOffscreenFallback) {
                val correctionFraction = (
                    abs(deltaPx) / viewportHeight.toFloat()
                ).coerceIn(0.15f, 0.50f)

                (durationMs * correctionFraction)
                    .toInt()
                    .coerceIn(100, durationMs)
            } else {
                durationMs
            }

            scrollRun = LyricScrollRun(
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
            horizontalAlignment = AbsoluteAlignment.Left,
            verticalArrangement = Arrangement.spacedBy(0.dp),
            contentPadding = PaddingValues(
                top = LYRIC_TOP_PADDING_DP.dp,
                bottom = maxHeight * LYRIC_BOTTOM_PADDING_FRACTION,
            ),
        ) {
            itemsIndexed(
                items = displayItems,
                key = { _, item -> item.key },
            ) { displayIndex, item ->

                val isActive =
                    displayIndex == activeDisplayIndex
                val rowAlignment =
                    (item as? LyricDisplayItem.LineItem)?.line?.let { line ->
                        singerAlignment(
                            singerId = line.singerId,
                            singerOrder = singerOrder,
                        )
                    } ?: AbsoluteAlignment.Left

                val hasActiveBackground by remember(item, lyricClock) {
                    derivedStateOf {
                        val line = (item as? LyricDisplayItem.LineItem)?.line
                        val now = lyricClock.longValue
                        line?.backgroundVocals?.any { background ->
                            now >= (background.startTimeMs - BACKGROUND_VOCAL_LEAD_MS).coerceAtLeast(0L) &&
                                now < effectiveCueEndTimeMs(background)
                        } == true
                    }
                }

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
                    targetValue =
                        if (isActive || hasActiveBackground) 1f
                        else LYRIC_LINE_ALPHA[alphaIndex],
                    animationSpec = tween(
                        durationMillis = LYRIC_SETTLE_MS,
                        easing = LYRIC_EASING,
                    ),
                    label = "lyric-row-alpha",
                )

                val rowScale by animateFloatAsState(
                    targetValue =
                        if (isActive || hasActiveBackground) 1f else 0.98f,
                    animationSpec = tween(
                        durationMillis = LYRIC_SETTLE_MS,
                        easing = LYRIC_EASING,
                    ),
                    label = "lyric-row-scale",
                )

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

                            isActive || hasActiveBackground ->
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
                                        pivotFractionX = if (rowAlignment == AbsoluteAlignment.Right) 1f else 0f,
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
                                        positionMs = lyricClock.longValue,
                                    )
                                } else {
                                    -1
                                }

                            LyricLineContent(
                                line = item.line,
                                activeWordIndex = activeWordIndex,
                                isActive = isActive,
                                lyricClock = lyricClock,
                                alignment = rowAlignment,
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

/**
 * Renders one active lyric line with the same word-floating/reveal renderer used by
 * [LyricViewer]. Intended for compact player surfaces that must not render static lyrics.
 * Background vocal rows are intentionally omitted here so the fixed-height preview remains
 * a single-line focus and cannot push the seek bar or neighboring controls.
 */
@Composable
fun LyricLinePreview(
    line: LyricLine,
    singerOrder: List<String>,
    positionMs: Long,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
) {
    val previewClock = remember(line) {
        mutableLongStateOf(positionMs.coerceAtLeast(0L))
    }

    var clockAnchorPositionMs by remember(line) {
        mutableLongStateOf(positionMs.coerceAtLeast(0L))
    }
    var clockAnchorFrameTimeNanos by remember(line) {
        mutableLongStateOf(0L)
    }
    var hasClockAnchor by remember(line) {
        mutableStateOf(false)
    }

    LaunchedEffect(line, positionMs, isPlaying) {
        val frameTimeNanos = withFrameNanos { it }
        clockAnchorPositionMs = positionMs.coerceAtLeast(0L)
        clockAnchorFrameTimeNanos = frameTimeNanos
        hasClockAnchor = true

        if (!isPlaying) {
            previewClock.longValue = positionMs.coerceAtLeast(0L)
        }
    }

    LaunchedEffect(line, isPlaying) {
        if (!isPlaying) {
            previewClock.longValue = positionMs.coerceAtLeast(0L)
            return@LaunchedEffect
        }

        while (true) {
            withFrameNanos { frameTimeNanos ->
                if (!hasClockAnchor) {
                    previewClock.longValue = positionMs.coerceAtLeast(0L)
                } else {
                    val elapsedMs = (
                        frameTimeNanos - clockAnchorFrameTimeNanos
                    ).coerceAtLeast(0L) / 1_000_000L
                    previewClock.longValue = (
                        clockAnchorPositionMs + elapsedMs
                    ).coerceAtLeast(0L)
                }
            }
        }
    }

    val activeWordIndex by remember(line, previewClock) {
        derivedStateOf {
            findActiveWordIndex(line, previewClock.longValue)
        }
    }

    val previewColorScheme = MaterialTheme.colorScheme.copy(
        onSurface = Color.White,
        onSurfaceVariant = Color.White,
    )

    MaterialTheme(
        colorScheme = previewColorScheme,
        typography = MaterialTheme.typography,
        shapes = MaterialTheme.shapes,
    ) {
        Box(
            modifier = modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            LyricLineContent(
                line = line,
                activeWordIndex = activeWordIndex,
                isActive = true,
                lyricClock = previewClock,
                modifier = Modifier.fillMaxWidth(),
                alignment = singerAlignment(line.singerId, singerOrder),
                compactPreview = true,
                showBackgroundVocals = false,
            )
        }
    }
}

private fun singerAlignment(
    singerId: String?,
    singerOrder: List<String>,
): Alignment.Horizontal {
    val normalizedSingerId = singerId
        ?.trim()
        ?.lowercase()
        ?.removeSuffix(":")
        ?.takeIf(String::isNotEmpty)
        ?: return AbsoluteAlignment.Left

    // Explicit v1/v2 identities always control the physical side. Do not gate
    // this on whether both singers appear in the current lyric payload: a v2
    // line remains a right-side line even when only that voice was supplied.
    val numericRole = SINGER_NUMBER_REGEX
        .matchEntire(normalizedSingerId)
        ?.groupValues
        ?.getOrNull(1)
        ?.toIntOrNull()

    if (numericRole != null) {
        return if (numericRole % 2 == 0) AbsoluteAlignment.Right else AbsoluteAlignment.Left
    }

    // Providers with named voice IDs fall back to their stable first-seen
    // order. Use absolute alignment because the player asks for physical left
    // and right lanes, not locale-dependent start/end lanes.
    val index = singerOrder.indexOf(normalizedSingerId)
    return if (index > 0 && index % 2 == 1) AbsoluteAlignment.Right else AbsoluteAlignment.Left
}

private fun hasMeaningfulLyricTimestamps(
    lyrics: SyncedLyrics,
): Boolean {
    if (lyrics.lines.any { it.backgroundVocals.isNotEmpty() }) {
        return true
    }

    if (lyrics.lines.any { line ->
            line.endTimeMs?.let { it > line.startTimeMs } == true
        }
    ) {
        return true
    }

    val hasLineTimestampProgression =
        lyrics.lines
            .zipWithNext()
            .any { (current, next) ->
                next.startTimeMs > current.startTimeMs
            }

    if (hasLineTimestampProgression) {
        return true
    }

    return lyrics.lines.any { line ->
        line.words
            .zipWithNext()
            .any { (current, next) ->
                next.startTimeMs > current.startTimeMs
            }
    }
}

@Composable
private fun StaticLyricViewer(
    lyrics: SyncedLyrics,
    singerOrder: List<String>,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()

    LaunchedEffect(lyrics) {
        listState.scrollToItem(0)
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = AbsoluteAlignment.Left,
        verticalArrangement = Arrangement.spacedBy(0.dp),
        contentPadding = PaddingValues(
            top = LYRIC_TOP_PADDING_DP.dp,
            bottom = 24.dp,
        ),
    ) {
        itemsIndexed(
            items = lyrics.lines,
            key = { index, line ->
                "static-line-$index-${line.startTimeMs}"
            },
        ) { _, line ->
            val lineAlignment =
                if (singerAlignment(line.singerId, singerOrder) == AbsoluteAlignment.Right) {
                    TextAlign.Right
                } else {
                    TextAlign.Left
                }
            Text(
                text =
                    line.words.joinToString(separator = " ") { word ->
                        word.text
                    },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(
                            vertical = LYRIC_LINE_SPACING_DP.dp / 2,
                        ),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = lineAlignment,
                style =
                    MaterialTheme.typography.titleLarge.copy(
                        fontSize = STATIC_LYRIC_FONT_SIZE_SP.sp,
                        lineHeight = STATIC_LYRIC_LINE_HEIGHT_SP.sp,
                        fontWeight = FontWeight.Black,
                    ),
                maxLines = 3,
            )
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
    alignment: Alignment.Horizontal = AbsoluteAlignment.Left,
    compactPreview: Boolean = false,
    showBackgroundVocals: Boolean = true,
) {
    val isBackground = line.isBackground
    val inactiveColor =
        MaterialTheme.colorScheme.onSurfaceVariant
            .copy(
                alpha = when {
                    isBackground -> 0.56f
                    compactPreview -> 0.58f
                    else -> 0.45f
                },
            )

    val activeColor =
        MaterialTheme.colorScheme.onSurface
            .copy(alpha = if (isBackground) 0.72f else 1f)

    val activeWordColor =
        MaterialTheme.colorScheme.onSurface
            .copy(alpha = if (isBackground) 0.82f else 1f)

    val lineColor by animateColorAsState(
        targetValue =
            if (isActive) activeColor else inactiveColor,
        animationSpec = tween(
            durationMillis = LYRIC_SETTLE_MS,
            easing = LYRIC_EASING,
        ),
        label = "lyric-line-color",
    )

    val lyricTextAlign =
        if (alignment == AbsoluteAlignment.Right) TextAlign.Right
        else TextAlign.Left

    val style =
        if (compactPreview) {
            MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Medium,
                textAlign = lyricTextAlign,
            )
        } else {
            MaterialTheme.typography.titleLarge.copy(
                fontSize = if (isBackground) {
                    BACKGROUND_VOCAL_FONT_SIZE_SP.sp
                } else {
                    LYRIC_FONT_SIZE_SP.sp
                },
                lineHeight = if (isBackground) {
                    BACKGROUND_VOCAL_LINE_HEIGHT_SP.sp
                } else {
                    LYRIC_LINE_HEIGHT_SP.sp
                },
                fontWeight = if (isBackground) FontWeight.SemiBold else FontWeight.Black,
                // Align the paragraph and its outer container together so wrapped lyrics
                // remain aligned with the selected singer lane.
                textAlign = lyricTextAlign,
            )
        }

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

    val geometryLayout = textLayoutResult
    val geometryText = geometryLayout?.layoutInput?.text?.text
    val geometrySize = geometryLayout?.size
    val geometryConstraints = geometryLayout?.layoutInput?.constraints
    val geometryDensity = geometryLayout?.layoutInput?.density
    val geometryLayoutDirection = geometryLayout?.layoutInput?.layoutDirection
    val geometryStyle =
        geometryLayout?.layoutInput?.style?.copy(
            color = Color.Unspecified,
        )

    val lyricVisualLineGeometry =
        remember(
            geometryText,
            geometrySize,
            geometryConstraints,
            geometryDensity,
            geometryLayoutDirection,
            geometryStyle,
            line,
            wordOffsets,
            isWordSynced,
        ) {
            if (isWordSynced) {
                geometryLayout?.let { layout ->
                    buildLyricVisualLineGeometry(
                        layout = layout,
                        line = line,
                        wordOffsets = wordOffsets,
                    )
                } ?: emptyList()
            } else {
                emptyList()
            }
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
        if (!isWordSynced) {
            remember(
                line,
                isActive,
                activeWordIndex,
                inactiveColor,
                activeColor,
                activeWordColor,
            ) {
                buildLyricAnnotatedString(
                    line = line,
                    isActive = isActive,
                    activeWordIndex = activeWordIndex,
                    inactiveColor = inactiveColor,
                    activeColor = activeColor,
                    activeWordColor = activeWordColor,
                    lineColor = Color.Unspecified,
                )
            }
        } else {
            null
        }

    val dimText =
        if (isWordSynced) {
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
        } else {
            null
        }

    val sweptText =
        if (isWordSynced && isActive) {
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
        } else {
            null
        }

    val density = LocalDensity.current

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = alignment,
    ) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment =
                if (alignment == AbsoluteAlignment.Right) AbsoluteAlignment.CenterRight
                else AbsoluteAlignment.CenterLeft,
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
                            pivotFractionX = if (alignment == AbsoluteAlignment.Right) 1f else 0f,
                            pivotFractionY = 0.5f,
                        )
                }

            val floatingModifier =
                Modifier.drawWithContent {
                    val layout = textLayoutResult

                    if (layout == null || line.words.isEmpty()) {
                        drawContent()
                    } else {
                        drawFloatingWords(
                            line = line,
                            visualLines = lyricVisualLineGeometry,
                            positionMs = lyricClock.longValue,
                        )
                    }
                }

            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment =
                    if (alignment == AbsoluteAlignment.Right) AbsoluteAlignment.CenterRight
                    else AbsoluteAlignment.CenterLeft,
            ) {
                when {
                    isWordSynced && isActive -> {
                        Text(
                            text = requireNotNull(dimText),
                            modifier = textModifier.then(floatingModifier),
                            color = lineColor,
                            style = style,
                            maxLines = if (compactPreview) 2 else 3,
                            onTextLayout = onTextLayout,
                        )

                        Text(
                            text = requireNotNull(sweptText),
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
                                                visualLines = lyricVisualLineGeometry,
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
                            maxLines = if (compactPreview) 2 else 3,
                        )
                    }

                    isWordSynced -> {
                        Text(
                            text = requireNotNull(dimText),
                            modifier = textModifier.then(floatingModifier),
                            color = lineColor,
                            style = style,
                            maxLines = if (compactPreview) 2 else 3,
                            onTextLayout = onTextLayout,
                        )
                    }

                    else -> {
                        Text(
                            text = requireNotNull(regularText),
                            modifier = textModifier,
                            color = lineColor,
                            style = style,
                            maxLines = if (compactPreview) 2 else 3,
                            onTextLayout = onTextLayout,
                        )
                    }
                }
            }
        }

        if (showBackgroundVocals) {
            line.backgroundVocals.forEach { backgroundLine ->
                BackgroundVocalContent(
                    line = backgroundLine,
                    lyricClock = lyricClock,
                    alignment = alignment,
                )
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
        MaterialTheme.colorScheme.primary

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

@Composable
private fun BackgroundVocalContent(
    line: LyricLine,
    lyricClock: MutableLongState,
    alignment: Alignment.Horizontal,
) {
    val endTimeMs = remember(line) {
        effectiveCueEndTimeMs(line)
    }
    val isVisible by remember(line, lyricClock, endTimeMs) {
        derivedStateOf {
            val now = lyricClock.longValue
            now >= (line.startTimeMs - BACKGROUND_VOCAL_LEAD_MS).coerceAtLeast(0L) &&
                now < endTimeMs
        }
    }
    val isActive by remember(line, lyricClock, endTimeMs) {
        derivedStateOf {
            val now = lyricClock.longValue
            now >= line.startTimeMs && now < endTimeMs
        }
    }
    val activeWordIndex by remember(line, lyricClock, endTimeMs) {
        derivedStateOf {
            val now = lyricClock.longValue
            if (now >= line.startTimeMs && now < endTimeMs) {
                findActiveWordIndex(line, now)
            } else {
                -1
            }
        }
    }

    AnimatedVisibility(
        visible = isVisible,
        modifier = Modifier.fillMaxWidth(),
        enter =
            expandVertically(
                expandFrom = Alignment.Top,
                animationSpec = tween(
                    durationMillis = BACKGROUND_VOCAL_ENTER_MS,
                    easing = LYRIC_EASING,
                ),
            ) +
                fadeIn(
                    animationSpec = tween(
                        durationMillis = BACKGROUND_VOCAL_ENTER_MS,
                        easing = LYRIC_EASING,
                    ),
                ) +
                slideInVertically(
                    animationSpec = tween(
                        durationMillis = BACKGROUND_VOCAL_ENTER_MS,
                        easing = LYRIC_EASING,
                    ),
                    initialOffsetY = { height -> height / 3 },
                ) +
                scaleIn(
                    initialScale = 0.94f,
                    transformOrigin = TransformOrigin(
                        pivotFractionX = if (alignment == AbsoluteAlignment.Right) 1f else 0f,
                        pivotFractionY = 0f,
                    ),
                    animationSpec = tween(
                        durationMillis = BACKGROUND_VOCAL_ENTER_MS,
                        easing = LYRIC_EASING,
                    ),
                ),
        exit =
            shrinkVertically(
                shrinkTowards = Alignment.Top,
                animationSpec = tween(
                    durationMillis = BACKGROUND_VOCAL_EXIT_MS,
                    easing = LYRIC_EASING,
                ),
            ) +
                fadeOut(
                    animationSpec = tween(
                        durationMillis = BACKGROUND_VOCAL_EXIT_MS,
                        easing = LYRIC_EASING,
                    ),
                ) +
                slideOutVertically(
                    animationSpec = tween(
                        durationMillis = BACKGROUND_VOCAL_EXIT_MS,
                        easing = LYRIC_EASING,
                    ),
                    targetOffsetY = { height -> -height / 8 },
                ) +
                scaleOut(
                    targetScale = 0.98f,
                    transformOrigin = TransformOrigin(
                        pivotFractionX = if (alignment == AbsoluteAlignment.Right) 1f else 0f,
                        pivotFractionY = 0f,
                    ),
                    animationSpec = tween(
                        durationMillis = BACKGROUND_VOCAL_EXIT_MS,
                        easing = LYRIC_EASING,
                    ),
                ),
    ) {
        LyricLineContent(
            line = line,
            activeWordIndex = activeWordIndex,
            isActive = isActive,
            lyricClock = lyricClock,
            alignment = alignment,
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    top = 2.dp,
                    bottom = 5.dp,
                    start = 4.dp,
                    end = 4.dp,
                ),
        )
    }
}

private fun effectiveCueEndTimeMs(
    line: LyricLine,
): Long {
    line.endTimeMs
        ?.takeIf { it > line.startTimeMs }
        ?.let { return it }

    val lastWord = line.words.lastOrNull() ?: return line.startTimeMs + 500L
    val previousWord = line.words.getOrNull(line.words.lastIndex - 1)
    val estimatedDuration = previousWord
        ?.let { lastWord.startTimeMs - it.startTimeMs }
        ?.takeIf { it > 0L }
        ?.coerceIn(250L, 900L)
        ?: 500L

    return max(line.startTimeMs + 1L, lastWord.startTimeMs + estimatedDuration)
}

private fun buildLyricDisplayItems(
    lyrics: SyncedLyrics,
): List<LyricDisplayItem> {
    if (lyrics.lines.isEmpty()) {
        return emptyList()
    }

    val result = mutableListOf<LyricDisplayItem>()
    val firstLine = lyrics.lines.first()

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
        val nextLine = lyrics.lines.getOrNull(index + 1)
        val estimatedMainLineEnd =
            if (
                nextLine != null &&
                nextLine.startTimeMs > line.startTimeMs
            ) {
                estimateLineEndTimeMs(
                    line = line,
                    nextLineStartMs = nextLine.startTimeMs,
                )
            } else {
                line.endTimeMs?.takeIf { it > line.startTimeMs }
                    ?: line.startTimeMs
            }
        val estimatedBackgroundEnd = line.backgroundVocals
            .maxOfOrNull(::effectiveCueEndTimeMs)
            ?: line.startTimeMs
        val estimatedLineEnd = max(estimatedMainLineEnd, estimatedBackgroundEnd)

        val scrollLeadMs =
            if (nextLine == null) {
                LINE_SCROLL_MIN_MS
            } else {
                (
                    nextLine.startTimeMs - estimatedLineEnd
                ).coerceAtLeast(0L).coerceIn(
                    LINE_SCROLL_MIN_MS,
                    LINE_SCROLL_MAX_MS,
                )
            }

        result += LyricDisplayItem.LineItem(
            sourceIndex = index,
            line = line,
            scrollLeadMs = scrollLeadMs,
        )

        if (
            nextLine == null ||
            nextLine.startTimeMs <= line.startTimeMs
        ) {
            return@forEachIndexed
        }

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

private fun buildLyricTimeline(
    items: List<LyricDisplayItem>,
): LyricTimeline {
    val lines = ArrayList<LyricTimelineLine>()
    val gaps = ArrayList<LyricTimelineGap>()
    var previousLineStartMs: Long? = null
    var previousGapStartMs: Long? = null
    var lineTimestampsNondecreasing = true
    var gapStartsNondecreasing = true

    items.forEachIndexed { displayIndex, item ->
        when (item) {
            is LyricDisplayItem.LineItem -> {
                val startTimeMs = item.line.startTimeMs

                val previousStartTimeMs = previousLineStartMs

                if (
                    previousStartTimeMs != null &&
                    startTimeMs < previousStartTimeMs
                ) {
                    lineTimestampsNondecreasing = false
                }

                previousLineStartMs = startTimeMs
                lines +=
                    LyricTimelineLine(
                        displayIndex = displayIndex,
                        item = item,
                    )
            }

            is LyricDisplayItem.GapItem -> {
                val previousStartTimeMs = previousGapStartMs

                if (
                    previousStartTimeMs != null &&
                    item.startTimeMs < previousStartTimeMs
                ) {
                    gapStartsNondecreasing = false
                }

                previousGapStartMs = item.startTimeMs
                gaps +=
                    LyricTimelineGap(
                        displayIndex = displayIndex,
                        item = item,
                    )
            }
        }
    }

    return LyricTimeline(
        items = items,
        lines = lines,
        gaps = gaps,
        lineTimestampsNondecreasing = lineTimestampsNondecreasing,
        gapStartsNondecreasing = gapStartsNondecreasing,
    )
}

private fun findActiveDisplayIndex(
    timeline: LyricTimeline,
    positionMs: Long,
): Int {
    if (
        !timeline.lineTimestampsNondecreasing ||
        !timeline.gapStartsNondecreasing
    ) {
        for (gap in timeline.gaps) {
            if (
                positionMs >= gap.item.startTimeMs &&
                positionMs < gap.item.endTimeMs
            ) {
                return gap.displayIndex
            }
        }
    } else {
        var low = 0
        var high = timeline.gaps.lastIndex
        var activeGapPosition = -1

        while (low <= high) {
            val middle = (low + high) ushr 1
            val gap = timeline.gaps[middle].item

            if (gap.startTimeMs <= positionMs) {
                activeGapPosition = middle
                low = middle + 1
            } else {
                high = middle - 1
            }
        }

        if (activeGapPosition >= 0) {
            val gap = timeline.gaps[activeGapPosition]

            if (positionMs < gap.item.endTimeMs) {
                return gap.displayIndex
            }
        }
    }

    val activeLinePosition =
        findActiveLinePosition(
            timeline = timeline,
            positionMs = positionMs,
        )

    return timeline.lines
        .getOrNull(activeLinePosition)
        ?.displayIndex
        ?: -1
}

private fun findActiveLinePosition(
    timeline: LyricTimeline,
    positionMs: Long,
): Int {
    if (!timeline.lineTimestampsNondecreasing) {
        var activeIndex = -1

        for (index in timeline.lines.indices) {
            if (timeline.lines[index].item.line.startTimeMs <= positionMs) {
                activeIndex = index
            } else {
                break
            }
        }

        return activeIndex
    }

    return findLastLinePositionAtOrBefore(
        timeline = timeline,
        positionMs = positionMs,
    )
}

private fun findLastLinePositionAtOrBefore(
    timeline: LyricTimeline,
    positionMs: Long,
): Int {
    var result = -1

    if (!timeline.lineTimestampsNondecreasing) {
        for (index in timeline.lines.indices) {
            if (timeline.lines[index].item.line.startTimeMs <= positionMs) {
                result = index
            }
        }

        return result
    }

    var low = 0
    var high = timeline.lines.lastIndex

    while (low <= high) {
        val middle = (low + high) ushr 1
        val startTimeMs =
            timeline.lines[middle].item.line.startTimeMs

        if (startTimeMs <= positionMs) {
            result = middle
            low = middle + 1
        } else {
            high = middle - 1
        }
    }

    return result
}

private fun findFirstLineAfterDisplayIndex(
    timeline: LyricTimeline,
    displayIndex: Int,
): Int {
    var low = 0
    var high = timeline.lines.size

    while (low < high) {
        val middle = (low + high) ushr 1

        if (timeline.lines[middle].displayIndex <= displayIndex) {
            low = middle + 1
        } else {
            high = middle
        }
    }

    return low.takeIf { it < timeline.lines.size } ?: -1
}

private fun buildScrollPlan(
    timeline: LyricTimeline,
    positionMs: Long,
): LyricScrollPlan {
    val activeIndex =
        findActiveDisplayIndex(
            timeline = timeline,
            positionMs = positionMs,
        )

    val activeItem =
        timeline.items.getOrNull(activeIndex)

    if (activeItem is LyricDisplayItem.GapItem) {
        val nextLinePosition =
            findFirstLineAfterDisplayIndex(
                timeline = timeline,
                displayIndex = activeIndex,
            )

        if (nextLinePosition < 0) {
            return LyricScrollPlan(
                targetDisplayIndex = activeIndex,
                durationMs = LINE_SCROLL_MIN_MS,
            )
        }

        val nextLine = timeline.lines[nextLinePosition]

        val leadMs =
            (
                activeItem.endTimeMs -
                    activeItem.startTimeMs
            ).coerceAtLeast(0L).coerceIn(
                LINE_SCROLL_MIN_MS,
                LINE_SCROLL_MAX_MS,
            )

        val targetIndex =
            if (
                nextLine.item.line.startTimeMs <=
                positionMs + leadMs
            ) {
                nextLine.displayIndex
            } else {
                activeIndex
            }

        return LyricScrollPlan(
            targetDisplayIndex = targetIndex,
            durationMs = leadMs,
        )
    }

    val currentLinePosition =
        findLastLinePositionAtOrBefore(
            timeline = timeline,
            positionMs = positionMs,
        )

    if (currentLinePosition < 0) {
        val firstLine = timeline.lines.firstOrNull()
            ?: return LyricScrollPlan(
                targetDisplayIndex = -1,
                durationMs = LINE_SCROLL_MIN_MS,
            )

        val targetIndex =
            if (
                firstLine.item.line.startTimeMs <=
                positionMs + LINE_SCROLL_MAX_MS
            ) {
                firstLine.displayIndex
            } else {
                -1
            }

        return LyricScrollPlan(
            targetDisplayIndex = targetIndex,
            durationMs = LINE_SCROLL_MAX_MS,
        )
    }

    val currentLine = timeline.lines[currentLinePosition]
    val nextLine = timeline.lines.getOrNull(currentLinePosition + 1)

    if (nextLine == null) {
        return LyricScrollPlan(
            targetDisplayIndex = currentLine.displayIndex,
            durationMs = LINE_SCROLL_MIN_MS,
        )
    }

    val leadMs = currentLine.item.scrollLeadMs

    val targetIndex =
        if (
            nextLine.item.line.startTimeMs <=
            positionMs + leadMs
        ) {
            nextLine.displayIndex
        } else {
            currentLine.displayIndex
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

    line.endTimeMs
        ?.takeIf { wordIndex == line.words.lastIndex && it > word.startTimeMs }
        ?.let { return it }

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

private fun buildLyricVisualLineGeometry(
    layout: TextLayoutResult,
    line: LyricLine,
    wordOffsets: List<Int>,
): List<LyricVisualLineGeometry> {
    val textLength = layout.layoutInput.text.length

    if (textLength == 0) {
        return emptyList()
    }

    val visualLines = ArrayList<LyricVisualLineGeometry>(layout.lineCount)

    for (visualLine in 0 until layout.lineCount) {
        val lineStart = layout.getLineStart(visualLine)
        val floatingEnd =
            layout.getLineEnd(
                visualLine,
                visibleEnd = true,
            )
        val revealEnd =
            layout.getLineEnd(
                visualLine,
                visibleEnd = false,
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
        val directionIndex =
            lineStart.coerceIn(
                0,
                (textLength - 1).coerceAtLeast(0),
            )
        val isRtl =
            layout.getParagraphDirection(directionIndex) ==
                ResolvedTextDirection.Rtl

        val wordSegments = ArrayList<FloatingWordGeometry>(line.words.size)

        if (lineStart < floatingEnd) {
            for (wordIndex in line.words.indices) {
                if (wordIndex >= wordOffsets.size) {
                    continue
                }

                val word = line.words[wordIndex]
                val wordStart = wordOffsets[wordIndex]
                val wordEnd = wordStart + word.text.length
                val start = max(wordStart, lineStart)
                val end = min(wordEnd, floatingEnd)

                if (start >= end) {
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
                val segmentLeft =
                    min(from, to).coerceIn(left, right)
                val segmentRight =
                    max(from, to).coerceIn(left, right)

                if (segmentRight <= segmentLeft) {
                    continue
                }

                wordSegments +=
                    FloatingWordGeometry(
                        wordIndex = wordIndex,
                        left = segmentLeft,
                        right = segmentRight,
                    )
            }
        }

        visualLines +=
            LyricVisualLineGeometry(
                visualLine = visualLine,
                start = lineStart,
                floatingEnd = floatingEnd,
                revealEnd = revealEnd,
                left = left,
                right = right,
                top = top,
                bottom = bottom,
                isRtl = isRtl,
                wordsByVisualOrder = wordSegments,
                wordsByHorizontalPosition = wordSegments.sortedBy { it.left },
            )
    }

    return visualLines
}

private fun ContentDrawScope.drawFloatingWords(
    line: LyricLine,
    visualLines: List<LyricVisualLineGeometry>,
    positionMs: Long,
) {
    if (line.words.isEmpty() || visualLines.isEmpty()) {
        drawContent()
        return
    }

    val peak = WORD_RISE_DP.dp.toPx()
    val wordLifts = FloatArray(line.words.size) { wordIndex ->
        calculateWordLift(
            line = line,
            wordIndex = wordIndex,
            positionMs = positionMs,
        )
    }

    var hasActiveSegments = false

    for (visualLine in visualLines) {
        if (visualLine.start >= visualLine.floatingEnd) {
            continue
        }

        for (segment in visualLine.wordsByVisualOrder) {
            if (wordLifts[segment.wordIndex] > 0.01f) {
                hasActiveSegments = true
                break
            }
        }

        if (hasActiveSegments) {
            break
        }
    }

    if (!hasActiveSegments) {
        drawContent()
        return
    }

    val flatPath = Path()
    var hasFlatContent = false

    for (visualLine in visualLines) {
        if (visualLine.start >= visualLine.floatingEnd) {
            continue
        }

        var edge = visualLine.left
        var hasRowSegments = false

        for (segment in visualLine.wordsByHorizontalPosition) {
            if (wordLifts[segment.wordIndex] <= 0.01f) {
                continue
            }

            hasRowSegments = true

            val segmentLeft =
                segment.left.coerceIn(
                    visualLine.left,
                    visualLine.right,
                )
            val segmentRight =
                segment.right.coerceIn(
                    visualLine.left,
                    visualLine.right,
                )

            if (segmentLeft > edge) {
                flatPath.addRect(
                    Rect(
                        left = edge,
                        top = visualLine.top,
                        right = segmentLeft,
                        bottom = visualLine.bottom,
                    ),
                )

                hasFlatContent = true
            }

            edge = max(edge, segmentRight)
        }

        if (!hasRowSegments) {
            flatPath.addRect(
                Rect(
                    left = visualLine.left,
                    top = visualLine.top,
                    right = visualLine.right,
                    bottom = visualLine.bottom,
                ),
            )

            hasFlatContent = true
        } else if (edge < visualLine.right) {
            flatPath.addRect(
                Rect(
                    left = edge,
                    top = visualLine.top,
                    right = visualLine.right,
                    bottom = visualLine.bottom,
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

    for (visualLine in visualLines) {
        if (visualLine.start >= visualLine.floatingEnd) {
            continue
        }

        for (segment in visualLine.wordsByVisualOrder) {
            val lift = wordLifts[segment.wordIndex]

            if (lift <= 0.01f) {
                continue
            }

            clipRect(
                left = segment.left - 0.5f,
                top = visualLine.top - peak - 1f,
                right = segment.right + 0.5f,
                bottom = visualLine.bottom + 1f,
            ) {
                translate(
                    top = -lift * peak,
                ) {
                    this@drawFloatingWords.drawContent()
                }
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

private fun estimateLineEndTimeMs(
    line: LyricLine,
    nextLineStartMs: Long,
): Long {
    val safeNextLineStartMs =
        nextLineStartMs.coerceAtLeast(line.startTimeMs)

    if (safeNextLineStartMs <= line.startTimeMs) {
        return line.startTimeMs
    }

    line.endTimeMs
        ?.takeIf { it > line.startTimeMs }
        ?.let { return it.coerceAtMost(safeNextLineStartMs) }

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
                    fontWeight = FontWeight.Black,
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
    visualLines: List<LyricVisualLineGeometry>,
    revealedCharacters: Float,
) {
    val textLength = layout.layoutInput.text.length

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

    for (visualLine in visualLines) {
        val lineStart = visualLine.start
        val lineEnd = visualLine.revealEnd

        if (revealedCharacters <= lineStart) {
            continue
        }

        val lineLeft = visualLine.left
        val lineRight = visualLine.right
        val lineTop = visualLine.top
        val lineBottom = visualLine.bottom

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
                layout.getLineForOffset(nextOffset) != visualLine.visualLine
            ) {
                if (visualLine.isRtl) lineLeft else lineRight
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
            if (visualLine.isRtl) revealX else lineLeft

        val clipRight =
            if (visualLine.isRtl) lineRight else revealX

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