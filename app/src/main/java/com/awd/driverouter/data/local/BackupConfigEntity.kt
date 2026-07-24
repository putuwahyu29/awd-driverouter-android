package com.awd.driverouter.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "backup_configs")
data class BackupConfigEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val localFolderUri: String,
    val localFolderName: String,
    val cloudFolderName: String,
    val strategy: AllocationStrategy,
    val syncMode: SyncMode = SyncMode.ONE_WAY,
    val syncIntervalMinutes: Int = 60, // Default 1 hour
    val isEnabled: Boolean = true,
    val lastBackupTime: Long = 0,
    val wifiOnly: Boolean = true
)
