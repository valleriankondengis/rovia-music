package com.rovia.music.ui.components

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.blur.BlurRadiusSpec
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun ArtworkBackdrop(
    artworkUri: String?,
    foregroundArtworkHeight: Dp,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current.applicationContext
    val materialSurface = MaterialTheme.colorScheme.surface
    val isDarkTheme = materialSurface.luminance() < 0.5f

    val artworkBitmap by produceState<Bitmap?>(
        initialValue = null,
        key1 = artworkUri,
        key2 = context,
    ) {
        value = ArtworkBitmapLoader.load(
            context = context,
            artworkUri = artworkUri,
            maxDimension = 1024,
        )
    }

    val backdropArtworkBitmap by produceState<Bitmap?>(
        initialValue = null,
        key1 = artworkBitmap,
    ) {
        val source = artworkBitmap?.takeUnless(Bitmap::isRecycled)
        value = source?.let { bitmap ->
            withContext(Dispatchers.Default) {
                createBackdropArtwork(bitmap)
            }
        }
    }

    val artworkBackgroundColor by produceState<Color?>(
        initialValue = null,
        key1 = artworkBitmap,
        key2 = isDarkTheme,
    ) {
        val bitmap = artworkBitmap?.takeUnless(Bitmap::isRecycled)
        value = bitmap?.let {
            withContext(Dispatchers.Default) {
                artworkSurfaceColor(it, isDarkTheme)
            }
        }
    }

    val targetBackgroundColor = artworkBackgroundColor ?: materialSurface
    val backgroundColor by animateColorAsState(
        targetValue = targetBackgroundColor,
        animationSpec = tween(durationMillis = 480),
        label = "artwork-backdrop-background-color",
    )

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundColor),
    ) {
        val imageHeight = foregroundArtworkHeight
            .coerceAtLeast(0.dp)
            .coerceAtMost(maxHeight)

        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth(1f / BACKDROP_RENDER_SCALE)
                .fillMaxHeight(1f / BACKDROP_RENDER_SCALE)
                .graphicsLayer {
                    scaleX = BACKDROP_RENDER_SCALE
                    scaleY = BACKDROP_RENDER_SCALE
                }
                .blur(
                    blurRadiusSpec = BlurRadiusSpec.verticalGradient(
                        startRadius = 20.dp,
                        endRadius = 30.dp,
                    ),
                    edgeTreatment = BlurredEdgeTreatment.Rectangle,
                ),
        ) {
            Crossfade(
                targetState = backdropArtworkBitmap?.takeUnless(Bitmap::isRecycled),
                animationSpec = tween(durationMillis = 460),
                label = "artwork-backdrop-blur-crossfade",
            ) { bitmap ->
                if (bitmap != null && !bitmap.isRecycled) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        alignment = Alignment.Center,
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0f to backgroundColor.copy(
                                alpha = if (isDarkTheme) 0.16f else 0.10f,
                            ),
                            0.36f to backgroundColor.copy(
                                alpha = if (isDarkTheme) 0.22f else 0.18f,
                            ),
                            0.66f to backgroundColor.copy(
                                alpha = if (isDarkTheme) 0.40f else 0.34f,
                            ),
                            1f to backgroundColor.copy(
                                alpha = if (isDarkTheme) 0.64f else 0.50f,
                            ),
                        ),
                    ),
                ),
        )

        if (artworkBitmap != null && imageHeight > 0.dp) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(imageHeight)
                    .graphicsLayer {
                        compositingStrategy = CompositingStrategy.Offscreen
                    }
                    .drawWithContent {
                        drawContent()
                        drawRect(
                            brush = Brush.verticalGradient(
                                colorStops = arrayOf(
                                    0f to Color.White,
                                    0.58f to Color.White,
                                    1f to Color.Transparent,
                                ),
                            ),
                            blendMode = BlendMode.DstIn,
                        )
                    },
            ) {
                Crossfade(
                    targetState = artworkBitmap?.takeUnless(Bitmap::isRecycled),
                    animationSpec = tween(durationMillis = 460),
                    label = "artwork-backdrop-image-crossfade",
                ) { bitmap ->
                    if (bitmap != null && !bitmap.isRecycled) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                            alignment = Alignment.Center,
                        )
                    }
                }
            }
        }

        val topScrim = Color.Black.copy(
            alpha = if (isDarkTheme) 0.28f else 0.10f,
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to topScrim,
                        0.14f to Color.Transparent,
                        1f to Color.Transparent,
                    ),
                ),
        )
    }
}

private fun createBackdropArtwork(source: Bitmap): Bitmap? {
    if (
        source.width <= 0 ||
        source.height <= 0 ||
        source.isRecycled
    ) {
        return null
    }

    val largestDimension = maxOf(source.width, source.height)

    if (largestDimension <= BACKDROP_SOURCE_MAX_DIMENSION) {
        return source
    }

    val scale = BACKDROP_SOURCE_MAX_DIMENSION.toFloat() / largestDimension
    val width = (source.width * scale).roundToInt().coerceAtLeast(1)
    val height = (source.height * scale).roundToInt().coerceAtLeast(1)

    return runCatching {
        Bitmap.createScaledBitmap(
            source,
            width,
            height,
            true,
        )
    }.getOrNull()
}

private fun artworkSurfaceColor(
    bitmap: Bitmap,
    isDarkTheme: Boolean,
): Color {
    if (bitmap.width <= 0 || bitmap.height <= 0) {
        return fallbackArtworkColor(isDarkTheme)
    }

    val samplingBitmap = if (bitmap.config == Bitmap.Config.HARDWARE) {
        bitmap.copy(Bitmap.Config.ARGB_8888, false)
            ?: return fallbackArtworkColor(isDarkTheme)
    } else {
        bitmap
    }

    try {
        val stepX = (samplingBitmap.width / 32).coerceAtLeast(1)
        val stepY = (samplingBitmap.height / 32).coerceAtLeast(1)
        val hsv = FloatArray(3)

        var hueSin = 0.0
        var hueCos = 0.0
        var totalWeight = 0.0
        var weightedSaturation = 0.0

        for (y in 0 until samplingBitmap.height step stepY) {
            for (x in 0 until samplingBitmap.width step stepX) {
                val pixel = samplingBitmap.getPixel(x, y)

                if (AndroidColor.alpha(pixel) < 128) continue

                AndroidColor.colorToHSV(pixel, hsv)

                val saturation = hsv[1].toDouble()
                val brightness = hsv[2].toDouble()

                if (
                    saturation < 0.12 ||
                    brightness < 0.04 ||
                    brightness > 0.98
                ) {
                    continue
                }

                val weight =
                    saturation * (0.35 + 1.0 - abs(brightness - 0.52))

                val radians = hsv[0] * PI / 180.0

                hueSin += sin(radians) * weight
                hueCos += cos(radians) * weight
                weightedSaturation += saturation * weight
                totalWeight += weight
            }
        }

        val hue = if (totalWeight > 0.0) {
            ((atan2(hueSin, hueCos) * 180.0 / PI) + 360.0) % 360.0
        } else {
            258.0
        }.toFloat()

        val averageSaturation = if (totalWeight > 0.0) {
            (weightedSaturation / totalWeight).toFloat()
        } else {
            0.18f
        }

        val saturation = if (isDarkTheme) {
            (0.28f + averageSaturation * 0.45f)
                .coerceIn(0.28f, 0.62f)
        } else {
            (averageSaturation * 0.25f)
                .coerceIn(0.08f, 0.24f)
        }

        val brightness = if (isDarkTheme) {
            (0.12f + averageSaturation * 0.06f)
                .coerceIn(0.12f, 0.18f)
        } else {
            0.97f
        }

        return Color(
            AndroidColor.HSVToColor(
                floatArrayOf(hue, saturation, brightness),
            ),
        )
    } finally {
        if (
            samplingBitmap !== bitmap &&
            !samplingBitmap.isRecycled
        ) {
            samplingBitmap.recycle()
        }
    }
}

private fun fallbackArtworkColor(
    isDarkTheme: Boolean,
): Color = if (isDarkTheme) {
    Color(0xFF17171B)
} else {
    Color(0xFFF7F5F8)
}

private const val BACKDROP_SOURCE_MAX_DIMENSION = 144
private const val BACKDROP_RENDER_SCALE = 4f