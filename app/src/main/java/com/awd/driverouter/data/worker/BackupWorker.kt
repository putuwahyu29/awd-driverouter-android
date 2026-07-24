package com.awd.driverouter.data.worker

import android.content.Context
import android.content.pm.ServiceInfo
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.awd.driverouter.R
import com.awd.driverouter.data.local.BackupDao
import com.awd.driverouter.data.local.CloudFileDao
import com.awd.driverouter.data.local.toDomain
import com.awd.driverouter.domain.manager.AllocationManager
import com.awd.driverouter.domain.repository.CloudRepository
import com.awd.driverouter.util.NotificationHelper
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@HiltWorker
class BackupWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
    private val repository: CloudRepository,
    private val transferRepository: com.awd.driverouter.domain.repository.TransferRepository,
    private val backupDao: BackupDao,
    private val cloudFileDao: CloudFileDao,
    private val allocationManager: AllocationManager
) : CoroutineWorker(context, params) {

    private val notificationHelper = NotificationHelper(context)

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val activeConfigs = backupDao.getActiveConfigs()
        if (activeConfigs.isEmpty()) return@withContext Result.success()

        notificationHelper.createNotificationChannel()
        val notificationId = "backup_root".hashCode()
        val builder = notificationHelper.getBackupNotificationBuilder(
            context.getString(R.string.backup_in_progress),
            context.getString(R.string.processing)
        )

        setForeground(
            ForegroundInfo(
                notificationId,
                builder.build(),
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                } else 0
            )
        )

        var hasFailure = false

        activeConfigs.forEach { config ->
            try {
                val folderUri = Uri.parse(config.localFolderUri)
                val rootFolder = DocumentFile.fromTreeUri(context, folderUri) 
                if (rootFolder == null || !rootFolder.exists()) {
                    Log.e("BackupWorker", "Local folder not found: ${config.localFolderUri}")
                    hasFailure = true
                    return@forEach
                }

                // Get target accounts based on strategy
                val targetAccountIds = allocationManager.getTargetAccountIds(config.strategy)
                if (targetAccountIds.isEmpty()) {
                    Log.e("BackupWorker", "No target accounts found for strategy: ${config.strategy}")
                    hasFailure = true
                    return@forEach
                }

                // For each account, ensure cloud folder exists and upload files
                targetAccountIds.forEach { accountId ->
                    // 1. Ensure Cloud Metadata is up to date for this folder to prevent duplicates
                    repository.getFilesByAccount(null, accountId) // Sync root to find/create backup folder
                    
                    val existingFolder = cloudFileDao.getFileByNameInFolder(config.cloudFolderName, null, accountId)
                    val cloudParentFolder = if (existingFolder != null) {
                        existingFolder.toDomain()
                    } else {
                        val createResult = repository.createFolder(config.cloudFolderName, null, accountId)
                        createResult.getOrNull()
                    }
                    
                    if (cloudParentFolder == null) {
                        Log.e("BackupWorker", "Failed to ensure cloud folder for account $accountId")
                        hasFailure = true
                        return@forEach
                    }

                    // 2. Sync metadata FOR the backup folder specifically
                    repository.getFilesByAccount(cloudParentFolder.id, accountId)

                    val localFiles = rootFolder.listFiles()
                    
                    // UPLOAD: Local -> Cloud
                    localFiles.filter { it.isFile }.forEach { file ->
                        val existingFile = cloudFileDao.getFileByNameInFolder(file.name ?: "", cloudParentFolder.id, accountId)
                        
                        // Check if upload is needed: if not exists OR size changed
                        val shouldUpload = existingFile == null || (file.length() > 0 && existingFile.size != file.length())
                        
                        if (shouldUpload) {
                            Log.d("BackupWorker", "Uploading ${file.name} to account $accountId")
                            repository.uploadFile(file.uri, cloudParentFolder, accountId)
                        } else {
                            Log.d("BackupWorker", "Skipping ${file.name}, already exists in account $accountId")
                        }
                    }

                    // 3. 2-Way Sync: Cloud -> Local
                    if (config.syncMode == com.awd.driverouter.data.local.SyncMode.TWO_WAY) {
                        val cloudFilesResult = repository.getFilesByAccount(cloudParentFolder.id, accountId)
                        cloudFilesResult.onSuccess { cloudFiles ->
                            cloudFiles.filter { !it.isFolder }.forEach { cloudFile ->
                                val localFile = localFiles.find { it.name == cloudFile.name }
                                
                                // Download if missing locally OR size mismatch
                                val shouldDownload = localFile == null || ((cloudFile.size ?: 0L) > 0 && localFile.length() != cloudFile.size)
                                
                                if (shouldDownload) {
                                    Log.d("BackupWorker", "2-Way Sync: Downloading ${cloudFile.name} to local folder")
                                    transferRepository.startDownload(cloudFile, config.localFolderUri)
                                }
                            }
                        }
                    }
                }
                
                // Update last backup time
                backupDao.updateConfig(config.copy(lastBackupTime = System.currentTimeMillis()))
                
            } catch (e: Exception) {
                Log.e("BackupWorker", "Backup failed for config ${config.id}", e)
                hasFailure = true
            }
        }
        
        // Show completion notification if it was a manual sync or batch
        if (!hasFailure) {
            notificationHelper.notifyComplete(context.getString(R.string.backup_complete), notificationId + 1)
        } else {
            notificationHelper.notifyFailed(context.getString(R.string.backup_failed), null, notificationId + 2)
        }

        if (hasFailure) Result.retry() else Result.success()
    }
}
