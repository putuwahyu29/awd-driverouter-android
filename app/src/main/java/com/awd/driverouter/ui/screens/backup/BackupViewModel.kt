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
        wifiOnly: Boolean
    ) {
        viewModelScope.launch {
            backupDao.insertConfig(
                BackupConfigEntity(
                    localFolderUri = localUri,
                    localFolderName = localName,
                    cloudFolderName = cloudName,
                    strategy = strategy,
                    syncMode = syncMode,
                    wifiOnly = wifiOnly
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

    private suspend fun scheduleBackupIfNeeded() {
        val activeConfigs = backupDao.getActiveConfigs()
        if (activeConfigs.isNotEmpty()) {
            // Simplified scheduling: if ANY backup is enabled, run the worker.
            // In a more complex app, we'd have different constraints per config.
            val anyWifiOnly = activeConfigs.any { it.wifiOnly }
            
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(if (anyWifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
                .build()

            val backupRequest = PeriodicWorkRequestBuilder<BackupWorker>(1, TimeUnit.HOURS)
                .setConstraints(constraints)
                .addTag("auto_backup")
                .build()

            workManager.enqueueUniquePeriodicWork(
                "auto_backup",
                ExistingPeriodicWorkPolicy.REPLACE,
                backupRequest
            )
        } else {
            workManager.cancelUniqueWork("auto_backup")
        }
    }
}
