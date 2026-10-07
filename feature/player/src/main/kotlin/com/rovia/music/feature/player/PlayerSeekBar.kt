@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
)

package com.rovia.music.feature.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.MaterialTheme
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.sin

@Composable
internal fun WavySeekBar(
    positionMs: Float,
    durationMs: Long,
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

    LaunchedEffect(wavelengthPx) {
        while (true) {
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
            3.5.dp.toPx()

        val strokeWidth =
            4.dp.toPx()

        val dotRadius =
            8.dp.toPx()

        val trackGap =
            10.dp.toPx()

        val progressX =
            size.width * progress

        val trackStart =
            progressX +
                dotRadius +
                trackGap

        if (trackStart < size.width) {
            drawLine(
                color = trackColor,
                start =
                    Offset(
                        x = trackStart,
                        y = centerY,
                    ),
                end =
                    Offset(
                        x = size.width,
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
    }
}

