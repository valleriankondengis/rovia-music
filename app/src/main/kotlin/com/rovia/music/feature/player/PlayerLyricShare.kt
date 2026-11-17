package com.rovia.music.feature.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.rovia.music.R
import com.rovia.music.ui.components.LyricLine
import com.rovia.music.ui.components.LyricShareLine
import com.rovia.music.ui.components.LyricShareRequest

private const val LYRIC_SHARE_CHAR_BUDGET = 250

class LyricSharePickerState {
    var picking by mutableStateOf(false)
        private set

    var picks by mutableStateOf<Set<Int>>(emptySet())
        private set

    var overBudget by mutableStateOf(false)
        private set

    fun beginPicking() {
        if (picking) return
        picking = true
        picks = emptySet()
        overBudget = false
    }

    fun pick(
        sourceIndex: Int,
        lines: List<LyricLine>,
    ) {
        if (picking) {
            toggle(sourceIndex, lines)
        } else {
            picking = true
            picks = emptySet()
            overBudget = false
            toggle(sourceIndex, lines)
        }
    }

    fun toggle(
        sourceIndex: Int,
        lines: List<LyricLine>,
    ) {
        when {
            sourceIndex in picks -> {
                picks = picks - sourceIndex
                overBudget = false
            }

            fitsOnCard(sourceIndex, lines) -> {
                picks = picks + sourceIndex
                overBudget = false
            }

            else -> {
                overBudget = true
            }
        }
    }

    fun cancel() {
        picking = false
        picks = emptySet()
        overBudget = false
    }

    fun buildRequest(
        title: String,
        artist: String,
        artworkUri: String?,
        lines: List<LyricLine>,
    ): LyricShareRequest? {
        val chosen = picks.sorted()

        val picked = buildList {
            chosen.forEachIndexed { position, sourceIndex ->
                if (position > 0 && sourceIndex > chosen[position - 1] + 1) {
                    add(LyricShareLine(text = "", isGap = true))
                }

                val line = lines.getOrNull(sourceIndex)
                    ?: return@forEachIndexed

                val text = lyricShareLineText(line)

                if (text.isNotBlank()) {
                    add(LyricShareLine(text = text))
                }
            }
        }

        if (picked.isEmpty()) {
            return null
        }

        cancel()

        return LyricShareRequest(
            title = title,
            artist = artist,
            artworkUri = artworkUri,
            lines = picked,
        )
    }

    private fun fitsOnCard(
        candidateIndex: Int,
        lines: List<LyricLine>,
    ): Boolean {
        val used = picks.sumOf { sourceIndex ->
            lines.getOrNull(sourceIndex)
                ?.let { lyricShareLineText(it).length }
                ?: 0
        }
        val candidate = lines.getOrNull(candidateIndex)
            ?.let { lyricShareLineText(it).length }
            ?: 0
        return used + candidate <= LYRIC_SHARE_CHAR_BUDGET
    }
}

@Composable
fun rememberLyricSharePickerState(): LyricSharePickerState =
    remember {
        LyricSharePickerState()
    }

fun lyricShareLineText(line: LyricLine): String =
    line.displayText ?: buildString {
        line.words.forEachIndexed { index, word ->
            append(word.text)
            if (index < line.words.lastIndex && word.spaceAfter) {
                append(' ')
            }
        }
    }

@Composable
fun LyricSharePickBar(
    state: LyricSharePickerState,
    onShareClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            if (state.overBudget) {
                Text(
                    text = stringResource(R.string.lyric_share_card_full),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(
                    onClick = { state.cancel() },
                ) {
                    Text(text = stringResource(R.string.lyric_pick_cancel))
                }

                Spacer(modifier = Modifier.weight(1f))

                Button(
                    onClick = onShareClick,
                    enabled = state.picks.isNotEmpty(),
                ) {
                    Text(text = stringResource(R.string.lyric_pick_share))
                }
            }
        }
    }
}
