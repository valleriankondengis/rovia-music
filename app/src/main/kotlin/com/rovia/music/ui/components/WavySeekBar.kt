package com.rovia.music.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.WavyProgressIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.times
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

// Time after the amplitude target reaches 0 until the wave is fully flat:
// the indicator's 500 ms amplitude fade, up to 500 ms of delay while a running
// amplitude animation finishes, plus slack for frame pickup and dropped frames.
private const val WaveFlattenMillis = 1_500L

/**
 * A seek bar whose played portion is drawn as a travelling wave while playback
 * runs. The track is a [LinearWavyProgressIndicator]; the thumb is drawn
 * separately and morphs from a circle into a vertical line while the user
 * scrubs, and the wave flattens when playback pauses or the user interacts.
 * The value is a 0..1 fraction; [onValueChange] reports the scrub position
 * live and [onValueCommit] fires once when the gesture ends.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun WavySeekBar(
    value: () -> Float,
    onValueChange: (Float) -> Unit,
    onValueCommit: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isPlaying: Boolean = true,
    activeTrackColor: Color = MaterialTheme.colorScheme.primaryContainer,
    inactiveTrackColor: Color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f),
    thumbColor: Color = MaterialTheme.colorScheme.primaryContainer,
    strokeWidth: Dp = 5.dp,
    thumbRadius: Dp = 8.dp,
    wavelength: Dp = 30.dp,
    semanticsLabel: String? = null,
) {
    val waveSpeed = wavelength / 2f
    val thumbLineHeightWhenInteracting = 24.dp
    val trackEdgePadding = thumbRadius
    val semanticsProgressStep = 0.01f

    val density = LocalDensity.current
    val strokeWidthPx = with(density) { strokeWidth.toPx() }
    val thumbRadiusPx = with(density) { thumbRadius.toPx() }
    val trackEdgePaddingPx = with(density) { trackEdgePadding.coerceAtLeast(0.dp).toPx() }
    val thumbLineHeightPx = with(density) { thumbLineHeightWhenInteracting.toPx() }

    val stroke = remember(strokeWidthPx) {
        Stroke(width = strokeWidthPx, cap = StrokeCap.Round)
    }

    // Always read through the latest provider: callers may swap the state
    // backing `value`, and a captured lambda would keep reading stale state.
    val latestValueProvider by rememberUpdatedState(value)

    val normalizedValueState = remember {
        derivedStateOf {
            val v = latestValueProvider()
            if (!v.isFinite()) 0f else v.coerceIn(0f, 1f)
        }
    }

    val safeSemanticsStep = semanticsProgressStep.coerceIn(0.005f, 0.25f)
    val semanticNormalizedValueState = remember(safeSemanticsStep, normalizedValueState) {
        derivedStateOf {
            val norm = normalizedValueState.value
            ((norm / safeSemanticsStep).roundToInt() * safeSemanticsStep).coerceIn(0f, 1f)
        }
    }
    val latestOnValueChange by rememberUpdatedState(onValueChange)
    val latestOnValueCommit by rememberUpdatedState(onValueCommit)
    var isPointerSeeking by remember { mutableStateOf(false) }
    val isInteracting = isPointerSeeking

    val thumbInteractionFraction by animateFloatAsState(
        targetValue = if (isInteracting) 1f else 0f,
        animationSpec = tween(250, easing = FastOutSlowInEasing),
        label = "ThumbInteractionAnim",
    )
    // The indicator animates amplitude itself and ignores a new target while
    // an animation is running, so it receives this target directly rather than
    // the animated value below; intermediate values would leave a small
    // non-zero amplitude after a pause and redraw every frame.
    val waveAmplitudeTarget = if (enabled && isPlaying && !isInteracting) 1f else 0f

    // A non-zero wave speed runs the wave offset animation every frame, even
    // once the wave is flat, so it is stopped after the amplitude fade ends to
    // avoid waking the main thread at the display refresh rate while paused.
    // A zero speed snaps the wave phase to 0, invisible on a flat wave.
    var isWaveStopped by remember { mutableStateOf(waveAmplitudeTarget == 0f) }
    LaunchedEffect(waveAmplitudeTarget) {
        if (waveAmplitudeTarget > 0f) {
            isWaveStopped = false
        } else if (!isWaveStopped) {
            val durationScale = coroutineContext[MotionDurationScale]?.scaleFactor ?: 1f
            delay((WaveFlattenMillis * durationScale).toLong())
            isWaveStopped = true
        }
    }
    // Restarts in the same composition that raises the amplitude target, so
    // the wave never grows back while stopped.
    val indicatorWaveSpeed = if (waveAmplitudeTarget > 0f || !isWaveStopped) waveSpeed else 0.dp

    val animatedAmplitude by animateFloatAsState(
        targetValue = waveAmplitudeTarget,
        animationSpec = ProgressIndicatorDefaults.ProgressAnimationSpec,
        label = "amplitude",
    )

    val currentHalfWidth = remember(thumbRadius, strokeWidth) {
        derivedStateOf {
            val fraction = thumbInteractionFraction
            val radius = thumbRadius
            val halfStroke = strokeWidth * 0.6f
            radius * (1f - fraction) + halfStroke * fraction
        }
    }

    val dynamicGapSize = remember {
        derivedStateOf {
            val fraction = thumbInteractionFraction
            val idleGap = 6.dp
            val draggingGap = currentHalfWidth.value + 1.2.dp
            idleGap + (draggingGap - idleGap) * fraction
        }
    }

    val renderedNormalizedProgress = remember {
        mutableFloatStateOf(normalizedValueState.value)
    }
    var lastProgressUpdateNanos by remember { mutableLongStateOf(0L) }
    LaunchedEffect(isInteracting, enabled, normalizedValueState) {
        snapshotFlow { normalizedValueState.value }.collect { target ->
            if (!enabled || isInteracting) {
                renderedNormalizedProgress.floatValue = target
                lastProgressUpdateNanos = System.nanoTime()
                return@collect
            }

            val start = renderedNormalizedProgress.floatValue
            if (abs(start - target) > 0.1f) {
                renderedNormalizedProgress.floatValue = target
                lastProgressUpdateNanos = System.nanoTime()
                return@collect
            }

            val nowNanos = System.nanoTime()
            val intervalMs = if (lastProgressUpdateNanos == 0L) {
                180L
            } else {
                ((nowNanos - lastProgressUpdateNanos) / 1_000_000L).coerceIn(1L, 250L)
            }
            lastProgressUpdateNanos = nowNanos

            if (abs(start - target) <= 0.0001f) {
                renderedNormalizedProgress.floatValue = target
                return@collect
            }

            val durationNanos = (intervalMs * 900_000L).coerceAtLeast(1_000_000L)
            var startFrameNanos = 0L
            var liveTarget = target
            while (isActive) {
                val frameNanos = withFrameNanos { it }
                if (startFrameNanos == 0L) startFrameNanos = frameNanos
                // Re-read the target every frame so the drawn position keeps
                // converging even if an upstream emission is missed.
                liveTarget = normalizedValueState.value
                val elapsedNanos = (frameNanos - startFrameNanos).coerceAtLeast(0L)
                val fraction =
                    (elapsedNanos.toDouble() / durationNanos.toDouble()).toFloat().coerceIn(0f, 1f)
                renderedNormalizedProgress.floatValue = start + (liveTarget - start) * fraction
                if (fraction >= 1f) break
            }
            renderedNormalizedProgress.floatValue = liveTarget
        }
    }

    val containerHeight =
        max(
            WavyProgressIndicatorDefaults.LinearContainerHeight,
            max(thumbRadius * 2, thumbLineHeightWhenInteracting),
        )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(containerHeight)
            .clearAndSetSemantics {
                if (!semanticsLabel.isNullOrBlank()) {
                    contentDescription = semanticsLabel
                }
                progressBarRangeInfo = ProgressBarRangeInfo(
                    current = semanticNormalizedValueState.value,
                    range = 0f..1f,
                    steps = 0,
                )
                if (enabled) {
                    setProgress { requested ->
                        val coerced = requested.coerceIn(0f, 1f)
                        latestOnValueChange(coerced)
                        latestOnValueCommit(coerced)
                        true
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        // Media progress is never mirrored; forcing LTR keeps the indicator
        // aligned with the thumb and the touch math, which use absolute x
        // coordinates.
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            LinearWavyProgressIndicator(
                progress = { renderedNormalizedProgress.floatValue },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = trackEdgePadding.coerceAtLeast(0.dp))
                    .clearAndSetSemantics { },
                color = activeTrackColor,
                trackColor = inactiveTrackColor,
                stroke = stroke,
                trackStroke = stroke,
                gapSize = 2f * dynamicGapSize.value * (1.0f + 0.1573f * animatedAmplitude * animatedAmplitude),
                stopSize = 3.dp,
                amplitude = { progress -> if (progress > 0f) waveAmplitudeTarget else 0f },
                wavelength = wavelength,
                waveSpeed = indicatorWaveSpeed,
            )

            Canvas(modifier = Modifier.fillMaxSize()) {
                val edgePaddingPx = trackEdgePaddingPx.coerceIn(0f, size.width / 2f)
                val trackStart = edgePaddingPx
                val trackEnd = size.width - edgePaddingPx
                val trackWidth = (trackEnd - trackStart).coerceAtLeast(0f)
                val thumbY = size.height / 2
                val renderedProgress = renderedNormalizedProgress.floatValue

                fun lerp(start: Float, stop: Float, fraction: Float): Float {
                    return start + (stop - start) * fraction
                }

                val currentWidth = lerp(thumbRadiusPx * 2f, strokeWidthPx * 1.2f, thumbInteractionFraction)
                val currentHeight = lerp(thumbRadiusPx * 2f, thumbLineHeightPx, thumbInteractionFraction)
                val rawThumbX = trackStart + (trackWidth * renderedProgress)
                val minThumbCenter = (currentWidth / 2f).coerceAtMost(size.width / 2f)
                val maxThumbCenter = (size.width - currentWidth / 2f).coerceAtLeast(minThumbCenter)
                val thumbX = rawThumbX.coerceIn(minThumbCenter, maxThumbCenter)

                drawRoundRect(
                    color = thumbColor,
                    topLeft = Offset(
                        thumbX - currentWidth / 2f,
                        thumbY - currentHeight / 2f,
                    ),
                    size = Size(currentWidth, currentHeight),
                    cornerRadius = CornerRadius(currentWidth / 2f),
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(enabled, trackEdgePaddingPx) {
                        if (!enabled) return@pointerInput

                        fun valueForX(rawX: Float): Float {
                            val edgePadding = trackEdgePaddingPx.coerceIn(0f, size.width / 2f)
                            val trackStart = edgePadding
                            val trackEnd = size.width - edgePadding
                            val trackWidth = (trackEnd - trackStart).coerceAtLeast(1f)
                            return ((rawX - trackStart) / trackWidth).coerceIn(0f, 1f)
                        }

                        awaitEachGesture {
                            var gestureValue: Float? = null
                            var committed = false
                            try {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                isPointerSeeking = true
                                down.consume()
                                var latestGestureValue = valueForX(down.position.x)
                                gestureValue = latestGestureValue
                                latestOnValueChange(latestGestureValue)

                                var pointerId = down.id
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == pointerId }
                                        ?: event.changes.firstOrNull { it.pressed }
                                        ?: break

                                    pointerId = change.id
                                    if (!change.pressed) {
                                        change.consume()
                                        break
                                    }

                                    if (change.position != change.previousPosition) {
                                        change.consume()
                                        latestGestureValue = valueForX(change.position.x)
                                        gestureValue = latestGestureValue
                                        latestOnValueChange(latestGestureValue)
                                    }
                                }

                                committed = true
                                latestOnValueCommit(latestGestureValue)
                            } finally {
                                isPointerSeeking = false
                                // A gesture can be torn down without an up event
                                // (pointerInput restart or disposal). Always
                                // terminate it; a latched "user is scrubbing" flag
                                // would otherwise stop playback tracking.
                                if (!committed) {
                                    gestureValue?.let { pendingValue ->
                                        latestOnValueCommit(pendingValue)
                                    }
                                }
                            }
                        }
                    },
            )
        }
    }
}
