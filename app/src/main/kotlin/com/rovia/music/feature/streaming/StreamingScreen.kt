package com.rovia.music.feature.streaming

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import com.rovia.music.R
import com.rovia.music.playback.controller.PlaybackController
import com.rovia.music.playback.streaming.StreamUrlProblem
import com.rovia.music.playback.streaming.streamTrack
import com.rovia.music.playback.streaming.streamUrlProblem

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
)
@Composable
fun StreamingScreen(
    streamingController: PlaybackController,
    miniPlayerVisible: Boolean,
    onBack: () -> Unit,
    onOpenPlayer: () -> Unit,
) {
    val context = LocalContext.current
    val queueState by streamingController.queueState.collectAsStateWithLifecycle()
    val currentTrack = queueState.items.getOrNull(queueState.currentIndex)?.track
    val mediaController by streamingController.mediaController
        .collectAsStateWithLifecycle()
    val historyStore = remember(context) {
        StreamingHistoryStore(context.applicationContext)
    }
    val historyEntries by historyStore.entries.collectAsStateWithLifecycle()

    var urlText by remember { mutableStateOf("") }
    var urlProblem by remember { mutableStateOf<StreamUrlProblem?>(null) }
    var submitFailed by remember { mutableStateOf(false) }

    var isPlaying by remember(mediaController) {
        mutableStateOf(mediaController?.isPlaying == true)
    }
    var playWhenReady by remember(mediaController) {
        mutableStateOf(mediaController?.playWhenReady == true)
    }
    var playbackState by remember(mediaController) {
        mutableIntStateOf(mediaController?.playbackState ?: Player.STATE_IDLE)
    }

    DisposableEffect(mediaController) {
        val player = mediaController ?: return@DisposableEffect onDispose { }
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlayWhenReadyChanged(
                playWhenReadyChanged: Boolean,
                reason: Int,
            ) {
                playWhenReady = playWhenReadyChanged
            }

            override fun onPlaybackStateChanged(state: Int) {
                playbackState = state
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    val isBuffering = playbackState == Player.STATE_BUFFERING
    val showPauseIcon = isPlaying ||
        (playWhenReady &&
            playbackState != Player.STATE_ENDED &&
            playbackState != Player.STATE_IDLE)

    fun submit() {
        val problem = streamUrlProblem(urlText)
        urlProblem = problem
        submitFailed = false
        if (problem != null) return
        if (streamingController.playQueue(listOf(streamTrack(urlText)), 0)) {
            historyStore.record(urlText)
            onOpenPlayer()
        } else {
            submitFailed = true
        }
    }

    fun playRecent(entry: StreamingHistoryEntry) {
        urlProblem = null
        submitFailed = false
        if (streamingController.playQueue(listOf(streamTrack(entry.url)), 0)) {
            historyStore.record(entry.url)
            onOpenPlayer()
        } else {
            submitFailed = true
        }
    }

    fun paste() {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE)
            as? ClipboardManager
        val pasted = clipboard?.primaryClip
            ?.takeIf { it.itemCount > 0 }
            ?.getItemAt(0)
            ?.coerceToText(context)
            ?.toString()
        if (!pasted.isNullOrBlank()) {
            urlText = pasted
            urlProblem = null
            submitFailed = false
        }
    }

    val errorText = when {
        submitFailed -> stringResource(R.string.streaming_error_failed)
        urlProblem == StreamUrlProblem.EMPTY ->
            stringResource(R.string.streaming_error_empty)
        urlProblem == StreamUrlProblem.INVALID ->
            stringResource(R.string.streaming_error_invalid)
        urlProblem == StreamUrlProblem.UNSUPPORTED_SCHEME ->
            stringResource(R.string.streaming_error_unsupported)
        else -> null
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.streaming_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_back),
                            contentDescription = stringResource(R.string.player_back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp)
                .padding(top = 8.dp)
                .padding(bottom = if (miniPlayerVisible) 88.dp else 18.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    MaterialTheme.colorScheme.primaryContainer,
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_stream),
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }

                        Column(
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.streaming_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = stringResource(R.string.streaming_description),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    TextField(
                        value = urlText,
                        onValueChange = {
                            urlText = it
                            urlProblem = null
                            submitFailed = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = {
                            Text(stringResource(R.string.streaming_url_label))
                        },
                        placeholder = {
                            Text(stringResource(R.string.streaming_url_hint))
                        },
                        singleLine = true,
                        trailingIcon = if (urlText.isNotEmpty()) {
                            {
                                IconButton(
                                    onClick = {
                                        urlText = ""
                                        urlProblem = null
                                        submitFailed = false
                                    },
                                ) {
                                    Icon(
                                        painter = painterResource(
                                            R.drawable.ic_close,
                                        ),
                                        contentDescription = stringResource(
                                            R.string.streaming_url_clear,
                                        ),
                                    )
                                }
                            }
                        } else {
                            null
                        },
                        isError = errorText != null,
                        supportingText = errorText?.let { message ->
                            { Text(message) }
                        },
                        shape = RoundedCornerShape(24.dp),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor =
                                MaterialTheme.colorScheme.surfaceContainerHighest,
                            unfocusedContainerColor =
                                MaterialTheme.colorScheme.surfaceContainerHighest,
                            errorContainerColor =
                                MaterialTheme.colorScheme.surfaceContainerHighest,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            errorIndicatorColor = Color.Transparent,
                        ),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Uri,
                            imeAction = ImeAction.Go,
                        ),
                        keyboardActions = KeyboardActions(
                            onGo = { submit() },
                        ),
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilledTonalButton(
                            onClick = { paste() },
                            modifier = Modifier.height(52.dp),
                        ) {
                            Icon(
                                painter = painterResource(
                                    R.drawable.ic_content_paste,
                                ),
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.streaming_paste))
                        }

                        Button(
                            onClick = { submit() },
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp),
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_play),
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.streaming_play))
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = currentTrack != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = stringResource(R.string.streaming_now_playing),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp),
                    )

                    Surface(
                        onClick = onOpenPlayer,
                        shape = MaterialTheme.shapes.extraLarge,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(
                                        MaterialTheme.colorScheme
                                            .surfaceContainerHighest
                                            .copy(alpha = 0.65f),
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_stream),
                                    contentDescription = null,
                                    modifier = Modifier.size(26.dp),
                                    tint = MaterialTheme.colorScheme
                                        .onPrimaryContainer,
                                )
                            }

                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Text(
                                    text = currentTrack?.title.orEmpty(),
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )

                                if (isBuffering) {
                                    Row(
                                        verticalAlignment =
                                            Alignment.CenterVertically,
                                        horizontalArrangement =
                                            Arrangement.spacedBy(6.dp),
                                    ) {
                                        ContainedLoadingIndicator(
                                            modifier = Modifier.size(18.dp),
                                        )
                                        Text(
                                            text = stringResource(
                                                R.string.streaming_buffering,
                                            ),
                                            style = MaterialTheme.typography
                                                .bodySmall,
                                        )
                                    }
                                } else {
                                    Text(
                                        text = if (showPauseIcon) {
                                            stringResource(R.string.streaming_live)
                                        } else {
                                            currentTrack?.artist.orEmpty()
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme
                                            .onPrimaryContainer
                                            .copy(alpha = 0.75f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }

                            FilledIconToggleButton(
                                checked = showPauseIcon,
                                onCheckedChange = { wantPlay ->
                                    if (wantPlay) {
                                        mediaController?.play()
                                    } else {
                                        mediaController?.pause()
                                    }
                                },
                                modifier = Modifier.size(48.dp),
                                colors = IconButtonDefaults
                                    .filledIconToggleButtonColors(
                                        containerColor =
                                            MaterialTheme.colorScheme.primary,
                                        contentColor =
                                            MaterialTheme.colorScheme.onPrimary,
                                        checkedContainerColor =
                                            MaterialTheme.colorScheme.primary,
                                        checkedContentColor =
                                            MaterialTheme.colorScheme.onPrimary,
                                    ),
                                shapes = IconButtonDefaults.toggleableShapes(),
                            ) {
                                Icon(
                                    painter = painterResource(
                                        if (showPauseIcon) {
                                            R.drawable.ic_pause
                                        } else {
                                            R.drawable.ic_play
                                        },
                                    ),
                                    contentDescription = stringResource(
                                        if (showPauseIcon) {
                                            R.string.player_pause
                                        } else {
                                            R.string.player_play
                                        },
                                    ),
                                    modifier = Modifier.size(24.dp),
                                )
                            }

                            IconButton(
                                onClick = { streamingController.stopPlayback() },
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_stop),
                                    contentDescription = stringResource(
                                        R.string.streaming_stop,
                                    ),
                                    tint = MaterialTheme.colorScheme
                                        .onPrimaryContainer,
                                )
                            }
                        }
                    }
                }
            }

            if (historyEntries.isNotEmpty()) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(1.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.streaming_history_title),
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 4.dp, bottom = 8.dp),
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        TextButton(
                            onClick = { historyStore.clear() },
                        ) {
                            Text(
                                text = stringResource(
                                    R.string.streaming_history_clear_all,
                                ),
                            )
                        }
                    }

                    val rowColors = ListItemDefaults.segmentedColors(
                        containerColor =
                            MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        leadingContentColor = MaterialTheme.colorScheme.onSurface,
                        trailingContentColor =
                            MaterialTheme.colorScheme.onSurfaceVariant,
                        supportingContentColor =
                            MaterialTheme.colorScheme.onSurfaceVariant,
                        selectedContainerColor =
                            MaterialTheme.colorScheme.primaryContainer,
                        selectedContentColor = MaterialTheme.colorScheme.onSurface,
                        selectedLeadingContentColor =
                            MaterialTheme.colorScheme.onSurface,
                        selectedTrailingContentColor =
                            MaterialTheme.colorScheme.onSurfaceVariant,
                        selectedSupportingContentColor =
                            MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    val currentStreamUrl = currentTrack?.uri?.toString()

                    historyEntries.forEachIndexed { index, entry ->
                        SegmentedListItem(
                            selected = currentStreamUrl != null &&
                                entry.url.equals(
                                    currentStreamUrl,
                                    ignoreCase = true,
                                ),
                            onClick = { playRecent(entry) },
                            modifier = Modifier.fillMaxWidth(),
                            shapes = ListItemDefaults.segmentedShapes(
                                index = index,
                                count = historyEntries.size,
                            ),
                            colors = rowColors,
                            verticalAlignment = Alignment.CenterVertically,
                            contentPadding = PaddingValues(
                                horizontal = 10.dp,
                                vertical = 4.dp,
                            ),
                            leadingContent = {
                                Box(
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(
                                            MaterialTheme.colorScheme
                                                .surfaceContainerHighest,
                                        ),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        painter = painterResource(
                                            R.drawable.ic_stream,
                                        ),
                                        contentDescription = null,
                                        modifier = Modifier.size(26.dp),
                                        tint = MaterialTheme.colorScheme
                                            .onSurfaceVariant,
                                    )
                                }
                            },
                            content = {
                                Text(
                                    text = entry.label,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            supportingContent = {
                                Text(
                                    text = entry.url,
                                    color = MaterialTheme.colorScheme
                                        .onSurfaceVariant,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            trailingContent = {
                                IconButton(
                                    onClick = { historyStore.remove(entry) },
                                ) {
                                    Icon(
                                        painter = painterResource(
                                            R.drawable.ic_close,
                                        ),
                                        contentDescription = stringResource(
                                            R.string.streaming_history_remove,
                                        ),
                                    )
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}
