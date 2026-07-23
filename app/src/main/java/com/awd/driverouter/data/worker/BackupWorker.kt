package com.awd.driverouter.data.worker

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.awd.driverouter.data.local.BackupDao
import com.awd.driverouter.data.local.CloudFileDao
import com.awd.driverouter.data.local.toDomain
import com.awd.driverouter.domain.manager.AllocationManager
import com.awd.driverouter.domain.repository.CloudRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@HiltWorker
class BackupWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
    private val repository: CloudRepository,
    private val backupDao: BackupDao,
    private val cloudFileDao: CloudFileDao,
    private val allocationManager: AllocationManager
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val activeConfigs = backupDao.getActiveConfigs()
        if (activeConfigs.isEmpty()) return@withContext Result.success()

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

                    val files = rootFolder.listFiles()
                    files.filter { it.isFile }.forEach { file ->
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

                    // 2-Way Sync: Basic implementation (Download new files from cloud)
                    if (config.syncMode == com.awd.driverouter.data.local.SyncMode.TWO_WAY) {
                        repository.getFilesByAccount(cloudParentFolder.id, accountId)
                    }
                }
                
                // Update last backup time
                backupDao.updateConfig(config.copy(lastBackupTime = System.currentTimeMillis()))
                
            } catch (e: Exception) {
                Log.e("BackupWorker", "Backup failed for config ${config.id}", e)
                hasFailure = true
            }
        }
        
        if (hasFailure) Result.retry() else Result.success()
    }
}
