package com.rovia.music.data.mediastore

import java.text.Normalizer
import com.rovia.music.util.KotlinDateTime
import org.kotlincrypto.hash.sha2.SHA256
import kotlin.uuid.Uuid
import android.Manifest
import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.provider.MediaStore
import android.util.Log
import androidx.room3.withWriteTransaction
import com.rovia.music.data.local.database.RoviaDatabase
import com.rovia.music.data.local.entity.AlbumArtistCrossRef
import com.rovia.music.data.local.entity.AlbumEntity
import com.rovia.music.data.local.entity.ArtistEntity
import com.rovia.music.data.local.entity.GenreEntity
import com.rovia.music.data.local.entity.MediaStoreSyncStateEntity
import com.rovia.music.data.local.entity.TrackArtistCrossRef
import com.rovia.music.data.local.entity.TrackEntity
import com.rovia.music.data.local.entity.TrackGenreCrossRef
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class MediaStoreVolumeGeneration(
    val version: String,
    val generation: Long
)

data class MediaStoreGenerationSnapshot(
    val presentVolumeNames: Set<String>,
    val volumes: Map<String, MediaStoreVolumeGeneration>
)

private data class ArtistPresentationCandidate(
    val displayName: String,
    val artworkSourceUri: String?,
    val releaseSortKey: Long,
    val dateModifiedSeconds: Long,
    val dateAddedSeconds: Long,
    val trackId: Long,
)

data class CatalogSyncReport(
    val fullSyncVolumeCount: Int = 0,
    val incrementalSyncVolumeCount: Int = 0,
    val insertedTrackCount: Int = 0,
    val updatedTrackCount: Int = 0,
    val unavailableTrackCount: Int = 0
) {
    val didWork: Boolean
        get() = fullSyncVolumeCount > 0 || incrementalSyncVolumeCount > 0 || unavailableTrackCount > 0
}

class MediaStoreCatalogSynchronizer(
    context: Context,
    private val database: RoviaDatabase
) {
    private val applicationContext = context.applicationContext
    private val contentResolver: ContentResolver = applicationContext.contentResolver
    private val syncMutex = Mutex()
    private var artistPresentationMetadataReconciled = false

    suspend fun captureGenerationSnapshot(): MediaStoreGenerationSnapshot? =
        withContext(Dispatchers.IO) {
            if (!hasAudioPermission()) return@withContext null

            val presentVolumes = try {
                MediaStore.getExternalVolumeNames(applicationContext).toSet()
            } catch (exception: Exception) {
                Log.w(TAG, "Unable to list MediaStore volumes", exception)
                return@withContext null
            }

            val volumeSnapshots = buildMap {
                presentVolumes.forEach { volumeName ->
                    try {
                        val version = MediaStore.getVersion(applicationContext, volumeName)
                        val generation = MediaStore.getGeneration(applicationContext, volumeName)
                        put(
                            volumeName,
                            MediaStoreVolumeGeneration(
                                version = version,
                                generation = generation
                            )
                        )
                    } catch (exception: Exception) {
                        Log.w(TAG, "Unable to read MediaStore checkpoint for $volumeName", exception)
                    }
                }
            }

            MediaStoreGenerationSnapshot(
                presentVolumeNames = presentVolumes,
                volumes = volumeSnapshots
            )
        }

    suspend fun synchronize(
        tracks: List<LocalAudioTrack>,
        initialSnapshot: MediaStoreGenerationSnapshot
    ): CatalogSyncReport = withContext(Dispatchers.IO) {
        syncMutex.withLock {
            if (!hasAudioPermission()) {
                return@withLock CatalogSyncReport()
            }

            val currentSnapshot = captureGenerationSnapshot()
                ?: return@withLock CatalogSyncReport()

            if (currentSnapshot != initialSnapshot) {
                return@withLock CatalogSyncReport()
            }

            val syncStateDao = database.mediaStoreSyncStateDao()
            val trackDao = database.trackDao()
            val states = syncStateDao.observeAll().first().associateBy { it.volumeName }
            var plans = currentSnapshot.volumes.mapNotNull { (volumeName, volumeSnapshot) ->
                val previous = states[volumeName]
                val previousCheckpoint = previous?.let {
                    maxOf(it.lastGenerationAdded, it.lastGenerationModified)
                } ?: 0L
                val requiresFullSync = previous == null ||
                    previous.forceFullSync ||
                    previous.mediaStoreVersion != volumeSnapshot.version ||
                    volumeSnapshot.generation < previousCheckpoint
                val hasGenerationChanges = volumeSnapshot.generation > previousCheckpoint

                if (!requiresFullSync && !hasGenerationChanges) {
                    null
                } else {
                    VolumeSyncPlan(
                        volumeName = volumeName,
                        snapshot = volumeSnapshot,
                        previousState = previous,
                        fullSync = requiresFullSync,
                        previousGenerationAdded = previous?.lastGenerationAdded ?: 0L,
                        previousGenerationModified = previous?.lastGenerationModified ?: 0L,
                        generationRows = queryGenerationRows(volumeName)
                    )
                }
            }

            val disconnectedVolumes = states.keys - currentSnapshot.presentVolumeNames
            val existingAvailableTracks = trackDao.observeAvailableTracks().first()
            val existingBySourceKey = existingAvailableTracks
                .filter { !it.sourceKey.isNullOrBlank() }
                .associateBy { it.sourceKey!! }
            val trackVolumes = tracks.mapNotNull { track ->
                val volumeName = resolveVolumeName(track, currentSnapshot.presentVolumeNames)
                    ?: return@mapNotNull null
                TrackInVolume(volumeName, track)
            }
            if (trackVolumes.size != tracks.size) {
                Log.w(TAG, "Catalog synchronization skipped because some tracks have no reliable volume mapping")
                return@withLock CatalogSyncReport()
            }

            val currentTracksByVolume = trackVolumes.groupBy { it.volumeName }
            val allCurrentSourceKeys = trackVolumes.mapTo(mutableSetOf()) { item ->
                sourceKey(item.volumeName, item.track.id)
            }
            val plannedVolumeNames = plans.mapTo(mutableSetOf()) { it.volumeName }
            val existingSourceKeysByVolume = existingAvailableTracks
                .filter { !it.volumeName.isNullOrBlank() && !it.sourceKey.isNullOrBlank() }
                .groupBy { it.volumeName!! }
                .mapValues { (_, volumeTracks) -> volumeTracks.mapNotNullTo(mutableSetOf()) { it.sourceKey } }
            val fallbackReconciliationVolumes = currentSnapshot.presentVolumeNames.filter { volumeName ->
                if (volumeName in plannedVolumeNames) {
                    false
                } else {
                    val currentKeys = currentTracksByVolume[volumeName].orEmpty()
                        .mapTo(mutableSetOf()) { sourceKey(volumeName, it.track.id) }
                    val existingKeys = existingSourceKeysByVolume[volumeName].orEmpty()
                    currentKeys != existingKeys || currentTracksByVolume[volumeName].orEmpty().any { item ->
                        val current = existingBySourceKey[sourceKey(volumeName, item.track.id)]
                        current == null || !lightweightMetadataMatches(
                            current = current,
                            track = item.track,
                            volumeName = volumeName
                        )
                    }
                }
            }
            val fallbackPlans = fallbackReconciliationVolumes.mapNotNull { volumeName ->
                val volumeSnapshot = currentSnapshot.volumes[volumeName] ?: return@mapNotNull null
                val previous = states[volumeName]
                VolumeSyncPlan(
                    volumeName = volumeName,
                    snapshot = volumeSnapshot,
                    previousState = previous,
                    fullSync = false,
                    previousGenerationAdded = previous?.lastGenerationAdded ?: 0L,
                    previousGenerationModified = previous?.lastGenerationModified ?: 0L,
                    generationRows = queryGenerationRows(volumeName)
                )
            }
            plans = plans + fallbackPlans
            val now = KotlinDateTime.nowEpochMilliseconds()

            if (plans.isEmpty() && disconnectedVolumes.isEmpty()) {
                if (!artistPresentationMetadataReconciled) {
                    database.withWriteTransaction {
                        reconcileArtistPresentationMetadata(now)
                    }
                    artistPresentationMetadataReconciled = true
                }
                return@withLock CatalogSyncReport()
            }

            val dirtyVolumeNames = plans.mapTo(mutableSetOf()) { it.volumeName }
            val dirtyTracks = plans.flatMap { plan ->
                currentTracksByVolume[plan.volumeName].orEmpty()
            }
            val relinkCandidates = findRelinkCandidates(
                existingAvailableTracks = existingAvailableTracks,
                existingBySourceKey = existingBySourceKey,
                dirtyTracks = dirtyTracks,
                currentSourceKeys = allCurrentSourceKeys,
                dirtyVolumeNames = dirtyVolumeNames,
                presentVolumeNames = currentSnapshot.presentVolumeNames
            )
            val scanToken = Uuid.random().toString()
            var insertedTrackCount = 0
            var updatedTrackCount = 0
            var unavailableTrackCount = 0
            val relinkedTrackIds = mutableSetOf<Long>()

            val preparedReleases = plans.flatMap { plan ->
                val volumeTracks = currentTracksByVolume[plan.volumeName].orEmpty().map { it.track }
                val yearRanges = queryAlbumYearRanges(plan.volumeName)
                AlbumParser.parse(volumeTracks).map { release ->
                    PreparedRelease(
                        volumeName = plan.volumeName,
                        release = release,
                        identityKey = releaseIdentityKey(plan.volumeName, release),
                        yearRange = release.albumId?.let(yearRanges::get)
                    )
                }
            }

            val currentSnapshotStillValid = captureGenerationSnapshot() == initialSnapshot
            if (!currentSnapshotStillValid) {
                return@withLock CatalogSyncReport()
            }

            database.withWriteTransaction {
                val artistDao = database.artistDao()
                val albumDao = database.albumDao()
                val genreDao = database.genreDao()
                val relationDao = database.catalogRelationDao()
                val artistCache = mutableMapOf<String, ArtistEntity>()
                val genreCache = mutableMapOf<String, GenreEntity>()
                val albumIdByTrackIdentity = mutableMapOf<TrackSourceIdentity, Long>()

                disconnectedVolumes.forEach { volumeName ->
                    existingAvailableTracks
                        .filter { it.volumeName == volumeName }
                        .forEach { track ->
                            trackDao.markUnavailable(track.trackId, now)
                            unavailableTrackCount++
                        }
                    states[volumeName]?.let { state ->
                        syncStateDao.save(state.copy(forceFullSync = true))
                    }
                }

                preparedReleases.forEach { prepared ->
                    val release = prepared.release
                    val artworkUri = selectArtworkSourceUri(prepared.volumeName, release)
                    val years = prepared.yearRange
                    val dateValues = resolveReleaseDate(years)
                    val identityKey = prepared.identityKey
                    val albumArtistRaw = ArtistCreditParser.resolveDisplayNames(
                        release.tracks.map(LocalAudioTrack::albumArtist)
                    ).joinToString(", ").ifBlank { null }
                    val candidate = AlbumEntity(
                        identityKey = identityKey,
                        title = release.title,
                        normalizedTitle = normalize(release.title),
                        albumArtistCreditRaw = albumArtistRaw,
                        providerAlbumId = release.albumId,
                        providerVolumeName = prepared.volumeName,
                        releaseDateRaw = dateValues.raw,
                        releaseYear = dateValues.year,
                        releaseMonth = null,
                        releaseDay = null,
                        releaseDatePrecision = dateValues.precision,
                        releaseDateSource = dateValues.source,
                        firstMediaStoreReleaseYear = years?.firstYear,
                        lastMediaStoreReleaseYear = years?.lastYear,
                        releaseType = release.type.name,
                        releaseTypeSource = releaseTypeSource(release.title),
                        artworkAssetKey = null,
                        artworkSourceUri = artworkUri,
                        createdAtEpochMillis = now,
                        updatedAtEpochMillis = now
                    )
                    val album = upsertAlbum(candidate, albumDao, now)
                    val albumArtistNames = ArtistCreditParser.parse(albumArtistRaw).ifEmpty {
                        ArtistCreditParser.resolvePrimaryDisplayName(
                            release.tracks.map(LocalAudioTrack::artist)
                        )?.let(::listOf).orEmpty()
                    }
                    val albumArtists = albumArtistNames.mapIndexed { index, name ->
                        val artist = upsertArtist(
                            name = name,
                            artworkSourceUri = artworkUri,
                            now = now,
                            cache = artistCache
                        )
                        AlbumArtistCrossRef(
                            albumId = album.albumId,
                            artistId = artist.artistId,
                            creditOrder = index
                        )
                    }
                    val currentAlbumArtists = relationDao
                        .getAlbumArtistRelations(album.albumId)
                        .sortedBy { it.creditOrder }
                    if (currentAlbumArtists.map { it.artistId to it.creditOrder } !=
                        albumArtists.map { it.artistId to it.creditOrder }
                    ) {
                        relationDao.deleteAlbumArtistRelations(album.albumId)
                        if (albumArtists.isNotEmpty()) {
                            relationDao.insertAlbumArtistRelations(albumArtists)
                        }
                    }

                    release.tracks.forEach { releaseTrack ->
                        albumIdByTrackIdentity[
                            TrackSourceIdentity(prepared.volumeName, releaseTrack.id)
                        ] = album.albumId
                    }
                }

                plans.forEach { plan ->
                    val volumeItems = currentTracksByVolume[plan.volumeName].orEmpty()
                    val oldCheckpoint = maxOf(
                        plan.previousGenerationAdded,
                        plan.previousGenerationModified
                    )
                    val volumeTrackRows = volumeItems.mapNotNull { item ->
                        val track = item.track
                        val key = sourceKey(plan.volumeName, track.id)
                        val current = existingBySourceKey[key]
                        val generation = plan.generationRows?.get(track.id)
                        val fingerprint = metadataFingerprint(track, generation?.modified)
                        val generationChanged = generation != null && (
                            generation.added > plan.previousGenerationAdded ||
                                generation.modified > plan.previousGenerationModified
                            )
                        val shouldProcess = plan.fullSync ||
                            plan.generationRows == null ||
                            current == null ||
                            !current.isAvailable ||
                            current.metadataFingerprint != fingerprint ||
                            current.lastSeenGeneration != generation?.modified ||
                            generationChanged ||
                            plan.snapshot.generation < oldCheckpoint

                        if (shouldProcess) {
                            PreparedTrack(
                                volumeName = plan.volumeName,
                                track = track,
                                sourceKey = key,
                                fingerprint = fingerprint,
                                generation = generation,
                                albumReleaseId = albumIdByTrackIdentity[
                                    TrackSourceIdentity(plan.volumeName, track.id)
                                ]
                            )
                        } else {
                            albumIdByTrackIdentity[TrackSourceIdentity(plan.volumeName, track.id)]
                                ?.let { albumId ->
                                    if (current.albumReleaseId != albumId) {
                                        PreparedTrack(
                                            volumeName = plan.volumeName,
                                            track = track,
                                            sourceKey = key,
                                            fingerprint = fingerprint,
                                            generation = generation,
                                            albumReleaseId = albumId
                                        )
                                    } else {
                                        null
                                    }
                                }
                        }
                    }

                    volumeTrackRows.forEach { prepared ->
                        val current = existingBySourceKey[prepared.sourceKey]
                        val rekeyCandidate = relinkCandidates[prepared.sourceKey]
                        val exactExisting = if (current == null && rekeyCandidate != null) {
                            trackDao.getBySourceKey(prepared.sourceKey)
                        } else {
                            null
                        }
                        val existing = current ?: exactExisting ?: rekeyCandidate
                        val artworkUri = trackContentUri(
                            volumeName = prepared.volumeName,
                            mediaStoreId = prepared.track.id
                        )
                        val title = prepared.track.title
                            ?: prepared.track.displayName?.substringBeforeLast('.')
                                ?.takeIf(String::isNotBlank)
                            ?: ""
                        val embeddedMetadataIsCurrent =
                            existing?.metadataFingerprint == prepared.fingerprint
                        val candidate = TrackEntity(
                            trackId = existing?.trackId ?: 0L,
                            sourceKey = prepared.sourceKey,
                            mediaStoreId = prepared.track.id,
                            volumeName = prepared.volumeName,
                            contentUri = artworkUri,
                            displayName = prepared.track.displayName,
                            title = title,
                            normalizedTitle = normalize(title),
                            artistCreditRaw = prepared.track.artist,
                            albumArtistCreditRaw = prepared.track.albumArtist,
                            albumTitleSnapshot = prepared.track.album,
                            albumReleaseId = prepared.albumReleaseId,
                            durationMs = prepared.track.durationMs,
                            mediaStoreYear = prepared.track.releaseYear,
                            // FIX: previously a non-current embedded cache wiped
                            // every release field to null, on the assumption that
                            // embedded metadata would refill them. Nothing ever
                            // refills them during sync, so the database ended up
                            // with no year/date at all. Fall back to the
                            // MediaStore year instead; embedded data, when it is
                            // current, still wins via the branches below.
                            releaseDateRaw = if (embeddedMetadataIsCurrent) {
                                existing.releaseDateRaw
                            } else {
                                prepared.track.releaseYear?.toString()
                            },
                            releaseYear = if (embeddedMetadataIsCurrent) {
                                existing.releaseYear
                            } else {
                                prepared.track.releaseYear
                            },
                            releaseMonth = if (embeddedMetadataIsCurrent) {
                                existing.releaseMonth
                            } else {
                                null
                            },
                            releaseDay = if (embeddedMetadataIsCurrent) {
                                existing.releaseDay
                            } else {
                                null
                            },
                            releaseDatePrecision = if (embeddedMetadataIsCurrent) {
                                existing.releaseDatePrecision
                            } else {
                                prepared.track.releaseYear?.let { "YEAR" }
                            },
                            releaseDateSource = if (embeddedMetadataIsCurrent) {
                                existing.releaseDateSource
                            } else {
                                prepared.track.releaseYear?.let { "MEDIASTORE_YEAR" }
                            },
                            trackNumber = prepared.track.trackNumber,
                            discNumber = prepared.track.discNumber,
                            dateAddedSeconds = prepared.track.dateAddedSeconds,
                            dateModifiedSeconds = prepared.track.dateModifiedSeconds,
                            relativePath = prepared.track.relativePath,
                            fileSizeBytes = prepared.track.fileSizeBytes,
                            mimeType = prepared.track.mimeType,
                            isCompilation = prepared.track.compilation?.let(::parseBooleanMetadata),
                            artworkAssetKey = existing?.artworkAssetKey,
                            artworkSourceUri = artworkUri,
                            metadataFingerprint = prepared.fingerprint,
                            metadataExtractionVersion = if (embeddedMetadataIsCurrent) {
                                existing.metadataExtractionVersion
                            } else {
                                0
                            },
                            metadataExtractedAtEpochMillis = if (embeddedMetadataIsCurrent) {
                                existing.metadataExtractedAtEpochMillis
                            } else {
                                null
                            },
                            lastSeenScanToken = if (plan.fullSync) scanToken else existing?.lastSeenScanToken,
                            lastSeenGeneration = prepared.generation?.modified
                                ?: existing?.lastSeenGeneration,
                            isAvailable = true,
                            createdAtEpochMillis = existing?.createdAtEpochMillis ?: now,
                            updatedAtEpochMillis = existing?.updatedAtEpochMillis ?: now
                        )

                        var insertedAsNewTrack = false
                        val savedTrack = if (existing == null) {
                            val insertedId = trackDao.insertIfAbsent(candidate)
                            if (insertedId != -1L) {
                                insertedAsNewTrack = true
                                insertedTrackCount++
                                candidate.copy(trackId = insertedId)
                            } else {
                                val conflicted = trackDao.getBySourceKey(prepared.sourceKey)
                                    ?: error("Track source key conflict without an existing row")
                                val updated = candidate.copy(
                                    trackId = conflicted.trackId,
                                    createdAtEpochMillis = conflicted.createdAtEpochMillis,
                                    updatedAtEpochMillis = now
                                )
                                trackDao.update(updated)
                                updatedTrackCount++
                                updated
                            }
                        } else {
                            val unchanged = candidate.copy(
                                createdAtEpochMillis = existing.createdAtEpochMillis,
                                updatedAtEpochMillis = existing.updatedAtEpochMillis
                            ) == existing
                            if (!unchanged) {
                                val updated = candidate.copy(
                                    createdAtEpochMillis = existing.createdAtEpochMillis,
                                    updatedAtEpochMillis = now
                                )
                                trackDao.update(updated)
                                updatedTrackCount++
                                updated
                            } else {
                                existing
                            }
                        }

                        if (rekeyCandidate != null &&
                            rekeyCandidate.trackId == savedTrack.trackId &&
                            rekeyCandidate.sourceKey != savedTrack.sourceKey
                        ) {
                            relinkedTrackIds += savedTrack.trackId
                        }

                        val currentArtistRelations = if (insertedAsNewTrack) {
                            emptyList()
                        } else {
                            relationDao.getTrackArtistRelations(savedTrack.trackId)
                                .sortedBy { it.creditOrder }
                        }
                        val artistNames = prepared.track.artistNames
                        val desiredArtistRelations = artistNames.mapIndexed { index, name ->
                            val artist = upsertArtist(
                                name = name,
                                artworkSourceUri = artworkUri,
                                now = now,
                                cache = artistCache
                            )
                            TrackArtistCrossRef(
                                trackId = savedTrack.trackId,
                                artistId = artist.artistId,
                                creditOrder = index,
                                creditRole = "CREDITED"
                            )
                        }
                        if (currentArtistRelations.map { Triple(it.artistId, it.creditOrder, it.creditRole) } !=
                            desiredArtistRelations.map { Triple(it.artistId, it.creditOrder, it.creditRole) }
                        ) {
                            relationDao.deleteTrackArtistRelations(savedTrack.trackId)
                            if (desiredArtistRelations.isNotEmpty()) {
                                relationDao.insertTrackArtistRelations(desiredArtistRelations)
                            }
                        }

                        val currentGenreRelations = if (insertedAsNewTrack) {
                            emptyList()
                        } else {
                            relationDao.getTrackGenreRelations(savedTrack.trackId)
                                .sortedBy { it.genreOrder }
                        }
                        val desiredGenreRelations = prepared.track.genres
                            .distinctBy(::normalize)
                            .mapIndexed { index, name ->
                                val genre = upsertGenre(name, now, genreCache)
                                TrackGenreCrossRef(
                                    trackId = savedTrack.trackId,
                                    genreId = genre.genreId,
                                    genreOrder = index
                                )
                            }
                        if (currentGenreRelations.map { it.genreId to it.genreOrder } !=
                            desiredGenreRelations.map { it.genreId to it.genreOrder }
                        ) {
                            relationDao.deleteTrackGenreRelations(savedTrack.trackId)
                            if (desiredGenreRelations.isNotEmpty()) {
                                relationDao.insertTrackGenreRelations(desiredGenreRelations)
                            }
                        }
                    }

                    existingAvailableTracks
                        .filter { it.volumeName == plan.volumeName }
                        .filter { it.sourceKey !in allCurrentSourceKeys }
                        .filterNot { it.trackId in relinkedTrackIds }
                        .forEach { track ->
                            trackDao.markUnavailable(track.trackId, now)
                            unavailableTrackCount++
                        }

                    val previous = plan.previousState
                    syncStateDao.save(
                        MediaStoreSyncStateEntity(
                            volumeName = plan.volumeName,
                            mediaStoreVersion = plan.snapshot.version,
                            lastGenerationAdded = plan.snapshot.generation,
                            lastGenerationModified = plan.snapshot.generation,
                            lastSuccessfulFullSyncAtEpochMillis = if (plan.fullSync) {
                                now
                            } else {
                                previous?.lastSuccessfulFullSyncAtEpochMillis
                            },
                            lastSuccessfulIncrementalSyncAtEpochMillis = if (plan.fullSync) {
                                previous?.lastSuccessfulIncrementalSyncAtEpochMillis
                            } else {
                                now
                            },
                            lastFullScanToken = if (plan.fullSync) scanToken else previous?.lastFullScanToken,
                            forceFullSync = false
                        )
                    )
                }

                reconcileArtistPresentationMetadata(now)
            }
            artistPresentationMetadataReconciled = true

            CatalogSyncReport(
                fullSyncVolumeCount = plans.count { it.fullSync },
                incrementalSyncVolumeCount = plans.count { !it.fullSync },
                insertedTrackCount = insertedTrackCount,
                updatedTrackCount = updatedTrackCount,
                unavailableTrackCount = unavailableTrackCount
            )
        }
    }

    private suspend fun reconcileArtistPresentationMetadata(now: Long) {
        val tracks = database.trackDao().getAllAvailableTracks()
        if (tracks.isEmpty()) return

        val artists = database.artistDao().getAll()
        if (artists.isEmpty()) return

        val albumsById = database.albumDao().getAll().associateBy(AlbumEntity::albumId)
        val trackArtists = database.catalogRelationDao()
            .getAllAvailableTrackArtistRelations()
        val albumArtists = database.catalogRelationDao()
            .getAllAlbumArtistRelations()
        val albumArtistIdsByAlbumId = albumArtists
            .groupBy { it.albumId }
            .mapValues { (_, relations) -> relations.mapTo(hashSetOf()) { it.artistId } }

        val tracksByArtistId = trackArtists.groupBy(TrackArtistCrossRef::artistId)
        val availableAlbumIdsByArtistId = albumArtistIdsByAlbumId.entries
            .flatMap { (albumId, artistIds) -> artistIds.map { it to albumId } }
            .groupBy({ it.first }, { it.second })
        val tracksById = tracks.associateBy(TrackEntity::trackId)
        val tracksByAlbumId = tracks.filter { it.albumReleaseId != null }
            .groupBy { it.albumReleaseId!! }

        artists.forEach { artist ->
            val normalizedArtistName = normalize(artist.name)
            val trackRows = tracksByArtistId[artist.artistId].orEmpty()
                .mapNotNull { tracksById[it.trackId] }
            val albumIdsForArtist = availableAlbumIdsByArtistId[artist.artistId].orEmpty()
            val albumRows = albumIdsForArtist
                .flatMap { albumId -> tracksByAlbumId[albumId].orEmpty() }
            val candidateRows = (trackRows + albumRows).distinctBy(TrackEntity::trackId)

            val primaryCandidates = albumIdsForArtist.flatMap { albumId ->
                val albumTracks = tracksByAlbumId[albumId].orEmpty()
                val album = albumsById[albumId]
                val albumArtistNames = ArtistCreditParser.resolveDisplayNames(
                    listOf(album?.albumArtistCreditRaw) + albumTracks.map(TrackEntity::albumArtistCreditRaw)
                )
                val explicitDisplayName = albumArtistNames.firstOrNull {
                    normalize(it) == normalizedArtistName
                }
                val displayName = if (albumArtistNames.isNotEmpty()) {
                    explicitDisplayName
                } else {
                    ArtistCreditParser.resolvePrimaryDisplayName(
                        albumTracks.map(TrackEntity::artistCreditRaw)
                    )?.takeIf { normalize(it) == normalizedArtistName }
                }
                if (displayName == null) {
                    emptyList()
                } else {
                    albumTracks.map { track ->
                        track.toArtistPresentationCandidate(displayName, album)
                    }
                }
            }

            val fallbackCandidates = candidateRows.mapNotNull { track ->
                val displayName = ArtistCreditParser.parse(track.artistCreditRaw)
                    .firstOrNull { normalize(it) == normalizedArtistName }
                    ?: return@mapNotNull null
                track.toArtistPresentationCandidate(displayName, track.albumReleaseId?.let(albumsById::get))
            }

            val candidates = primaryCandidates.ifEmpty { fallbackCandidates }
            if (candidates.isEmpty()) return@forEach

            val latestCandidate = candidates.maxWithOrNull(
                compareBy<ArtistPresentationCandidate> { it.releaseSortKey }
                    .thenBy { it.dateModifiedSeconds }
                    .thenBy { it.dateAddedSeconds }
                    .thenBy { it.trackId }
            ) ?: return@forEach
            val displayName = latestCandidate.displayName
            val artworkSourceUri = latestCandidate.artworkSourceUri
                ?.takeIf(String::isNotBlank)
                ?: artist.artworkSourceUri

            val desired = artist.copy(
                name = displayName,
                normalizedName = normalize(displayName),
                artworkAssetKey = if (artworkSourceUri != artist.artworkSourceUri) {
                    null
                } else {
                    artist.artworkAssetKey
                },
                artworkSourceUri = artworkSourceUri,
                updatedAtEpochMillis = if (
                    displayName != artist.name ||
                    normalize(displayName) != artist.normalizedName ||
                    artworkSourceUri != artist.artworkSourceUri
                ) now else artist.updatedAtEpochMillis
            )
            if (desired != artist) database.artistDao().update(desired)
        }
    }

    private fun TrackEntity.toArtistPresentationCandidate(
        displayName: String,
        album: AlbumEntity?,
    ): ArtistPresentationCandidate {
        val resolvedReleaseYear = album?.releaseYear
            ?: album?.lastMediaStoreReleaseYear
            ?: album?.firstMediaStoreReleaseYear
            ?: releaseYear
            ?: mediaStoreYear
            ?: 0
        val resolvedReleaseMonth = album?.releaseMonth ?: releaseMonth ?: 0
        val resolvedReleaseDay = album?.releaseDay ?: releaseDay ?: 0
        val releaseSortKey = resolvedReleaseYear.toLong() * 10_000L +
            resolvedReleaseMonth.coerceIn(0, 12) * 100L + resolvedReleaseDay.coerceIn(0, 31)
        return ArtistPresentationCandidate(
            displayName = displayName,
            artworkSourceUri = album?.artworkSourceUri
                ?: artworkSourceUri
                ?: contentUri,
            releaseSortKey = releaseSortKey,
            dateModifiedSeconds = dateModifiedSeconds,
            dateAddedSeconds = dateAddedSeconds,
            trackId = trackId,
        )
    }

    private suspend fun upsertArtist(
        name: String,
        artworkSourceUri: String?,
        now: Long,
        cache: MutableMap<String, ArtistEntity>
    ): ArtistEntity {
        val displayName = name.trim().replace(WHITESPACE, " ")
        val normalizedName = normalize(displayName)
        val identityKey = "artist:$normalizedName"
        cache[identityKey]?.let { cached ->
            if (cached.artworkSourceUri == null && artworkSourceUri != null) {
                val updated = cached.copy(
                    artworkSourceUri = artworkSourceUri,
                    updatedAtEpochMillis = now
                )
                database.artistDao().update(updated)
                cache[identityKey] = updated
                return updated
            }
            return cached
        }

        val existing = database.artistDao().getByIdentityKey(identityKey)
        if (existing != null) {
            val desired = existing.copy(
                artworkSourceUri = existing.artworkSourceUri ?: artworkSourceUri,
                updatedAtEpochMillis = if (
                    existing.artworkSourceUri == null && artworkSourceUri != null
                ) now else existing.updatedAtEpochMillis
            )
            if (desired != existing) database.artistDao().update(desired)
            cache[identityKey] = desired
            return desired
        }

        val candidate = ArtistEntity(
            name = displayName,
            normalizedName = normalizedName,
            identityKey = identityKey,
            artworkSourceUri = artworkSourceUri,
            createdAtEpochMillis = now,
            updatedAtEpochMillis = now
        )
        val insertedId = database.artistDao().insertIfAbsent(candidate)
        val saved = if (insertedId != -1L) {
            candidate.copy(artistId = insertedId)
        } else {
            database.artistDao().getByIdentityKey(identityKey)
                ?: error("Artist identity conflict without an existing row")
        }
        cache[identityKey] = saved
        return saved
    }

    private suspend fun upsertGenre(
        name: String,
        now: Long,
        cache: MutableMap<String, GenreEntity>
    ): GenreEntity {
        val displayName = name.trim().replace(WHITESPACE, " ")
        val normalizedName = normalize(displayName)
        val identityKey = "genre:$normalizedName"
        cache[identityKey]?.let { return it }
        val existing = database.genreDao().getByIdentityKey(identityKey)
        if (existing != null) {
            val desired = existing.copy(
                name = displayName,
                normalizedName = normalizedName,
                updatedAtEpochMillis = if (
                    existing.name != displayName || existing.normalizedName != normalizedName
                ) now else existing.updatedAtEpochMillis
            )
            if (desired != existing) database.genreDao().update(desired)
            cache[identityKey] = desired
            return desired
        }

        val candidate = GenreEntity(
            name = displayName,
            normalizedName = normalizedName,
            identityKey = identityKey,
            createdAtEpochMillis = now,
            updatedAtEpochMillis = now
        )
        val insertedId = database.genreDao().insertIfAbsent(candidate)
        val saved = if (insertedId != -1L) {
            candidate.copy(genreId = insertedId)
        } else {
            database.genreDao().getByIdentityKey(identityKey)
                ?: error("Genre identity conflict without an existing row")
        }
        cache[identityKey] = saved
        return saved
    }

    private suspend fun upsertAlbum(
        candidate: AlbumEntity,
        dao: com.rovia.music.data.local.dao.AlbumDao,
        now: Long
    ): AlbumEntity {
        val existing = dao.getByIdentityKey(candidate.identityKey)
        if (existing != null) {
            val preserveExistingReleaseDate =
                releaseDateSourcePriority(existing.releaseDateSource) >
                    releaseDateSourcePriority(candidate.releaseDateSource)
            val preserveExistingReleaseType =
                releaseTypeSourcePriority(existing.releaseTypeSource) >
                    releaseTypeSourcePriority(candidate.releaseTypeSource)
            val desired = candidate.copy(
                albumId = existing.albumId,
                releaseDateRaw = if (preserveExistingReleaseDate) {
                    existing.releaseDateRaw
                } else {
                    candidate.releaseDateRaw
                },
                releaseYear = if (preserveExistingReleaseDate) {
                    existing.releaseYear
                } else {
                    candidate.releaseYear
                },
                releaseMonth = if (preserveExistingReleaseDate) {
                    existing.releaseMonth
                } else {
                    candidate.releaseMonth
                },
                releaseDay = if (preserveExistingReleaseDate) {
                    existing.releaseDay
                } else {
                    candidate.releaseDay
                },
                releaseDatePrecision = if (preserveExistingReleaseDate) {
                    existing.releaseDatePrecision
                } else {
                    candidate.releaseDatePrecision
                },
                releaseDateSource = if (preserveExistingReleaseDate) {
                    existing.releaseDateSource
                } else {
                    candidate.releaseDateSource
                },
                releaseType = if (preserveExistingReleaseType) {
                    existing.releaseType
                } else {
                    candidate.releaseType
                },
                releaseTypeSource = if (preserveExistingReleaseType) {
                    existing.releaseTypeSource
                } else {
                    candidate.releaseTypeSource
                },
                artworkAssetKey = existing.artworkAssetKey,
                artworkSourceUri = candidate.artworkSourceUri ?: existing.artworkSourceUri,
                createdAtEpochMillis = existing.createdAtEpochMillis,
                updatedAtEpochMillis = existing.updatedAtEpochMillis
            )
            val unchanged = desired == existing
            if (unchanged) return existing
            val updated = desired.copy(updatedAtEpochMillis = now)
            dao.update(updated)
            return updated
        }

        val insertedId = dao.insertIfAbsent(candidate)
        if (insertedId != -1L) return candidate.copy(albumId = insertedId)
        return dao.getByIdentityKey(candidate.identityKey)
            ?: error("Album identity conflict without an existing row")
    }

    private fun queryGenerationRows(volumeName: String): Map<Long, GenerationRow>? {
        return try {
            val uri = MediaStore.Audio.Media.getContentUri(volumeName)
            val cursor = contentResolver.query(
                uri,
                arrayOf(
                    MediaStore.Audio.Media._ID,
                    MediaStore.MediaColumns.GENERATION_ADDED,
                    MediaStore.MediaColumns.GENERATION_MODIFIED
                ),
                "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} > 0",
                null,
                null
            ) ?: return null

            cursor.use { result ->
                val idIndex = result.getColumnIndex(MediaStore.Audio.Media._ID)
                val addedIndex = result.getColumnIndex(MediaStore.MediaColumns.GENERATION_ADDED)
                val modifiedIndex = result.getColumnIndex(MediaStore.MediaColumns.GENERATION_MODIFIED)
                if (idIndex < 0 || addedIndex < 0 || modifiedIndex < 0) {
                    return null
                }

                buildMap {
                    while (result.moveToNext()) {
                        put(
                            result.getLong(idIndex),
                            GenerationRow(
                                added = result.getLong(addedIndex),
                                modified = result.getLong(modifiedIndex)
                            )
                        )
                    }
                }
            }
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            Log.w(TAG, "Unable to query item generations for $volumeName", exception)
            null
        }
    }

    private fun queryAlbumYearRanges(volumeName: String): Map<Long, AlbumYearRange> {
        return try {
            val uri = MediaStore.Audio.Albums.getContentUri(volumeName)
            val cursor = contentResolver.query(
                uri,
                arrayOf(
                    MediaStore.Audio.Albums._ID,
                    MediaStore.Audio.AlbumColumns.FIRST_YEAR,
                    MediaStore.Audio.AlbumColumns.LAST_YEAR
                ),
                null,
                null,
                null
            ) ?: return emptyMap()

            cursor.use { result ->
                val idIndex = result.getColumnIndex(MediaStore.Audio.Albums._ID)
                val firstIndex = result.getColumnIndex(MediaStore.Audio.AlbumColumns.FIRST_YEAR)
                val lastIndex = result.getColumnIndex(MediaStore.Audio.AlbumColumns.LAST_YEAR)
                if (idIndex < 0 || firstIndex < 0 || lastIndex < 0) {
                    return emptyMap()
                }

                buildMap {
                    while (result.moveToNext()) {
                        val firstYear = result.getIntOrNull(firstIndex)?.takeIf(::isValidYear)
                        val lastYear = result.getIntOrNull(lastIndex)?.takeIf(::isValidYear)
                        put(
                            result.getLong(idIndex),
                            AlbumYearRange(firstYear, lastYear)
                        )
                    }
                }
            }
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            Log.w(TAG, "Unable to query album release years for $volumeName", exception)
            emptyMap()
        }
    }

    private fun findRelinkCandidates(
        existingAvailableTracks: List<TrackEntity>,
        existingBySourceKey: Map<String, TrackEntity>,
        dirtyTracks: List<TrackInVolume>,
        currentSourceKeys: Set<String>,
        dirtyVolumeNames: Set<String>,
        presentVolumeNames: Set<String>
    ): Map<String, TrackEntity> {
        val oldCandidates = existingAvailableTracks.filter { old ->
            old.sourceKey != null &&
                old.sourceKey !in currentSourceKeys &&
                old.volumeName in presentVolumeNames &&
                old.volumeName in dirtyVolumeNames
        }.mapNotNull { old ->
            relinkFingerprint(
                title = old.title,
                artistCredit = old.artistCreditRaw,
                albumTitle = old.albumTitleSnapshot,
                durationMs = old.durationMs,
                fileSizeBytes = old.fileSizeBytes
            )?.let { it to old }
        }.groupBy({ it.first }, { it.second })

        val newCandidates = dirtyTracks.filter { item ->
            sourceKey(item.volumeName, item.track.id) !in existingBySourceKey
        }.mapNotNull { item ->
            relinkFingerprint(
                title = item.track.title ?: item.track.displayName?.substringBeforeLast('.').orEmpty(),
                artistCredit = item.track.artist,
                albumTitle = item.track.album,
                durationMs = item.track.durationMs,
                fileSizeBytes = item.track.fileSizeBytes
            )?.let { it to item }
        }.groupBy({ it.first }, { it.second })

        val result = mutableMapOf<String, TrackEntity>()
        newCandidates.forEach { (fingerprint, newItems) ->
            val oldItems = oldCandidates[fingerprint].orEmpty()
            if (newItems.size == 1 && oldItems.size == 1) {
                val item = newItems.single()
                result[sourceKey(item.volumeName, item.track.id)] = oldItems.single()
            }
        }
        return result
    }

    private fun lightweightMetadataMatches(
        current: TrackEntity,
        track: LocalAudioTrack,
        volumeName: String
    ): Boolean {
        val title = track.title
            ?: track.displayName?.substringBeforeLast('.')?.takeIf(String::isNotBlank)
            ?: ""
        val contentUri = trackContentUri(volumeName, track.id)
        return current.isAvailable &&
            current.sourceKey == sourceKey(volumeName, track.id) &&
            current.mediaStoreId == track.id &&
            current.volumeName == volumeName &&
            current.contentUri == contentUri &&
            current.displayName == track.displayName &&
            current.title == title &&
            current.normalizedTitle == normalize(title) &&
            current.artistCreditRaw == track.artist &&
            current.albumArtistCreditRaw == track.albumArtist &&
            current.albumTitleSnapshot == track.album &&
            current.durationMs == track.durationMs &&
            current.mediaStoreYear == track.releaseYear &&
            current.trackNumber == track.trackNumber &&
            current.discNumber == track.discNumber &&
            current.dateAddedSeconds == track.dateAddedSeconds &&
            current.dateModifiedSeconds == track.dateModifiedSeconds &&
            current.relativePath == track.relativePath &&
            current.fileSizeBytes == track.fileSizeBytes &&
            current.mimeType == track.mimeType
    }

    private fun metadataFingerprint(track: LocalAudioTrack, generationModified: Long?): String {
        val values = listOf(
            track.id.toString(),
            track.volumeName.orEmpty(),
            track.title.orEmpty(),
            track.artist.orEmpty(),
            track.album.orEmpty(),
            track.albumId?.toString().orEmpty(),
            track.albumArtist.orEmpty(),
            track.durationMs.toString(),
            track.releaseYear?.toString().orEmpty(),
            track.dateAddedSeconds.toString(),
            track.dateModifiedSeconds.toString(),
            track.relativePath.orEmpty(),
            track.trackNumber?.toString().orEmpty(),
            track.discNumber?.toString().orEmpty(),
            track.displayName.orEmpty(),
            track.fileSizeBytes?.toString().orEmpty(),
            track.mimeType.orEmpty(),
            track.genres.joinToString("\u001e") { normalize(it) },
            generationModified?.toString().orEmpty()
        ).joinToString("\u001f")
        val digest = SHA256().apply { update(values.encodeToByteArray()) }.digest()
        return buildString(digest.size * 2) {
            digest.forEach { byte ->
                val value = byte.toInt() and 0xff
                append("0123456789abcdef"[value ushr 4])
                append("0123456789abcdef"[value and 0x0f])
            }
        }
    }

    private fun relinkFingerprint(
        title: String,
        artistCredit: String?,
        albumTitle: String?,
        durationMs: Long,
        fileSizeBytes: Long?
    ): String? {
        if (durationMs <= 0L || fileSizeBytes == null || fileSizeBytes <= 0L) return null
        val normalizedTitle = normalize(title)
        if (normalizedTitle.isBlank()) return null
        val artistIdentity = ArtistCreditParser.parse(artistCredit)
            .joinToString("|") { normalize(it) }
        return listOf(
            normalizedTitle,
            artistIdentity,
            normalize(albumTitle.orEmpty()),
            durationMs.toString(),
            fileSizeBytes.toString()
        ).joinToString("\u001f")
    }

    private fun selectArtworkSourceUri(
        volumeName: String,
        release: AlbumRelease
    ): String? = CatalogArtworkSelector.albumArtworkTrack(release.tracks)?.let { track ->
        trackContentUri(volumeName, track.id)
    }

    private fun trackContentUri(volumeName: String, mediaStoreId: Long): String =
        ContentUris.withAppendedId(
            MediaStore.Audio.Media.getContentUri(volumeName),
            mediaStoreId
        ).toString()

    private fun releaseIdentityKey(volumeName: String, release: AlbumRelease): String {
        val providerAlbumId = release.albumId
        if (providerAlbumId != null && providerAlbumId > 0L) {
            return "media-store:$volumeName:$providerAlbumId"
        }
        val artistIdentity = normalize(
            release.albumArtist
                ?: release.tracks.firstOrNull()?.artistNames?.firstOrNull()
                ?: release.artists.firstOrNull().orEmpty()
        )
        return "fallback:${normalize(release.title)}|artist:$artistIdentity"
    }

    private fun resolveReleaseDate(years: AlbumYearRange?): ReleaseDateValues {
        val first = years?.firstYear
        val last = years?.lastYear
        // FIX: previously a multi-year album stored raw = "2019-2023"
        // with year = null. That range is not a release date, it broke
        // date parsing downstream, and it leaked into Track Info as the
        // track's release date. The first/last years are still kept in
        // firstMediaStoreReleaseYear / lastMediaStoreReleaseYear on the
        // album row; releaseDateRaw now always holds a single year, and
        // releaseYear is never null when any year is known.
        return when {
            first != null && (last == null || first == last) -> ReleaseDateValues(
                raw = first.toString(),
                year = first,
                precision = "YEAR",
                source = "MEDIASTORE_ALBUM_FIRST_YEAR"
            )
            first != null && last != null -> ReleaseDateValues(
                raw = first.toString(),
                year = first,
                precision = "YEAR_RANGE",
                source = "MEDIASTORE_ALBUM_FIRST_LAST_YEAR"
            )
            first == null && last != null -> ReleaseDateValues(
                raw = last.toString(),
                year = last,
                precision = "LAST_YEAR_ONLY",
                source = "MEDIASTORE_ALBUM_LAST_YEAR"
            )
            else -> ReleaseDateValues(null, null, null, null)
        }
    }

    private fun releaseDateSourcePriority(source: String?): Int = when (source) {
        "EMBEDDED_DATE" -> 50
        "EMBEDDED_YEAR" -> 40
        "MEDIASTORE_ALBUM_FIRST_YEAR", "MEDIASTORE_YEAR" -> 30
        "MEDIASTORE_ALBUM_FIRST_LAST_YEAR" -> 20
        "MEDIASTORE_ALBUM_LAST_YEAR" -> 10
        "FILE_DATE_MODIFIED" -> 5
        "FILE_DATE_CREATED" -> 4
        else -> 0
    }

    private fun releaseTypeSourcePriority(source: String?): Int = when (source) {
        "EMBEDDED_TAG", "EMBEDDED_RELEASE_TYPE" -> 40
        "TITLE_MARKER" -> 30
        "LOCAL_HEURISTIC" -> 10
        else -> 0
    }

    private fun releaseTypeSource(title: String): String {
        val normalizedTitle = normalize(title)
        val hasMarker = RELEASE_TYPE_MARKERS.any { marker ->
            normalizedTitle == marker ||
                normalizedTitle.endsWith(" $marker") ||
                normalizedTitle.endsWith(" - $marker") ||
                normalizedTitle.endsWith(" – $marker") ||
                normalizedTitle.endsWith(" — $marker") ||
                normalizedTitle.endsWith("($marker)") ||
                normalizedTitle.endsWith("[$marker]")
        }
        return if (hasMarker) "TITLE_MARKER" else "LOCAL_HEURISTIC"
    }

    private fun resolveVolumeName(
        track: LocalAudioTrack,
        presentVolumeNames: Set<String>
    ): String? {
        track.volumeName?.takeIf { it in presentVolumeNames }?.let { return it }
        if (track.volumeName == null) {
            return MediaStore.VOLUME_EXTERNAL_PRIMARY.takeIf { it in presentVolumeNames }
                ?: presentVolumeNames.singleOrNull()
        }
        return presentVolumeNames.singleOrNull()
    }

    private fun sourceKey(volumeName: String, mediaStoreId: Long): String =
        "media-store:$volumeName:$mediaStoreId"

    private fun hasAudioPermission(): Boolean =
        applicationContext.checkSelfPermission(Manifest.permission.READ_MEDIA_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    private fun normalize(value: String): String = Normalizer
        .normalize(value.trim().replace(WHITESPACE, " "), Normalizer.Form.NFC)
        .lowercase()

    private fun parseBooleanMetadata(value: String?): Boolean? = when (normalize(value.orEmpty())) {
        "1", "true", "yes" -> true
        "0", "false", "no" -> false
        else -> null
    }

    private fun isValidYear(year: Int): Boolean = year in 1000..9999

    private fun Cursor.getIntOrNull(columnIndex: Int): Int? {
        if (columnIndex < 0 || isNull(columnIndex)) return null
        return getInt(columnIndex)
    }

    private data class VolumeSyncPlan(
        val volumeName: String,
        val snapshot: MediaStoreVolumeGeneration,
        val previousState: MediaStoreSyncStateEntity?,
        val fullSync: Boolean,
        val previousGenerationAdded: Long,
        val previousGenerationModified: Long,
        val generationRows: Map<Long, GenerationRow>?
    )

    private data class GenerationRow(
        val added: Long,
        val modified: Long
    )

    private data class AlbumYearRange(
        val firstYear: Int?,
        val lastYear: Int?
    )

    private data class ReleaseDateValues(
        val raw: String?,
        val year: Int?,
        val precision: String?,
        val source: String?
    )

    private data class TrackInVolume(
        val volumeName: String,
        val track: LocalAudioTrack
    )

    private data class PreparedRelease(
        val volumeName: String,
        val release: AlbumRelease,
        val identityKey: String,
        val yearRange: AlbumYearRange?
    )

    private data class TrackSourceIdentity(
        val volumeName: String,
        val mediaStoreId: Long
    )

    private data class PreparedTrack(
        val volumeName: String,
        val track: LocalAudioTrack,
        val sourceKey: String,
        val fingerprint: String,
        val generation: GenerationRow?,
        val albumReleaseId: Long?
    )

    private companion object {
        const val TAG = "MediaStoreCatalogSync"
        val WHITESPACE = Regex("\\s+")
        val RELEASE_TYPE_MARKERS = setOf(
            "single",
            "single release",
            "ep",
            "extended play",
            "album",
            "full album"
        )
    }
}
