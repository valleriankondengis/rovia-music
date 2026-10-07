@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
)

package com.rovia.music.feature.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rovia.music.core.model.Track
import com.rovia.music.core.ui.component.AlbumArtwork

@Composable
internal fun PlayerArtwork(
    track: Track?,
    size: Dp = 300.dp,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        AlbumArtwork(
            artworkUri = track?.artworkUri,
            fallbackText = track?.title ?: "Rovia",
            contentDescription = null,
            modifier = Modifier.size(size),
            size = size,
            shape = MaterialTheme.shapes.extraLarge,
        )
    }
}
