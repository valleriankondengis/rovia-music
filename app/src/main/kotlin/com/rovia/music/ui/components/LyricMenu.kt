package com.rovia.music.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.rovia.music.R
import com.rovia.music.ui.theme.ArtworkContentTheme

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LyricMenuButton(
    onShareClick: () -> Unit,
    onOffsetClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    seedColor: Color? = null,
) {
    var expanded by remember {
        mutableStateOf(false)
    }
    val dismiss = { expanded = false }

    Box(
        modifier = modifier,
    ) {
        ArtworkContentTheme(seedColor = seedColor) {
            FilledIconButton(
                onClick = { expanded = true },
                enabled = enabled,
                modifier = Modifier.size(40.dp),
                shapes = IconButtonDefaults.shapes(),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    disabledContainerColor =
                        MaterialTheme.colorScheme.surfaceContainerHigh,
                    disabledContentColor =
                        MaterialTheme.colorScheme.outline,
                ),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_more_vert),
                    contentDescription = stringResource(R.string.lyric_menu),
                    modifier = Modifier.size(24.dp),
                )
            }

            DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = Color.Transparent,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
        ) {
            CompositionLocalProvider(
                LocalTrackMenuDismiss provides dismiss,
            ) {
                Column(
                    modifier = Modifier.clip(RoundedCornerShape(16.dp)),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    TrackMenuItem(
                        text = stringResource(R.string.lyric_menu_share),
                        leadingIcon = {
                            Icon(
                                painter = painterResource(R.drawable.ic_lyrics),
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                            )
                        },
                        enabled = enabled,
                        onClick = onShareClick,
                    )

                    TrackMenuItem(
                        text = stringResource(R.string.lyric_menu_offset),
                        leadingIcon = {
                            Icon(
                                painter = painterResource(R.drawable.ic_offset),
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                            )
                        },
                        enabled = enabled,
                        onClick = onOffsetClick,
                    )
                }
            }
        }
        }
    }
}
