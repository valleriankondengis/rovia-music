@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
)

package com.rovia.music.feature.settings

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rovia.music.R
import com.rovia.music.data.mediastore.MediaStoreFolderRepository
import com.rovia.music.data.mediastore.MusicFolder
import com.rovia.music.data.repository.FolderFilterRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private const val FOLDER_DELETE_ACTION_LAYOUT_ID = "folderDeleteAction"
private const val FOLDER_ROW_LAYOUT_ID = "folderRow"

private val FolderDeleteActionWidth = 104.dp
private val FolderListHorizontalPadding = 18.dp

@Composable
fun FolderFilterScreen(
    folderFilterRepository: FolderFilterRepository,
    folderScannerRepository: MediaStoreFolderRepository,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val excludedFolderSet by
        folderFilterRepository.excludedFolders.collectAsStateWithLifecycle()

    val excludedFolders = remember(excludedFolderSet) {
        excludedFolderSet.sortedWith(String.CASE_INSENSITIVE_ORDER)
    }

    val coroutineScope = rememberCoroutineScope()

    var showFolderPicker by remember {
        mutableStateOf(false)
    }

    var scannedFolders by remember {
        mutableStateOf<List<MusicFolder>>(emptyList())
    }

    var isLoadingFolders by remember {
        mutableStateOf(false)
    }

    var folderLoadFailed by remember {
        mutableStateOf(false)
    }

    var reloadRequest by remember {
        mutableIntStateOf(0)
    }

    var isSaving by remember {
        mutableStateOf(false)
    }

    var saveFailed by remember {
        mutableStateOf(false)
    }

    val navigationBarBottomInset = WindowInsets.navigationBars
        .asPaddingValues()
        .calculateBottomPadding()

    val listBottomPadding = maxOf(
        116.dp,
        navigationBarBottomInset + 100.dp,
    )

    LaunchedEffect(showFolderPicker, reloadRequest) {
        if (!showFolderPicker) {
            return@LaunchedEffect
        }

        isLoadingFolders = true
        folderLoadFailed = false

        try {
            scannedFolders = folderScannerRepository.loadFolders()
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            scannedFolders = emptyList()
            folderLoadFailed = true
        } finally {
            isLoadingFolders = false
        }
    }

    Column(
        modifier = modifier.fillMaxSize(),
    ) {
        TopAppBar(
            modifier = Modifier.padding(horizontal = 10.dp),
            title = {
                Text(
                    text = stringResource(R.string.folder_filter_title),
                )
            },
            navigationIcon = {
                FilledIconButton(
                    onClick = onBack,
                    shapes = IconButtonDefaults.shapes(),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor =
                            MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor =
                            MaterialTheme.colorScheme.onSurface,
                    ),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_arrow_back),
                        contentDescription = stringResource(
                            R.string.folder_filter_back,
                        ),
                    )
                }
            },
            actions = {
                FilledIconButton(
                    onClick = {
                        saveFailed = false
                        showFolderPicker = true
                    },
                    shapes = IconButtonDefaults.shapes(),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor =
                            MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor =
                            MaterialTheme.colorScheme.onSurface,
                    ),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_add),
                        contentDescription = stringResource(
                            R.string.folder_filter_add,
                        ),
                    )
                }
            },
        )

        Text(
            text = stringResource(
                if (excludedFolders.size == 1) {
                    R.string.folder_filter_count_one
                } else {
                    R.string.folder_filter_count_other
                },
                excludedFolders.size,
            ),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(
                start = 20.dp,
                top = 8.dp,
                end = 20.dp,
            ),
        )

        Spacer(Modifier.height(8.dp))

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = FolderListHorizontalPadding)
                .clip(MaterialTheme.shapes.extraLarge),
            contentPadding = PaddingValues(
                top = 14.dp,
                bottom = listBottomPadding,
            ),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            if (saveFailed) {
                item(key = "folder_filter_save_error") {
                    Text(
                        text = stringResource(
                            R.string.folder_filter_save_error,
                        ),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(
                            horizontal = 12.dp,
                            vertical = 8.dp,
                        ),
                    )
                }
            }

            if (excludedFolders.isEmpty()) {
                item(key = "folder_filter_empty") {
                    Text(
                        text = stringResource(
                            R.string.folder_filter_empty_exclusions,
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(
                            horizontal = 12.dp,
                            vertical = 20.dp,
                        ),
                    )
                }
            } else {
                itemsIndexed(
                    items = excludedFolders,
                    key = { _, folderPath -> folderPath },
                ) { index, folderPath ->
                    SwipeToDeleteFolderItem(
                        folderPath = folderPath,
                        folderName = folderPath.substringAfterLast('/'),
                        index = index,
                        itemCount = excludedFolders.size,
                        enabled = !isSaving,
                        onDelete = { deletedFolderPath ->
                            isSaving = true

                            try {
                                folderFilterRepository.setExcludedFolders(
                                    excludedFolderSet - deletedFolderPath,
                                )
                                saveFailed = false
                                true
                            } catch (exception: CancellationException) {
                                throw exception
                            } catch (exception: Exception) {
                                saveFailed = true
                                false
                            } finally {
                                isSaving = false
                            }
                        },
                    )
                }
            }
        }
    }

    if (showFolderPicker) {
        FolderPickerDialog(
            folders = scannedFolders,
            selectedFolders = excludedFolderSet,
            isLoading = isLoadingFolders,
            loadFailed = folderLoadFailed,
            isSaving = isSaving,
            saveFailed = saveFailed,
            onRetry = {
                reloadRequest += 1
            },
            onDismiss = {
                if (!isSaving) {
                    showFolderPicker = false
                }
            },
            onDone = { selectedFolders ->
                coroutineScope.launch {
                    isSaving = true
                    saveFailed = false

                    try {
                        folderFilterRepository.setExcludedFolders(
                            selectedFolders,
                        )
                        showFolderPicker = false
                    } catch (exception: CancellationException) {
                        throw exception
                    } catch (exception: Exception) {
                        saveFailed = true
                    } finally {
                        isSaving = false
                    }
                }
            },
        )
    }
}

@Composable
private fun SwipeToDeleteFolderItem(
    folderPath: String,
    folderName: String,
    index: Int,
    itemCount: Int,
    enabled: Boolean,
    onDelete: suspend (String) -> Boolean,
) {
    BoxWithConstraints(
        modifier = Modifier.fillMaxWidth(),
    ) {
        val density = LocalDensity.current

        val itemWidthPx = with(density) {
            maxWidth.roundToPx().toFloat()
        }

        val actionWidthPx = with(density) {
            FolderDeleteActionWidth.roundToPx().toFloat()
        }.coerceAtMost(itemWidthPx)

        val dismissThresholdPx = actionWidthPx + itemWidthPx * 0.35f
        val maximumDragDistancePx = itemWidthPx + actionWidthPx

        val itemShapes = ListItemDefaults.segmentedShapes(
            index = index,
            count = itemCount,
        )

        var dragDistancePx by remember(folderPath) {
            mutableFloatStateOf(0f)
        }

        var isDismissing by remember(folderPath) {
            mutableStateOf(false)
        }

        val coroutineScope = rememberCoroutineScope()

        var animationJob by remember(folderPath) {
            mutableStateOf<Job?>(null)
        }

        fun animateTo(targetDistance: Float) {
            animationJob?.cancel()

            animationJob = coroutineScope.launch {
                animateFolderSwipeDistance(
                    initialValue = dragDistancePx,
                    targetValue = targetDistance,
                    onValueChange = {
                        dragDistancePx = it
                    },
                )
            }
        }

        fun startDismissAnimation() {
            if (isDismissing || !enabled) {
                return
            }

            isDismissing = true
            animationJob?.cancel()

            animationJob = coroutineScope.launch {
                animateFolderSwipeDistance(
                    initialValue = dragDistancePx,
                    targetValue = maximumDragDistancePx,
                    onValueChange = {
                        dragDistancePx = it
                    },
                )

                val deleted = onDelete(folderPath)

                if (!deleted) {
                    isDismissing = false

                    animateFolderSwipeDistance(
                        initialValue = dragDistancePx,
                        targetValue = 0f,
                        onValueChange = {
                            dragDistancePx = it
                        },
                    )
                }
            }
        }

        Layout(
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(
                    folderPath,
                    itemWidthPx,
                    actionWidthPx,
                    isDismissing,
                    enabled,
                ) {
                    if (isDismissing || !enabled) {
                        return@pointerInput
                    }

                    detectHorizontalDragGestures(
                        onDragStart = {
                            animationJob?.cancel()
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()

                            dragDistancePx =
                                (dragDistancePx - dragAmount).coerceIn(
                                    0f,
                                    maximumDragDistancePx,
                                )
                        },
                        onDragEnd = {
                            if (dragDistancePx >= dismissThresholdPx) {
                                startDismissAnimation()
                            } else {
                                val targetDistance =
                                    if (
                                        dragDistancePx >= actionWidthPx * 0.5f
                                    ) {
                                        actionWidthPx
                                    } else {
                                        0f
                                    }

                                animateTo(targetDistance)
                            }
                        },
                        onDragCancel = {
                            animateTo(
                                if (dragDistancePx >= actionWidthPx * 0.5f) {
                                    actionWidthPx
                                } else {
                                    0f
                                },
                            )
                        },
                    )
                },
            content = {
                Box(
                    modifier = Modifier
                        .layoutId(FOLDER_DELETE_ACTION_LAYOUT_ID)
                        .width(FolderDeleteActionWidth)
                        .fillMaxHeight()
                        .graphicsLayer {
                            translationX = -dragDistancePx
                        }
                        .clip(MaterialTheme.shapes.extraLarge)
                        .background(MaterialTheme.colorScheme.error)
                        .clickable(
                            enabled = enabled &&
                                dragDistancePx >= actionWidthPx - 1f &&
                                !isDismissing,
                            role = Role.Button,
                            onClick = {
                                startDismissAnimation()
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.folder_filter_delete),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onError,
                        modifier = Modifier.graphicsLayer {
                            alpha = if (actionWidthPx > 0f) {
                                (dragDistancePx / actionWidthPx)
                                    .coerceIn(0f, 1f)
                            } else {
                                0f
                            }
                        },
                    )
                }

                SegmentedListItem(
                    modifier = Modifier
                        .layoutId(FOLDER_ROW_LAYOUT_ID)
                        .fillMaxWidth()
                        .graphicsLayer {
                            translationX = -dragDistancePx
                        },
                    enabled = enabled && !isDismissing,
                    shapes = itemShapes,
                    colors = ListItemDefaults.segmentedColors(
                        containerColor =
                            MaterialTheme.colorScheme.surfaceContainerLow,
                        contentColor =
                            MaterialTheme.colorScheme.onSurface,
                    ),
                    contentPadding = PaddingValues(
                        horizontal = 16.dp,
                        vertical = 12.dp,
                    ),
                    leadingContent = {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(
                                    color =
                                        MaterialTheme.colorScheme.primaryContainer,
                                    shape = CircleShape,
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_folder),
                                contentDescription = null,
                                tint =
                                    MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    },
                    supportingContent = {
                        Text(
                            text = folderPath,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    },
                    content = {
                        Text(
                            text = folderName,
                            style = MaterialTheme.typography.titleMedium,
                        )
                    },
                )
            },
            measurePolicy = { measurables, constraints ->
                val actionMeasurable = measurables.first {
                    it.layoutId == FOLDER_DELETE_ACTION_LAYOUT_ID
                }

                val rowMeasurable = measurables.first {
                    it.layoutId == FOLDER_ROW_LAYOUT_ID
                }

                val rowConstraints = if (constraints.hasBoundedWidth) {
                    constraints.copy(
                        minWidth = constraints.maxWidth,
                        maxWidth = constraints.maxWidth,
                    )
                } else {
                    constraints
                }

                val rowPlaceable = rowMeasurable.measure(rowConstraints)
                val itemWidth = rowPlaceable.width

                val actionWidth = with(this) {
                    FolderDeleteActionWidth.roundToPx()
                }.coerceAtMost(itemWidth)

                val actionPlaceable = actionMeasurable.measure(
                    Constraints.fixed(
                        width = actionWidth,
                        height = rowPlaceable.height,
                    ),
                )

                layout(
                    width = itemWidth,
                    height = rowPlaceable.height,
                ) {
                    actionPlaceable.place(
                        x = itemWidth,
                        y = 0,
                    )

                    rowPlaceable.place(
                        x = 0,
                        y = 0,
                    )
                }
            },
        )
    }
}

private suspend fun animateFolderSwipeDistance(
    initialValue: Float,
    targetValue: Float,
    onValueChange: (Float) -> Unit,
) {
    animate(
        initialValue = initialValue,
        targetValue = targetValue,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
    ) { value, _ ->
        onValueChange(value)
    }
}

@Composable
private fun FolderPickerDialog(
    folders: List<MusicFolder>,
    selectedFolders: Set<String>,
    isLoading: Boolean,
    loadFailed: Boolean,
    isSaving: Boolean,
    saveFailed: Boolean,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    onDone: (Set<String>) -> Unit,
) {
    var draftSelection by remember(selectedFolders) {
        mutableStateOf(selectedFolders)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(
                    R.string.folder_filter_picker_title,
                ),
            )
        },
        text = {
            Column {
                when {
                    isLoading -> {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                            )

                            Text(
                                text = stringResource(
                                    R.string.folder_filter_loading,
                                ),
                            )
                        }
                    }

                    loadFailed -> {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = stringResource(
                                    R.string.folder_filter_load_error,
                                ),
                                color = MaterialTheme.colorScheme.error,
                            )

                            TextButton(
                                onClick = onRetry,
                                shapes = ButtonDefaults.shapes(),
                            ) {
                                Text(
                                    text = stringResource(
                                        R.string.folder_filter_retry,
                                    ),
                                )
                            }
                        }
                    }

                    folders.isEmpty() -> {
                        Text(
                            text = stringResource(
                                R.string.folder_filter_empty,
                            ),
                            modifier = Modifier.padding(vertical = 16.dp),
                        )
                    }

                    else -> {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 360.dp),
                        ) {
                            items(
                                items = folders,
                                key = { it.relativePath },
                            ) { folder ->
                                val selected = draftSelection.contains(
                                    folder.relativePath,
                                )

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(
                                            enabled = !isSaving,
                                            role = Role.Checkbox,
                                        ) {
                                            draftSelection =
                                                if (selected) {
                                                    draftSelection -
                                                        folder.relativePath
                                                } else {
                                                    draftSelection +
                                                        folder.relativePath
                                                }
                                        }
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Checkbox(
                                        checked = selected,
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
                                        enabled = !isSaving,
                                    )

                                    Text(
                                        text = folder.relativePath,
                                        style = MaterialTheme.typography.bodyLarge,
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(start = 8.dp),
                                    )
                                }
                            }
                        }
                    }
                }

                if (saveFailed) {
                    Text(
                        text = stringResource(
                            R.string.folder_filter_save_error,
                        ),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onDone(draftSelection)
                },
                enabled = !isLoading && !loadFailed && !isSaving,
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(
                    text = stringResource(
                        R.string.folder_filter_done,
                    ),
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isSaving,
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(
                    text = stringResource(
                        R.string.folder_filter_cancel,
                    ),
                )
            }
        },
    )
}