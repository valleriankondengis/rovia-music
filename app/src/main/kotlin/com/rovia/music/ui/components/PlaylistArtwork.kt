package com.rovia.music.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rovia.music.R

/**
 * Artwork for a playlist. Playlists never carry imported images: the playlist
 * name itself is the artwork, set as cover typography on a background color
 * derived deterministically from the name. Every size shows the full name,
 * scaled down on small tiles.
 */
@Composable
fun PlaylistArtwork(
    name: String,
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
    shape: Shape = MaterialTheme.shapes.large,
) {
    val colorScheme = MaterialTheme.colorScheme
    val palette = remember(colorScheme) {
        listOf(
            colorScheme.primaryContainer to colorScheme.onPrimaryContainer,
            colorScheme.secondaryContainer to colorScheme.onSecondaryContainer,
            colorScheme.tertiaryContainer to colorScheme.onTertiaryContainer,
            colorScheme.primary to colorScheme.onPrimary,
            colorScheme.secondary to colorScheme.onSecondary,
            colorScheme.tertiary to colorScheme.onTertiary,
            colorScheme.surfaceContainerHighest to colorScheme.onSurface,
        )
    }
    val (containerColor, contentColor) = remember(name, palette) {
        val index = (name.trim().lowercase().hashCode() and Int.MAX_VALUE) % palette.size
        palette[index]
    }
    val displayName = name.trim().ifBlank { stringResource(R.string.search_section_playlists) }

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(shape)
            .background(containerColor),
        contentAlignment = Alignment.Center,
    ) {
        if (size > 64.dp) {
            Text(
                text = displayName,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(size * 0.11f),
                style = if (size >= 200.dp) {
                    MaterialTheme.typography.headlineLarge
                } else {
                    MaterialTheme.typography.titleLarge
                },
                fontWeight = FontWeight.Bold,
                color = contentColor,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
        } else {
            Text(
                text = displayName,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(size * 0.13f),
                fontSize = (size.value * 0.21f).sp,
                fontWeight = FontWeight.Bold,
                lineHeight = (size.value * 0.24f).sp,
                color = contentColor,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Tile used for the Liked Songs entry wherever a playlist-style row appears. */
@Composable
fun LikedArtwork(
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
    shape: Shape = MaterialTheme.shapes.large,
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
) {
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(shape)
            .background(containerColor),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_favorite),
            contentDescription = null,
            modifier = Modifier.size(size * 0.52f),
            tint = contentColor,
        )
    }
}
