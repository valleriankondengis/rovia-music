
package com.rovia.music.feature.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.rovia.music.core.library.LyricsRepository
import com.rovia.music.core.library.MusicRepository
import com.rovia.music.core.model.Track
import com.rovia.music.core.playback.PlaybackController
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PlayerViewModel(
    private val playbackController: PlaybackController,
    private val lyricsRepository: LyricsRepository,
    private val musicRepository: MusicRepository? = null,
) : ViewModel() {

    private val lyrics =
        MutableStateFlow<com.rovia.music.core.model.SyncedLyrics?>(
            null,
        )

    private val isLyricsLoading =
        MutableStateFlow(false)

    private val isLyricsVisible =
        MutableStateFlow(false)

    /**
     * Latest catalog version of the active track.
     *
     * This is a UI metadata snapshot only. Playback itself remains
     * controlled by PlaybackController and Media3.
     */
    private val catalogTrack =
        MutableStateFlow<Track?>(null)

    /**
     * Combines playback state with the latest catalog metadata
     * when both tracks refer to the same URI.
     *
     * Position, playing state, duration, repeat mode, and shuffle
     * state always remain sourced from PlaybackController.
     */
    private val playbackStateWithCatalog =
        combine(
            playbackController.playbackState,
            catalogTrack,
        ) { playbackState, refreshedTrack ->
            val activeTrack =
                playbackState.currentTrack

            val matchingCatalogTrack =
                refreshedTrack?.takeIf { track ->
                    track.uri == activeTrack?.uri
                }

            if (matchingCatalogTrack != null) {
                playbackState.copy(
                    currentTrack = matchingCatalogTrack,
                )
            } else {
                playbackState
            }
        }

    val uiState: StateFlow<PlayerUiState> =
        combine(
            playbackStateWithCatalog,
            lyrics,
            isLyricsLoading,
            isLyricsVisible,
        ) {
            playbackState,
            lyricsData,
            loading,
            visible,
            ->
            PlayerUiState(
                playbackState = playbackState,
                lyrics = lyricsData,
                isLyricsLoading = loading,
                isLyricsVisible = visible,
            )
        }.stateIn(
            scope = viewModelScope,
            started =
                SharingStarted.WhileSubscribed(
                    5_000L,
                ),
            initialValue =
                PlayerUiState(),
        )

    init {
        observeCurrentTrack()
        observeCurrentTrackMetadata()
    }

    fun toggleLyrics() {
        isLyricsVisible.value =
            !isLyricsVisible.value
    }

    fun hideLyrics() {
        isLyricsVisible.value = false
    }

    /**
     * Loads embedded lyrics when the playback URI changes.
     */
    private fun observeCurrentTrack() {
        viewModelScope.launch {
            playbackController.playbackState
                .map { state ->
                    state.currentTrack
                }
                .distinctUntilChangedBy { track ->
                    track?.uri
                }
                .collectLatest { track ->
                    loadLyrics(track)
                }
        }
    }

    /**
     * Observes only the catalog entry corresponding to the active URI.
     *
     * A track change cancels the previous observation. If no matching
     * catalog entry exists, the original playback track remains visible.
     */
    private fun observeCurrentTrackMetadata() {
        val repository =
            musicRepository ?: return

        viewModelScope.launch {
            playbackController.playbackState
                .map { state ->
                    state.currentTrack?.uri
                }
                .distinctUntilChangedBy { uri ->
                    uri
                }
                .collectLatest { uri ->
                    catalogTrack.value = null

                    if (uri != null) {
                        repository
                            .observeTrackByUri(uri)
                            .collectLatest { track ->
                                catalogTrack.value =
                                    track?.takeIf {
                                        it.uri == uri
                                    }
                            }
                    }
                }
        }
    }

    private suspend fun loadLyrics(
        track: Track?,
    ) {
        lyrics.value = null

        if (track == null) {
            isLyricsLoading.value = false
            return
        }

        isLyricsLoading.value = true

        val result =
            try {
                lyricsRepository
                    .getEmbeddedLyrics(track)
            } catch (
                exception: CancellationException,
            ) {
                throw exception
            } catch (
                _: Exception,
            ) {
                null
            }

        lyrics.value = result
        isLyricsLoading.value = false
    }
}

class PlayerViewModelFactory(
    private val playbackController: PlaybackController,
    private val lyricsRepository: LyricsRepository,
    private val musicRepository: MusicRepository? = null,
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(
        modelClass: Class<T>,
    ): T {
        if (
            modelClass.isAssignableFrom(
                PlayerViewModel::class.java,
            )
        ) {
            @Suppress("UNCHECKED_CAST")
            return PlayerViewModel(
                playbackController =
                    playbackController,
                lyricsRepository =
                    lyricsRepository,
                musicRepository =
                    musicRepository,
            ) as T
        }

        throw IllegalArgumentException(
            "Unknown ViewModel class: ${modelClass.name}",
        )
    }
}
