
@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
)

package com.rovia.music.feature.library

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.rovia.music.core.ui.R as CoreUiR

@Composable
fun LibrarySortBottomSheet(
    selectedOption: LibrarySortOption,
    sortOrder: LibrarySortOrder,
    onDismissRequest: () -> Unit,
    onSortOptionChange: (LibrarySortOption) -> Unit,
    onToggleSortOrder: () -> Unit,
    browseMode: LibraryBrowseMode = LibraryBrowseMode.ALL_SONGS,
) {
    val visibleOptions =
        when (browseMode) {
            LibraryBrowseMode.ALL_SONGS,
            LibraryBrowseMode.FOLDER,
            ->
                listOf(
                    LibrarySortOption.DEFAULT,
                    LibrarySortOption.TITLE,
                    LibrarySortOption.ARTIST,
                    LibrarySortOption.ALBUM,
                    LibrarySortOption.GENRE,
                    LibrarySortOption.DATE_ADDED,
                    LibrarySortOption.DATE_MODIFIED,
                )

            LibraryBrowseMode.ARTIST ->
                listOf(
                    LibrarySortOption.DEFAULT,
                    LibrarySortOption.ARTIST,
                    LibrarySortOption.DATE_ADDED,
                    LibrarySortOption.DATE_MODIFIED,
                )

            LibraryBrowseMode.ALBUM ->
                listOf(
                    LibrarySortOption.DEFAULT,
                    LibrarySortOption.ALBUM,
                    LibrarySortOption.DATE_ADDED,
                    LibrarySortOption.DATE_MODIFIED,
                )

            LibraryBrowseMode.GENRE ->
                listOf(
                    LibrarySortOption.DEFAULT,
                    LibrarySortOption.GENRE,
                    LibrarySortOption.DATE_ADDED,
                    LibrarySortOption.DATE_MODIFIED,
                )
        }

    val sheetState =
        rememberBottomSheetState(
            initialValue = SheetValue.PartiallyExpanded,
            enabledValues =
                setOf(
                    SheetValue.Hidden,
                    SheetValue.PartiallyExpanded,
                    SheetValue.Expanded,
                ),
        )

    ModalBottomSheet(
        modifier = Modifier.fillMaxHeight(),
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        scrimColor = Color.Transparent,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
        ) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(
                            start = 24.dp,
                            end = 16.dp,
                            bottom = 8.dp,
                        ),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.library_sort_title),
                    style = MaterialTheme.typography.headlineSmall,
                )

                SortOrderButton(
                    sortOrder = sortOrder,
                    enabled =
                        selectedOption in visibleOptions &&
                            selectedOption != LibrarySortOption.DEFAULT,
                    onClick = onToggleSortOrder,
                )
            }

            visibleOptions.forEach { option ->
                val label =
                    when (option) {
                        LibrarySortOption.DEFAULT ->
                            stringResource(R.string.library_sort_default)

                        LibrarySortOption.TITLE ->
                            stringResource(R.string.library_sort_title_name)

                        LibrarySortOption.ARTIST ->
                            stringResource(R.string.library_sort_artist)

                        LibrarySortOption.ALBUM ->
                            stringResource(R.string.library_sort_album)

                        LibrarySortOption.GENRE ->
                            stringResource(R.string.library_genre)

                        LibrarySortOption.DATE_ADDED ->
                            stringResource(R.string.library_sort_date_added)

                        LibrarySortOption.DATE_MODIFIED ->
                            stringResource(R.string.library_sort_date_modified)
                    }

                LibrarySortOptionItem(
                    selected = selectedOption == option,
                    label = label,
                    onClick = {
                        onSortOptionChange(option)
                    },
                )
            }
        }
    }
}

@Composable
private fun SortOrderButton(
    sortOrder: LibrarySortOrder,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val targetRotation =
        when (sortOrder) {
            LibrarySortOrder.ASCENDING -> 0f
            LibrarySortOrder.DESCENDING -> 180f
        }

    val arrowRotation by
        animateFloatAsState(
            targetValue = targetRotation,
            animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
            label = "sortOrderArrowRotation",
        )

    FilledIconButton(
        onClick = onClick,
        enabled = enabled,
        shapes = IconButtonDefaults.shapes(),
        colors =
            IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurface,
                disabledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
    ) {
        Icon(
            painter = painterResource(CoreUiR.drawable.ic_sort_arrow),
            contentDescription =
                stringResource(
                    if (sortOrder == LibrarySortOrder.ASCENDING) {
                        R.string.library_sort_order_ascending
                    } else {
                        R.string.library_sort_order_descending
                    },
                ),
            modifier =
                Modifier.graphicsLayer {
                    rotationZ = arrowRotation
                },
        )
    }
}

@Composable
private fun LibrarySortOptionItem(
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
) {
    ListItem(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shapes = ListItemDefaults.shapes(),
        colors =
            ListItemDefaults.colors(
                containerColor = Color.Transparent,
            ),
        leadingContent = {
            RadioButton(
                selected = selected,
                onClick = null,
            )
        },
    ) {
        Text(text = label)
    }
}
