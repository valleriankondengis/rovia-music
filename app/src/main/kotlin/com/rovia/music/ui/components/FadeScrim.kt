package com.rovia.music.ui.components

import androidx.compose.animation.core.EaseInCubic
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.blur.BlurRadiusSpec
import androidx.compose.ui.graphics.blur.BlurStop
import androidx.compose.ui.unit.dp

private val FadeScrimHeight = 180.dp
private val FadeScrimHeightWithMiniPlayer = 254.dp
private const val FadeScrimStops = 16

private val FadeScrimBlurTopHeight = 140.dp
private val FadeScrimBlurBottomHeight = 56.dp
private val FadeScrimBlurRadius = 12.dp
private const val MaxTopBlurFraction = 0.32f
private const val MaxBottomBlurFraction = 0.22f
private const val BottomScrimEndAlpha = 0.72f

enum class FadeScrimEdge {
    Top,
    Bottom
}

@Composable
fun FadeScrim(
    edge: FadeScrimEdge,
    modifier: Modifier = Modifier,
    withMiniPlayer: Boolean = false,
    pageColor: Color = MaterialTheme.colorScheme.background
) {
    val navigationBarInset = if (edge == FadeScrimEdge.Bottom) {
        WindowInsets.navigationBars
            .asPaddingValues()
            .calculateBottomPadding()
    } else {
        0.dp
    }

    val targetHeight = when (edge) {
        FadeScrimEdge.Top -> FadeScrimHeight
        FadeScrimEdge.Bottom -> navigationBarInset + if (withMiniPlayer) {
            FadeScrimHeightWithMiniPlayer
        } else {
            FadeScrimHeight
        }
    }

    val height by animateDpAsState(
        targetValue = targetHeight,
        animationSpec = tween(durationMillis = 220),
        label = if (edge == FadeScrimEdge.Top) {
            "topFadeScrimHeight"
        } else {
            "bottomFadeScrimHeight"
        }
    )

    val gradient = remember(pageColor, edge, height) {
        if (edge == FadeScrimEdge.Top) {
            Brush.verticalGradient(
                colorStops = Array(FadeScrimStops) { index ->
                    val fraction = index / (FadeScrimStops - 1f)
                    fraction to pageColor.copy(
                        alpha = EaseInCubic.transform(fraction)
                    )
                }
            )
        } else {
            val bottomBlurFraction = (FadeScrimBlurBottomHeight / height)
                .coerceIn(0.01f, 0.95f)
            val bottomStartFraction = 1f - bottomBlurFraction
            val bottomStartAlpha = EaseInCubic.transform(bottomStartFraction)

            val stops = buildList {
                repeat(FadeScrimStops) { index ->
                    val fraction = index / (FadeScrimStops - 1f)
                    if (fraction < bottomStartFraction) {
                        add(
                            fraction to pageColor.copy(
                                alpha = EaseInCubic.transform(fraction)
                            )
                        )
                    }
                }

                add(bottomStartFraction to pageColor.copy(alpha = bottomStartAlpha))
                add(1f to pageColor.copy(alpha = BottomScrimEndAlpha))
            }
                .sortedBy { it.first }
                .distinctBy { it.first }
                .toTypedArray()

            Brush.verticalGradient(colorStops = stops)
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .then(
                if (edge == FadeScrimEdge.Top) {
                    Modifier.rotate(180f)
                } else {
                    Modifier
                }
            )
            .background(gradient)
    )
}

@Composable
@Suppress("UNUSED_PARAMETER")
fun Modifier.fadeScrimProgressiveBlur(
    withMiniPlayer: Boolean = false
): Modifier {
    return blur {
        val layerHeight = size.height

        if (layerHeight <= 0.dp) {
            blurRadiusSpec = BlurRadiusSpec.uniform(0.dp)
        } else {
            val topFadeFraction = (FadeScrimBlurTopHeight / layerHeight)
                .coerceIn(0.01f, MaxTopBlurFraction)
            val bottomFadeFraction = (FadeScrimBlurBottomHeight / layerHeight)
                .coerceIn(0.01f, MaxBottomBlurFraction)
            val bottomFadeStart = (1f - bottomFadeFraction)
                .coerceAtLeast(topFadeFraction + 0.02f)
                .coerceAtMost(0.99f)

            blurRadiusSpec = BlurRadiusSpec.verticalGradient(
                stops = listOf(
                    BlurStop(
                        fraction = 0f,
                        radius = FadeScrimBlurRadius
                    ),
                    BlurStop(
                        fraction = topFadeFraction,
                        radius = 0.dp
                    ),
                    BlurStop(
                        fraction = bottomFadeStart,
                        radius = 0.dp
                    ),
                    BlurStop(
                        fraction = 1f,
                        radius = FadeScrimBlurRadius
                    )
                )
            )
        }

        edgeTreatment = BlurredEdgeTreatment.Unbounded
    }
}