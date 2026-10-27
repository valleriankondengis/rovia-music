package com.rovia.music.feature.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rovia.music.R
import com.rovia.music.playback.controller.PlaybackQueueState
import com.rovia.music.ui.components.TrackList

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun QueueSheet(
    queueState: PlaybackQueueState,
    onDismissRequest: () -> Unit,
    onTrackSelected: (Int) -> Unit,
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
        onDismissRequest = onDismissRequest,
        modifier = Modifier.fillMaxSize(),
        sheetState = sheetState,
        scrimColor = Color.Transparent,
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
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
                    outerPadding = PaddingValues(bottom = 16.dp),
                    currentTrackId = currentTrackId,
                    listState = listState,
                )
            }
        }
    }
}