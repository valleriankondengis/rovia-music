package com.rovia.music.core.library

import com.rovia.music.core.model.MusicFolder

interface FolderScannerRepository {

    suspend fun getScannedFolders(): List<MusicFolder>
}