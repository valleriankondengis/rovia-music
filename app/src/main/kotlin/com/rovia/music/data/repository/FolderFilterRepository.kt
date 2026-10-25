package com.rovia.music.data.repository

import kotlinx.coroutines.flow.StateFlow

interface FolderFilterRepository {
    val excludedFolders: StateFlow<Set<String>>

    suspend fun setExcludedFolders(folders: Set<String>)
}

internal fun normalizeFolderPath(path: String): String =
    path.replace('\\', '/')
        .split('/')
        .filter { it.isNotBlank() }
        .joinToString("/")

internal fun isExcludedFolderPath(
    path: String,
    excludedFolders: Set<String>,
): Boolean {
    val normalizedPath = normalizeFolderPath(path)

    if (normalizedPath.isBlank()) {
        return false
    }

    return excludedFolders.any { excludedFolder ->
        normalizedPath == excludedFolder ||
            normalizedPath.startsWith("$excludedFolder/")
    }
}