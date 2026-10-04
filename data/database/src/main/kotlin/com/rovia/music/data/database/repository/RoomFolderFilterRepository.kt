package com.rovia.music.data.database.repository

import com.rovia.music.core.library.FolderFilterRepository
import com.rovia.music.data.database.dao.ExcludedFolderDao
import com.rovia.music.data.database.entity.ExcludedFolderEntity
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn

class RoomFolderFilterRepository(
    private val dao: ExcludedFolderDao,
    scope: CoroutineScope,
) : FolderFilterRepository {

    private val initialized =
        CompletableDeferred<Unit>()

    override val excludedFolders: StateFlow<Set<String>> =
        dao.observeExcludedFolders()
            .map { folders ->
                folders
                    .asSequence()
                    .map(::normalizeFolderPath)
                    .filter(String::isNotBlank)
                    .toSet()
            }
            .onEach {
                initialized.complete(Unit)
            }
            .stateIn(
                scope = scope,
                started = SharingStarted.Eagerly,
                initialValue = emptySet(),
            )

    override suspend fun awaitInitialized() {
        initialized.await()
    }

    override suspend fun setExcludedFolders(
        folders: Set<String>,
    ) {
        val normalizedFolders =
            folders
                .asSequence()
                .map(::normalizeFolderPath)
                .filter(String::isNotBlank)
                .toSet()

        val entities =
            normalizedFolders
                .map { relativePath ->
                    ExcludedFolderEntity(
                        relativePath = relativePath,
                    )
                }

        dao.replaceAll(entities)
    }

    private fun normalizeFolderPath(
        path: String,
    ): String {
        val normalized =
            path.trim()

        if (
            normalized.isEmpty() ||
            normalized.endsWith("/")
        ) {
            return normalized
        }

        return "$normalized/"
    }
}