package com.rovia.music.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.rememberSliderState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rovia.music.R
import com.rovia.music.ui.theme.ArtworkContentTheme
import java.util.Locale
import kotlin.math.roundToInt

const val LYRIC_OFFSET_MIN_MS = -5_000
const val LYRIC_OFFSET_MAX_MS = 5_000
const val LYRIC_OFFSET_STEP_MS = 100

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricOffsetBottomSheet(
    offsetMs: Int,
    onOffsetChange: (Int) -> Unit,
    onDismissRequest: () -> Unit,
    seedColor: Color? = null,
) {
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(
            SheetValue.Hidden,
            SheetValue.PartiallyExpanded,
            SheetValue.Expanded,
        ),
    )

    val sliderState = rememberSliderState(
        value = offsetMs.toFloat(),
        steps = ((LYRIC_OFFSET_MAX_MS - LYRIC_OFFSET_MIN_MS) / LYRIC_OFFSET_STEP_MS) - 1,
        trackRange = LYRIC_OFFSET_MIN_MS.toFloat()..LYRIC_OFFSET_MAX_MS.toFloat(),
    )

    LaunchedEffect(offsetMs) {
        val target = offsetMs.toFloat()

        if (sliderState.value != target) {
            sliderState.value = target
        }
    }

    val decreaseDescription = stringResource(R.string.lyric_offset_decrease)
    val increaseDescription = stringResource(R.string.lyric_offset_increase)

    ArtworkContentTheme(seedColor = seedColor) {
        ModalBottomSheet(
            onDismissRequest = onDismissRequest,
            sheetState = sheetState,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp)
                    .padding(bottom = 24.dp),
            ) {
                Text(
                    text = stringResource(R.string.lyric_menu_offset),
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = stringResource(R.string.lyric_offset_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = formatLyricOffset(offsetMs),
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    IconButton(
                        onClick = {
                            onOffsetChange(
                                (offsetMs - LYRIC_OFFSET_STEP_MS)
                                    .coerceAtLeast(LYRIC_OFFSET_MIN_MS),
                            )
                        },
                        enabled = offsetMs > LYRIC_OFFSET_MIN_MS,
                        modifier = Modifier.semantics {
                            contentDescription = decreaseDescription
                        },
                    ) {
                        Text(
                            text = "−",
                            style = MaterialTheme.typography.titleLarge,
                        )
                    }

                    Slider(
                        state = sliderState,
                        onValueChange = { value ->
                            onOffsetChange(
                                (value / LYRIC_OFFSET_STEP_MS).roundToInt() *
                                    LYRIC_OFFSET_STEP_MS,
                            )
                        },
                        modifier = Modifier.weight(1f),
                    )

                    IconButton(
                        onClick = {
                            onOffsetChange(
                                (offsetMs + LYRIC_OFFSET_STEP_MS)
                                    .coerceAtMost(LYRIC_OFFSET_MAX_MS),
                            )
                        },
                        enabled = offsetMs < LYRIC_OFFSET_MAX_MS,
                        modifier = Modifier.semantics {
                            contentDescription = increaseDescription
                        },
                    ) {
                        Text(
                            text = "+",
                            style = MaterialTheme.typography.titleLarge,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                TextButton(
                    onClick = { onOffsetChange(0) },
                    enabled = offsetMs != 0,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) {
                    Text(text = stringResource(R.string.lyric_offset_reset))
                }
            }
        }
    }
}

private fun formatLyricOffset(offsetMs: Int): String =
    String.format(
        Locale.getDefault(),
        "%+.1f s",
        offsetMs / 1_000f,
    )
