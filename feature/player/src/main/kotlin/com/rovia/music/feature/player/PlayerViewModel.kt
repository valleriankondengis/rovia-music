package com.rovia.music.feature.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.rovia.music.core.library.LyricsRepository
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
) : ViewModel() {

    private val lyrics =
        MutableStateFlow<com.rovia.music.core.model.SyncedLyrics?>(
            null,
        )

    private val isLyricsLoading =
        MutableStateFlow(false)

    private val isLyricsVisible =
        MutableStateFlow(false)

    val uiState: StateFlow<PlayerUiState> =
        combine(
            playbackController.playbackState,
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
    }

    fun toggleLyrics() {
        isLyricsVisible.value =
            !isLyricsVisible.value
    }

    private fun observeCurrentTrack() {
        viewModelScope.launch {
            playbackController.playbackState
                .map { state ->
                    state.currentTrack
                }
                .distinctUntilChangedBy { track ->
                    track?.id
                }
                .collectLatest { track ->
                    loadLyrics(track)
                }
        }
    }

    private suspend fun loadLyrics(
        track: com.rovia.music.core.model.Track?,
    ) {
        isLyricsVisible.value = false
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
            ) as T
        }

        throw IllegalArgumentException(
            "Unknown ViewModel class: ${modelClass.name}",
        )
    }
}