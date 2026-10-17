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
import com.rovia.music.core.playback.AudioOutputController
import com.rovia.music.core.playback.PlayerPlaybackController
import com.rovia.music.core.ui.component.MiniPlayerContent
import com.rovia.music.feature.player.PlayerRoute
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch

@Composable
fun UnifiedPlayerSheet(
    playbackState: PlaybackState,
    playbackController: PlayerPlaybackController,
    lyricsRepository: LyricsRepository,
    musicRepository: MusicRepository?,
    navigationBar: @Composable () -> Unit,
    onProgressChanged: (Float) -> Unit = {},
    showBottomChrome: Boolean = true,
    modifier: Modifier = Modifier,
    audioOutputController: AudioOutputController? = null,
    initiallyExpanded: Boolean = false,
    onPlayerCloseRequested: (() -> Unit)? = null,
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
     * The saveable progress remains the single source of truth
     * for the visible state of the sheet.
     */
    val progressState =
        rememberSaveable(initiallyExpanded) {
            mutableFloatStateOf(
                if (initiallyExpanded) {
                    1f
                } else {
                    0f
                },
            )
        }

    /*
     * Reuse animation instances instead of allocating a new
     * Animatable every time the sheet starts settling.
     */
    val sheetProgressAnimation =
        remember(initiallyExpanded) {
            Animatable(
                progressState.floatValue,
            )
        }

    val dismissProgressAnimation =
        remember {
            Animatable(0f)
        }

    /*
     * This callback's current consumer only needs to know whether
     * the player is open (progress > 0f).
     *
     * Keep reporting the full progress locally for animation, but
     * notify the parent only when transitioning between the
     * collapsed and open states. This avoids triggering the
     * navigation hierarchy on every animation frame.
     */
    val reportedOpenState =
        remember(initiallyExpanded) {
            booleanArrayOf(
                progressState.floatValue > 0f,
            )
        }

    /*
     * Separate visual progress for MiniPlayer dismissal.
     * This state intentionally does not survive rotation.
     */
    val miniPlayerDismissProgressState =
        remember {
            mutableFloatStateOf(0f)
        }

    /*
     * Track active jobs so gesture input can cancel a settle
     * animation before applying a new progress value.
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
     * PlayerRoute registers its close action here.
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
     * Collapsed MiniPlayer bottom inset:
     * 80dp + 20dp + 8dp = 108dp
     */
    val miniPlayerBottomInset =
        NavigationBarBottomInset +
            MiniPlayerNavigationSpacing

    /*
     * MiniPlayer and NavigationBar both use 14dp horizontal
     * inset when collapsed. The Player surface expands that
     * inset to 0dp as it opens.
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
     * Propagate the restored progress once after composition.
     */
    LaunchedEffect(Unit) {
        reportedOpenState[0] =
            progressState.floatValue > 0f

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

        val isOpen =
            clamped > 0f

        /*
         * Avoid notifying the parent for every frame. The local
         * progress state continues updating at animation frequency.
         */
        if (isOpen != reportedOpenState[0]) {
            reportedOpenState[0] = isOpen

            onProgressChanged(
                clamped,
            )
        }
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

        /*
         * LAZY start ensures the active job is registered before
         * its coroutine can finish and clear the job reference.
         */
        val job =
            scope.launch(
                start = CoroutineStart.LAZY,
            ) {
                val runningJob =
                    currentCoroutineContext()[Job]

                try {
                    /*
                     * Synchronize the reused Animatable with the
                     * latest gesture or predictive-back progress.
                     */
                    sheetProgressAnimation.snapTo(
                        startProgress,
                    )

                    sheetProgressAnimation.animateTo(
                        targetValue = targetProgress,
                        animationSpec =
                            motionScheme
                                .defaultSpatialSpec(),
                    ) {
                        setProgress(
                            value,
                        )
                    }

                    setProgress(
                        targetProgress,
                    )

                    onSettled?.invoke()
                } finally {
                    /*
                     * An older cancelled animation must never clear
                     * the reference to a newer animation job.
                     */
                    if (
                        sheetSettleJobState.value === runningJob
                    ) {
                        sheetSettleJobState.value = null
                    }
                }
            }

        sheetSettleJobState.value = job
        job.start()
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

        val job =
            scope.launch(
                start = CoroutineStart.LAZY,
            ) {
                val runningJob =
                    currentCoroutineContext()[Job]

                try {
                    dismissProgressAnimation.snapTo(
                        startProgress,
                    )

                    dismissProgressAnimation.animateTo(
                        targetValue = targetProgress,
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
                    if (
                        dismissSettleJobState.value === runningJob
                    ) {
                        dismissSettleJobState.value = null
                    }
                }
            }

        dismissSettleJobState.value = job
        job.start()
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
         * It may otherwise leave composition as progress reaches 0.
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

                setProgress(
                    nextProgress,
                )
            }

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

        /*
         * The MiniPlayer pill transitions to square corners
         * as the Player expands.
         */
        val cornerTop =
            lerpDp(
                start = 40.dp,
                stop = 0.dp,
                fraction = progress,
            )

        val cornerBottom =
            lerpDp(
                start = 40.dp,
                stop = 0.dp,
                fraction = progress,
            )

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
         * Do not compose an empty collapsed Player surface on
         * destinations that have disabled bottom chrome.
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

                                scaleX = dismissScale
                                scaleY = dismissScale

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
                                 * Keep gesture recognition stable
                                 * throughout the expansion animation.
                                 */
                                var gestureDirection = 0
                                var dragProgress = 0f
                                var dragDismissProgress = 0f
                                var dragStartProgress = 0f

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
                                            gestureDirection == 0 &&
                                            dragAmount != 0f
                                        ) {
                                            gestureDirection =
                                                if (dragAmount < 0f) {
                                                    -1
                                                } else {
                                                    1
                                                }
                                        }

                                        when {
                                            /*
                                             * MiniPlayer downward
                                             * dismissal with resistance.
                                             */
                                            dragStartProgress <=
                                                0.001f &&
                                                gestureDirection > 0 -> {
                                                if (dragAmount > 0f) {
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
                                                            ).coerceIn(
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
                                             * Upward drag expands
                                             * the Player surface.
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
                                             * Downward drag collapses
                                             * an expanded Player.
                                             */
                                            gestureDirection > 0 &&
                                                dragStartProgress >
                                                0.001f -> {
                                                if (dragAmount > 0f) {
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
                                             * Dismiss the MiniPlayer.
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

                                            gestureDirection < 0 -> {
                                                if (dragProgress >= 0.5f) {
                                                    expand()
                                                } else {
                                                    collapse()
                                                }
                                            }

                                            gestureDirection > 0 &&
                                                dragStartProgress >
                                                0.001f -> {
                                                if (dragProgress >= 0.5f) {
                                                    expand()
                                                } else {
                                                    collapse()
                                                }
                                            }

                                            else -> {
                                                if (dragProgress >= 0.5f) {
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

                                            dragProgress >= 0.5f -> {
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
                    color = surfaceColor,
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
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        if (progress > 0f) {
                            PlayerRoute(
                                playbackController =
                                    playbackController,
                                lyricsRepository =
                                    lyricsRepository,
                                musicRepository =
                                    musicRepository,
                                onClose = {
                                    val closeRequested =
                                        onPlayerCloseRequested

                                    if (closeRequested != null) {
                                        closeRequested()
                                    } else {
                                        collapse()
                                    }
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
                                audioOutputController =
                                    audioOutputController,
                            )
                        }

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
                                                progress < 0.999f,
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
                                        if (playbackState.isPlaying) {
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