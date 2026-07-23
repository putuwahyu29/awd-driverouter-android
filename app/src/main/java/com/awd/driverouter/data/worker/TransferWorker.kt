package com.awd.driverouter.data.worker

import android.content.ContentValues
import android.content.Context
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.awd.driverouter.R
import com.awd.driverouter.data.local.AccountDao
import com.awd.driverouter.data.local.SettingsManager
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
    private val settingsManager: SettingsManager,
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
        val mimeType = inputData.getString("mime_type") ?: "application/octet-stream"

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
            setForeground(
                ForegroundInfo(
                    notificationId,
                    builder.build(),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            )

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
                val expectedSize = inputData.getLong("expected_size", 0L)
                
                val customUriStr = settingsManager.downloadLocationUri.value
                val tempDir = File(context.cacheDir, "temp_downloads")
                if (!tempDir.exists()) tempDir.mkdirs()
                val tempFile = File(tempDir, fileName)
                
                val result = provider.downloadFile(account, fileId, tempFile, progressCallback)
                
                if (result.isSuccess) {
                    // Integrity Check: Verify file size
                    val actualSize = tempFile.length()
                    if (expectedSize > 0 && actualSize != expectedSize) {
                        tempFile.delete() // Clean up corrupted file
                        return fail(transferId, "Integrity check failed: expected $expectedSize, got $actualSize", notificationId)
                    }
                    
                    if (customUriStr != null) {
                        // Save to Custom SAF Folder
                        try {
                            val treeUri = Uri.parse(customUriStr)
                            val pickedDir = DocumentFile.fromTreeUri(context, treeUri)
                            val newFile = pickedDir?.createFile(mimeType, fileName)
                            
                            if (newFile != null) {
                                context.contentResolver.openOutputStream(newFile.uri)?.use { output ->
                                    tempFile.inputStream().use { input ->
                                        input.copyTo(output)
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("TransferWorker", "Failed to copy to SAF", e)
                        } finally {
                            tempFile.delete()
                        }
                    } else {
                        // Default to Public Downloads
                        try {
                            saveToPublicDownloads(tempFile, fileName, mimeType)
                        } catch (e: Exception) {
                            Log.e("TransferWorker", "Failed to save to public downloads", e)
                        } finally {
                            tempFile.delete()
                        }
                    }
                    
                    transferDao.updateProgress(transferId, 1f, TransferStatus.COMPLETED)
                    notificationHelper.notifyComplete(fileName, notificationId)
                    Result.success()
                } else {
                    if (tempFile.exists()) tempFile.delete()
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
        } catch (e: Throwable) {
            Log.e("TransferWorker", "CRITICAL ERROR in transfer $transferId", e)
            fail(transferId, "System Error: ${e.message}", transferId.hashCode())
        }
    }

    private fun saveToPublicDownloads(source: File, name: String, mime: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, mime)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            
            val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val uri = context.contentResolver.insert(collection, values)
            
            if (uri != null) {
                context.contentResolver.openOutputStream(uri)?.use { output ->
                    source.inputStream().use { input ->
                        input.copyTo(output)
                    }
                }
                
                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                context.contentResolver.update(uri, values, null, null)
            }
        } else {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!downloadsDir.exists()) downloadsDir.mkdirs()
            val destFile = File(downloadsDir, name)
            source.copyTo(destFile, overwrite = true)
        }
    }

    private suspend fun fail(id: String, reason: String? = null, notificationId: Int? = null): Result {
        Log.e("TransferWorker", "Transfer $id failed: $reason")
        transferDao.updateProgress(id, 0f, TransferStatus.FAILED)
        return Result.failure()
    }
}
