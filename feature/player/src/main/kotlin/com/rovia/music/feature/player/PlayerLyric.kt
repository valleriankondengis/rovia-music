@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
)

package com.rovia.music.feature.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.rovia.music.core.model.SyncedLyrics
import com.rovia.music.core.ui.R as CoreUiR

@Composable
internal fun PlayerLyric(
    lyrics: SyncedLyrics?,
    hasLyrics: Boolean,
    isLyricsLoading: Boolean,
    playbackPositionMs: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!isLyricsLoading && hasLyrics && lyrics != null) {
        LyricViewer(
            lyrics = lyrics,
            positionMs = playbackPositionMs,
            onSeek = onSeek,
            modifier = modifier,
        )
    } else {
        Box(
            modifier = modifier,
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(CoreUiR.drawable.ic_lyrics),
                contentDescription = stringResource(R.string.player_show_lyrics),
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
