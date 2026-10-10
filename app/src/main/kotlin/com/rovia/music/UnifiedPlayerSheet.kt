
package com.rovia.music

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.rovia.music.core.library.LyricsRepository
import com.rovia.music.core.library.MusicRepository
import com.rovia.music.core.model.PlaybackState
import com.rovia.music.core.playback.PlaybackController
import com.rovia.music.core.ui.component.MiniPlayerContent
import com.rovia.music.feature.player.PlayerRoute
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Composable
fun UnifiedPlayerSheet(
    playbackState: PlaybackState,
    playbackController: PlaybackController,
    lyricsRepository: LyricsRepository,
    musicRepository: MusicRepository,
    navigationBar: @Composable () -> Unit,
    onProgressChanged: (Float) -> Unit = {},
    showBottomChrome: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val track =
        playbackState.currentTrack

    val density =
        LocalDensity.current

    val configuration =
        LocalConfiguration.current

    val scope =
        rememberCoroutineScope()

    val motionScheme =
        MaterialTheme.motionScheme

    /*
     * 0f = collapsed / MiniPlayer
     * 1f = expanded / Full Player
     *
     * rememberSaveable is important here.
     *
     * The Player sheet is not a Navigation 3 destination.
     * Therefore its expanded/collapsed state must survive
     * configuration changes such as portrait <-> landscape.
     */
    val progressState =
        rememberSaveable {
            mutableFloatStateOf(0f)
        }

    /*
     * Separate visual progress for MiniPlayer dismissal.
     *
     * This state intentionally does not survive rotation.
     */
    val miniPlayerDismissProgressState =
        remember {
            mutableFloatStateOf(0f)
        }

    /*
     * Animation jobs are explicitly tracked so a new gesture
     * can always cancel the previous settle animation.
     */
    val sheetSettleJobState =
        remember {
            mutableStateOf<Job?>(null)
        }

    val dismissSettleJobState =
        remember {
            mutableStateOf<Job?>(null)
        }

    /*
     * PlayerRoute registers its normal close action here.
     * The action hides fullscreen lyrics and collapses the sheet.
     */
    val playerCloseActionState =
        remember {
            mutableStateOf<(() -> Unit)?>(null)
        }

    val progress =
        progressState.floatValue

    val miniPlayerDismissProgress =
        miniPlayerDismissProgressState.floatValue

    val collapsedHeight =
        80.dp

    /*
     * NavigationBar contract:
     *
     * NavigationBarHeight = 80dp
     * NavigationBarBottomPadding = 20dp
     * MiniPlayerNavigationSpacing = 8dp
     *
     * Therefore the collapsed MiniPlayer bottom position is:
     *
     * 80dp + 20dp + 8dp = 108dp
     */
    val miniPlayerBottomInset =
        NavigationBarBottomInset +
            MiniPlayerNavigationSpacing

    /*
     * MiniPlayer and NavigationBar both use 14dp horizontal
     * inset when collapsed.
     *
     * The Player surface expands that inset to 0dp as it opens.
     */
    val currentHorizontalPadding =
        lerpDp(
            start = 14.dp,
            stop = 0.dp,
            fraction = progress,
        )

    val screenHeight =
        configuration
            .screenHeightDp
            .dp

    /*
     * Restore the external progress observer after a
     * configuration change.
     *
     * The local saveable state is restored before this effect
     * runs, so the restored Player state is propagated.
     */
    LaunchedEffect(Unit) {
        onProgressChanged(
            progressState.floatValue,
        )
    }

    LaunchedEffect(track) {
        miniPlayerDismissProgressState.floatValue = 0f
    }

    fun setProgress(
        value: Float,
    ) {
        val clamped =
            value.coerceIn(
                0f,
                1f,
            )

        progressState.floatValue = clamped
        onProgressChanged(clamped)
    }

    fun cancelSheetAnimation() {
        sheetSettleJobState.value?.cancel()
        sheetSettleJobState.value = null
    }

    fun cancelDismissAnimation() {
        dismissSettleJobState.value?.cancel()
        dismissSettleJobState.value = null
    }

    fun settleTo(
        target: Float,
        onSettled: (() -> Unit)? = null,
    ) {
        cancelSheetAnimation()

        val targetProgress =
            target.coerceIn(
                0f,
                1f,
            )

        val startProgress =
            progressState.floatValue.coerceIn(
                0f,
                1f,
            )

        sheetSettleJobState.value =
            scope.launch {
                try {
                    val animation =
                        Animatable(
                            startProgress,
                        )

                    animation.animateTo(
                        targetValue =
                            targetProgress,
                        animationSpec =
                            motionScheme
                                .defaultSpatialSpec(),
                    ) {
                        setProgress(value)
                    }

                    setProgress(targetProgress)
                    onSettled?.invoke()
                } finally {
                    sheetSettleJobState.value = null
                }
            }
    }

    fun settleMiniPlayerDismiss(
        target: Float,
        onSettled: (() -> Unit)? = null,
    ) {
        cancelDismissAnimation()

        val targetProgress =
            target.coerceIn(
                0f,
                1f,
            )

        val startProgress =
            miniPlayerDismissProgressState
                .floatValue
                .coerceIn(
                    0f,
                    1f,
                )

        dismissSettleJobState.value =
            scope.launch {
                try {
                    val animation =
                        Animatable(
                            startProgress,
                        )

                    animation.animateTo(
                        targetValue =
                            targetProgress,
                        animationSpec =
                            motionScheme
                                .defaultSpatialSpec(),
                    ) {
                        miniPlayerDismissProgressState
                            .floatValue =
                            value.coerceIn(
                                0f,
                                1f,
                            )
                    }

                    miniPlayerDismissProgressState
                        .floatValue =
                        targetProgress

                    onSettled?.invoke()
                } finally {
                    dismissSettleJobState.value = null
                }
            }
    }

    fun expand() {
        cancelDismissAnimation()
        miniPlayerDismissProgressState.floatValue = 0f
        settleTo(1f)
    }

    fun collapse() {
        cancelDismissAnimation()
        miniPlayerDismissProgressState.floatValue = 0f
        settleTo(0f)
    }

    fun stopMiniPlayer() {
        settleMiniPlayerDismiss(
            target = 1f,
        ) {
            playbackController.stopAndClearQueue()
        }
    }

    PredictiveBackHandler(
        enabled = progress > 0f,
    ) { backProgress ->
        /*
         * Capture the close action before consuming the gesture.
         *
         * The progress can reach zero during the final gesture
         * event, causing PlayerRoute to leave composition before
         * the event stream finishes. Keeping this local reference
         * ensures the completed gesture can still hide fullscreen
         * lyrics and close the sheet.
         */
        val closePlayerAction =
            playerCloseActionState.value

        try {
            backProgress.collect { backEvent ->
                cancelSheetAnimation()

                val nextProgress =
                    (
                        1f -
                            backEvent.progress
                        ).coerceIn(
                            0f,
                            1f,
                        )

                setProgress(nextProgress)
            }

            /*
             * Only a completed predictive Back gesture invokes
             * the close action. A cancelled gesture uses the
             * CancellationException branch below instead.
             */
            if (closePlayerAction != null) {
                closePlayerAction()
            } else {
                collapse()
            }
        } catch (_: CancellationException) {
            settleTo(1f)
        }
    }

    if (track != null) {
        val currentHeight =
            lerpDp(
                start = collapsedHeight,
                stop = screenHeight,
                fraction = progress,
            )

        val currentBottomPadding =
            lerpDp(
                start = miniPlayerBottomInset,
                stop = 0.dp,
                fraction = progress,
            )

        val cornerTop =
            lerpDp(
                start = 32.dp,
                stop = 0.dp,
                fraction = progress,
            )

        val cornerBottom =
            lerpDp(
                start = 20.dp,
                stop = 0.dp,
                fraction = progress,
            )

        /*
         * Preserve the exact MiniPlayer Card container color.
         */
        val miniPlayerContainerColor =
            CardDefaults
                .cardColors()
                .containerColor

        val surfaceColor =
            lerpColor(
                start = miniPlayerContainerColor,
                stop =
                    MaterialTheme
                        .colorScheme
                        .background,
                fraction = progress,
            )

        val playerContentAlpha =
            (
                (
                    progress -
                        0.08f
                    ) / 0.92f
                ).coerceIn(
                    0f,
                    1f,
                )

        val miniPlayerContentAlpha =
            (
                1f -
                    progress
                ).coerceIn(
                    0f,
                    1f,
                )

        /*
         * Player surface visibility:
         *
         * Keep the surface visible when bottom chrome is enabled
         * or when the Full Player is expanded.
         *
         * When bottom chrome is disabled and the Player is
         * collapsed, do not compose the surface at all.
         *
         * This prevents an empty 80dp surface from appearing
         * at the bottom of destinations such as Settings.
         */
        if (
            showBottomChrome ||
                progress > 0f
        ) {
            Box(
                modifier =
                    modifier
                        .fillMaxSize()
                        .padding(
                            bottom =
                                currentBottomPadding,
                        ),
            ) {
                /*
                 * Single physical Player surface.
                 *
                 * Horizontal inset:
                 * 0f progress -> 14dp
                 * 1f progress -> 0dp
                 */
                Surface(
                    modifier =
                        Modifier
                            .align(
                                Alignment.BottomCenter,
                            )
                            .fillMaxWidth()
                            .padding(
                                start =
                                    currentHorizontalPadding,
                                end =
                                    currentHorizontalPadding,
                            )
                            .height(
                                currentHeight,
                            )
                            .graphicsLayer {
                                val dismissProgress =
                                    miniPlayerDismissProgress
                                        .coerceIn(
                                            0f,
                                            1f,
                                        )

                                val dismissScale =
                                    (
                                        1f -
                                            (
                                                0.04f *
                                                    dismissProgress
                                                )
                                        ).coerceIn(
                                            0.96f,
                                            1f,
                                        )

                                scaleX =
                                    dismissScale

                                scaleY =
                                    dismissScale

                                translationY =
                                    with(density) {
                                        160.dp.toPx() *
                                            dismissProgress
                                    }

                                transformOrigin =
                                    TransformOrigin(
                                        pivotFractionX = 0.5f,
                                        pivotFractionY = 1f,
                                    )
                            }
                            .pointerInput(Unit) {
                                /*
                                 * Stable gesture detector.
                                 *
                                 * Do not make progress a
                                 * pointerInput key.
                                 */
                                var gestureDirection =
                                    0

                                var dragProgress =
                                    0f

                                var dragDismissProgress =
                                    0f

                                var dragStartProgress =
                                    0f

                                detectVerticalDragGestures(
                                    onDragStart = {
                                        cancelSheetAnimation()
                                        cancelDismissAnimation()

                                        dragStartProgress =
                                            progressState
                                                .floatValue
                                                .coerceIn(
                                                    0f,
                                                    1f,
                                                )

                                        dragProgress =
                                            dragStartProgress

                                        dragDismissProgress =
                                            miniPlayerDismissProgressState
                                                .floatValue
                                                .coerceIn(
                                                    0f,
                                                    1f,
                                                )

                                        gestureDirection = 0
                                    },
                                    onVerticalDrag = {
                                        change,
                                        dragAmount,
                                    ->
                                        if (
                                            gestureDirection ==
                                                0 &&
                                                dragAmount != 0f
                                        ) {
                                            gestureDirection =
                                                if (
                                                    dragAmount < 0f
                                                ) {
                                                    -1
                                                } else {
                                                    1
                                                }
                                        }

                                        when {
                                            /*
                                             * MiniPlayer ->
                                             * downward dismissal.
                                             */
                                            dragStartProgress <=
                                                0.001f &&
                                                gestureDirection > 0 -> {
                                                if (
                                                    dragAmount > 0f
                                                ) {
                                                    val collapsedHeightPx =
                                                        with(density) {
                                                            collapsedHeight
                                                                .toPx()
                                                        }.coerceAtLeast(
                                                            1f,
                                                        )

                                                    val resistedDrag =
                                                        dragAmount /
                                                            (
                                                                collapsedHeightPx *
                                                                    1.35f
                                                                )

                                                    dragDismissProgress =
                                                        (
                                                            dragDismissProgress +
                                                                resistedDrag
                                                            )
                                                            .coerceIn(
                                                                0f,
                                                                1f,
                                                            )

                                                    miniPlayerDismissProgressState
                                                        .floatValue =
                                                        dragDismissProgress

                                                    change.consume()
                                                }
                                            }

                                            /*
                                             * Upward gesture expands Player.
                                             */
                                            gestureDirection < 0 -> {
                                                val heightPx =
                                                    size.height
                                                        .coerceAtLeast(
                                                            1,
                                                        )
                                                        .toFloat()

                                                dragProgress =
                                                    (
                                                        dragProgress -
                                                            (
                                                                dragAmount /
                                                                    heightPx
                                                                )
                                                        ).coerceIn(
                                                            0f,
                                                            1f,
                                                        )

                                                setProgress(
                                                    dragProgress,
                                                )

                                                change.consume()
                                            }

                                            /*
                                             * Downward gesture collapses Player.
                                             */
                                            gestureDirection > 0 &&
                                                dragStartProgress >
                                                0.001f -> {
                                                if (
                                                    dragAmount > 0f
                                                ) {
                                                    val heightPx =
                                                        size.height
                                                            .coerceAtLeast(
                                                                1,
                                                            )
                                                            .toFloat()

                                                    dragProgress =
                                                        (
                                                            dragProgress -
                                                                (
                                                                    dragAmount /
                                                                        heightPx
                                                                    )
                                                            ).coerceIn(
                                                                0f,
                                                                1f,
                                                            )

                                                    setProgress(
                                                        dragProgress,
                                                    )

                                                    change.consume()
                                                }
                                            }
                                        }
                                    },
                                    onDragEnd = {
                                        when {
                                            /*
                                             * Dismiss MiniPlayer.
                                             */
                                            dragStartProgress <=
                                                0.001f &&
                                                gestureDirection > 0 -> {
                                                if (
                                                    dragDismissProgress >=
                                                        0.45f
                                                ) {
                                                    stopMiniPlayer()
                                                } else {
                                                    settleMiniPlayerDismiss(
                                                        target = 0f,
                                                    )
                                                }
                                            }

                                            /*
                                             * Finish upward expansion.
                                             */
                                            gestureDirection < 0 -> {
                                                if (
                                                    dragProgress >=
                                                        0.5f
                                                ) {
                                                    expand()
                                                } else {
                                                    collapse()
                                                }
                                            }

                                            /*
                                             * Finish downward collapse.
                                             */
                                            gestureDirection > 0 &&
                                                dragStartProgress >
                                                0.001f -> {
                                                if (
                                                    dragProgress >=
                                                        0.5f
                                                ) {
                                                    expand()
                                                } else {
                                                    collapse()
                                                }
                                            }

                                            else -> {
                                                if (
                                                    dragProgress >=
                                                        0.5f
                                                ) {
                                                    expand()
                                                } else {
                                                    collapse()
                                                }
                                            }
                                        }

                                        gestureDirection = 0
                                    },
                                    onDragCancel = {
                                        when {
                                            dragStartProgress <=
                                                0.001f &&
                                                gestureDirection > 0 -> {
                                                settleMiniPlayerDismiss(
                                                    target = 0f,
                                                )
                                            }

                                            dragProgress >=
                                                0.5f -> {
                                                expand()
                                            }

                                            else -> {
                                                collapse()
                                            }
                                        }

                                        gestureDirection = 0
                                    },
                                )
                            },
                    color =
                        surfaceColor,
                    shape =
                        RoundedCornerShape(
                            topStart = cornerTop,
                            topEnd = cornerTop,
                            bottomStart = cornerBottom,
                            bottomEnd = cornerBottom,
                        ),
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp,
                ) {
                    Box(
                        modifier =
                            Modifier.fillMaxSize(),
                    ) {
                        /*
                         * PlayerRoute only exists once the surface
                         * starts expanding.
                         */
                        if (progress > 0f) {
                            PlayerRoute(
                                playbackController =
                                    playbackController,
                                lyricsRepository =
                                    lyricsRepository,
                                musicRepository =
                                    musicRepository,
                                onClose = {
                                    collapse()
                                },
                                onCloseActionAvailable = { closeAction ->
                                    playerCloseActionState.value =
                                        closeAction
                                },
                                modifier =
                                    Modifier
                                        .fillMaxSize()
                                        .graphicsLayer {
                                            alpha =
                                                playerContentAlpha
                                        },
                            )
                        }

                        /*
                         * MiniPlayer stays aligned with the
                         * same surface.
                         */
                        if (
                            showBottomChrome &&
                                miniPlayerContentAlpha > 0f
                        ) {
                            Box(
                                modifier =
                                    Modifier
                                        .align(
                                            Alignment.BottomCenter,
                                        )
                                        .fillMaxWidth()
                                        .clickable(
                                            enabled =
                                                progress <
                                                    0.999f,
                                            onClick = {
                                                expand()
                                            },
                                        )
                                        .graphicsLayer {
                                            alpha =
                                                miniPlayerContentAlpha
                                        },
                            ) {
                                MiniPlayerContent(
                                    playbackState =
                                        playbackState,
                                    onPrevious =
                                        playbackController::skipToPrevious,
                                    onPlayPause = {
                                        if (
                                            playbackState.isPlaying
                                        ) {
                                            playbackController.pause()
                                        } else {
                                            playbackController.resume()
                                        }
                                    },
                                    onNext =
                                        playbackController::skipToNext,
                                    showArtwork = true,
                                    modifier =
                                        Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    /*
     * NavigationBar remains outside the Player surface.
     *
     * It is not composed when the Player is fully expanded,
     * so it cannot intercept Player touch events.
     */
    if (
        showBottomChrome &&
            progress < 0.999f
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .zIndex(2f)
                    .graphicsLayer {
                        alpha =
                            (
                                1f -
                                    progress
                                ).coerceIn(
                                    0f,
                                    1f,
                                )

                        translationY =
                            with(density) {
                                NavigationBarHeight
                                    .toPx() *
                                    0.15f *
                                    progress
                            }
                    },
        ) {
            Box(
                modifier =
                    Modifier
                        .align(
                            Alignment.BottomCenter,
                        )
                        .fillMaxWidth(),
            ) {
                navigationBar()
            }
        }
    }
}

private fun lerpDp(
    start: Dp,
    stop: Dp,
    fraction: Float,
): Dp {
    val clampedFraction =
        fraction.coerceIn(
            0f,
            1f,
        )

    return (
        start.value +
            (
                stop.value -
                    start.value
                ) *
                clampedFraction
        ).dp
}

private fun lerpColor(
    start: Color,
    stop: Color,
    fraction: Float,
): Color {
    val clampedFraction =
        fraction.coerceIn(
            0f,
            1f,
        )

    return Color(
        red =
            start.red +
                (
                    stop.red -
                        start.red
                    ) *
                    clampedFraction,
        green =
            start.green +
                (
                    stop.green -
                        start.green
                    ) *
                    clampedFraction,
        blue =
            start.blue +
                (
                    stop.blue -
                        start.blue
                    ) *
                    clampedFraction,
        alpha =
            start.alpha +
                (
                    stop.alpha -
                        start.alpha
                    ) *
                    clampedFraction,
    )
}
