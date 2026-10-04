package com.rovia.music.core.library

import com.rovia.music.core.model.MusicFolder
import com.rovia.music.core.model.Track

interface FolderBrowserRepository {

    suspend fun getRootFolders(): List<MusicFolder>

    suspend fun getSubFolders(
        parentRelativePath: String,
    ): List<MusicFolder>

    suspend fun getSongs(
        relativePath: String,
    ): List<Track>
}