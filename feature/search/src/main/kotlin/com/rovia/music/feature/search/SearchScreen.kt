@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
)

package com.rovia.music.feature.search

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSearchBarState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.rovia.music.core.model.Track
import com.rovia.music.core.ui.R as CoreUiR
import com.rovia.music.core.ui.component.TrackRow

@Composable
fun SearchScreen(
    uiState: SearchUiState,
    textFieldState: androidx.compose.foundation.text.input.TextFieldState,
    onTrackClick: (List<Track>, Int) -> Unit,
    onOpenSettings: () -> Unit,
    hasMiniPlayer: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize(),
    ) {
        SearchHeader(
            textFieldState = textFieldState,
            onOpenSettings = onOpenSettings,
        )

        SearchResults(
            uiState = uiState,
            onTrackClick = onTrackClick,
            hasMiniPlayer = hasMiniPlayer,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun SearchHeader(
    textFieldState: androidx.compose.foundation.text.input.TextFieldState,
    onOpenSettings: () -> Unit,
) {
    val searchBarState = rememberSearchBarState()

    Column(
        modifier = Modifier.fillMaxWidth(),
    ) {
        TopAppBar(
            modifier = Modifier.padding(horizontal = 4.dp),
            title = {
                Text(
                    text = stringResource(
                        R.string.search_title,
                    ),
                )
            },
            actions = {
                FilledIconButton(
                    onClick = onOpenSettings,
                    shapes = IconButtonDefaults.shapes(),
                    colors =
                        IconButtonDefaults.filledIconButtonColors(
                            containerColor =
                                MaterialTheme
                                    .colorScheme
                                    .surfaceContainerHigh,
                            contentColor =
                                MaterialTheme
                                    .colorScheme
                                    .onSurface,
                        ),
                ) {
                    Icon(
                        painter =
                            painterResource(
                                CoreUiR.drawable.ic_settings,
                            ),
                        contentDescription =
                            stringResource(
                                R.string.action_settings,
                            ),
                    )
                }
            },
        )

        SearchBarDefaults.InputField(
            textFieldState = textFieldState,
            searchBarState = searchBarState,
            onSearch = {},
            placeholder = {
                Text(
                    text = stringResource(
                        R.string.search_hint,
                    ),
                )
            },
            leadingIcon = {
                Icon(
                    painter =
                        painterResource(
                            CoreUiR.drawable.ic_search,
                        ),
                    contentDescription =
                        stringResource(
                            R.string.search_content_description,
                        ),
                )
            },
            trailingIcon = {
                if (textFieldState.text.isNotEmpty()) {
                    IconButton(
                        onClick = {
                            textFieldState.edit {
                                replace(
                                    0,
                                    length,
                                    "",
                                )
                            }
                        },
                        colors =
                            IconButtonDefaults.iconButtonColors(
                                contentColor =
                                    MaterialTheme
                                        .colorScheme
                                        .onSurfaceVariant,
                            ),
                    ) {
                        ClearSearchIcon()
                    }
                }
            },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(72.dp)
                    .padding(
                        start = 8.dp,
                        end = 8.dp,
                        bottom = 8.dp,
                    ),
            colors =
                SearchBarDefaults.inputFieldColors(
                    focusedContainerColor =
                        MaterialTheme
                            .colorScheme
                            .surfaceContainerHigh,
                    unfocusedContainerColor =
                        MaterialTheme
                            .colorScheme
                            .surfaceContainerHigh,
                ),
        )
    }
}

@Composable
private fun ClearSearchIcon(
    modifier: Modifier = Modifier,
) {
    val iconColor =
        MaterialTheme
            .colorScheme
            .onSurfaceVariant

    Canvas(
        modifier = modifier.height(24.dp),
    ) {
        val strokeWidth = 2.dp.toPx()
        val inset = 6.dp.toPx()

        drawLine(
            color = iconColor,
            start =
                androidx.compose.ui.geometry.Offset(
                    x = inset,
                    y = inset,
                ),
            end =
                androidx.compose.ui.geometry.Offset(
                    x = size.width - inset,
                    y = size.height - inset,
                ),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )

        drawLine(
            color = iconColor,
            start =
                androidx.compose.ui.geometry.Offset(
                    x = size.width - inset,
                    y = inset,
                ),
            end =
                androidx.compose.ui.geometry.Offset(
                    x = inset,
                    y = size.height - inset,
                ),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
    }
}

@Composable
private fun SearchResults(
    uiState: SearchUiState,
    onTrackClick: (List<Track>, Int) -> Unit,
    hasMiniPlayer: Boolean,
    modifier: Modifier = Modifier,
) {
    val results =
        when (uiState) {
            is SearchUiState.Content -> {
                if (uiState.query.isNotBlank()) {
                    uiState.results
                } else {
                    emptyList()
                }
            }

            else -> emptyList()
        }

    if (results.isEmpty()) {
        return
    }

    val bottomContentPadding =
        if (hasMiniPlayer) {
            200.dp
        } else {
            100.dp
        }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding =
            PaddingValues(
                top = 8.dp,
                bottom = bottomContentPadding,
            ),
    ) {
        itemsIndexed(
            items = results,
            key = { _, track ->
                track.id
            },
        ) { index, track ->
            TrackRow(
                track = track,
                onClick = {
                    onTrackClick(
                        results,
                        index,
                    )
                },
                showAlbum = true,
            )
        }
    }
}