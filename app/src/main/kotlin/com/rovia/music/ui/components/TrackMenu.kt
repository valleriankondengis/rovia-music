package com.rovia.music.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.rovia.music.R

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TrackMenu(
    onTrackInfoClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    menuContent: @Composable ColumnScope.() -> Unit = {},
) {
    var expanded by remember { mutableStateOf(false) }

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
            shape = MaterialTheme.shapes.large,
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            DropdownMenuItem(
                text = {
                    Text(
                        text = stringResource(R.string.track_info_open),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                },
                onClick = {
                    expanded = false
                    onTrackInfoClick()
                },
            )

            menuContent()
        }
    }
}