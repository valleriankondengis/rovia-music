package com.rovia.music.data.repository

import android.net.Uri
import com.rovia.music.data.local.dao.ArtistTrackRelationRow
import com.rovia.music.data.local.dao.TrackSearchArtistRow
import com.rovia.music.data.local.dao.TrackSearchGenreRow
import com.rovia.music.data.local.database.RoviaDatabase
import com.rovia.music.data.local.entity.AlbumEntity
import com.rovia.music.data.local.entity.ArtistEntity
import com.rovia.music.data.local.entity.GenreEntity
import com.rovia.music.data.local.entity.TrackEntity
import com.rovia.music.data.local.entity.TrackArtistCrossRef
import com.rovia.music.data.local.entity.TrackGenreCrossRef
import com.rovia.music.data.mediastore.AlbumParser
import com.rovia.music.data.mediastore.AlbumRelease
import com.rovia.music.data.mediastore.ArtistCreditParser
import com.rovia.music.data.mediastore.CatalogArtworkSelector
import com.rovia.music.data.mediastore.LocalAudioTrack
import com.rovia.music.data.mediastore.MediaStoreAudioRepository
import java.text.Normalizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

class RoomLibraryRepository(
    private val database: RoviaDatabase,
    private val folderFilterRepository: FolderFilterRepository,
    private val mediaStoreAudioRepository: MediaStoreAudioRepository,
) : LibraryRepository {
    override fun observeCatalog(): Flow<LibraryCatalog> {
        val catalogRows = combine(
            database.trackDao().observeAvailableTracks(),
            database.artistDao().observeAll(),
            database.albumDao().observeAllByReleaseDate(),
        ) { tracks, artists, albums ->
            LibraryCatalogRows(
                tracks = tracks,
                artists = artists,
                albums = albums,
            )
        }

        val relationRows = combine(
            database.genreDao().observeAll(),
            database.catalogRelationDao().observeAllTrackArtistRelations(),
            database.catalogRelationDao().observeAllArtistReleaseTrackRelations(),
            database.catalogRelationDao().observeAllTrackGenreRelations(),
        ) { genres, trackArtists, artistReleaseTracks, trackGenres ->
            LibraryRelationRows(
                genres = genres,
                trackArtists = trackArtists,
                artistReleaseTracks = artistReleaseTracks,
                trackGenres = trackGenres,
            )
        }

        return combine(
            catalogRows,
            relationRows,
            folderFilterRepository.excludedFolders,
        ) { catalog, relations, excludedFolders ->
            LibrarySnapshot(
                tracks = catalog.tracks,
                artists = catalog.artists,
                albums = catalog.albums,
                genres = relations.genres,
                trackArtists = relations.trackArtists,
                artistReleaseTracks = relations.artistReleaseTracks,
                trackGenres = relations.trackGenres,
                excludedFolders = excludedFolders,
            )
        }.map { snapshot ->
            withContext(Dispatchers.Default) {
                snapshot.toLibraryCatalog()
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeArtistDetail(artistName: String): Flow<ArtistDetailCatalog> {
        val identityKey = "artist:${ArtistCreditParser.normalizeIdentity(artistName)}"
        val trackRows = database.artistDao()
            .observeByIdentityKey(identityKey)
            .flatMapLatest { artist ->
                if (artist == null) {
                    flowOf(emptyList<TrackEntity>())
                } else {
                    database.trackDao().observeTracksForArtists(listOf(artist.artistId))
                }
            }

        return observeDetailTracks(trackRows).mapLatest { tracks ->
            withContext(Dispatchers.Default) {
                ArtistDetailCatalog(
                    tracks = tracks,
                    releases = AlbumParser.parse(tracks).filter { it.isForArtist(artistName) },
                )
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeGenreDetail(
        genreKey: String,
        artistName: String?,
    ): Flow<GenreDetailCatalog> {
        val normalizedGenreKey = normalizeLibraryIdentity(
            genreKey.removePrefix("genre:"),
        )
        val genreIdentityKey = "genre:$normalizedGenreKey"
        val genreFlow = database.genreDao().observeByIdentityKey(genreIdentityKey)
        val normalizedArtistName = artistName
            ?.takeIf(String::isNotBlank)
            ?.let { value -> ArtistCreditParser.normalizeIdentity(value) }
        val trackRows = genreFlow.flatMapLatest { genre ->
            if (genre == null) {
                flowOf(emptyList<TrackEntity>())
            } else if (normalizedArtistName == null) {
                database.trackDao().observeTracksForGenre(genre.genreId)
            } else {
                database.artistDao()
                    .observeByIdentityKey("artist:$normalizedArtistName")
                    .flatMapLatest { artist ->
                        if (artist == null) {
                            flowOf(emptyList<TrackEntity>())
                        } else {
                            database.trackDao().observeTracksForGenreAndArtist(
                                genreId = genre.genreId,
                                artistId = artist.artistId,
                            )
                        }
                    }
            }
        }

        return combine(genreFlow, observeDetailTracks(trackRows)) { genre, tracks ->
            GenreDetailCatalog(
                title = genre?.name?.takeIf(String::isNotBlank) ?: genreKey.removePrefix("genre:"),
                tracks = tracks,
            )
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeRelease(releaseKey: String): Flow<AlbumRelease?> {
        val normalizedReleaseKey = normalizeReleaseKey(releaseKey)
        val releaseRows: Flow<List<AlbumEntity>> = when {
            releaseKey.startsWith("album-id:") -> {
                val providerAlbumId = releaseKey.removePrefix("album-id:").toLongOrNull()
                if (providerAlbumId == null) {
                    flowOf(emptyList<AlbumEntity>())
                } else {
                    database.albumDao().observeByProviderAlbumId(providerAlbumId)
                }
            }

            releaseKey.startsWith("album-title:") -> {
                val suffix = releaseKey.removePrefix("album-title:")
                val artistSeparator = "|album-artist:"
                val separatorIndex = suffix.indexOf(artistSeparator)
                if (separatorIndex < 0) {
                    flowOf(emptyList<AlbumEntity>())
                } else {
                    val titleIdentity = ArtistCreditParser.normalizeIdentity(
                        suffix.substring(0, separatorIndex),
                    )
                    val artistIdentity = ArtistCreditParser.normalizeIdentity(
                        suffix.substring(separatorIndex + artistSeparator.length),
                    )
                    val databaseIdentity = "fallback:$titleIdentity|artist:$artistIdentity"
                    database.albumDao().observeByIdentityKey(databaseIdentity)
                }
            }

            else -> database.albumDao().observeByIdentityKey(releaseKey)
        }

        val trackRows = releaseRows.flatMapLatest { albums ->
            val albumIds = albums.map(AlbumEntity::albumId).distinct()
            if (albumIds.isEmpty()) {
                flowOf(emptyList<TrackEntity>())
            } else {
                database.trackDao().observeTracksForAlbums(albumIds)
            }
        }

        return observeDetailTracks(trackRows).mapLatest { tracks ->
            withContext(Dispatchers.Default) {
                AlbumParser.parse(tracks).firstOrNull { it.key == normalizedReleaseKey }
            }
        }
    }


    private fun normalizeReleaseKey(releaseKey: String): String {
        if (!releaseKey.startsWith("album-title:")) return releaseKey

        val suffix = releaseKey.removePrefix("album-title:")
        val artistSeparator = "|album-artist:"
        val separatorIndex = suffix.indexOf(artistSeparator)
        if (separatorIndex < 0) return releaseKey

        val titleIdentity = ArtistCreditParser.normalizeIdentity(
            suffix.substring(0, separatorIndex),
        )
        val artistIdentity = ArtistCreditParser.normalizeIdentity(
            suffix.substring(separatorIndex + artistSeparator.length),
        )
        return "album-title:$titleIdentity|album-artist:$artistIdentity"
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeDetailTracks(
        trackRows: Flow<List<TrackEntity>>,
    ): Flow<List<LocalAudioTrack>> = combine(
        trackRows,
        folderFilterRepository.excludedFolders,
    ) { rows, excludedFolders ->
        DetailTrackSnapshot(rows, excludedFolders)
    }.mapLatest { snapshot ->
        withContext(Dispatchers.Default) {
            val visibleRows = snapshot.tracks.filterNot { track ->
                isExcludedFolderPath(
                    path = track.relativePath.orEmpty(),
                    excludedFolders = snapshot.excludedFolders,
                )
            }
            if (visibleRows.isEmpty()) {
                emptyList()
            } else {
                val trackIds = visibleRows.map(TrackEntity::trackId)
                val albumIds = visibleRows.mapNotNull(TrackEntity::albumReleaseId).distinct()
                val albumsById = albumIds
                    .chunked(400)
                    .flatMap { chunk -> database.albumDao().getByIds(chunk) }
                    .associateBy(AlbumEntity::albumId)
                val artistRows = trackIds.chunked(400).flatMap { chunk ->
                    database.trackDao().getSearchArtists(chunk)
                }
                val genreRows = trackIds.chunked(400).flatMap { chunk ->
                    database.trackDao().getSearchGenres(chunk)
                }
                val artistNamesByTrackId = artistRows
                    .groupBy(TrackSearchArtistRow::trackId)
                    .mapValues { (_, rows) -> rows.map(TrackSearchArtistRow::artistName) }
                val genreNamesByTrackId = genreRows
                    .groupBy(TrackSearchGenreRow::trackId)
                    .mapValues { (_, rows) -> rows.map(TrackSearchGenreRow::genreName).distinct() }

                visibleRows.map { row ->
                    row.toLocalAudioTrack(
                        album = row.albumReleaseId?.let(albumsById::get),
                        relatedArtistNames = artistNamesByTrackId[row.trackId].orEmpty(),
                        relatedGenreNames = genreNamesByTrackId[row.trackId].orEmpty(),
                    )
                }
            }
        }
    }

    override suspend fun hasAvailableTracks(): Boolean =
        database.trackDao().observeAvailableTrackCount().first() > 0L

    override suspend fun ensureCatalogReady(forceRefresh: Boolean) {
        val databaseAlreadyHasTracks = hasAvailableTracks()
        if (databaseAlreadyHasTracks && !forceRefresh) return

        val mediaStoreCatalog = mediaStoreAudioRepository.catalogState.value
        mediaStoreAudioRepository.refresh(
            forceRefresh = forceRefresh || !mediaStoreCatalog.isLoaded,
        )

        val mediaStoreHasTracks = mediaStoreAudioRepository.catalogState.value.tracks.isNotEmpty()
        if (!databaseAlreadyHasTracks && mediaStoreHasTracks) {
            val roomCatalogReady = withTimeoutOrNull(ROOM_CATALOG_SYNC_TIMEOUT_MS) {
                database.trackDao().observeAvailableTrackCount().first { count -> count > 0L }
                true
            } ?: false

            if (!roomCatalogReady) {
                throw IllegalStateException("The Room catalog did not finish its initial synchronization.")
            }
        }
    }

    private fun LibrarySnapshot.toLibraryCatalog(): LibraryCatalog {
        val visibleRows = tracks.filterNot { track ->
            isExcludedFolderPath(
                path = track.relativePath.orEmpty(),
                excludedFolders = excludedFolders,
            )
        }
        if (visibleRows.isEmpty()) return LibraryCatalog()

        val visibleTrackIds = visibleRows.mapTo(hashSetOf(), TrackEntity::trackId)
        val artistsById = artists.associateBy(ArtistEntity::artistId)
        val albumsById = albums.associateBy(AlbumEntity::albumId)
        val genresById = genres.associateBy(GenreEntity::genreId)

        val visibleTrackArtists = trackArtists.filter { it.trackId in visibleTrackIds }
        val visibleArtistReleaseTracks = artistReleaseTracks.filter { it.trackId in visibleTrackIds }
        val visibleTrackGenres = trackGenres.filter { it.trackId in visibleTrackIds }

        val artistNamesByTrackId = visibleTrackArtists
            .groupBy(TrackArtistCrossRef::trackId)
            .mapValues { (_, relations) ->
                relations.sortedBy(TrackArtistCrossRef::creditOrder)
                    .mapNotNull { relation -> artistsById[relation.artistId]?.name }
            }
        val genreNamesByTrackId = visibleTrackGenres
            .groupBy(TrackGenreCrossRef::trackId)
            .mapValues { (_, relations) ->
                relations.sortedBy(TrackGenreCrossRef::genreOrder)
                    .mapNotNull { relation -> genresById[relation.genreId]?.name }
                    .distinct()
            }

        val localTracks = visibleRows.map { row ->
            val album = row.albumReleaseId?.let(albumsById::get)
            row.toLocalAudioTrack(
                album = album,
                relatedArtistNames = artistNamesByTrackId[row.trackId].orEmpty(),
                relatedGenreNames = genreNamesByTrackId[row.trackId].orEmpty(),
            )
        }
        val localTracksByRoomId = visibleRows.indices.associate { index ->
            visibleRows[index].trackId to localTracks[index]
        }
        val artistArtworkTracksByArtistId = visibleArtistReleaseTracks
            .groupBy(ArtistTrackRelationRow::artistId)
            .mapValues { (_, relations) ->
                relations.distinctBy(ArtistTrackRelationRow::trackId)
                    .mapNotNull { relation -> localTracksByRoomId[relation.trackId] }
            }

        val artistCollections = visibleTrackArtists
            .groupBy(TrackArtistCrossRef::artistId)
            .mapNotNull { (artistId, relations) ->
                val artist = artistsById[artistId] ?: return@mapNotNull null
                val artistTracks = relations.distinctBy(TrackArtistCrossRef::trackId)
                    .mapNotNull { localTracksByRoomId[it.trackId] }
                if (artistTracks.isEmpty()) return@mapNotNull null

                val artworkTracks = artistArtworkTracksByArtistId[artistId]
                    .orEmpty()
                    .ifEmpty { artistTracks }
                val latestReleaseArtworkUri = CatalogArtworkSelector
                    .artistArtworkTrack(
                        artistName = artist.name,
                        tracks = artworkTracks,
                    )
                    ?.uri
                    ?.takeIf { it != Uri.EMPTY }
                    ?.toString()
                    ?.takeIf(String::isNotBlank)

                LibraryCollection(
                    key = artist.identityKey,
                    title = artist.name,
                    destinationKey = artist.name,
                    trackCount = artistTracks.size,
                    artworkUri = latestReleaseArtworkUri ?: artist.artworkSourceUri,
                )
            }
            .sortedBy { it.title.lowercase() }

        val albumCollections = AlbumParser.parse(localTracks).map { release ->
            LibraryCollection(
                key = release.key,
                title = release.title,
                destinationKey = release.key,
                trackCount = release.tracks.size,
                detail = release.albumArtist,
                artworkUri = release.artworkUri,
            )
        }

        val genreCollections = visibleTrackGenres
            .groupBy(TrackGenreCrossRef::genreId)
            .mapNotNull { (genreId, relations) ->
                val genre = genresById[genreId] ?: return@mapNotNull null
                val genreTracks = relations.distinctBy(TrackGenreCrossRef::trackId)
                    .mapNotNull { localTracksByRoomId[it.trackId] }
                if (genreTracks.isEmpty()) return@mapNotNull null

                LibraryCollection(
                    key = genre.identityKey,
                    title = genre.name,
                    destinationKey = normalizeLibraryIdentity(genre.name),
                    trackCount = genreTracks.size,
                    artworkUri = CatalogArtworkSelector.latestReleaseTrack(genreTracks)?.uri?.toString(),
                )
            }
            .sortedBy { it.title.lowercase() }

        val folderTracks = localTracks
            .groupBy { track -> normalizeFolderPath(track.relativePath.orEmpty()) }

        val folderCounts = linkedMapOf<String, Int>()
        folderTracks.forEach { (path, tracksInFolder) ->
            if (path.isBlank()) return@forEach
            val segments = path.split('/').filter(String::isNotBlank)
            for (index in segments.indices) {
                val parentPath = segments.take(index + 1).joinToString("/")
                folderCounts[parentPath] = (folderCounts[parentPath] ?: 0) + tracksInFolder.size
            }
        }

        val folderEntries = folderCounts.map { (path, count) ->
            LibraryFolder(
                path = path,
                title = path.substringAfterLast('/').ifBlank { path },
                trackCount = count,
            )
        }
        val folderChildren = folderEntries
            .groupBy { folder -> folder.path.substringBeforeLast('/', "") }
            .mapValues { (_, children) -> children.sortedBy { it.title.lowercase() } }

        return LibraryCatalog(
            tracks = localTracks,
            artists = artistCollections,
            albums = albumCollections,
            genres = genreCollections,
            folderChildren = folderChildren,
            folderTracks = folderTracks,
        )
    }

    private fun TrackEntity.toLocalAudioTrack(
        album: AlbumEntity?,
        relatedArtistNames: List<String>,
        relatedGenreNames: List<String>,
    ): LocalAudioTrack {
        val artistCredit = artistCreditRaw
            ?.takeIf(String::isNotBlank)
            ?: relatedArtistNames.joinToString(", ").takeIf(String::isNotBlank)
        val uri = contentUri
            ?.takeIf(String::isNotBlank)
            ?.let(Uri::parse)
            ?: Uri.EMPTY

        return LocalAudioTrack(
            id = mediaStoreId ?: trackId,
            uri = uri,
            title = title,
            artist = artistCredit,
            album = albumTitleSnapshot ?: album?.title,
            durationMs = durationMs,
            dateAddedSeconds = dateAddedSeconds,
            relativePath = relativePath,
            dateModifiedSeconds = dateModifiedSeconds,
            albumId = album?.providerAlbumId,
            albumArtist = albumArtistCreditRaw ?: album?.albumArtistCreditRaw,
            releaseYear = releaseYear ?: album?.releaseYear ?: mediaStoreYear,
            genres = relatedGenreNames,
            releaseDate = releaseDateRaw ?: album?.releaseDateRaw,
            trackNumber = trackNumber,
            discNumber = discNumber,
            cdTrackNumber = trackNumber?.toString(),
            compilation = isCompilation?.toString(),
            releaseType = album?.releaseType,
            mimeType = mimeType,
            displayName = displayName,
            fileSizeBytes = fileSizeBytes,
            volumeName = volumeName,
            releaseMonth = releaseMonth ?: album?.releaseMonth,
            releaseDay = releaseDay ?: album?.releaseDay,
            releaseDatePrecision = releaseDatePrecision ?: album?.releaseDatePrecision,
            releaseDateSource = releaseDateSource ?: album?.releaseDateSource,
            releaseTypeSource = album?.releaseTypeSource,
        )
    }

    private companion object {
        const val ROOM_CATALOG_SYNC_TIMEOUT_MS = 60_000L
    }
}

private data class LibraryCatalogRows(
    val tracks: List<TrackEntity>,
    val artists: List<ArtistEntity>,
    val albums: List<AlbumEntity>,
)

private data class LibraryRelationRows(
    val genres: List<GenreEntity>,
    val trackArtists: List<TrackArtistCrossRef>,
    val artistReleaseTracks: List<ArtistTrackRelationRow>,
    val trackGenres: List<TrackGenreCrossRef>,
)

private data class DetailTrackSnapshot(
    val tracks: List<TrackEntity>,
    val excludedFolders: Set<String>,
)

private data class LibrarySnapshot(
    val tracks: List<TrackEntity>,
    val artists: List<ArtistEntity>,
    val albums: List<AlbumEntity>,
    val genres: List<GenreEntity>,
    val trackArtists: List<TrackArtistCrossRef>,
    val artistReleaseTracks: List<ArtistTrackRelationRow>,
    val trackGenres: List<TrackGenreCrossRef>,
    val excludedFolders: Set<String>,
)

private fun normalizeLibraryIdentity(value: String): String = Normalizer
    .normalize(value.trim().replace(Regex("\\s+"), " "), Normalizer.Form.NFC)
    .lowercase()
