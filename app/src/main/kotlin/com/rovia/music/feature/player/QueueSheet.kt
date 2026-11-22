package com.rovia.music.feature.player

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import com.rovia.music.R
import com.rovia.music.playback.controller.PlaybackQueueState
import com.rovia.music.ui.components.TrackList
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun QueueSheet(
    queueState: PlaybackQueueState,
    onDismissRequest: () -> Unit,
    onTrackSelected: (Int) -> Unit,
    onRepeatClick: () -> Unit,
    onShuffleClick: () -> Unit,
    navigationBarInset: Dp = 0.dp,
) {
    val initialIndex = queueState.currentIndex.coerceAtLeast(0)
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = initialIndex,
    )
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(
            SheetValue.Hidden,
            SheetValue.PartiallyExpanded,
            SheetValue.Expanded,
        ),
    )

    val tracks = queueState.items.map { it.track }
    val currentTrackId = queueState.items
        .getOrNull(queueState.currentIndex)
        ?.track
        ?.id
        ?.toString()

    LaunchedEffect(queueState.currentIndex, queueState.items.size) {
        if (queueState.currentIndex in queueState.items.indices) {
            listState.animateScrollToItem(queueState.currentIndex)
        }
    }

    ModalBottomSheet(
        modifier = Modifier.fillMaxHeight(),
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        scrimColor = Color.Transparent,
    ) {
        val sheetBackScope = rememberCoroutineScope()
        PredictiveBackHandler(enabled = sheetState.currentValue == SheetValue.Expanded) { progress ->
            progress.collect { }
            sheetBackScope.launch { sheetState.hide() }.invokeOnCompletion {
                if (!sheetState.isVisible) onDismissRequest()
            }
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .padding(top = 8.dp, bottom = 8.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringResource(R.string.queue_sheet_title),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                )

                if (tracks.isNotEmpty()) {
                    ButtonGroup(
                        overflowIndicator = { state ->
                            ButtonGroupDefaults.OverflowIndicator(state)
                        },
                        expandedRatio = 0.4f,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        customItem(
                            buttonGroupContent = {
                                val interactionSource = remember { MutableInteractionSource() }
                                ToggleButton(
                                    checked = queueState.shuffleModeEnabled,
                                    onCheckedChange = { onShuffleClick() },
                                    shapes = ButtonGroupDefaults.connectedLeadingButtonShapes(),
                                    colors = ToggleButtonDefaults.colors(
                                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        checkedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                        checkedContentColor = MaterialTheme.colorScheme.primary,
                                    ),
                                    contentPadding = PaddingValues(0.dp),
                                    interactionSource = interactionSource,
                                    modifier = Modifier
                                        .animateWidth(interactionSource)
                                        .size(44.dp),
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_shuffle),
                                        contentDescription = stringResource(R.string.player_shuffle),
                                        modifier = Modifier.size(22.dp),
                                    )
                                }
                            },
                            menuContent = { state ->
                                DropdownMenuItem(
                                    text = { Text(text = stringResource(R.string.player_shuffle)) },
                                    onClick = {
                                        onShuffleClick()
                                        state.dismiss()
                                    },
                                )
                            },
                        )
                        customItem(
                            buttonGroupContent = {
                                val interactionSource = remember { MutableInteractionSource() }
                                ToggleButton(
                                    checked = queueState.repeatMode != Player.REPEAT_MODE_OFF,
                                    onCheckedChange = { onRepeatClick() },
                                    shapes = ButtonGroupDefaults.connectedTrailingButtonShapes(),
                                    colors = ToggleButtonDefaults.colors(
                                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        checkedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                        checkedContentColor = MaterialTheme.colorScheme.primary,
                                    ),
                                    contentPadding = PaddingValues(0.dp),
                                    interactionSource = interactionSource,
                                    modifier = Modifier
                                        .animateWidth(interactionSource)
                                        .size(44.dp),
                                ) {
                                    Icon(
                                        painter = painterResource(
                                            if (queueState.repeatMode == Player.REPEAT_MODE_ONE) {
                                                R.drawable.ic_repeat_one
                                            } else {
                                                R.drawable.ic_repeat
                                            }
                                        ),
                                        contentDescription = stringResource(
                                            if (queueState.repeatMode == Player.REPEAT_MODE_ONE) {
                                                R.string.player_repeat_one
                                            } else {
                                                R.string.player_repeat
                                            }
                                        ),
                                        modifier = Modifier.size(22.dp),
                                    )
                                }
                            },
                            menuContent = { state ->
                                DropdownMenuItem(
                                    text = { Text(text = stringResource(R.string.player_repeat)) },
                                    onClick = {
                                        onRepeatClick()
                                        state.dismiss()
                                    },
                                )
                            },
                        )
                    }
                }
            }

            Text(
                text = stringResource(
                    R.string.queue_track_count,
                    queueState.items.size,
                ),
                modifier = Modifier.padding(start = 18.dp, top = 6.dp, end = 18.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (tracks.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.queue_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                TrackList(
                    tracks = tracks,
                    onTrackClick = { _, index -> onTrackSelected(index) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    currentTrackId = currentTrackId,
                    listState = listState,
                )
            }
        }
    }
}
