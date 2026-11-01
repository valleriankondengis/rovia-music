package com.rovia.music.data.repository

import android.net.Uri
import com.rovia.music.data.local.dao.ArtistSearchRow
import com.rovia.music.data.local.dao.ArtistTrackRelationRow
import com.rovia.music.data.local.dao.LikedTrackSearchRow
import com.rovia.music.data.local.dao.PlaylistSearchRow
import com.rovia.music.data.local.database.RoviaDatabase
import com.rovia.music.data.local.entity.AlbumEntity
import com.rovia.music.data.local.entity.TrackEntity
import com.rovia.music.data.mediastore.AlbumParser
import com.rovia.music.data.mediastore.ArtistCreditParser
import com.rovia.music.data.mediastore.AlbumRelease
import com.rovia.music.data.mediastore.AlbumReleaseType
import com.rovia.music.data.mediastore.CatalogArtworkSelector
import com.rovia.music.data.mediastore.LocalAudioTrack
import java.text.Normalizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext


data class SearchSongResult(
    val track: LocalAudioTrack,
    val score: Int,
)

data class SearchArtistResult(
    val name: String,
    val artworkUri: String?,
    val trackCount: Int,
    val score: Int,
)

data class SearchAlbumResult(
    val release: AlbumRelease,
    val score: Int,
)

private const val DATABASE_SEARCH_RESULT_LIMIT = 30

private val DatabaseSearchWhitespace = Regex("\\s+")

data class SearchPlaylistResult(
    val playlistId: Long,
    val name: String,
    val description: String?,
    val artworkUri: String?,
    val trackCount: Long,
    val matchingTrackTitle: String?,
    val matchingTrackArtist: String?,
    val score: Int,
)

data class SearchLikedTrackResult(
    val likedTrackId: Long,
    val track: LocalAudioTrack?,
    val title: String,
    val artist: String?,
    val album: String?,
    val artworkUri: String?,
    val sourceKey: String?,
    val likedAtEpochMillis: Long,
    val score: Int,
)

data class DatabaseSearchResults(
    val songs: List<SearchSongResult> = emptyList(),
    val artists: List<SearchArtistResult> = emptyList(),
    val albums: List<SearchAlbumResult> = emptyList(),
    val likedTracks: List<SearchLikedTrackResult> = emptyList(),
    val playlists: List<SearchPlaylistResult> = emptyList(),
) {
    val isEmpty: Boolean
        get() = songs.isEmpty() && artists.isEmpty() && albums.isEmpty() &&
            likedTracks.isEmpty() && playlists.isEmpty()
}

class DatabaseSearchRepository(
    private val database: RoviaDatabase,
) {
    fun observeSearch(
        query: String,
        limit: Int = DATABASE_SEARCH_RESULT_LIMIT,
    ): Flow<DatabaseSearchResults> {
        val trimmedQuery = query.trim().replace(DatabaseSearchWhitespace, " ")
        if (trimmedQuery.isBlank()) return flowOf(DatabaseSearchResults())

        val normalizedQuery = normalizeDatabaseQuery(trimmedQuery)
        val plainQuery = trimmedQuery.lowercase()
        val resultLimit = limit.coerceIn(1, 100)
        val trackDao = database.trackDao()

        return combine(
            trackDao.searchAvailableTracks(normalizedQuery, resultLimit),
            database.artistDao().searchForDatabaseSearch(normalizedQuery, resultLimit),
            database.albumDao().search(normalizedQuery, resultLimit),
            database.likedTrackDao().searchLikedTracks(
                normalizedQuery = normalizedQuery,
                plainQuery = plainQuery,
                limit = resultLimit,
            ),
            database.playlistDao().searchPlaylistsForSearch(
                normalizedQuery = normalizedQuery,
                plainQuery = plainQuery,
                limit = resultLimit,
            ),
        ) { tracks, artists, albums, likedTracks, playlists ->
            SearchQueryRows(
                tracks = tracks,
                artists = artists,
                albums = albums,
                likedTracks = likedTracks,
                playlists = playlists,
            )
        }.map { rows ->
            withContext(Dispatchers.Default) {
                buildResults(rows, resultLimit)
            }
        }
    }

    private suspend fun buildResults(
        queryRows: SearchQueryRows,
        resultLimit: Int,
    ): DatabaseSearchResults {
        val trackDao = database.trackDao()
        val albumDao = database.albumDao()
        val relatedAlbumIds = queryRows.tracks.mapNotNull(TrackEntity::albumReleaseId)
        val allAlbumIds = (queryRows.albums.map(AlbumEntity::albumId) + relatedAlbumIds)
            .distinct()
        val albumEntitiesById = if (allAlbumIds.isEmpty()) {
            emptyMap<Long, AlbumEntity>()
        } else {
            albumDao.getByIds(allAlbumIds).associateBy(AlbumEntity::albumId)
        }

        val albumTrackRows = if (allAlbumIds.isEmpty()) {
            emptyList()
        } else {
            trackDao.getAvailableTracksForAlbums(allAlbumIds)
        }
        val artistIds = queryRows.artists
            .map(ArtistSearchRow::artistId)
            .distinct()
        val artistTrackRelations = if (artistIds.isEmpty()) {
            emptyList()
        } else {
            database.catalogRelationDao().getAvailableTrackRelationsForArtists(artistIds)
        }
        val artistTrackRows = if (artistIds.isEmpty()) {
            emptyList()
        } else {
            trackDao.getAvailableTracksForArtists(artistIds)
        }
        val artistTracksByRoomId = artistTrackRows
            .zip(mapTrackRows(artistTrackRows, includeGenres = false))
            .associate { (row, track) -> row.trackId to track }
        val artistTracksByArtistId = artistTrackRelations
            .groupBy(ArtistTrackRelationRow::artistId)
            .mapValues { (_, relations) ->
                relations.mapNotNull { relation ->
                    artistTracksByRoomId[relation.trackId]
                }
            }

        val likedTrackIds = queryRows.likedTracks.mapNotNull(LikedTrackSearchRow::availableTrackId)
            .distinct()
        val likedTrackRows = if (likedTrackIds.isEmpty()) {
            emptyList()
        } else {
            trackDao.getAvailableTracksByIds(likedTrackIds)
        }
        val playableRows = (queryRows.tracks + likedTrackRows)
            .distinctBy(TrackEntity::trackId)
        val playableTrackIds = playableRows.mapTo(hashSetOf<Long>(), TrackEntity::trackId)
        val albumOnlyRows = albumTrackRows.filterNot { it.trackId in playableTrackIds }
        val mappedPlayableTracks = playableRows.zip(mapTrackRows(playableRows))
            .associate { (row, track) -> row.trackId to track }
        val mappedAlbumTracks = albumOnlyRows.zip(
            mapTrackRows(albumOnlyRows, includeGenres = false),
        ).associate { (row, track) -> row.trackId to track }
        val mappedTracksByRoomId = mappedAlbumTracks + mappedPlayableTracks

        val songs = queryRows.tracks.mapIndexedNotNull { index, row ->
            mappedTracksByRoomId[row.trackId]?.let { track ->
                SearchSongResult(
                    track = track,
                    score = queryRows.tracks.size - index,
                )
            }
        }

        val artists = queryRows.artists.mapIndexed { index, artist ->
            val artistArtworkTrack = CatalogArtworkSelector.artistArtworkTrack(
                artistName = artist.name,
                tracks = artistTracksByArtistId[artist.artistId].orEmpty(),
            )
            val latestReleaseArtworkUri = artistArtworkTrack?.uri
                ?.takeIf { it != Uri.EMPTY }
                ?.toString()
                ?.takeIf(String::isNotBlank)

            SearchArtistResult(
                name = artist.name,
                artworkUri = latestReleaseArtworkUri ?: artist.artworkSourceUri,
                trackCount = artist.trackCount,
                score = queryRows.artists.size - index,
            )
        }

        val albumResults = albumEntitiesById.values.mapNotNull { album ->
            val albumRows = albumTrackRows.filter { it.albumReleaseId == album.albumId }
            if (albumRows.isEmpty()) return@mapNotNull null
            val releaseTracks = albumRows.mapNotNull { row -> mappedTracksByRoomId[row.trackId] }
            val parsedReleases = AlbumParser.parse(releaseTracks)
            val release = parsedReleases.firstOrNull { candidate ->
                candidate.albumId == album.providerAlbumId &&
                    candidate.title.equals(album.title, ignoreCase = true)
            } ?: parsedReleases.firstOrNull { candidate ->
                candidate.title.equals(album.title, ignoreCase = true)
            } ?: album.toRelease(releaseTracks)
            release to album
        }.distinctBy { (release, _) -> release.key }
            .map { (release, album) ->
                val matchingIndex = queryRows.albums.indexOfFirst { it.albumId == album.albumId }
                val relatedIndex = queryRows.tracks.indexOfFirst {
                    it.albumReleaseId == album.albumId
                }
                val score = when {
                    matchingIndex >= 0 -> queryRows.albums.size - matchingIndex + resultLimit
                    relatedIndex >= 0 -> queryRows.tracks.size - relatedIndex
                    else -> 1
                }
                SearchAlbumResult(release = release, score = score)
            }
            .sortedWith(
                compareByDescending<SearchAlbumResult> { it.score }
                    .thenBy { it.release.title.lowercase() },
            )
            .take(resultLimit)

        val likedResults = queryRows.likedTracks.mapIndexed { index, liked ->
            val availableTrack = liked.availableTrackId?.let(mappedTracksByRoomId::get)
            SearchLikedTrackResult(
                likedTrackId = liked.likedTrackId,
                track = availableTrack,
                title = availableTrack?.title?.takeIf(String::isNotBlank)
                    ?: liked.titleSnapshot,
                artist = availableTrack?.artist?.takeIf(String::isNotBlank)
                    ?: liked.artistCreditSnapshot,
                album = availableTrack?.album?.takeIf(String::isNotBlank)
                    ?: liked.albumTitleSnapshot,
                artworkUri = liked.artworkSourceUriSnapshot
                    ?: availableTrack?.uri?.toString(),
                sourceKey = liked.sourceKeySnapshot,
                likedAtEpochMillis = liked.likedAtEpochMillis,
                score = queryRows.likedTracks.size - index,
            )
        }

        val playlists = queryRows.playlists.mapIndexed { index, playlist ->
            SearchPlaylistResult(
                playlistId = playlist.playlistId,
                name = playlist.name,
                description = playlist.description,
                artworkUri = playlist.artworkSourceUri,
                trackCount = playlist.trackCount,
                matchingTrackTitle = playlist.matchingTrackTitle,
                matchingTrackArtist = playlist.matchingTrackArtist,
                score = resultLimit - playlist.matchPriority - index,
            )
        }

        return DatabaseSearchResults(
            songs = songs,
            artists = artists,
            albums = albumResults,
            likedTracks = likedResults,
            playlists = playlists,
        )
    }

    private suspend fun mapTrackRows(
        rows: List<TrackEntity>,
        includeGenres: Boolean = true,
    ): List<LocalAudioTrack> {
        if (rows.isEmpty()) return emptyList()

        val albumIds = rows.mapNotNull(TrackEntity::albumReleaseId).distinct()
        val albumsById = if (albumIds.isEmpty()) {
            emptyMap<Long, AlbumEntity>()
        } else {
            database.albumDao().getByIds(albumIds).associateBy(AlbumEntity::albumId)
        }
        val genresByTrackId = if (includeGenres) {
            database.trackDao()
                .getSearchGenres(rows.map(TrackEntity::trackId).distinct())
                .groupBy { it.trackId }
                .mapValues { (_, genres) -> genres.map { it.genreName }.distinct() }
        } else {
            emptyMap()
        }

        return rows.map { row ->
            val album = row.albumReleaseId?.let(albumsById::get)
            val uri = row.contentUri
                ?.takeIf(String::isNotBlank)
                ?.let(Uri::parse)
                ?: Uri.EMPTY
            val releaseYear = row.releaseYear ?: album?.releaseYear ?: row.mediaStoreYear
            LocalAudioTrack(
                id = row.mediaStoreId ?: row.trackId,
                uri = uri,
                title = row.title,
                artist = row.artistCreditRaw,
                album = row.albumTitleSnapshot ?: album?.title,
                durationMs = row.durationMs,
                dateAddedSeconds = row.dateAddedSeconds,
                relativePath = row.relativePath,
                dateModifiedSeconds = row.dateModifiedSeconds,
                albumId = album?.providerAlbumId,
                albumArtist = row.albumArtistCreditRaw ?: album?.albumArtistCreditRaw,
                releaseYear = releaseYear,
                genres = genresByTrackId[row.trackId].orEmpty(),
                releaseDate = row.releaseDateRaw ?: album?.releaseDateRaw,
                releaseMonth = row.releaseMonth ?: album?.releaseMonth,
                releaseDay = row.releaseDay ?: album?.releaseDay,
                releaseDatePrecision = row.releaseDatePrecision ?: album?.releaseDatePrecision,
                releaseDateSource = row.releaseDateSource ?: album?.releaseDateSource,
                releaseTypeSource = album?.releaseTypeSource,
                trackNumber = row.trackNumber,
                discNumber = row.discNumber,
                cdTrackNumber = row.trackNumber?.toString(),
                compilation = row.isCompilation?.toString(),
                releaseType = album?.releaseType,
                mimeType = row.mimeType,
                displayName = row.displayName,
                fileSizeBytes = row.fileSizeBytes,
                volumeName = row.volumeName,
            )
        }
    }

    private fun AlbumEntity.toRelease(
        tracks: List<LocalAudioTrack>,
    ): AlbumRelease {
        val artistNames = ArtistCreditParser.resolveDisplayNames(
            tracks.map(LocalAudioTrack::artist)
        )
        val albumArtistNames = ArtistCreditParser.resolveDisplayNames(
            listOf(albumArtistCreditRaw) + tracks.map(LocalAudioTrack::albumArtist)
        )
        val displayArtists = albumArtistNames.ifEmpty { artistNames }
        val albumArtist = displayArtists.joinToString(", ").ifBlank { null }
        val titleKey = "album-title:${normalizeAlbumTitle(title)}|album-artist:${normalizeAlbumTitle(albumArtist.orEmpty())}"
        val mappedType = runCatching {
            AlbumReleaseType.valueOf(releaseType)
        }.getOrDefault(AlbumReleaseType.UNKNOWN)
        return AlbumRelease(
            key = providerAlbumId?.let { "album-id:$it" } ?: titleKey,
            albumId = providerAlbumId,
            title = title,
            albumArtist = albumArtist,
            artists = displayArtists,
            tracks = tracks,
            totalDurationMs = tracks.sumOf { it.durationMs.coerceAtLeast(0L) },
            type = mappedType,
        )
    }
}

private data class SearchQueryRows(
    val tracks: List<TrackEntity>,
    val artists: List<ArtistSearchRow>,
    val albums: List<AlbumEntity>,
    val likedTracks: List<LikedTrackSearchRow>,
    val playlists: List<PlaylistSearchRow>,
)

private fun normalizeDatabaseQuery(value: String): String = Normalizer
    .normalize(value.trim().replace(DatabaseSearchWhitespace, " "), Normalizer.Form.NFC)
    .lowercase()

private fun normalizeAlbumTitle(value: String): String = Normalizer
    .normalize(value.trim(), Normalizer.Form.NFC)
    .lowercase()
