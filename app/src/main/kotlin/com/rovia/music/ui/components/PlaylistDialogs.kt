package com.rovia.music.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rovia.music.R
import com.rovia.music.data.repository.PlaylistSummary

/** Name prompt used both for creating a playlist and for renaming one. */
@Composable
fun PlaylistNameDialog(
    @StringRes titleRes: Int,
    @StringRes confirmRes: Int,
    initialName: String = "",
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = stringResource(titleRes))
        },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(text = stringResource(R.string.playlist_name_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name.trim()) },
                enabled = name.isNotBlank(),
            ) {
                Text(text = stringResource(confirmRes))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.common_cancel))
            }
        },
    )
}

@Composable
fun CreatePlaylistDialog(
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit,
) {
    PlaylistNameDialog(
        titleRes = R.string.playlist_create,
        confirmRes = R.string.playlist_create_action,
        onDismiss = onDismiss,
        onConfirm = onCreate,
    )
}

/**
 * Playlist picker shown when adding a track: lists the existing playlists and
 * offers inline creation.
 */
@Composable
fun AddToPlaylistDialog(
    playlists: List<PlaylistSummary>,
    onDismiss: () -> Unit,
    onSelectPlaylist: (Long) -> Unit,
    onCreatePlaylist: (String) -> Unit,
) {
    var creating by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(
                    if (creating) R.string.playlist_create else R.string.playlist_add_to,
                ),
            )
        },
        text = {
            if (creating) {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text(text = stringResource(R.string.playlist_name_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 320.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    items(
                        items = playlists,
                        key = { playlist -> playlist.id },
                    ) { playlist ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onSelectPlaylist(playlist.id) }
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            PlaylistArtwork(
                                name = playlist.name,
                                modifier = Modifier.size(44.dp),
                                size = 44.dp,
                                shape = RoundedCornerShape(10.dp),
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = playlist.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = pluralStringResource(
                                        R.plurals.library_track_count,
                                        playlist.trackCount,
                                        playlist.trackCount,
                                    ),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (creating) {
                TextButton(
                    onClick = { onCreatePlaylist(newName.trim()) },
                    enabled = newName.isNotBlank(),
                ) {
                    Text(text = stringResource(R.string.playlist_create_action))
                }
            } else {
                TextButton(onClick = { creating = true }) {
                    Icon(
                        painter = painterResource(R.drawable.ic_add),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = stringResource(R.string.playlist_create),
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    if (creating) {
                        creating = false
                    } else {
                        onDismiss()
                    }
                },
            ) {
                Text(text = stringResource(R.string.common_cancel))
            }
        },
    )
}
