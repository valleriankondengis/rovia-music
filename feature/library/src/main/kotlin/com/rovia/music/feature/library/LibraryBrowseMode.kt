
package com.rovia.music.feature.library

/**
 * The active browsing mode inside the Library screen.
 *
 * This is presentation state for navigating the local music
 * catalog. It does not represent a separate data source.
 */
enum class LibraryBrowseMode {
    ALL_SONGS,
    ARTIST,
    ALBUM,
    GENRE,
    FOLDER,
}

/**
 * Identifies a real artist, album, or genre selected by the user.
 *
 * Album selection includes its artist identity so albums with the
 * same title from different artists can remain separate.
 */
sealed interface LibraryCollectionSelection {

    data class Artist(
        val name: String,
    ) : LibraryCollectionSelection

    data class Album(
        val title: String,
        val artist: String?,
    ) : LibraryCollectionSelection

    data class Genre(
        val name: String,
    ) : LibraryCollectionSelection
}
