package com.awd.driverouter.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TransferDao {
    @Query("SELECT * FROM transfers ORDER BY id DESC")
    fun getAllTransfers(): Flow<List<TransferEntity>>

    @Query("SELECT * FROM transfers WHERE fileId = :fileId AND status != 'COMPLETED' AND status != 'FAILED' ORDER BY id DESC LIMIT 1")
    fun getTransferByFileId(fileId: String): Flow<TransferEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransfer(transfer: TransferEntity)

    @Query("UPDATE transfers SET progress = :progress, status = :status WHERE id = :id")
    suspend fun updateProgress(id: String, progress: Float, status: com.awd.driverouter.domain.model.TransferStatus)

    @Query("DELETE FROM transfers WHERE id = :id")
    suspend fun deleteTransfer(id: String)
    
    @Query("DELETE FROM transfers WHERE status = 'COMPLETED' OR status = 'FAILED'")
    suspend fun clearHistory()
}
