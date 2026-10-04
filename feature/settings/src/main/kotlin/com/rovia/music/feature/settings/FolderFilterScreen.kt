@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
)

package com.rovia.music.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rovia.music.core.library.FolderFilterRepository
import com.rovia.music.core.library.FolderScannerRepository
import com.rovia.music.core.model.MusicFolder
import com.rovia.music.core.ui.R as CoreUiR
import kotlinx.coroutines.launch

@Composable
fun FolderFilterScreen(
    folderFilterRepository: FolderFilterRepository,
    folderScannerRepository: FolderScannerRepository,
    filteredFolderCount: Int,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showFolderPicker by remember {
        mutableStateOf(false)
    }

    var scannedFolders by remember {
        mutableStateOf<List<MusicFolder>>(
            emptyList(),
        )
    }

    var isLoadingFolders by remember {
        mutableStateOf(false)
    }

    val coroutineScope =
        rememberCoroutineScope()

    val excludedFolders by
        folderFilterRepository.excludedFolders
            .collectAsStateWithLifecycle()

    val filteredFolders =
        excludedFolders
            .sortedWith(
                String.CASE_INSENSITIVE_ORDER,
            )

    LaunchedEffect(showFolderPicker) {
        if (!showFolderPicker) {
            return@LaunchedEffect
        }

        isLoadingFolders = true

        scannedFolders =
            folderScannerRepository
                .getScannedFolders()

        isLoadingFolders = false
    }

    Column(
        modifier = modifier.fillMaxSize(),
    ) {
        TopAppBar(
            modifier =
                Modifier.padding(
                    horizontal = 4.dp,
                ),
            title = {
                Text(
                    text =
                        stringResource(
                            R.string.folder_filter_title,
                        ),
                )
            },
            navigationIcon = {
                FilledIconButton(
                    onClick = onBack,
                    shapes = IconButtonDefaults.shapes(),
                    colors =
                        IconButtonDefaults
                            .filledIconButtonColors(
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
                                CoreUiR.drawable.ic_arrow_back,
                            ),
                        contentDescription =
                            stringResource(
                                R.string.folder_filter_back,
                            ),
                    )
                }
            },
            actions = {
                FilledIconButton(
                    onClick = {
                        showFolderPicker = true
                    },
                    shapes = IconButtonDefaults.shapes(),
                    colors =
                        IconButtonDefaults
                            .filledIconButtonColors(
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
                                CoreUiR.drawable.ic_add,
                            ),
                        contentDescription =
                            stringResource(
                                R.string.folder_filter_add,
                            ),
                    )
                }
            },
        )

        Text(
            text =
                stringResource(
                    id =
                        if (filteredFolderCount == 1) {
                            R.string.folder_filter_count_one
                        } else {
                            R.string.folder_filter_count_other
                        },
                    filteredFolderCount,
                ),
            style =
                MaterialTheme
                    .typography
                    .bodyLarge,
            modifier =
                Modifier.padding(
                    start = 20.dp,
                    top = 8.dp,
                    end = 20.dp,
                ),
        )

        Spacer(
            modifier =
                Modifier.height(8.dp),
        )

        LazyColumn(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .weight(1f),
        ) {
            items(
                items = filteredFolders,
                key = {
                    it
                },
            ) { folderPath ->

                val folderName =
                    folderPath
                        .trimEnd('/')
                        .substringAfterLast('/')

                ListItem(
                    leadingContent = {
                        Row(
                            modifier =
                                Modifier
                                    .size(40.dp)
                                    .background(
                                        color =
                                            MaterialTheme
                                                .colorScheme
                                                .primaryContainer,
                                        shape =
                                            MaterialTheme
                                                .shapes
                                                .large,
                                    ),
                            horizontalArrangement =
                                Arrangement.Center,
                            verticalAlignment =
                                Alignment.CenterVertically,
                        ) {
                            Icon(
                                painter =
                                    painterResource(
                                        R.drawable.ic_folder_filter,
                                    ),
                                contentDescription = null,
                                tint =
                                    MaterialTheme
                                        .colorScheme
                                        .onPrimaryContainer,
                                modifier =
                                    Modifier.size(20.dp),
                            )
                        }
                    },
                    supportingContent = {
                        Text(
                            text = folderPath,
                            style =
                                MaterialTheme
                                    .typography
                                    .bodyMedium,
                        )
                    },
                    shapes = ListItemDefaults.shapes(),
                    colors =
                        ListItemDefaults.colors(
                            containerColor =
                                MaterialTheme
                                    .colorScheme
                                    .surface,
                        ),
                ) {
                    Text(
                        text = folderName,
                        style =
                            MaterialTheme
                                .typography
                                .titleMedium,
                    )
                }

                HorizontalDivider()
            }
        }
    }

    if (showFolderPicker) {
        FolderPickerDialog(
            folders = scannedFolders,
            selectedFolders = excludedFolders,
            isLoading = isLoadingFolders,
            onDismiss = {
                showFolderPicker = false
            },
            onDone = { selectedFolders ->
                coroutineScope.launch {
                    folderFilterRepository
                        .setExcludedFolders(
                            selectedFolders,
                        )

                    showFolderPicker = false
                }
            },
        )
    }
}

@Composable
private fun FolderPickerDialog(
    folders: List<MusicFolder>,
    selectedFolders: Set<String>,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onDone: (Set<String>) -> Unit,
) {
    var draftSelection by remember(
        selectedFolders,
    ) {
        mutableStateOf(
            selectedFolders,
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text =
                    stringResource(
                        R.string.folder_filter_picker_title,
                    ),
            )
        },
        text = {
            when {
                isLoading -> {
                    Text(
                        text =
                            stringResource(
                                R.string.folder_filter_loading,
                            ),
                    )
                }

                folders.isEmpty() -> {
                    Text(
                        text =
                            stringResource(
                                R.string.folder_filter_empty,
                            ),
                    )
                }

                else -> {
                    LazyColumn(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(360.dp),
                    ) {
                        items(
                            items = folders,
                            key = {
                                it.relativePath
                            },
                        ) { folder ->
                            val isSelected =
                                draftSelection.contains(
                                    folder.relativePath,
                                )

                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(
                                            vertical = 4.dp,
                                        ),
                                verticalAlignment =
                                    Alignment.CenterVertically,
                            ) {
                                Checkbox(
                                    checked = isSelected,
                                    onCheckedChange = { checked ->
                                        draftSelection =
                                            if (checked) {
                                                draftSelection +
                                                    folder.relativePath
                                            } else {
                                                draftSelection -
                                                    folder.relativePath
                                            }
                                    },
                                )

                                Text(
                                    text =
                                        folder.relativePath,
                                    style =
                                        MaterialTheme
                                            .typography
                                            .bodyLarge,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onDone(
                        draftSelection,
                    )
                },
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(
                    text =
                        stringResource(
                            R.string.folder_filter_done,
                        ),
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(
                    text =
                        stringResource(
                            R.string.folder_filter_cancel,
                        ),
                )
            }
        },
    )
}