package com.awd.driverouter.domain.repository

import android.net.Uri
import com.awd.driverouter.domain.model.CloudFile
import com.awd.driverouter.domain.model.Transfer
import kotlinx.coroutines.flow.Flow

interface TransferRepository {
    fun getAllTransfers(): Flow<List<Transfer>>
    
    suspend fun startDownload(file: CloudFile)
    
    suspend fun startUpload(uri: Uri, accountId: String, targetFolderId: String?)
    
    suspend fun cancelTransfer(transferId: String)
    
    suspend fun clearHistory()
}
