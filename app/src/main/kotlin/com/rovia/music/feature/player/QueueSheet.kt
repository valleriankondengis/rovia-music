package com.rovia.music.feature.player

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import com.rovia.music.R
import com.rovia.music.playback.controller.PlaybackQueueState
import com.rovia.music.ui.components.TrackList
import kotlin.math.roundToInt

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

    var contentHeightPx by remember { mutableIntStateOf(0) }
    var contentTopInWindowPx by remember { mutableIntStateOf(Int.MAX_VALUE) }
    val windowHeightPx = LocalWindowInfo.current.containerSize.height

    ModalBottomSheet(
        modifier = Modifier.fillMaxHeight(),
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        scrimColor = Color.Transparent,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { size -> contentHeightPx = size.height }
                .onGloballyPositioned { coordinates ->
                    contentTopInWindowPx = coordinates.positionInWindow().y.roundToInt()
                },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding()
                    .padding(top = 8.dp, bottom = 8.dp),
            ) {
                Text(
                    text = stringResource(R.string.queue_sheet_title),
                    modifier = Modifier.padding(horizontal = 18.dp),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = stringResource(
                        R.string.queue_track_count,
                        queueState.items.size,
                    ),
                    modifier = Modifier.padding(horizontal = 18.dp),
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
                        outerPadding = PaddingValues(bottom = 88.dp),
                        currentTrackId = currentTrackId,
                        listState = listState,
                    )
                }
            }

            if (tracks.isNotEmpty()) {
                ButtonGroup(
                    overflowIndicator = { state ->
                        ButtonGroupDefaults.OverflowIndicator(state)
                    },
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .offset {
                            // The sheet keeps its full height at every anchor and
                            // clips the overflow. The group is shifted up by the
                            // height below the window's bottom edge, measured from
                            // the content's window position, so it stays at the same
                            // screen position at every anchor.
                            val visibleHeightPx = windowHeightPx - contentTopInWindowPx
                            val hiddenHeight = (contentHeightPx - visibleHeightPx)
                                .toFloat()
                                .coerceIn(0f, contentHeightPx / 2f)
                            IntOffset(0, -hiddenHeight.roundToInt())
                        }
                        .padding(
                            start = 14.dp,
                            bottom = 26.dp + navigationBarInset,
                        ),
                    expandedRatio = 0.4f,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    customItem(
                        buttonGroupContent = {
                            val interactionSource = remember { MutableInteractionSource() }
                            FilledIconButton(
                                onClick = onShuffleClick,
                                interactionSource = interactionSource,
                                modifier = Modifier
                                    .animateWidth(interactionSource)
                                    .size(44.dp),
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    contentColor = if (queueState.shuffleModeEnabled) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                ),
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
                            FilledIconButton(
                                onClick = onRepeatClick,
                                interactionSource = interactionSource,
                                modifier = Modifier
                                    .animateWidth(interactionSource)
                                    .size(44.dp),
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    contentColor = if (queueState.repeatMode != Player.REPEAT_MODE_OFF) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                ),
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
    }
}
