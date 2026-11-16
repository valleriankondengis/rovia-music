package com.rovia.music.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.rovia.music.R
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rovia.music.data.mediastore.LocalAudioTrack
import com.rovia.music.data.repository.PlaylistSummary
import com.rovia.music.data.repository.UserLibraryRepository
import kotlinx.coroutines.launch

/** Dismiss handle for the open track menu, in the Essentials menu pattern. */
val LocalTrackMenuDismiss = staticCompositionLocalOf<(() -> Unit)?> { null }

/**
 * One row of a track menu: a segmented card row on surfaceContainerHigh that
 * dismisses the menu after its action, following the Essentials
 * SegmentedDropdownMenu design.
 */
@Composable
fun ColumnScope.TrackMenuItem(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: @Composable (() -> Unit)? = null,
    enabled: Boolean = true,
) {
    val dismiss = LocalTrackMenuDismiss.current
    val colorScheme = MaterialTheme.colorScheme

    DropdownMenuItem(
        text = {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
            )
        },
        onClick = {
            onClick()
            dismiss?.invoke()
        },
        modifier = modifier
            .clip(MaterialTheme.shapes.extraSmall)
            .background(colorScheme.surfaceContainerHigh),
        leadingIcon = leadingIcon,
        enabled = enabled,
        colors = MenuDefaults.itemColors(
            textColor = colorScheme.onSurface,
            leadingIconColor = colorScheme.onSurface,
            trailingIconColor = colorScheme.onSurface,
            disabledTextColor = colorScheme.onSurface.copy(alpha = 0.38f),
            disabledLeadingIconColor = colorScheme.onSurface.copy(alpha = 0.38f),
            disabledTrailingIconColor = colorScheme.onSurface.copy(alpha = 0.38f),
        ),
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TrackMenu(
    onTrackInfoClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    track: LocalAudioTrack? = null,
    isLiked: Boolean = false,
    onToggleLike: ((LocalAudioTrack) -> Unit)? = null,
    onAddToPlaylist: ((LocalAudioTrack) -> Unit)? = null,
    menuContent: @Composable ColumnScope.(dismissMenu: () -> Unit) -> Unit = {},
) {
    var expanded by remember { mutableStateOf(false) }
    val dismiss = { expanded = false }

    Box(modifier = modifier) {
        FilledIconButton(
            onClick = { expanded = true },
            enabled = enabled,
            modifier = Modifier.size(40.dp),
            shapes = IconButtonDefaults.shapes(),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                    alpha = 0.38f,
                ),
            ),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_more_vert),
                contentDescription = stringResource(R.string.track_menu_open),
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
            CompositionLocalProvider(LocalTrackMenuDismiss provides dismiss) {
                Column(
                    modifier = Modifier.clip(RoundedCornerShape(16.dp)),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    TrackMenuItem(
                        text = stringResource(R.string.track_info_open),
                        leadingIcon = {
                            Icon(
                                painter = painterResource(R.drawable.ic_info),
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                            )
                        },
                        enabled = enabled,
                        onClick = onTrackInfoClick,
                    )

                    if (track != null && onToggleLike != null) {
                        TrackMenuItem(
                            text = stringResource(
                                if (isLiked) R.string.like_remove else R.string.like_add
                            ),
                            leadingIcon = {
                                Icon(
                                    painter = painterResource(
                                        if (isLiked) {
                                            R.drawable.ic_favorite
                                        } else {
                                            R.drawable.ic_favorite_border
                                        }
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp),
                                )
                            },
                            enabled = enabled,
                            onClick = { onToggleLike(track) },
                        )
                    }

                    if (track != null && onAddToPlaylist != null) {
                        TrackMenuItem(
                            text = stringResource(R.string.playlist_add_to),
                            leadingIcon = {
                                Icon(
                                    painter = painterResource(R.drawable.ic_add),
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp),
                                )
                            },
                            enabled = enabled,
                            onClick = { onAddToPlaylist(track) },
                        )
                    }

                    menuContent(dismiss)
                }
            }
        }
    }
}


/**
 * Shared wiring for the liked and playlist actions in track menus outside
 * the library screens: the observed playlists and liked keys, the like
 * toggle, and the add-to-playlist picker state.
 */
class UserLibraryMenuState internal constructor(
    val playlists: List<PlaylistSummary>,
    val likedTrackKeys: Set<String>,
    val onToggleLike: (LocalAudioTrack) -> Unit,
    val pickerTrack: LocalAudioTrack?,
    val onShowPlaylistPicker: (LocalAudioTrack) -> Unit,
    val onDismissPlaylistPicker: () -> Unit,
    val onAddToPlaylist: (Long, LocalAudioTrack) -> Unit,
    val onCreatePlaylist: (String, LocalAudioTrack) -> Unit,
)

@Composable
fun rememberUserLibraryMenuState(
    repository: UserLibraryRepository,
): UserLibraryMenuState {
    val playlistsFlow = remember(repository) { repository.observePlaylists() }
    val playlists by playlistsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val likedKeysFlow = remember(repository) { repository.observeLikedTrackKeys() }
    val likedTrackKeys by likedKeysFlow.collectAsStateWithLifecycle(initialValue = emptySet())
    val scope = rememberCoroutineScope()
    var pickerTrack by remember { mutableStateOf<LocalAudioTrack?>(null) }

    return remember(playlists, likedTrackKeys, pickerTrack) {
        UserLibraryMenuState(
            playlists = playlists,
            likedTrackKeys = likedTrackKeys,
            onToggleLike = { track ->
                scope.launch { repository.toggleLike(track) }
            },
            pickerTrack = pickerTrack,
            onShowPlaylistPicker = { track -> pickerTrack = track },
            onDismissPlaylistPicker = { pickerTrack = null },
            onAddToPlaylist = { playlistId, track ->
                scope.launch { repository.addTrackToPlaylist(playlistId, track) }
            },
            onCreatePlaylist = { name, track ->
                scope.launch {
                    val playlistId = repository.createPlaylist(name)
                    repository.addTrackToPlaylist(playlistId, track)
                }
            },
        )
    }
}

/** Hosts the add-to-playlist picker for a [UserLibraryMenuState]. */
@Composable
fun UserLibraryMenuDialogHost(state: UserLibraryMenuState) {
    state.pickerTrack?.let { track ->
        AddToPlaylistDialog(
            playlists = state.playlists,
            onDismiss = state.onDismissPlaylistPicker,
            onSelectPlaylist = { playlistId ->
                state.onDismissPlaylistPicker()
                state.onAddToPlaylist(playlistId, track)
            },
            onCreatePlaylist = { name ->
                state.onDismissPlaylistPicker()
                state.onCreatePlaylist(name, track)
            },
        )
    }
}
