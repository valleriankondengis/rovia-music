package com.rovia.music.data.local.entity

import androidx.room3.Entity
import androidx.room3.PrimaryKey

@Entity(tableName = "media_store_sync_state")
data class MediaStoreSyncStateEntity(
    @PrimaryKey
    val volumeName: String,
    val mediaStoreVersion: String? = null,
    val lastGenerationAdded: Long = 0,
    val lastGenerationModified: Long = 0,
    val lastSuccessfulFullSyncAtEpochMillis: Long? = null,
    val lastSuccessfulIncrementalSyncAtEpochMillis: Long? = null,
    val lastFullScanToken: String? = null,
    val forceFullSync: Boolean = true
)
