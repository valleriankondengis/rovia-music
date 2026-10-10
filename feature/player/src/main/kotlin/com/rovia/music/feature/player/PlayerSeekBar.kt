@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
)

package com.rovia.music.feature.player

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.sin

@Composable
internal fun WavySeekBar(
    positionMs: Float,
    durationMs: Long,
    isPlaying: Boolean,
    onPositionChange: (Float) -> Unit,
    onSeekFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current

    val wavelength = 48.dp

    val wavelengthPx =
        with(density) {
            wavelength.toPx()
        }

    val waveStrength by
        animateFloatAsState(
            targetValue = if (isPlaying) 1f else 0f,
            animationSpec =
                tween(
                    durationMillis = 650,
                    easing = FastOutSlowInEasing,
                ),
            label = "seekBarWaveStrength",
        )

    var waveOffset by remember {
        mutableFloatStateOf(0f)
    }

    var widthPx by remember {
        mutableFloatStateOf(0f)
    }

    val primaryColor =
        MaterialTheme.colorScheme.primary

    val trackColor =
        MaterialTheme.colorScheme.outlineVariant

    val progress =
        if (durationMs > 0L) {
            (
                positionMs /
                    durationMs.toFloat()
            ).coerceIn(0f, 1f)
        } else {
            0f
        }

    fun positionFromX(
        x: Float,
    ): Float {
        if (
            widthPx <= 0f ||
            durationMs <= 0L
        ) {
            return 0f
        }

        return (
            x.coerceIn(
                0f,
                widthPx,
            ) / widthPx
        ) * durationMs
    }

    LaunchedEffect(wavelengthPx, isPlaying) {
        while (isPlaying) {
            waveOffset =
                (
                    waveOffset +
                        wavelengthPx / 60f
                ) % wavelengthPx

            delay(16L)
        }
    }

    Canvas(
        modifier =
            modifier
                .height(32.dp)
                .onSizeChanged { size ->
                    widthPx =
                        size.width.toFloat()
                }
                .pointerInput(
                    widthPx,
                    durationMs,
                ) {
                    detectTapGestures { offset ->
                        onPositionChange(
                            positionFromX(
                                offset.x,
                            ),
                        )
                        onSeekFinished()
                    }
                }
                .pointerInput(
                    widthPx,
                    durationMs,
                ) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            onPositionChange(
                                positionFromX(
                                    offset.x,
                                ),
                            )
                        },
                        onDrag = { change, _ ->
                            change.consume()

                            onPositionChange(
                                positionFromX(
                                    change.position.x,
                                ),
                            )
                        },
                        onDragEnd = {
                            onSeekFinished()
                        },
                    )
                },
    ) {
        if (widthPx <= 0f) {
            return@Canvas
        }

        val centerY =
            size.height / 2f

        val amplitude =
            3.5.dp.toPx() * waveStrength

        val strokeWidth =
            4.dp.toPx()

        val dotRadius =
            8.dp.toPx()

        val trackGap =
            10.dp.toPx()

        val stopIndicatorSize =
            SliderDefaults.TrackStopIndicatorSize

        val stopIndicatorRadius =
            with(density) {
                stopIndicatorSize.toPx() / 2f
            }

        // Keep the native stop indicator fully inside the Canvas bounds.
        val stopIndicatorCenterX =
            (size.width - stopIndicatorRadius)
                .coerceAtLeast(0f)

        val progressX =
            size.width * progress

        val trackStart =
            progressX +
                dotRadius +
                trackGap

        if (trackStart < stopIndicatorCenterX) {
            drawLine(
                color = trackColor,
                start =
                    Offset(
                        x = trackStart,
                        y = centerY,
                    ),
                end =
                    Offset(
                        x = stopIndicatorCenterX,
                        y = centerY,
                    ),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round,
            )
        }

        if (progressX > 0f) {
            val path = Path()

            path.moveTo(
                x = 0f,
                y = centerY,
            )

            if (waveStrength > 0f) {
                var x = 1.5f

                while (x <= progressX) {
                    val phase =
                        (
                            (x - waveOffset) /
                                wavelengthPx
                        ) *
                            (
                                2f *
                                    PI.toFloat()
                            )

                    val wave =
                        amplitude *
                            sin(
                                phase.toDouble(),
                            ).toFloat()

                    val ramp =
                        (
                            x /
                                wavelengthPx
                        ).coerceIn(
                            0f,
                            1f,
                        )

                    path.lineTo(
                        x = x,
                        y =
                            centerY +
                                wave * ramp,
                    )

                    x += 1.5f
                }
            } else {
                path.lineTo(
                    x = progressX,
                    y = centerY,
                )
            }

            drawPath(
                path = path,
                color = primaryColor,
                style =
                    Stroke(
                        width = strokeWidth,
                        cap = StrokeCap.Round,
                    ),
            )
        }

        drawCircle(
            color = primaryColor,
            radius = dotRadius,
            center =
                Offset(
                    x = progressX,
                    y = centerY,
                ),
        )

        // Material 3 Slider's native stop indicator at the track's right end.
        with(SliderDefaults) {
            drawStopIndicator(
                offset =
                    Offset(
                        x = stopIndicatorCenterX,
                        y = centerY,
                    ),
                size = stopIndicatorSize,
                color = primaryColor,
            )
        }
    }
}
