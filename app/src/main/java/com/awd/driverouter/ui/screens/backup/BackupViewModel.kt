package com.awd.driverouter.ui.screens.backup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.*
import com.awd.driverouter.data.local.AllocationStrategy
import com.awd.driverouter.data.local.BackupConfigEntity
import com.awd.driverouter.data.local.BackupDao
import com.awd.driverouter.data.local.SyncMode
import com.awd.driverouter.data.worker.BackupWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltViewModel
class BackupViewModel @Inject constructor(
    @ApplicationContext private val context: android.content.Context,
    private val backupDao: BackupDao
) : ViewModel() {

    private val workManager = WorkManager.getInstance(context)

    val backupConfigs: StateFlow<List<BackupConfigEntity>> = backupDao.getAllConfigs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addBackupConfig(
        localUri: String,
        localName: String,
        cloudName: String,
        strategy: AllocationStrategy,
        syncMode: SyncMode,
        wifiOnly: Boolean,
        intervalMinutes: Int = 60
    ) {
        viewModelScope.launch {
            backupDao.insertConfig(
                BackupConfigEntity(
                    localFolderUri = localUri,
                    localFolderName = localName,
                    cloudFolderName = cloudName,
                    strategy = strategy,
                    syncMode = syncMode,
                    wifiOnly = wifiOnly,
                    syncIntervalMinutes = intervalMinutes
                )
            )
            scheduleBackupIfNeeded()
        }
    }

    fun updateConfig(config: BackupConfigEntity) {
        viewModelScope.launch {
            backupDao.updateConfig(config)
            scheduleBackupIfNeeded()
        }
    }

    fun deleteConfig(config: BackupConfigEntity) {
        viewModelScope.launch {
            backupDao.deleteConfig(config)
            scheduleBackupIfNeeded()
        }
    }

    fun toggleConfig(config: BackupConfigEntity, isEnabled: Boolean) {
        viewModelScope.launch {
            backupDao.updateConfig(config.copy(isEnabled = isEnabled))
            scheduleBackupIfNeeded()
        }
    }

    fun syncNow(config: BackupConfigEntity) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(if (config.wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
            .build()

        val syncRequest = OneTimeWorkRequestBuilder<BackupWorker>()
            .setConstraints(constraints)
            .addTag("manual_backup_${config.id}")
            .build()

        workManager.enqueueUniqueWork(
            "manual_backup_${config.id}",
            ExistingWorkPolicy.REPLACE,
            syncRequest
        )
    }

    private suspend fun scheduleBackupIfNeeded() {
        val allConfigs = backupDao.getAllConfigs().stateIn(viewModelScope).value 
        
        allConfigs.forEach { config ->
            val workName = "auto_backup_${config.id}"
            if (config.isEnabled) {
                val constraints = Constraints.Builder()
                    .setRequiredNetworkType(if (config.wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
                    .build()

                // WorkManager minimum interval is 15 minutes
                val interval = config.syncIntervalMinutes.coerceAtLeast(15).toLong()

                val backupRequest = PeriodicWorkRequestBuilder<BackupWorker>(interval, TimeUnit.MINUTES)
                    .setConstraints(constraints)
                    .addTag(workName)
                    .build()

                workManager.enqueueUniquePeriodicWork(
                    workName,
                    ExistingPeriodicWorkPolicy.UPDATE, // Use UPDATE to preserve state if possible
                    backupRequest
                )
            } else {
                workManager.cancelUniqueWork(workName)
            }
        }
        
        // Also cancel the legacy global "auto_backup" if it exists
        workManager.cancelUniqueWork("auto_backup")
    }
}
