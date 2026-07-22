package com.awd.driverouter.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface BackupDao {
    @Query("SELECT * FROM backup_configs")
    fun getAllConfigs(): Flow<List<BackupConfigEntity>>

    @Query("SELECT * FROM backup_configs WHERE isEnabled = 1")
    suspend fun getActiveConfigs(): List<BackupConfigEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConfig(config: BackupConfigEntity)

    @Update
    suspend fun updateConfig(config: BackupConfigEntity)

    @Delete
    suspend fun deleteConfig(config: BackupConfigEntity)
}
