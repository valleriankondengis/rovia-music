package com.rovia.music.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rovia.music.core.library.FolderBrowserRepository
import com.rovia.music.core.library.MusicRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class LibraryViewModel(
    private val musicRepository: MusicRepository,
    private val folderBrowserRepository: FolderBrowserRepository,
) : ViewModel() {

    private val _uiState =
        MutableStateFlow<LibraryUiState>(
            LibraryUiState.Loading,
        )

    val uiState: StateFlow<LibraryUiState> =
        _uiState.asStateFlow()

    init {
        loadRootContent()
    }

    fun openFolder(
        relativePath: String,
    ) {
        viewModelScope.launch {
            val currentState =
                _uiState.value as? LibraryUiState.Content
                    ?: return@launch

            try {
                val folders =
                    folderBrowserRepository.getSubFolders(
                        parentRelativePath =
                            relativePath,
                    )

                val folderTracks =
                    folderBrowserRepository.getSongs(
                        relativePath =
                            relativePath,
                    )

                _uiState.value =
                    currentState.copy(
                        folders = folders,
                        folderTracks = folderTracks,
                        currentFolderPath =
                            normalizePath(
                                relativePath,
                            ),
                    )
            } catch (throwable: Throwable) {
                _uiState.value =
                    LibraryUiState.Error(
                        throwable = throwable,
                    )
            }
        }
    }

    fun goToParentFolder() {
        val currentState =
            _uiState.value as? LibraryUiState.Content
                ?: return

        val currentFolderPath =
            currentState.currentFolderPath
                ?: return

        val parentPath =
            getParentPath(
                currentFolderPath,
            )

        viewModelScope.launch {
            try {
                if (parentPath == null) {
                    val rootFolders =
                        folderBrowserRepository
                            .getRootFolders()

                    _uiState.value =
                        currentState.copy(
                            folders = rootFolders,
                            folderTracks =
                                emptyList(),
                            currentFolderPath = null,
                        )
                    return@launch
                }

                val folders =
                    folderBrowserRepository.getSubFolders(
                        parentRelativePath =
                            parentPath,
                    )

                val folderTracks =
                    folderBrowserRepository.getSongs(
                        relativePath =
                            parentPath,
                    )

                _uiState.value =
                    currentState.copy(
                        folders = folders,
                        folderTracks = folderTracks,
                        currentFolderPath =
                            parentPath,
                    )
            } catch (throwable: Throwable) {
                _uiState.value =
                    LibraryUiState.Error(
                        throwable = throwable,
                    )
            }
        }
    }

    fun showAllSongs() {
        val currentState =
            _uiState.value as? LibraryUiState.Content
                ?: return

        _uiState.value =
            currentState.copy(
                folderTracks = emptyList(),
                currentFolderPath = null,
            )
    }

    fun showRootFolders() {
        viewModelScope.launch {
            val currentState =
                _uiState.value as? LibraryUiState.Content
                    ?: return@launch

            try {
                val rootFolders =
                    folderBrowserRepository
                        .getRootFolders()

                _uiState.value =
                    currentState.copy(
                        folders = rootFolders,
                        folderTracks = emptyList(),
                        currentFolderPath = null,
                    )
            } catch (throwable: Throwable) {
                _uiState.value =
                    LibraryUiState.Error(
                        throwable = throwable,
                    )
            }
        }
    }

    fun setSortOption(
        option: LibrarySortOption,
    ) {
        val currentState =
            _uiState.value as? LibraryUiState.Content
                ?: return

        _uiState.value =
            currentState.copy(
                sortOption = option,
            )
    }

    fun toggleSortOrder() {
        val currentState =
            _uiState.value as? LibraryUiState.Content
                ?: return

        if (
            currentState.sortOption ==
                LibrarySortOption.DEFAULT
        ) {
            return
        }

        val nextOrder =
            when (currentState.sortOrder) {
                LibrarySortOrder.ASCENDING ->
                    LibrarySortOrder.DESCENDING

                LibrarySortOrder.DESCENDING ->
                    LibrarySortOrder.ASCENDING
            }

        _uiState.value =
            currentState.copy(
                sortOrder = nextOrder,
            )
    }

    private fun loadRootContent() {
        viewModelScope.launch {
            try {
                val tracks =
                    musicRepository.getAllTracks()

                val folders =
                    folderBrowserRepository.getRootFolders()

                _uiState.value =
                    LibraryUiState.Content(
                        tracks = tracks,
                        folders = folders,
                    )
            } catch (throwable: Throwable) {
                _uiState.value =
                    LibraryUiState.Error(
                        throwable = throwable,
                    )
            }
        }
    }

    private fun normalizePath(
        path: String,
    ): String {
        return path.trimEnd('/') + "/"
    }

    private fun getParentPath(
        path: String,
    ): String? {
        val normalizedPath =
            normalizePath(path)

        val withoutTrailingSlash =
            normalizedPath.trimEnd('/')

        val parentSeparatorIndex =
            withoutTrailingSlash.lastIndexOf('/')

        if (parentSeparatorIndex < 0) {
            return null
        }

        val parentPath =
            withoutTrailingSlash
                .substring(
                    startIndex = 0,
                    endIndex = parentSeparatorIndex + 1,
                )

        return parentPath.takeIf {
            it.isNotBlank()
        }
    }
}