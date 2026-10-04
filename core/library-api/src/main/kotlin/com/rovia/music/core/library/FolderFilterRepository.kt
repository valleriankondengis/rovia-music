package com.rovia.music.core.library

import kotlinx.coroutines.flow.StateFlow

interface FolderFilterRepository {

    val excludedFolders: StateFlow<Set<String>>

    suspend fun awaitInitialized()

    suspend fun setExcludedFolders(
        folders: Set<String>,
    )
}