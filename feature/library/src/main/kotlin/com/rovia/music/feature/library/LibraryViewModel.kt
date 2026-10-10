
package com.rovia.music.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rovia.music.core.library.FolderBrowserRepository
import com.rovia.music.core.library.MusicRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
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

    /**
     * Changes the active Library browsing mode.
     *
     * Switching to Folder returns to the root folder list.
     * Switching to another mode clears any selected collection
     * and folder-specific content without modifying playback.
     */
    fun setBrowseMode(
        mode: LibraryBrowseMode,
    ) {
        if (mode == LibraryBrowseMode.FOLDER) {
            showRootFolders()
            return
        }

        val currentState =
            _uiState.value as? LibraryUiState.Content
                ?: return

        _uiState.value =
            currentState.copy(
                browseMode = mode,
                selectedCollection = null,
                folderTracks = emptyList(),
                currentFolderPath = null,
            )
    }

    /**
     * Opens an artist collection selected from real track metadata.
     */
    fun openArtist(
        name: String,
    ) {
        if (name.isBlank()) {
            return
        }

        openCollection(
            mode = LibraryBrowseMode.ARTIST,
            selection =
                LibraryCollectionSelection.Artist(
                    name = name,
                ),
        )
    }

    /**
     * Opens an album collection.
     *
     * The artist identity is part of the selection so albums
     * with the same title from different artists can stay separate.
     */
    fun openAlbum(
        title: String,
        artist: String?,
    ) {
        if (title.isBlank()) {
            return
        }

        openCollection(
            mode = LibraryBrowseMode.ALBUM,
            selection =
                LibraryCollectionSelection.Album(
                    title = title,
                    artist = artist,
                ),
        )
    }

    /**
     * Opens a genre collection selected from real track metadata.
     */
    fun openGenre(
        name: String,
    ) {
        if (name.isBlank()) {
            return
        }

        openCollection(
            mode = LibraryBrowseMode.GENRE,
            selection =
                LibraryCollectionSelection.Genre(
                    name = name,
                ),
        )
    }

    /**
     * Returns from a collection's track list to its collection list.
     */
    fun clearCollectionSelection() {
        val currentState =
            _uiState.value as? LibraryUiState.Content
                ?: return

        if (currentState.selectedCollection == null) {
            return
        }

        _uiState.value =
            currentState.copy(
                selectedCollection = null,
            )
    }

    private fun openCollection(
        mode: LibraryBrowseMode,
        selection: LibraryCollectionSelection,
    ) {
        val currentState =
            _uiState.value as? LibraryUiState.Content
                ?: return

        _uiState.value =
            currentState.copy(
                browseMode = mode,
                selectedCollection = selection,
                folderTracks = emptyList(),
                currentFolderPath = null,
            )
    }

    fun openFolder(
        relativePath: String,
    ) {
        viewModelScope.launch {
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

                val latestState =
                    _uiState.value as? LibraryUiState.Content
                        ?: return@launch

                _uiState.value =
                    latestState.copy(
                        browseMode =
                            LibraryBrowseMode.FOLDER,
                        selectedCollection = null,
                        folders = folders,
                        folderTracks = folderTracks,
                        currentFolderPath =
                            normalizePath(
                                relativePath,
                            ),
                    )
            } catch (
                cancellation: CancellationException,
            ) {
                throw cancellation
            } catch (
                throwable: Throwable,
            ) {
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

                    val latestState =
                        _uiState.value
                            as? LibraryUiState.Content
                            ?: return@launch

                    _uiState.value =
                        latestState.copy(
                            browseMode =
                                LibraryBrowseMode.FOLDER,
                            selectedCollection = null,
                            folders = rootFolders,
                            folderTracks = emptyList(),
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

                val latestState =
                    _uiState.value as? LibraryUiState.Content
                        ?: return@launch

                _uiState.value =
                    latestState.copy(
                        browseMode =
                            LibraryBrowseMode.FOLDER,
                        selectedCollection = null,
                        folders = folders,
                        folderTracks = folderTracks,
                        currentFolderPath =
                            parentPath,
                    )
            } catch (
                cancellation: CancellationException,
            ) {
                throw cancellation
            } catch (
                throwable: Throwable,
            ) {
                _uiState.value =
                    LibraryUiState.Error(
                        throwable = throwable,
                    )
            }
        }
    }

    fun showAllSongs() {
        setBrowseMode(
            LibraryBrowseMode.ALL_SONGS,
        )
    }

    fun showRootFolders() {
        val currentState =
            _uiState.value as? LibraryUiState.Content
                ?: return

        /*
         * Update the presentation state immediately, then load
         * the actual root folders without blocking navigation.
         */
        _uiState.value =
            currentState.copy(
                browseMode =
                    LibraryBrowseMode.FOLDER,
                selectedCollection = null,
                folderTracks = emptyList(),
                currentFolderPath = null,
            )

        viewModelScope.launch {
            try {
                val rootFolders =
                    folderBrowserRepository
                        .getRootFolders()

                val latestState =
                    _uiState.value as? LibraryUiState.Content
                        ?: return@launch

                /*
                 * Do not overwrite folder content if the user
                 * navigated into a folder while this request ran.
                 */
                if (
                    latestState.browseMode ==
                        LibraryBrowseMode.FOLDER &&
                    latestState.currentFolderPath == null
                ) {
                    _uiState.value =
                        latestState.copy(
                            folders = rootFolders,
                        )
                }
            } catch (
                cancellation: CancellationException,
            ) {
                throw cancellation
            } catch (
                throwable: Throwable,
            ) {
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
            var rootFoldersLoadStarted = false

            try {
                musicRepository
                    .observeAllTracks()
                    .collect { tracks ->
                        val currentState =
                            _uiState.value

                        /*
                         * Publish the track catalog as soon as its
                         * first snapshot is available. Root folder
                         * loading runs independently.
                         *
                         * Subsequent catalog emissions update only
                         * the song list, preserving the current mode,
                         * collection, folder, and sorting choices.
                         */
                        _uiState.value =
                            when (currentState) {
                                is LibraryUiState.Content ->
                                    currentState.copy(
                                        tracks = tracks,
                                    )

                                else ->
                                    LibraryUiState.Content(
                                        tracks = tracks,
                                        folders = emptyList(),
                                    )
                            }

                        if (!rootFoldersLoadStarted) {
                            rootFoldersLoadStarted = true

                            launch {
                                try {
                                    val rootFolders =
                                        folderBrowserRepository
                                            .getRootFolders()

                                    val latestState =
                                        _uiState.value
                                            as? LibraryUiState.Content
                                            ?: return@launch

                                    /*
                                     * Update only the root folder list.
                                     * Preserve whichever browsing mode
                                     * and collection the user currently
                                     * has selected.
                                     */
                                    if (
                                        latestState.currentFolderPath ==
                                            null
                                    ) {
                                        _uiState.value =
                                            latestState.copy(
                                                folders =
                                                    rootFolders,
                                            )
                                    }
                                } catch (
                                    cancellation: CancellationException,
                                ) {
                                    throw cancellation
                                } catch (
                                    throwable: Throwable,
                                ) {
                                    _uiState.value =
                                        LibraryUiState.Error(
                                            throwable = throwable,
                                        )
                                }
                            }
                        }
                    }
            } catch (
                cancellation: CancellationException,
            ) {
                throw cancellation
            } catch (
                throwable: Throwable,
            ) {
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
