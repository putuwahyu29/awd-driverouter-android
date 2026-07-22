package com.awd.driverouter.data.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.awd.driverouter.R
import com.awd.driverouter.data.local.AccountDao
import com.awd.driverouter.data.local.TransferDao
import com.awd.driverouter.data.local.toDomain
import com.awd.driverouter.domain.model.TransferStatus
import com.awd.driverouter.domain.provider.CloudProvider
import com.awd.driverouter.util.NotificationHelper
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.io.File

@HiltWorker
class TransferWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
    private val transferDao: TransferDao,
    private val accountDao: AccountDao,
    private val providers: List<@JvmSuppressWildcards CloudProvider>
) : CoroutineWorker(context, params) {

    private val notificationHelper = NotificationHelper(context)

    override suspend fun doWork(): Result {
        val transferId = inputData.getString("transfer_id") ?: return Result.failure()
        
        val fileId = inputData.getString("file_id")
        val accountId = inputData.getString("account_id")
        val providerId = inputData.getString("provider") ?: return fail(transferId, "Missing provider ID")
        val type = inputData.getString("type") ?: "DOWNLOAD"
        val fileName = inputData.getString("file_name") ?: "file"

        return try {
            val provider = providers.find { it.providerId == providerId } 
                ?: return fail(transferId, "Provider $providerId not found")
            
            val accounts = accountDao.getAllAccounts().first()
            val accountEntity = accounts.find { it.id == accountId }
            val account = accountEntity?.toDomain() ?: return fail(transferId, "Account $accountId not found")

            val title = if (type == "DOWNLOAD") context.getString(R.string.downloading_file, fileName)
                        else context.getString(R.string.uploading_file, fileName)
            
            notificationHelper.createNotificationChannel()
            val builder = notificationHelper.getTransferNotificationBuilder(title, context.getString(R.string.processing))
            
            val notificationId = transferId.hashCode()
            setForeground(ForegroundInfo(notificationId, builder.build()))

            transferDao.updateProgress(transferId, 0f, TransferStatus.RUNNING)
            notificationHelper.updateProgress(builder, 0, notificationId)

            var lastUpdateMillis = 0L
            var lastProgress = 0f
            
            val progressCallback: (Float) -> Unit = { progress ->
                val currentTime = System.currentTimeMillis()
                // Update if progress increased by 1% or 500ms passed
                if (progress - lastProgress >= 0.01f || currentTime - lastUpdateMillis >= 500L) {
                    lastUpdateMillis = currentTime
                    lastProgress = progress
                    
                    kotlinx.coroutines.runBlocking {
                        transferDao.updateProgress(transferId, progress, TransferStatus.RUNNING)
                    }
                    notificationHelper.updateProgress(builder, (progress * 100).toInt(), notificationId)
                }
            }
            
            if (type == "DOWNLOAD") {
                if (fileId == null) return fail(transferId, "Missing file ID for download")
                val downloadsDir = File(context.getExternalFilesDir(null), "Downloads")
                if (!downloadsDir.exists()) downloadsDir.mkdirs()
                
                val destination = File(downloadsDir, fileName)
                
                val result = provider.downloadFile(account, fileId, destination, progressCallback)
                
                if (result.isSuccess) {
                    transferDao.updateProgress(transferId, 1f, TransferStatus.COMPLETED)
                    notificationHelper.notifyComplete(fileName, notificationId)
                    Result.success()
                } else {
                    fail(transferId, "Download failed: ${result.exceptionOrNull()?.message}", notificationId)
                }
            } else {
                // UPLOAD
                val tempFilePath = inputData.getString("temp_file_path") ?: return fail(transferId, "Missing temp file path")
                val folderId = inputData.getString("folder_id")
                val tempFile = File(tempFilePath)
                
                if (!tempFile.exists()) return fail(transferId, "Temp file not found: $tempFilePath")
                
                val result = provider.uploadFile(account, tempFile, fileName, folderId, progressCallback)
                tempFile.delete() 
                
                if (result.isSuccess) {
                    transferDao.updateProgress(transferId, 1f, TransferStatus.COMPLETED)
                    notificationHelper.notifyComplete(fileName, notificationId)
                    Result.success()
                } else {
                    fail(transferId, "Upload failed: ${result.exceptionOrNull()?.message}", notificationId)
                }
            }
        } catch (e: Exception) {
            Log.e("TransferWorker", "Critical error in transfer $transferId", e)
            fail(transferId, e.message, transferId.hashCode())
        }
    }

    private suspend fun fail(id: String, reason: String? = null, notificationId: Int? = null): Result {
        Log.e("TransferWorker", "Transfer $id failed: $reason")
        transferDao.updateProgress(id, 0f, TransferStatus.FAILED)
        return Result.failure()
    }
}
