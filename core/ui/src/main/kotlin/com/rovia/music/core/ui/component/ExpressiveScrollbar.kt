
package com.rovia.music.core.ui.component

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * Expressive-style scrollbar for a vertical LazyColumn.
 *
 * Scroll progress is observed from drawing/layout phases rather than
 * being delegated directly in composition. This reduces unnecessary
 * recomposition during fast scrolling.
 */
@Composable
fun ExpressiveScrollbar(
    state: LazyListState,
    modifier: Modifier = Modifier,
    thumbHeight: Dp = 48.dp,
    thickness: Dp = 8.dp,
    expandedWidth: Dp = 28.dp,
    rightCornerRadius: Dp = 6.dp,
    endPadding: Dp = 4.dp,
    trackGap: Dp = 8.dp,
    bottomPadding: Dp = 100.dp,
) {
    val canScroll by remember(state) {
        derivedStateOf {
            state.canScrollForward || state.canScrollBackward
        }
    }

    if (!canScroll) return

    /*
     * Keep the derived state object itself in composition.
     * Read its value only inside drawing/interaction callbacks.
     */
    val scrollProgressState = remember(state) {
        derivedStateOf {
            calculateScrollProgress(state)
        }
    }

    val resolvedExpandedWidth =
        expandedWidth.coerceAtLeast(thickness)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxHeight()
            .width(resolvedExpandedWidth + endPadding)
            .padding(bottom = bottomPadding),
    ) {
        val density = LocalDensity.current
        val boxWidthPx = with(density) { maxWidth.toPx() }
        val thumbHeightPx = with(density) { thumbHeight.toPx() }
        val scrollableHeightPx =
            (constraints.maxHeight.toFloat() - thumbHeightPx)
                .coerceAtLeast(1f)

        val latestScrollableHeight by rememberUpdatedState(
            scrollableHeightPx,
        )
        val latestThumbHeight by rememberUpdatedState(
            thumbHeightPx,
        )

        val coroutineScope = rememberCoroutineScope()
        val scrollJob = remember(state) {
            mutableStateOf<Job?>(null)
        }

        val latestState by rememberUpdatedState(state)

        val seekToProgress: (Float) -> Unit = { targetProgress ->
            val targetIndex = resolveTargetIndex(
                state = latestState,
                progress = targetProgress,
            )

            if (targetIndex >= 0) {
                scrollJob.value?.cancel()
                scrollJob.value = coroutineScope.launch {
                    latestState.scrollToItem(targetIndex)
                }
            }
        }

        val latestSeekToProgress by rememberUpdatedState(
            seekToProgress,
        )

        var isPressed by remember(state) {
            mutableStateOf(false)
        }

        var isDragging by remember(state) {
            mutableStateOf(false)
        }

        var dragProgress by remember(state) {
            mutableFloatStateOf(-1f)
        }

        val isInteracting = isPressed || isDragging

        val animatedWidth by animateDpAsState(
            targetValue = if (isInteracting) {
                resolvedExpandedWidth
            } else {
                thickness
            },
            animationSpec = tween(
                durationMillis = 200,
                easing = FastOutSlowInEasing,
            ),
            label = "ExpressiveScrollbarWidth",
        )

        /*
         * Width and icon opacity animate when interacting.
         * Thumb position itself follows scrolling directly,
         * avoiding an animation that continually chases new targets.
         */
        val iconAlpha by animateFloatAsState(
            targetValue = if (isInteracting) 1f else 0f,
            animationSpec = tween(durationMillis = 200),
            label = "ExpressiveScrollbarIconAlpha",
        )

        val primaryColor = MaterialTheme.colorScheme.primary
        val trackColor = MaterialTheme.colorScheme.secondaryContainer
        val iconColor = MaterialTheme.colorScheme.onPrimary

        val indicatorPath = remember(state) {
            Path()
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(state) {
                    detectTapGestures(
                        onPress = {
                            isPressed = true

                            try {
                                awaitRelease()
                            } finally {
                                isPressed = false
                            }
                        },
                        onTap = { position ->
                            val targetProgress = (
                                (position.y - latestThumbHeight / 2f) /
                                    latestScrollableHeight
                                ).coerceIn(0f, 1f)

                            latestSeekToProgress(targetProgress)
                        },
                    )
                }
                .pointerInput(state) {
                    var grabOffset = 0f

                    detectDragGestures(
                        onDragStart = { position ->
                            isDragging = true

                            val handleTop =
                                scrollProgressState.value *
                                    latestScrollableHeight

                            val handleBottom =
                                handleTop + latestThumbHeight

                            grabOffset = if (
                                position.y >= handleTop &&
                                position.y <= handleBottom
                            ) {
                                position.y - handleTop
                            } else {
                                latestThumbHeight / 2f
                            }

                            val targetProgress = (
                                (position.y - grabOffset) /
                                    latestScrollableHeight
                                ).coerceIn(0f, 1f)

                            dragProgress = targetProgress
                            latestSeekToProgress(targetProgress)
                        },
                        onDragEnd = {
                            isDragging = false
                            dragProgress = -1f
                        },
                        onDragCancel = {
                            isDragging = false
                            dragProgress = -1f
                        },
                        onDrag = { change, _ ->
                            change.consume()

                            val targetProgress = (
                                (change.position.y - grabOffset) /
                                    latestScrollableHeight
                                ).coerceIn(0f, 1f)

                            dragProgress = targetProgress
                            latestSeekToProgress(targetProgress)
                        },
                    )
                },
        ) {
            Canvas(
                modifier = Modifier.fillMaxSize(),
            ) {
                val paddingPx = endPadding.toPx()
                val trackWidthPx = thickness.toPx()
                val gapPx = trackGap.toPx()

                val rightAnchorX = size.width - paddingPx
                val trackCenterX =
                    rightAnchorX - trackWidthPx / 2f

                /*
                 * Read the current progress during drawing.
                 * LazyListState changes can invalidate this draw
                 * without requiring the whole Composable to recompose.
                 */
                val displayedProgress =
                    if (isDragging && dragProgress >= 0f) {
                        dragProgress
                    } else {
                        scrollProgressState.value
                    }

                val handleTop =
                    displayedProgress.coerceIn(0f, 1f) *
                        scrollableHeightPx

                if (handleTop > gapPx) {
                    drawLine(
                        color = trackColor,
                        start = Offset(trackCenterX, 0f),
                        end = Offset(
                            trackCenterX,
                            handleTop - gapPx,
                        ),
                        strokeWidth = trackWidthPx,
                        cap = StrokeCap.Round,
                    )
                }

                if (
                    handleTop + thumbHeightPx + gapPx <
                    size.height
                ) {
                    drawLine(
                        color = trackColor,
                        start = Offset(
                            trackCenterX,
                            handleTop + thumbHeightPx + gapPx,
                        ),
                        end = Offset(
                            trackCenterX,
                            size.height,
                        ),
                        strokeWidth = trackWidthPx,
                        cap = StrokeCap.Round,
                    )
                }

                val indicatorWidthPx = animatedWidth.toPx()
                val indicatorLeft = rightAnchorX - indicatorWidthPx

                val leftRadius = indicatorWidthPx / 2f
                val rightRadius = rightCornerRadius.toPx()
                    .coerceIn(
                        0f,
                        minOf(
                            indicatorWidthPx / 2f,
                            thumbHeightPx / 2f,
                        ),
                    )

                indicatorPath.reset()
                indicatorPath.addRoundRect(
                    RoundRect(
                        rect = Rect(
                            offset = Offset(
                                indicatorLeft,
                                handleTop,
                            ),
                            size = Size(
                                indicatorWidthPx,
                                thumbHeightPx,
                            ),
                        ),
                        topLeft = CornerRadius(leftRadius, leftRadius),
                        topRight = CornerRadius(rightRadius, rightRadius),
                        bottomRight = CornerRadius(rightRadius, rightRadius),
                        bottomLeft = CornerRadius(leftRadius, leftRadius),
                    ),
                )

                drawPath(
                    path = indicatorPath,
                    color = primaryColor,
                )
            }

            if (iconAlpha > 0f) {
                Canvas(
                    modifier = Modifier
                        .offset {
                            val displayedProgress =
                                if (isDragging && dragProgress >= 0f) {
                                    dragProgress
                                } else {
                                    scrollProgressState.value
                                }

                            val handleTop =
                                displayedProgress.coerceIn(0f, 1f) *
                                    scrollableHeightPx

                            val iconSizePx = with(density) {
                                24.dp.toPx()
                            }
                            val paddingPx = with(density) {
                                endPadding.toPx()
                            }
                            val animatedWidthPx = with(density) {
                                animatedWidth.toPx()
                            }

                            IntOffset(
                                x = (
                                    boxWidthPx -
                                        paddingPx -
                                        animatedWidthPx / 2f -
                                        iconSizePx / 2f
                                    ).roundToInt(),
                                y = (
                                    handleTop +
                                        thumbHeightPx / 2f -
                                        iconSizePx / 2f
                                    ).roundToInt(),
                            )
                        }
                        .size(24.dp)
                        .graphicsLayer {
                            alpha = iconAlpha
                            scaleX = iconAlpha
                            scaleY = iconAlpha
                        },
                ) {
                    val centerX = size.width / 2f
                    val centerY = size.height / 2f
                    val halfWidth = 4.dp.toPx()
                    val verticalInset = 2.dp.toPx()
                    val arrowDepth = 6.dp.toPx()
                    val strokeWidth = 2.dp.toPx()

                    // Upper chevron.
                    drawLine(
                        color = iconColor,
                        start = Offset(
                            centerX - halfWidth,
                            centerY - verticalInset,
                        ),
                        end = Offset(
                            centerX,
                            centerY - arrowDepth,
                        ),
                        strokeWidth = strokeWidth,
                        cap = StrokeCap.Round,
                    )

                    drawLine(
                        color = iconColor,
                        start = Offset(
                            centerX,
                            centerY - arrowDepth,
                        ),
                        end = Offset(
                            centerX + halfWidth,
                            centerY - verticalInset,
                        ),
                        strokeWidth = strokeWidth,
                        cap = StrokeCap.Round,
                    )

                    // Lower chevron.
                    drawLine(
                        color = iconColor,
                        start = Offset(
                            centerX - halfWidth,
                            centerY + verticalInset,
                        ),
                        end = Offset(
                            centerX,
                            centerY + arrowDepth,
                        ),
                        strokeWidth = strokeWidth,
                        cap = StrokeCap.Round,
                    )

                    drawLine(
                        color = iconColor,
                        start = Offset(
                            centerX,
                            centerY + arrowDepth,
                        ),
                        end = Offset(
                            centerX + halfWidth,
                            centerY + verticalInset,
                        ),
                        strokeWidth = strokeWidth,
                        cap = StrokeCap.Round,
                    )
                }
            }
        }
    }
}

private fun calculateScrollProgress(
    state: LazyListState,
): Float {
    val layoutInfo = state.layoutInfo
    val totalItems = layoutInfo.totalItemsCount
    val visibleItems = layoutInfo.visibleItemsInfo

    if (totalItems <= 0 || visibleItems.isEmpty()) {
        return 0f
    }

    if (!state.canScrollBackward) return 0f
    if (!state.canScrollForward) return 1f

    val viewportHeight = (
        layoutInfo.viewportEndOffset -
            layoutInfo.viewportStartOffset
        ).toFloat().coerceAtLeast(1f)

    val stride = estimateAverageItemStridePx(state)

    val estimatedContentHeight =
        totalItems.toFloat() * stride +
            layoutInfo.beforeContentPadding +
            layoutInfo.afterContentPadding

    val maxScroll = (
        estimatedContentHeight - viewportHeight
        ).coerceAtLeast(1f)

    val currentScroll =
        layoutInfo.beforeContentPadding.toFloat() +
            state.firstVisibleItemIndex.toFloat() * stride +
            state.firstVisibleItemScrollOffset

    return (currentScroll / maxScroll).coerceIn(0f, 1f)
}

private fun resolveTargetIndex(
    state: LazyListState,
    progress: Float,
): Int {
    val layoutInfo = state.layoutInfo
    val totalItems = layoutInfo.totalItemsCount

    if (totalItems <= 0) return -1

    val viewportHeight = (
        layoutInfo.viewportEndOffset -
            layoutInfo.viewportStartOffset
        ).toFloat().coerceAtLeast(1f)

    val stride = estimateAverageItemStridePx(state)

    val estimatedVisibleItems = ceil(
        viewportHeight / stride,
    ).toInt().coerceAtLeast(1)

    val maxFirstVisibleIndex = (
        totalItems - estimatedVisibleItems
        ).coerceAtLeast(0)

    return (
        progress.coerceIn(0f, 1f) *
            maxFirstVisibleIndex
        ).roundToInt().coerceIn(0, totalItems - 1)
}

/**
 * Estimates visible item stride without zip/map/filter/average
 * intermediate collections. This function runs on the small
 * currently visible range rather than iterating over the library.
 */
private fun estimateAverageItemStridePx(
    state: LazyListState,
): Float {
    val layoutInfo = state.layoutInfo
    val visibleItems = layoutInfo.visibleItemsInfo

    if (visibleItems.isEmpty()) return 1f

    var totalStride = 0f
    var strideCount = 0
    var fallbackTotal = 0f

    val spacing = layoutInfo.mainAxisItemSpacing.toFloat()

    for (index in visibleItems.indices) {
        val current = visibleItems[index]
        fallbackTotal += current.size.toFloat() + spacing

        if (index == 0) continue

        val previous = visibleItems[index - 1]

        if (current.index == previous.index + 1) {
            val stride = (current.offset - previous.offset).toFloat()

            if (stride > 0f) {
                totalStride += stride
                strideCount++
            }
        }
    }

    val observedAverage =
        if (strideCount > 0) {
            totalStride / strideCount
        } else {
            0f
        }

    val fallbackAverage =
        fallbackTotal / visibleItems.size

    return (if (observedAverage > 0f) {
        observedAverage
    } else {
        fallbackAverage
    }).takeIf { it.isFinite() && it > 0f } ?: 1f
}
