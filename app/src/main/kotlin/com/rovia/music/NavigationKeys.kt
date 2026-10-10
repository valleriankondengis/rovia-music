
package com.rovia.music

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
data object Home : NavKey

@Serializable
data object Search : NavKey

@Serializable
data object Library : NavKey

@Serializable
data object Settings : NavKey

@Serializable
data object FolderFilter : NavKey

@Serializable
data object About : NavKey

@Serializable
data object Player : NavKey

/**
 * Navigation destination for a recently played artist.
 */
@Serializable
data class ArtistDetail(
    val artistName: String,
) : NavKey

/**
 * Navigation destination for a recently played album.
 *
 * Artist identity is included to distinguish albums that share
 * the same title but belong to different artists.
 */
@Serializable
data class AlbumDetail(
    val albumTitle: String,
    val artistName: String?,
) : NavKey

/**
 * Navigation destination for a recently played genre.
 */
@Serializable
data class GenreDetail(
    val genreName: String,
) : NavKey
