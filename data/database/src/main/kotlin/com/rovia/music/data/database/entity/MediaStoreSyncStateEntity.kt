package com.rovia.music.data.database.entity

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.PrimaryKey

@Entity(
    tableName = "media_store_sync_state",
)
data class MediaStoreSyncStateEntity(
    /*
     * Identifies the MediaStore volume whose synchronization
     * checkpoint is stored in this row.
     */
    @PrimaryKey
    @ColumnInfo(name = "volume_name")
    val volumeName: String,

    /*
     * MediaStore version observed during the last successful
     * synchronization.
     */
    @ColumnInfo(name = "media_store_version")
    val mediaStoreVersion: String,

    /*
     * Generation successfully processed by the catalog
     * synchronization pipeline.
     *
     * This value must only advance after the corresponding
     * catalog changes have been committed.
     */
    @ColumnInfo(name = "last_successful_generation")
    val lastSuccessfulGeneration: Long,

    @ColumnInfo(name = "last_successful_sync_epoch_millis")
    val lastSuccessfulSyncEpochMillis: Long,

    /*
     * Null until the first full catalog scan succeeds.
     */
    @ColumnInfo(name = "last_full_scan_epoch_millis")
    val lastFullScanEpochMillis: Long? = null,
)