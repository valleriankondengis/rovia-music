package com.rovia.music.ui.components

import android.content.Context
import android.media.MediaMetadataRetriever
import android.text.format.DateFormat as AndroidDateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import com.rovia.music.R
import com.rovia.music.data.lyrics.EmbeddedLyricsReader
import com.rovia.music.data.mediastore.LocalAudioTrack
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlin.math.roundToLong

private val TRACK_INFO_HORIZONTAL_PADDING = 18.dp
private val TRACK_INFO_VERTICAL_SPACING = 1.dp
private val TRACK_INFO_CONTENT_PADDING = PaddingValues(
    horizontal = 16.dp,
    vertical = 14.dp,
)

@Stable
class TrackInfoState {
    var selectedTrack by mutableStateOf<LocalAudioTrack?>(null)
        private set

    fun show(track: LocalAudioTrack) {
        selectedTrack = track
    }

    fun dismiss() {
        selectedTrack = null
    }
}

@Composable
fun rememberTrackInfoState(): TrackInfoState = remember {
    TrackInfoState()
}

@Composable
fun TrackInfoSheetHost(
    state: TrackInfoState,
) {
    val selectedTrack = state.selectedTrack
    val context = LocalContext.current

    var enrichedTrack by remember(selectedTrack?.uri) {
        mutableStateOf<LocalAudioTrack?>(null)
    }

    LaunchedEffect(selectedTrack) {
        enrichedTrack = null
        val base = selectedTrack ?: return@LaunchedEffect
        enrichedTrack = try {
            readEmbeddedTrackInfo(context = context, track = base)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            base
        }
    }

    selectedTrack?.let { track ->
        TrackInfoBottomSheet(
            track = enrichedTrack ?: track,
            onDismissRequest = state::dismiss,
        )
    }
}

private suspend fun readEmbeddedTrackInfo(
    context: Context,
    track: LocalAudioTrack,
): LocalAudioTrack = withContext(Dispatchers.IO) {
    val retriever = MediaMetadataRetriever()
    try {
        retriever.setDataSource(context, track.uri)

        fun value(key: Int): String? = retriever.extractMetadata(key)
            ?.trim()
            ?.takeIf(String::isNotBlank)

        val dateRaw = value(MediaMetadataRetriever.METADATA_KEY_DATE)
        val isDateYearRange = dateRaw?.let(TRACK_INFO_YEAR_RANGE_PATTERN::matches) == true
        val dateMatch = dateRaw
            ?.takeUnless { isDateYearRange }
            ?.let { TRACK_INFO_DATE_PATTERN.matchEntire(it) }
        val parsedYear = dateMatch?.groupValues?.get(1)
            ?.toIntOrNull()?.takeIf { it in 1000..9999 }
        val parsedMonth = dateMatch?.groupValues?.get(2)
            ?.takeIf(String::isNotEmpty)?.toIntOrNull()
        val parsedDay = dateMatch?.groupValues?.get(3)
            ?.takeIf(String::isNotEmpty)?.toIntOrNull()
        val yearFromTag = value(MediaMetadataRetriever.METADATA_KEY_YEAR)
            ?.toIntOrNull()?.takeIf { it in 1000..9999 }
        val releaseYear = parsedYear ?: yearFromTag ?: track.releaseYear

        val releaseDate = when {
            dateMatch != null -> dateRaw
            yearFromTag != null -> yearFromTag.toString()
            isDateYearRange -> releaseYear?.toString()
            track.releaseDate?.trim()?.let {
                TRACK_INFO_YEAR_RANGE_PATTERN.matches(it)
            } == true -> releaseYear?.toString()
            else -> track.releaseDate
        }
        val releasePrecision = when {
            dateMatch != null && parsedDay != null -> "DAY"
            dateMatch != null && parsedMonth != null -> "MONTH"
            dateMatch != null || yearFromTag != null || isDateYearRange -> "YEAR"
            else -> track.releaseDatePrecision
        }
        val releaseSource = when {
            dateMatch != null -> "EMBEDDED_DATE"
            yearFromTag != null -> "EMBEDDED_YEAR"
            else -> track.releaseDateSource
        }
        val metadataDateEpochMillis = if (
            parsedYear != null && parsedMonth != null
        ) {
            runCatching {
                LocalDate(parsedYear, parsedMonth, parsedDay ?: 1)
                    .atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()
            }.getOrNull()
        } else {
            track.metadataDateEpochMillis
        }
        val inferredDateEpochMillis = when {
            parsedYear != null && parsedMonth == null ->
                com.rovia.music.util.KotlinDateTime.yearStartEpochMillis(parsedYear)
            parsedYear != null -> track.inferredDateEpochMillis
            else -> track.inferredDateEpochMillis
                ?: releaseYear?.let {
                    com.rovia.music.util.KotlinDateTime.yearStartEpochMillis(it)
                }
        }
        val embeddedGenre = value(MediaMetadataRetriever.METADATA_KEY_GENRE)
            ?.split(',', ';')
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            ?.distinctBy { it.lowercase() }
            .orEmpty()
        val cdTrackNumber = value(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)
        val embeddedTrackNumber = cdTrackNumber
            ?.substringBefore('/')?.trim()?.toIntOrNull()?.takeIf { it > 0 }
        val embeddedDiscNumber = value(MediaMetadataRetriever.METADATA_KEY_DISC_NUMBER)
            ?.substringBefore('/')?.trim()?.toIntOrNull()?.takeIf { it > 0 }

        track.copy(
            albumArtist = track.albumArtist ?: value(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST),
            releaseYear = releaseYear,
            releaseDate = releaseDate,
            releaseMonth = if (dateMatch != null) parsedMonth else track.releaseMonth,
            releaseDay = if (dateMatch != null) parsedDay else track.releaseDay,
            releaseDatePrecision = releasePrecision,
            releaseDateSource = releaseSource,
            metadataDateEpochMillis = metadataDateEpochMillis,
            inferredDateEpochMillis = inferredDateEpochMillis,
            genres = track.genres.ifEmpty { embeddedGenre },
            trackNumber = embeddedTrackNumber ?: track.trackNumber,
            discNumber = embeddedDiscNumber ?: track.discNumber,
            cdTrackNumber = cdTrackNumber ?: track.cdTrackNumber,
            compilation = track.compilation ?: value(MediaMetadataRetriever.METADATA_KEY_COMPILATION),
            composer = track.composer ?: value(MediaMetadataRetriever.METADATA_KEY_COMPOSER),
            author = track.author ?: value(MediaMetadataRetriever.METADATA_KEY_AUTHOR),
            writer = track.writer ?: value(MediaMetadataRetriever.METADATA_KEY_WRITER),
            mimeType = track.mimeType ?: value(MediaMetadataRetriever.METADATA_KEY_MIMETYPE),
            sampleRateHz = track.sampleRateHz
                ?: value(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE)
                    ?.toIntOrNull()?.takeIf { it > 0 },
            bitsPerSample = track.bitsPerSample
                ?: value(MediaMetadataRetriever.METADATA_KEY_BITS_PER_SAMPLE)
                    ?.toIntOrNull()?.takeIf { it > 0 },
            bitrateBps = track.bitrateBps
                ?: value(MediaMetadataRetriever.METADATA_KEY_BITRATE)
                    ?.toIntOrNull()?.takeIf { it > 0 },
        )
    } catch (_: Exception) {
        track
    } finally {
        runCatching { retriever.release() }
    }
}

private val TRACK_INFO_DATE_PATTERN =
    Regex("""^(\d{4})(?:[-/.](\d{1,2}))?(?:[-/.](\d{1,2}))?.*$""")
private val TRACK_INFO_YEAR_RANGE_PATTERN = Regex("""^\d{4}-\d{4}$""")

private data class TrackInfoEntry(
    val label: String,
    val value: String,
)

private sealed interface EmbeddedLyricsUiState {
    data object Loading : EmbeddedLyricsUiState
    data class Loaded(val text: String) : EmbeddedLyricsUiState
    data object Unavailable : EmbeddedLyricsUiState
}

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
)
@Composable
fun TrackInfoBottomSheet(
    track: LocalAudioTrack,
    onDismissRequest: () -> Unit,
) {
    val context = LocalContext.current
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(
            SheetValue.Hidden,
            SheetValue.PartiallyExpanded,
            SheetValue.Expanded,
        ),
    )

    var lyricsUiState by remember(track.uri) {
        mutableStateOf<EmbeddedLyricsUiState>(EmbeddedLyricsUiState.Unavailable)
    }

    LaunchedEffect(track.uri) {
        lyricsUiState = EmbeddedLyricsUiState.Loading

        val lyricsText = try {
            EmbeddedLyricsReader.read(
                context = context,
                mediaItem = MediaItem.fromUri(track.uri),
            )
                ?.toDisplayText()
                ?.takeIf(String::isNotBlank)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            null
        }

        lyricsUiState = lyricsText?.let { text -> EmbeddedLyricsUiState.Loaded(text) }
            ?: EmbeddedLyricsUiState.Unavailable
    }

    val trackInfoEntries = listOf(
        TrackInfoEntry(
            label = stringResource(R.string.track_info_album),
            value = track.album.orEmpty().takeIf(String::isNotBlank).orEmpty(),
        ),
        TrackInfoEntry(
            label = stringResource(R.string.track_info_album_artist),
            value = track.albumArtist.orEmpty().takeIf(String::isNotBlank).orEmpty(),
        ),
        TrackInfoEntry(
            label = stringResource(R.string.track_info_genre),
            value = track.genre.orEmpty().takeIf(String::isNotBlank).orEmpty(),
        ),
        TrackInfoEntry(
            label = stringResource(R.string.track_info_year),
            value = track.year?.takeIf { it > 0 }?.toString().orEmpty(),
        ),
        TrackInfoEntry(
            label = stringResource(R.string.track_info_release_date),
            value = track.releaseDate.orEmpty().takeIf(String::isNotBlank).orEmpty(),
        ),
        TrackInfoEntry(
            label = stringResource(R.string.track_info_track),
            value = track.trackNumber?.takeIf { it > 0 }?.toString().orEmpty(),
        ),
        TrackInfoEntry(
            label = stringResource(R.string.track_info_disc),
            value = track.discNumber?.takeIf { it > 0 }?.toString().orEmpty(),
        ),
        TrackInfoEntry(
            label = stringResource(R.string.track_info_cd_track),
            value = track.cdTrackNumber.orEmpty().takeIf(String::isNotBlank).orEmpty(),
        ),
        TrackInfoEntry(
            label = stringResource(R.string.track_info_compilation),
            value = track.compilation.orEmpty().takeIf(String::isNotBlank).orEmpty(),
        ),
        TrackInfoEntry(
            label = stringResource(R.string.track_info_release_type),
            value = track.releaseType.orEmpty().takeIf(String::isNotBlank).orEmpty(),
        ),
        TrackInfoEntry(
            label = stringResource(R.string.track_info_label),
            value = track.label.orEmpty().takeIf(String::isNotBlank).orEmpty(),
        ),
        TrackInfoEntry(
            label = stringResource(R.string.track_info_copyright),
            value = track.copyright.orEmpty().takeIf(String::isNotBlank).orEmpty(),
        ),
        TrackInfoEntry(
            label = stringResource(R.string.track_info_composer),
            value = track.composer.orEmpty().takeIf(String::isNotBlank).orEmpty(),
        ),
        TrackInfoEntry(
            label = stringResource(R.string.track_info_author),
            value = track.author.orEmpty().takeIf(String::isNotBlank).orEmpty(),
        ),
        TrackInfoEntry(
            label = stringResource(R.string.track_info_writer),
            value = track.writer.orEmpty().takeIf(String::isNotBlank).orEmpty(),
        ),
        TrackInfoEntry(
            label = stringResource(R.string.track_info_duration),
            value = formatTrackDuration(track.durationMs),
        ),
        TrackInfoEntry(
            label = stringResource(R.string.track_info_format),
            value = track.mimeType
                ?.takeIf(String::isNotBlank)
                ?.let(::formatAudioType)
                .orEmpty(),
        ),
        TrackInfoEntry(
            label = stringResource(R.string.track_info_sample_rate),
            value = track.sampleRateHz
                ?.takeIf { it > 0 }
                ?.let(::formatSampleRate)
                .orEmpty(),
        ),
        TrackInfoEntry(
            label = stringResource(R.string.track_info_bit_depth),
            value = track.bitsPerSample?.takeIf { it > 0 }?.let { "$it-bit" }.orEmpty(),
        ),
        TrackInfoEntry(
            label = stringResource(R.string.track_info_bitrate),
            value = track.bitrateBps
                ?.takeIf { it > 0 }
                ?.let(::formatBitrate)
                .orEmpty(),
        ),
        TrackInfoEntry(
            label = stringResource(R.string.track_info_file_name),
            value = track.displayName.orEmpty().takeIf(String::isNotBlank).orEmpty(),
        ),
        TrackInfoEntry(
            label = stringResource(R.string.track_info_file_size),
            value = track.fileSizeBytes
                ?.takeIf { it > 0L }
                ?.let(::formatFileSize)
                .orEmpty(),
        ),
        TrackInfoEntry(
            label = stringResource(R.string.track_info_path),
            value = track.relativePath.orEmpty().takeIf(String::isNotBlank).orEmpty(),
        ),
        TrackInfoEntry(
            label = stringResource(R.string.track_info_volume),
            value = track.volumeName.orEmpty().takeIf(String::isNotBlank).orEmpty(),
        ),
        TrackInfoEntry(
            label = stringResource(R.string.track_info_added),
            value = formatEpochSeconds(track.dateAddedEpochSeconds),
        ),
        TrackInfoEntry(
            label = stringResource(R.string.track_info_modified),
            value = track.dateModifiedEpochSeconds
                .takeIf { it > 0L }
                ?.let(::formatEpochSeconds)
                .orEmpty(),
        ),
        TrackInfoEntry(
            label = stringResource(R.string.track_info_created_inferred),
            value = track.inferredDateEpochMillis
                ?.takeIf { it > 0L }
                ?.let(::formatEpochMillis)
                .orEmpty(),
        ),
        TrackInfoEntry(
            label = stringResource(R.string.track_info_metadata_date),
            value = track.metadataDateEpochMillis
                ?.takeIf { it > 0L }
                ?.let(::formatEpochMillis)
                .orEmpty(),
        ),
    )

    ModalBottomSheet(
        modifier = Modifier.fillMaxHeight(),
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        scrimColor = Color.Transparent,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(vertical = 8.dp),
        ) {
            val title = track.title
                ?.takeIf(String::isNotBlank)
                ?: stringResource(R.string.player_unknown_track)

            AlbumArtwork(
                artworkUri = track.uri.toString(),
                fallbackText = title,
                contentDescription = null,
                modifier = Modifier
                    .size(96.dp)
                    .align(Alignment.CenterHorizontally),
                size = 96.dp,
                shape = MaterialTheme.shapes.large,
            )

            Spacer(Modifier.height(16.dp))

            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
            )

            track.artist
                ?.takeIf(String::isNotBlank)
                ?.let { artist ->
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = artist,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp),
                    )
                }

            Spacer(Modifier.height(20.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = TRACK_INFO_HORIZONTAL_PADDING)
                    .clip(MaterialTheme.shapes.extraLarge),
                verticalArrangement = Arrangement.spacedBy(TRACK_INFO_VERTICAL_SPACING),
            ) {
                trackInfoEntries.forEachIndexed { index, entry ->
                    SegmentedListItem(
                        modifier = Modifier.fillMaxWidth(),
                        shapes = ListItemDefaults.segmentedShapes(
                            index = index,
                            count = trackInfoEntries.size,
                        ),
                        colors = ListItemDefaults.segmentedColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                        ),
                        contentPadding = TRACK_INFO_CONTENT_PADDING,
                        verticalAlignment = Alignment.CenterVertically,
                        content = {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = entry.label,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(0.38f),
                                )
                                Text(
                                    text = entry.value,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(0.62f),
                                )
                            }
                        },
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = TRACK_INFO_HORIZONTAL_PADDING),
            ) {
                Text(
                    text = stringResource(R.string.track_info_lyrics),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                    ),
                )

                Spacer(Modifier.height(10.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.extraLarge)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    when (val state = lyricsUiState) {
                        EmbeddedLyricsUiState.Loading -> {
                            Text(
                                text = stringResource(R.string.track_info_lyrics_loading),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        is EmbeddedLyricsUiState.Loaded -> {
                            SelectionContainer {
                                Text(
                                    text = state.text,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }

                        EmbeddedLyricsUiState.Unavailable -> {
                            Text(
                                text = stringResource(R.string.track_info_lyrics_unavailable),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

private fun SyncedLyrics.toDisplayText(): String =
    lines.joinToString(separator = "\n") { line ->
        line.words.joinToString(separator = " ") { word -> word.text }
    }

private fun formatTrackDuration(durationMs: Long): String {
    if (durationMs <= 0L) return ""
    val totalSeconds = durationMs / 1_000L
    return "${totalSeconds / 60L}:${(totalSeconds % 60L).toString().padStart(2, '0')}"
}

private fun formatAudioType(mimeType: String): String = mimeType
    .substringAfter(delimiter = "/", missingDelimiterValue = mimeType)
    .removePrefix("x-")
    .uppercase()

private fun formatSampleRate(sampleRateHz: Int): String =
    if (sampleRateHz % 1_000 == 0) {
        "${sampleRateHz / 1_000} kHz"
    } else {
        "${formatDecimal(sampleRateHz / 1_000.0, 1)} kHz"
    }

private fun formatBitrate(bitrateBps: Int): String = when {
    bitrateBps >= 1_000_000 -> "${formatDecimal(bitrateBps / 1_000_000.0, 2)} Mbps"
    bitrateBps >= 1_000 -> "${(bitrateBps + 500) / 1_000} kbps"
    bitrateBps > 0 -> "$bitrateBps bps"
    else -> ""
}

private fun formatFileSize(fileSizeBytes: Long): String {
    if (fileSizeBytes <= 0L) return ""
    val units = arrayOf("B", "KiB", "MiB", "GiB", "TiB")
    var value = fileSizeBytes.toDouble()
    var unitIndex = 0

    while (value >= 1_024.0 && unitIndex < units.lastIndex) {
        value /= 1_024.0
        unitIndex++
    }

    return if (unitIndex == 0) {
        "$fileSizeBytes ${units[unitIndex]}"
    } else {
        "${formatDecimal(value, 2)} ${units[unitIndex]}"
    }
}

private fun formatDecimal(value: Double, decimalPlaces: Int): String {
    val scale = when (decimalPlaces) {
        1 -> 10L
        else -> 100L
    }
    val rounded = (value * scale).roundToLong()
    val fraction = (rounded % scale).toString().padStart(decimalPlaces, '0')
    return "${rounded / scale}.$fraction"
}

private fun formatEpochSeconds(epochSeconds: Long): String =
    if (epochSeconds > 0L) formatEpochMillis(epochSeconds * 1_000L) else ""

private fun formatEpochMillis(epochMillis: Long): String =
    if (epochMillis > 0L) {
        AndroidDateFormat.format("dd MMM yyyy, HH:mm", epochMillis).toString()
    } else {
        ""
    }