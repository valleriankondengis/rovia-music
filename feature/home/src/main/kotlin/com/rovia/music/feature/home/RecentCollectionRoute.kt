
package com.rovia.music.feature.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rovia.music.core.library.MusicRepository
import com.rovia.music.core.model.Track
import com.rovia.music.core.playback.PlaybackController
import java.util.Locale

@Composable
fun RecentCollectionRoute(
    musicRepository: MusicRepository,
    playbackController: PlaybackController,
    collectionType: RecentCollectionType,
    collectionName: String,
    albumArtist: String? = null,
    onBack: () -> Unit,
) {
    val allTracksFlow =
        remember(musicRepository) {
            musicRepository.observeAllTracks()
        }

    val allRecentPlaysFlow =
        remember(playbackController) {
            playbackController.recentPlays
        }

    val allTracks by
        allTracksFlow.collectAsStateWithLifecycle(
            initialValue = emptyList(),
        )

    val allRecentPlays by
        allRecentPlaysFlow.collectAsStateWithLifecycle(
            initialValue = emptyList(),
        )

    val playbackState by
        playbackController.playbackState
            .collectAsStateWithLifecycle()

    val matchingTracks =
        remember(
            allTracks,
            collectionType,
            collectionName,
            albumArtist,
        ) {
            allTracks.filter { track ->
                track.matchesRecentCollection(
                    collectionType = collectionType,
                    collectionName = collectionName,
                    collectionArtist = albumArtist,
                )
            }
        }

    /*
     * The source of collection artwork is the most recently
     * played track that belongs to the selected collection.
     *
     * Do not fall back to an unrelated catalog track.
     */
    val artworkUri =
        remember(
            allRecentPlays,
            collectionType,
            collectionName,
            albumArtist,
        ) {
            allRecentPlays
                .firstOrNull { track ->
                    track.matchesRecentCollection(
                        collectionType = collectionType,
                        collectionName = collectionName,
                        collectionArtist = albumArtist,
                    )
                }
                ?.artworkUri
        }

    RecentCollectionScreen(
        collectionType = collectionType,
        title = collectionName,
        albumArtist = albumArtist,
        artworkUri = artworkUri,
        tracks = matchingTracks,
        currentTrackId = playbackState.currentTrack?.id,
        hasMiniPlayer = playbackState.currentTrack != null,
        onBack = onBack,
        onPlayAll = {
            if (matchingTracks.isNotEmpty()) {
                playbackController.playQueue(
                    tracks = matchingTracks,
                    startIndex = 0,
                )
            }
        },
        onTrackClick = { tracks, startIndex ->
            playbackController.playQueue(
                tracks = tracks,
                startIndex = startIndex,
            )
        },
        onRestartCurrentTrack = {
            playbackController.restartCurrentTrack()
        },
    )
}

private fun Track.matchesRecentCollection(
    collectionType: RecentCollectionType,
    collectionName: String,
    collectionArtist: String?,
): Boolean {
    val normalizedCollectionName =
        normalizeCollectionMetadata(collectionName)

    return when (collectionType) {
        RecentCollectionType.ARTIST -> {
            normalizeCollectionMetadata(
                artist ?: albumArtist,
            ) == normalizedCollectionName
        }

        RecentCollectionType.ALBUM -> {
            normalizeCollectionMetadata(album) ==
                normalizedCollectionName &&
                normalizeCollectionMetadata(
                    albumArtist ?: artist,
                ) ==
                normalizeCollectionMetadata(collectionArtist)
        }

        RecentCollectionType.GENRE -> {
            normalizeCollectionMetadata(genre) ==
                normalizedCollectionName
        }
    }
}

private fun normalizeCollectionMetadata(
    value: String?,
): String? =
    value
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?.lowercase(Locale.ROOT)
