
package com.rovia.music.core.library

import com.rovia.music.core.model.Track
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

interface MusicRepository {

    suspend fun getRecentlyAdded(
        limit: Int,
    ): List<Track>

    suspend fun getAllTracks(): List<Track>

    /**
     * Observes recently added tracks.
     *
     * The default implementation preserves compatibility with
     * existing repositories. A persistent catalog repository
     * should override this method with a database-backed Flow.
     */
    fun observeRecentlyAdded(
        limit: Int,
    ): Flow<List<Track>> =
        flow {
            emit(
                getRecentlyAdded(
                    limit = limit,
                ),
            )
        }

    /**
     * Observes the complete music catalog.
     *
     * The default implementation emits one snapshot for existing
     * repository implementations. The Room-backed implementation
     * overrides this method to emit catalog changes automatically.
     */
    fun observeAllTracks(): Flow<List<Track>> =
        flow {
            emit(
                getAllTracks(),
            )
        }

    /**
     * Observes a track by its complete content URI.
     *
     * URI is used instead of the MediaStore ID because IDs can
     * overlap between different storage volumes.
     *
     * Existing repositories remain compatible through this default
     * implementation. The Room-backed repository should override
     * this method with a query scoped to the requested URI.
     */
    fun observeTrackByUri(
        uri: String,
    ): Flow<Track?> =
        observeAllTracks().map { tracks ->
            tracks.firstOrNull { track ->
                track.uri == uri
            }
        }
}
