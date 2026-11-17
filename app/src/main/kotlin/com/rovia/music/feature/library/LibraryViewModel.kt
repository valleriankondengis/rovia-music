package com.rovia.music.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.rovia.music.data.mediastore.LocalAudioTrack
import com.rovia.music.data.repository.LibraryCatalog
import com.rovia.music.data.repository.LibraryRepository
import com.rovia.music.data.repository.PlaylistSummary
import com.rovia.music.data.repository.LikedEntityItem
import com.rovia.music.data.repository.UserLibraryRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LibraryUiState(
    val catalog: LibraryCatalog = LibraryCatalog(),
    val isCatalogLoaded: Boolean = false,
    val isLoading: Boolean = true,
    val loadFailed: Boolean = false,
    val playlists: List<PlaylistSummary> = emptyList(),
    val likedCount: Int = 0,
    val likedTrackKeys: Set<String> = emptySet(),
    val likedEntities: List<LikedEntityItem> = emptyList(),
)

class LibraryViewModel(
    private val libraryRepository: LibraryRepository,
    private val userLibraryRepository: UserLibraryRepository,
) : ViewModel() {

    init {
        viewModelScope.launch {
            userLibraryRepository.observePlaylists().collect { playlists ->
                mutableState.update { current -> current.copy(playlists = playlists) }
            }
        }
        viewModelScope.launch {
            userLibraryRepository.observeLikedCount().collect { count ->
                mutableState.update { current -> current.copy(likedCount = count) }
            }
        }
        viewModelScope.launch {
            userLibraryRepository.observeLikedTrackKeys().collect { keys ->
                mutableState.update { current -> current.copy(likedTrackKeys = keys) }
            }
        }
        viewModelScope.launch {
            userLibraryRepository.observeLikedEntities().collect { entities ->
                mutableState.update { current -> current.copy(likedEntities = entities) }
            }
        }
    }

    fun createPlaylist(name: String) {
        viewModelScope.launch {
            userLibraryRepository.createPlaylist(name)
        }
    }

    fun addTrackToPlaylist(playlistId: Long, track: LocalAudioTrack) {
        viewModelScope.launch {
            userLibraryRepository.addTrackToPlaylist(playlistId, track)
        }
    }

    fun createPlaylistAndAdd(name: String, track: LocalAudioTrack) {
        viewModelScope.launch {
            val playlistId = userLibraryRepository.createPlaylist(name)
            userLibraryRepository.addTrackToPlaylist(playlistId, track)
        }
    }

    fun toggleLike(track: LocalAudioTrack) {
        viewModelScope.launch {
            userLibraryRepository.toggleLike(track)
        }
    }

    private val mutableState = MutableStateFlow(LibraryUiState())
    val state = mutableState.asStateFlow()

    private var hasAudioPermission = false
    private var catalogLoadJob: Job? = null
    private var catalogObservationJob: Job? = null

    fun onAudioPermissionChanged(granted: Boolean) {
        if (hasAudioPermission == granted) return

        hasAudioPermission = granted
        if (!granted) {
            catalogLoadJob?.cancel()
            catalogLoadJob = null
            catalogObservationJob?.cancel()
            catalogObservationJob = null
            mutableState.update {
                it.copy(
                    isCatalogLoaded = false,
                    isLoading = false,
                    loadFailed = false,
                )
            }
            return
        }

        mutableState.update { current ->
            current.copy(
                isLoading = current.catalog.tracks.isEmpty() && !current.isCatalogLoaded,
                loadFailed = false,
            )
        }
        observeCatalog()
        loadCatalogIfNeeded(forceRefresh = false)
    }

    fun retry() {
        if (!hasAudioPermission) return
        observeCatalog()
        loadCatalogIfNeeded(forceRefresh = true)
    }

    private fun observeCatalog() {
        if (catalogObservationJob?.isActive == true) return

        catalogObservationJob = viewModelScope.launch {
            libraryRepository.observeCatalog()
                .catch {
                    mutableState.update { current ->
                        current.copy(
                            isCatalogLoaded = true,
                            isLoading = false,
                            loadFailed = current.catalog.tracks.isEmpty(),
                        )
                    }
                }
                .collect { catalog ->
                    mutableState.update { current ->
                        current.copy(
                            catalog = catalog,
                            isCatalogLoaded = true,
                            loadFailed = false,
                        )
                    }
                }
        }
    }

    private fun loadCatalogIfNeeded(forceRefresh: Boolean) {
        if (catalogLoadJob?.isActive == true) return

        catalogLoadJob = viewModelScope.launch {
            val databaseAlreadyHasTracks = try {
                libraryRepository.hasAvailableTracks()
            } catch (exception: CancellationException) {
                throw exception
            } catch (_: Exception) {
                false
            }

            if (databaseAlreadyHasTracks && !forceRefresh) {
                mutableState.update {
                    it.copy(
                        isLoading = false,
                        loadFailed = false,
                    )
                }
                return@launch
            }

            mutableState.update { current ->
                current.copy(
                    isLoading = current.catalog.tracks.isEmpty(),
                    loadFailed = false,
                )
            }

            try {
                libraryRepository.ensureCatalogReady(forceRefresh)
                mutableState.update {
                    it.copy(
                        isCatalogLoaded = true,
                        isLoading = false,
                        loadFailed = false,
                    )
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (_: Exception) {
                mutableState.update { current ->
                    current.copy(
                        isCatalogLoaded = true,
                        isLoading = false,
                        loadFailed = current.catalog.tracks.isEmpty(),
                    )
                }
            }
        }
    }
}

class LibraryViewModelFactory(
    private val libraryRepository: LibraryRepository,
    private val userLibraryRepository: UserLibraryRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (!modelClass.isAssignableFrom(LibraryViewModel::class.java)) {
            throw IllegalArgumentException("Unsupported ViewModel: ${modelClass.name}")
        }
        return LibraryViewModel(
            libraryRepository = libraryRepository,
            userLibraryRepository = userLibraryRepository,
        ) as T
    }
}
