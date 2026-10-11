
package com.rovia.music

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rovia.music.core.library.LyricsRepository
import com.rovia.music.core.playback.AudioOutputController
import com.rovia.music.core.playback.ExternalPlaybackController

/**
 * Hosts the existing Rovia Player surface for a track selected
 * through an external content URI.
 *
 * This host does not access AppContainer, the Room catalog,
 * Folder Filter, or the regular MusicRepository.
 *
 * All visible player controls and artwork/lyrics presentation
 * are provided by the existing UnifiedPlayerSheet and PlayerRoute.
 */
@Composable
fun ExternalPlayerHost(
    playbackController: ExternalPlaybackController,
    lyricsRepository: LyricsRepository,
    audioOutputController: AudioOutputController,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val playbackState by
        playbackController.playbackState
            .collectAsStateWithLifecycle()

    UnifiedPlayerSheet(
        playbackState = playbackState,
        playbackController = playbackController,
        lyricsRepository = lyricsRepository,
        musicRepository = null,
        navigationBar = {},
        showBottomChrome = false,
        modifier = modifier.fillMaxSize(),
        audioOutputController = audioOutputController,
        initiallyExpanded = true,
        onPlayerCloseRequested = onClose,
    )
}
