package com.rovia.music.data.repository

import android.content.Context
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

class SharedPreferencesFolderFilterRepository(
    context: Context,
) : FolderFilterRepository {

    private val preferences = context.applicationContext
        .getSharedPreferences(
            PREFERENCES_NAME,
            Context.MODE_PRIVATE,
        )

    private val mutableExcludedFolders = MutableStateFlow(
        preferences.getStringSet(
            EXCLUDED_FOLDERS_KEY,
            emptySet(),
        )
            .orEmpty()
            .map(::normalizeFolderPath)
            .filter { it.isNotBlank() }
            .toSet(),
    )

    override val excludedFolders: StateFlow<Set<String>> =
        mutableExcludedFolders

    override suspend fun setExcludedFolders(
        folders: Set<String>,
    ) {
        val normalizedFolders = folders
            .map(::normalizeFolderPath)
            .filter { it.isNotBlank() }
            .toSet()

        withContext(Dispatchers.IO) {
            val saved = preferences.edit()
                .putStringSet(
                    EXCLUDED_FOLDERS_KEY,
                    normalizedFolders.toMutableSet(),
                )
                .commit()

            if (!saved) {
                throw IOException(
                    "Unable to save excluded folders.",
                )
            }

            mutableExcludedFolders.value = normalizedFolders
        }
    }

    private companion object {
        const val PREFERENCES_NAME = "rovia_folder_filter"
        const val EXCLUDED_FOLDERS_KEY = "excluded_folders"
    }
}