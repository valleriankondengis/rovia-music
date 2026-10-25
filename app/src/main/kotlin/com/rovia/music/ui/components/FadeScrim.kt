
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val FadeScrimHeight = 180.dp
private val FadeScrimHeightWithMiniPlayer = 254.dp
private const val FadeScrimStops = 16

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

        FadeScrimEdge.Bottom -> navigationBarInset +
            if (withMiniPlayer) {
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

    val gradient = remember(pageColor) {
        Brush.verticalGradient(
            colorStops = Array(FadeScrimStops) { index ->
                val fraction = index / (FadeScrimStops - 1f)

                fraction to pageColor.copy(
                    alpha = EaseInCubic.transform(fraction)
                )
            }
        )
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
