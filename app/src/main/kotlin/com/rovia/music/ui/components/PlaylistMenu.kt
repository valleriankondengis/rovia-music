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

/**
 * The playlist page's overflow menu, built the way the lyric menu is:
 * a filled icon button and a transparent dropdown of segmented rows,
 * all inside an artwork content theme so the button and the rows take
 * the playlist artwork's colors. The playlist page's artwork theme
 * is dark, so the menu's artwork scheme is dark as well.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PlaylistMenuButton(
    onRenameClick: () -> Unit,
    onDeleteClick: () -> Unit,
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
        ArtworkContentTheme(seedColor = seedColor, darkTheme = true) {
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
                    contentDescription = stringResource(R.string.playlist_menu),
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
                            text = stringResource(R.string.playlist_rename),
                            leadingIcon = {
                                Icon(
                                    painter = painterResource(R.drawable.ic_edit),
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp),
                                )
                            },
                            enabled = enabled,
                            onClick = onRenameClick,
                        )

                        TrackMenuItem(
                            text = stringResource(R.string.playlist_delete),
                            leadingIcon = {
                                Icon(
                                    painter = painterResource(R.drawable.ic_delete),
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp),
                                )
                            },
                            enabled = enabled,
                            onClick = onDeleteClick,
                        )
                    }
                }
            }
        }
    }
}
