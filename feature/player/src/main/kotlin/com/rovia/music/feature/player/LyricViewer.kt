package com.rovia.music.feature.player

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rovia.music.core.model.LyricLine
import com.rovia.music.core.model.SyncedLyrics
import kotlinx.coroutines.delay
import kotlinx.coroutines.yield
import kotlin.math.abs
import kotlin.math.min

@Composable
fun LyricViewer(
    lyrics: SyncedLyrics,
    positionMs: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()

    /*
     * Pada awal lagu, lyric tetap dimulai dari atas.
     *
     * Setelah lyric aktif mencapai titik tengah viewport,
     * centeredMode akan aktif.
     */
    var centeredMode by remember(lyrics) {
        mutableStateOf(false)
    }

    val activeLineIndex =
        remember(
            lyrics,
            positionMs,
        ) {
            findActiveLineIndex(
                lyrics = lyrics,
                positionMs = positionMs,
            )
        }

    BoxWithConstraints(
        modifier = modifier,
    ) {
        /*
         * Ketika centered mode aktif, ruang kosong di atas dan
         * bawah memungkinkan lyric pertama dan terakhir juga
         * dapat ditempatkan di tengah viewport.
         */
        val centeredPadding =
            maxHeight / 2

        /*
         * ======================================================
         * 1. Menentukan kapan lyric mulai di-center.
         * ======================================================
         */
        LaunchedEffect(
            activeLineIndex,
            centeredMode,
        ) {
            if (
                centeredMode ||
                activeLineIndex < 0 ||
                activeLineIndex >= lyrics.lines.size
            ) {
                return@LaunchedEffect
            }

            val layoutInfo =
                listState.layoutInfo

            val viewportStart =
                layoutInfo.viewportStartOffset

            val viewportEnd =
                layoutInfo.viewportEndOffset

            val viewportHeight =
                (
                    viewportEnd -
                        viewportStart
                ).coerceAtLeast(1)

            val viewportCenter =
                viewportStart +
                    viewportHeight / 2

            val activeItem =
                layoutInfo.visibleItemsInfo
                    .firstOrNull {
                        it.index ==
                            activeLineIndex
                    }

            if (activeItem != null) {
                val activeItemCenter =
                    activeItem.offset +
                        activeItem.size / 2

                /*
                 * Selama lyric aktif masih berada di atas
                 * titik tengah, jangan melakukan scroll.
                 */
                if (
                    activeItemCenter >=
                        viewportCenter
                ) {
                    centeredMode = true
                }
            } else if (
                activeLineIndex > 0
            ) {
                /*
                 * Jika user melakukan seek langsung ke
                 * bagian tengah/akhir lagu, langsung gunakan
                 * centered mode.
                 */
                centeredMode = true
            }
        }

        /*
         * ======================================================
         * 2. Menjaga lyric aktif tepat di tengah.
         * ======================================================
         */
        LaunchedEffect(
            activeLineIndex,
            centeredMode,
        ) {
            if (
                !centeredMode ||
                activeLineIndex < 0 ||
                activeLineIndex >= lyrics.lines.size
            ) {
                return@LaunchedEffect
            }

            /*
             * Tunggu LazyColumn menerapkan content padding
             * setelah centeredMode berubah.
             */
            yield()

            /*
             * Pastikan lyric aktif terlihat terlebih dahulu.
             */
            var layoutInfo =
                listState.layoutInfo

            var activeItem =
                layoutInfo.visibleItemsInfo
                    .firstOrNull {
                        it.index ==
                            activeLineIndex
                    }

            if (activeItem == null) {
                listState.scrollToItem(
                    index = activeLineIndex,
                )

                yield()

                layoutInfo =
                    listState.layoutInfo

                activeItem =
                    layoutInfo.visibleItemsInfo
                        .firstOrNull {
                            it.index ==
                                activeLineIndex
                        }
            }

            if (activeItem == null) {
                return@LaunchedEffect
            }

            /*
             * Koreksi posisi dalam beberapa langkah kecil.
             */
            repeat(18) {
                layoutInfo =
                    listState.layoutInfo

                activeItem =
                    layoutInfo.visibleItemsInfo
                        .firstOrNull {
                            it.index ==
                                activeLineIndex
                        }

                if (activeItem == null) {
                    return@repeat
                }

                val viewportStart =
                    layoutInfo.viewportStartOffset

                val viewportEnd =
                    layoutInfo.viewportEndOffset

                val viewportHeight =
                    (
                        viewportEnd -
                            viewportStart
                    ).coerceAtLeast(1)

                val viewportCenter =
                    viewportStart +
                        viewportHeight / 2f

                val activeItemCenter =
                    activeItem.offset +
                        activeItem.size / 2f

                val distanceFromCenter =
                    activeItemCenter -
                        viewportCenter

                /*
                 * Sudah cukup dekat dengan pusat.
                 */
                if (
                    abs(
                        distanceFromCenter,
                    ) <= 1f
                ) {
                    return@repeat
                }

                /*
                 * Bergerak sebagian dari jarak yang tersisa.
                 */
                val scrollStep =
                    distanceFromCenter * 0.45f

                listState.scroll {
                    scrollBy(
                        scrollStep,
                    )
                }

                delay(16L)
            }

            /*
             * Koreksi terakhir dengan posisi aktual.
             */
            layoutInfo =
                listState.layoutInfo

            activeItem =
                layoutInfo.visibleItemsInfo
                    .firstOrNull {
                        it.index ==
                            activeLineIndex
                    }

            if (activeItem != null) {
                val viewportStart =
                    layoutInfo.viewportStartOffset

                val viewportEnd =
                    layoutInfo.viewportEndOffset

                val viewportHeight =
                    (
                        viewportEnd -
                            viewportStart
                    ).coerceAtLeast(1)

                val viewportCenter =
                    viewportStart +
                        viewportHeight / 2f

                val activeItemCenter =
                    activeItem.offset +
                        activeItem.size / 2f

                val remainingDistance =
                    activeItemCenter -
                        viewportCenter

                if (
                    abs(
                        remainingDistance,
                    ) > 1f
                ) {
                    listState.scroll {
                        scrollBy(
                            remainingDistance,
                        )
                    }
                }
            }
        }

        LazyColumn(
            state = listState,
            modifier =
                Modifier.fillMaxSize(),
            horizontalAlignment =
                Alignment.Start,
            verticalArrangement =
                Arrangement.spacedBy(14.dp),
            contentPadding =
                if (centeredMode) {
                    PaddingValues(
                        vertical =
                            centeredPadding,
                    )
                } else {
                    PaddingValues(0.dp)
                },
        ) {
            itemsIndexed(
                items = lyrics.lines,
                key = { index, line ->
                    "${index}-${line.startTimeMs}"
                },
            ) { index, line ->

                val isActive =
                    index == activeLineIndex

                val activeWordIndex =
                    if (isActive) {
                        findActiveWordIndex(
                            line = line,
                            positionMs = positionMs,
                        )
                    } else {
                        -1
                    }

                LyricLineContent(
                    line = line,
                    activeWordIndex =
                        activeWordIndex,
                    isActive = isActive,
                    onClick = {
                        onSeek(
                            line.startTimeMs,
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun LyricLineContent(
    line: LyricLine,
    activeWordIndex: Int,
    isActive: Boolean,
    onClick: () -> Unit,
) {
    val inactiveColor =
        MaterialTheme.colorScheme
            .onSurfaceVariant
            .copy(
                alpha = 0.45f,
            )

    val activeColor =
        MaterialTheme.colorScheme.onSurface

    val lineColor by animateColorAsState(
        targetValue =
            if (isActive) {
                activeColor
            } else {
                inactiveColor
            },
        animationSpec =
            tween(
                durationMillis = 350,
                easing = FastOutSlowInEasing,
            ),
        label = "lyric-line-color",
    )

    val text =
        buildAnnotatedString {
            line.words.forEachIndexed {
                index,
                word,
                ->
                val wordColor =
                    when {
                        !isActive ->
                            inactiveColor

                        activeWordIndex < 0 ->
                            inactiveColor

                        index == activeWordIndex ->
                            MaterialTheme
                                .colorScheme
                                .primary

                        index < activeWordIndex ->
                            activeColor

                        else ->
                            inactiveColor
                    }

                val wordFontWeight =
                    when {
                        index == activeWordIndex &&
                            isActive ->
                            FontWeight.Bold

                        isActive ->
                            FontWeight.Medium

                        else ->
                            FontWeight.Normal
                    }

                pushStyle(
                    SpanStyle(
                        color = wordColor,
                        fontWeight =
                            wordFontWeight,
                    ),
                )

                append(word.text)

                if (
                    index < line.words.lastIndex
                ) {
                    append(" ")
                }

                pop()
            }
        }

    val density = LocalDensity.current

    /*
     * Ukur lebar lyric yang benar-benar dirender.
     *
     * Ini digunakan agar lyric yang sangat panjang tidak
     * diperbesar melebihi lebar viewport.
     */
    var textWidthPx by remember(text) {
        mutableIntStateOf(0)
    }

    BoxWithConstraints(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(
                    onClick = onClick,
                ),
        contentAlignment =
            Alignment.CenterStart,
    ) {
        val availableWidthPx =
            with(density) {
                maxWidth.toPx()
            }

        val safeWidthPx =
            (
                availableWidthPx -
                    with(density) {
                        12.dp.toPx()
                    }
            ).coerceAtLeast(1f)

        val adaptiveScale =
            if (
                isActive &&
                    textWidthPx > 0
            ) {
                min(
                    1.10f,
                    safeWidthPx /
                        textWidthPx
                            .toFloat(),
                ).coerceAtLeast(1f)
            } else if (isActive) {
                1.10f
            } else {
                1.0f
            }

        val lineScale by animateFloatAsState(
            targetValue = adaptiveScale,
            animationSpec =
                tween(
                    durationMillis = 400,
                    easing = FastOutSlowInEasing,
                ),
            label = "lyric-line-scale",
        )

        Text(
            text = text,
            modifier =
                Modifier
                    .graphicsLayer {
                        scaleX = lineScale
                        scaleY = lineScale

                        /*
                         * Tetap rata kiri ketika membesar.
                         */
                        transformOrigin =
                            TransformOrigin(
                                pivotFractionX = 0f,
                                pivotFractionY = 0.5f,
                            )
                    },
            color = lineColor,
            style =
                if (isActive) {
                    MaterialTheme.typography.titleLarge
                } else {
                    MaterialTheme.typography.bodyLarge
                },
            maxLines = 3,
            onTextLayout = { layoutResult ->
                textWidthPx =
                    layoutResult.size.width
            },
        )
    }
}

private fun findActiveLineIndex(
    lyrics: SyncedLyrics,
    positionMs: Long,
): Int {
    if (lyrics.lines.isEmpty()) {
        return -1
    }

    var activeIndex = -1

    lyrics.lines.forEachIndexed {
        index,
        line,
        ->
        if (
            line.startTimeMs <= positionMs
        ) {
            activeIndex = index
        } else {
            return@forEachIndexed
        }
    }

    return activeIndex
}

private fun findActiveWordIndex(
    line: LyricLine,
    positionMs: Long,
): Int {
    if (line.words.isEmpty()) {
        return -1
    }

    var activeIndex = -1

    line.words.forEachIndexed {
        index,
        word,
        ->
        if (
            word.startTimeMs <= positionMs
        ) {
            activeIndex = index
        } else {
            return@forEachIndexed
        }
    }

    return activeIndex
}