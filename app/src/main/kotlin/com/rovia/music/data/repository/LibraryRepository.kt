package com.rovia.music.data.repository

import com.rovia.music.data.mediastore.AlbumRelease
import com.rovia.music.data.mediastore.LocalAudioTrack
import kotlinx.coroutines.flow.Flow

data class LibraryCollection(
    val key: String,
    val title: String,
    val destinationKey: String,
    val trackCount: Int,
    val detail: String? = null,
    val artworkUri: String? = null,
)

data class LibraryFolder(
    val path: String,
    val title: String,
    val trackCount: Int,
)

data class LibraryCatalog(
    val tracks: List<LocalAudioTrack> = emptyList(),
    val artists: List<LibraryCollection> = emptyList(),
    val albums: List<LibraryCollection> = emptyList(),
    val genres: List<LibraryCollection> = emptyList(),
    val folderChildren: Map<String, List<LibraryFolder>> = emptyMap(),
    val folderTracks: Map<String, List<LocalAudioTrack>> = emptyMap(),
)

data class ArtistDetailCatalog(
    val tracks: List<LocalAudioTrack> = emptyList(),
    val releases: List<AlbumRelease> = emptyList(),
)

data class GenreDetailCatalog(
    val title: String = "",
    val tracks: List<LocalAudioTrack> = emptyList(),
)

interface LibraryRepository {
    fun observeCatalog(): Flow<LibraryCatalog>

    fun observeArtistDetail(artistName: String): Flow<ArtistDetailCatalog>

    fun observeGenreDetail(genreKey: String, artistName: String? = null): Flow<GenreDetailCatalog>

    fun observeRelease(releaseKey: String): Flow<AlbumRelease?>

    suspend fun hasAvailableTracks(): Boolean

    suspend fun ensureCatalogReady(forceRefresh: Boolean = false)
}
