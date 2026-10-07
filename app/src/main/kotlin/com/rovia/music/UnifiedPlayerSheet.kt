package com.rovia.music

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.rovia.music.core.library.LyricsRepository
import com.rovia.music.core.model.PlaybackState
import com.rovia.music.core.playback.PlaybackController
import com.rovia.music.core.ui.component.MiniPlayer
import com.rovia.music.feature.player.PlayerRoute
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun UnifiedPlayerSheet(
    playbackState: PlaybackState,
    playbackController: PlaybackController,
    lyricsRepository: LyricsRepository,
    navigationBar: @Composable () -> Unit,
    onProgressChanged: (Float) -> Unit = {},
    showBottomChrome: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val track =
        playbackState.currentTrack

    val density =
        LocalDensity.current

    val scope =
        rememberCoroutineScope()

    val motionScheme =
        MaterialTheme.motionScheme

    var progress by rememberSaveable {
        mutableFloatStateOf(0f)
    }

    var miniPlayerDismissProgress by remember {
        mutableFloatStateOf(0f)
    }

    var miniPlayerGestureDirection by remember {
        mutableFloatStateOf(0f)
    }

    LaunchedEffect(track) {
        miniPlayerDismissProgress = 0f
        miniPlayerGestureDirection = 0f
    }

    fun setProgress(
        value: Float,
    ) {
        val clamped =
            value.coerceIn(
                0f,
                1f,
            )

        progress = clamped
        onProgressChanged(clamped)
    }

    fun settleTo(
        target: Float,
        onSettled: (() -> Unit)? = null,
    ) {
        val targetProgress =
            target.coerceIn(
                0f,
                1f,
            )

        val startProgress =
            progress.coerceIn(
                0f,
                1f,
            )

        scope.launch {
            val animation =
                Animatable(startProgress)

            animation.animateTo(
                targetValue =
                    targetProgress,
                animationSpec =
                    motionScheme
                        .defaultSpatialSpec(),
            ) {
                progress =
                    value.coerceIn(
                        0f,
                        1f,
                    )
            }

            setProgress(targetProgress)
            onSettled?.invoke()
        }
    }

    fun settleMiniPlayerDismiss(
        target: Float,
        onSettled: (() -> Unit)? = null,
    ) {
        val targetProgress =
            target.coerceIn(
                0f,
                1f,
            )

        val startProgress =
            miniPlayerDismissProgress.coerceIn(
                0f,
                1f,
            )

        scope.launch {
            val animation =
                Animatable(startProgress)

            animation.animateTo(
                targetValue =
                    targetProgress,
                animationSpec =
                    motionScheme
                        .defaultSpatialSpec(),
            ) {
                miniPlayerDismissProgress =
                    value.coerceIn(
                        0f,
                        1f,
                    )
            }

            miniPlayerDismissProgress =
                targetProgress

            onSettled?.invoke()
        }
    }

    fun expand() {
        settleMiniPlayerDismiss(0f)
        settleTo(1f)
    }

    fun collapse() {
        settleMiniPlayerDismiss(0f)
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
        try {
            backProgress.collect { backEvent ->
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

            collapse()
        } catch (_: CancellationException) {
            settleTo(1f)
        }
    }

    Box(
        modifier =
            modifier
                .fillMaxSize(),
    ) {
        /*
         * Full Player
         */
        if (
            track != null &&
                progress > 0f
        ) {
            val cornerProgress =
                (1f - progress)
                    .coerceIn(
                        0f,
                        1f,
                    )

            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            alpha =
                                progress.coerceIn(
                                    0f,
                                    1f,
                                )

                            val scale =
                                (
                                    0.96f +
                                        0.04f *
                                            progress
                                ).coerceIn(
                                    0.96f,
                                    1f,
                                )

                            scaleX = scale
                            scaleY = scale

                            translationY =
                                48.dp.toPx() *
                                    cornerProgress

                            transformOrigin =
                                TransformOrigin(
                                    pivotFractionX = 0.5f,
                                    pivotFractionY = 1f,
                                )
                        }
                        .pointerInput(Unit) {
                            detectVerticalDragGestures(
                                onVerticalDrag = {
                                    change,
                                    dragAmount,
                                ->
                                    if (
                                        dragAmount > 0f
                                    ) {
                                        val height =
                                            size.height
                                                .coerceAtLeast(
                                                    1,
                                                )
                                                .toFloat()

                                        progress =
                                            (
                                                progress -
                                                    (
                                                        dragAmount /
                                                            height
                                                    )
                                            ).coerceIn(
                                                0f,
                                                1f,
                                            )

                                        change.consume()
                                    }
                                },
                                onDragEnd = {
                                    if (
                                        progress >=
                                            0.5f
                                    ) {
                                        expand()
                                    } else {
                                        collapse()
                                    }
                                },
                                onDragCancel = {
                                    if (
                                        progress >=
                                            0.5f
                                    ) {
                                        expand()
                                    } else {
                                        collapse()
                                    }
                                },
                            )
                        },
            ) {
                PlayerRoute(
                    playbackController =
                        playbackController,
                    lyricsRepository =
                        lyricsRepository,
                    onClose = {
                        collapse()
                    },
                )
            }
        }

        /*
         * MiniPlayer
         *
         * The state remains alive inside this composable even
         * when showBottomChrome is false.
         */
        if (
            showBottomChrome &&
                track != null &&
                progress < 0.999f
        ) {
            Box(
                modifier =
                    Modifier
                        .align(
                            Alignment.BottomCenter,
                        )
                        .fillMaxWidth()
                        .padding(
                            start = 14.dp,
                            end = 14.dp,
                            bottom =
                                NavigationBarBottomInset +
                                    MiniPlayerNavigationSpacing,
                        )
                        .graphicsLayer {
                            alpha =
                                (
                                    1f -
                                        progress
                                ).coerceIn(
                                    0f,
                                    1f,
                                )

                            val scale =
                                (
                                    1f -
                                        (
                                            0.04f *
                                                miniPlayerDismissProgress
                                        )
                                ).coerceIn(
                                    0.96f,
                                    1f,
                                )

                            scaleX = scale
                            scaleY = scale

                            translationY =
                                160.dp.toPx() *
                                    miniPlayerDismissProgress

                            transformOrigin =
                                TransformOrigin(
                                    pivotFractionX = 0.5f,
                                    pivotFractionY = 0f,
                                )
                        }
                        .zIndex(1f)
                        .pointerInput(Unit) {
                            detectVerticalDragGestures(
                                onDragStart = {
                                    miniPlayerGestureDirection =
                                        0f
                                },
                                onVerticalDrag = {
                                    change,
                                    dragAmount,
                                ->
                                    if (
                                        miniPlayerGestureDirection ==
                                            0f &&
                                            dragAmount != 0f
                                    ) {
                                        miniPlayerGestureDirection =
                                            if (
                                                dragAmount < 0f
                                            ) {
                                                -1f
                                            } else {
                                                1f
                                            }
                                    }

                                    when {
                                        /*
                                         * Swipe down:
                                         * drag MiniPlayer downward.
                                         */
                                        miniPlayerGestureDirection >
                                            0f &&
                                            dragAmount > 0f -> {
                                            val height =
                                                size.height
                                                    .coerceAtLeast(
                                                        1,
                                                    )
                                                    .toFloat()

                                            val resistedDrag =
                                                dragAmount /
                                                    (
                                                        height *
                                                            1.35f
                                                    )

                                            miniPlayerDismissProgress =
                                                (
                                                    miniPlayerDismissProgress +
                                                        resistedDrag
                                                ).coerceIn(
                                                    0f,
                                                    1f,
                                                )

                                            change.consume()
                                        }

                                        /*
                                         * Swipe up:
                                         * open Player interactively.
                                         */
                                        miniPlayerGestureDirection <
                                            0f &&
                                            dragAmount < 0f -> {
                                            val height =
                                                size.height
                                                    .coerceAtLeast(
                                                        1,
                                                    )
                                                    .toFloat()

                                            progress =
                                                (
                                                    progress -
                                                        (
                                                            dragAmount /
                                                                height
                                                        )
                                                ).coerceIn(
                                                    0f,
                                                    1f,
                                                )

                                            change.consume()
                                        }
                                    }
                                },
                                onDragEnd = {
                                    when {
                                        /*
                                         * Swipe down MiniPlayer.
                                         */
                                        miniPlayerGestureDirection >
                                            0f -> {
                                            if (
                                                miniPlayerDismissProgress >=
                                                    0.45f
                                            ) {
                                                stopMiniPlayer()
                                            } else {
                                                settleMiniPlayerDismiss(
                                                    0f,
                                                )
                                            }
                                        }

                                        /*
                                         * Swipe up MiniPlayer.
                                         */
                                        miniPlayerGestureDirection <
                                            0f &&
                                            progress >=
                                                0.5f -> {
                                            expand()
                                        }

                                        else -> {
                                            collapse()
                                        }
                                    }

                                    miniPlayerGestureDirection =
                                        0f
                                },
                                onDragCancel = {
                                    if (
                                        miniPlayerGestureDirection >
                                            0f
                                    ) {
                                        settleMiniPlayerDismiss(
                                            0f,
                                        )
                                    } else if (
                                        progress >=
                                            0.5f
                                    ) {
                                        expand()
                                    } else {
                                        collapse()
                                    }

                                    miniPlayerGestureDirection =
                                        0f
                                },
                            )
                        },
            ) {
                MiniPlayer(
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
                    onOpenPlayer = {
                        expand()
                    },
                    modifier =
                        Modifier.fillMaxWidth(),
                )
            }
        }

        /*
         * Navigation bar
         *
         * IMPORTANT:
         * Do not keep an invisible NavigationBar layer above
         * the fullscreen Player. When progress reaches 1f,
         * the NavigationBar is removed from composition so it
         * cannot intercept touches intended for the fullscreen
         * lyrics MiniPlayer.
         */
        if (
            showBottomChrome &&
                progress < 0.999f
        ) {
            Box(
                modifier =
                    Modifier
                        .align(
                            Alignment.BottomCenter,
                        )
                        .fillMaxWidth()
                        .graphicsLayer {
                            alpha =
                                (
                                    1f -
                                        progress
                                ).coerceIn(
                                    0f,
                                    1f,
                                )

                            val navigationTranslation =
                                with(density) {
                                    NavigationBarHeight
                                        .toPx() *
                                        0.15f
                                }

                            translationY =
                                navigationTranslation *
                                    progress
                        }
                        .zIndex(2f),
            ) {
                navigationBar()
            }
        }
    }
}