package com.awd.driverouter.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.awd.driverouter.data.local.AllocationStrategy
import com.awd.driverouter.data.local.AppLanguage
import com.awd.driverouter.data.local.AppTheme
import com.awd.driverouter.data.local.SettingsManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsManager: SettingsManager
) : ViewModel() {

    val theme: StateFlow<AppTheme> = settingsManager.theme
    val strategy: StateFlow<AllocationStrategy> = settingsManager.strategy
    val language: StateFlow<AppLanguage> = settingsManager.language
    
    val downloadLocationName: StateFlow<String?> = settingsManager.downloadLocationName
    
    val isAppLockEnabled: StateFlow<Boolean> = settingsManager.isAppLockEnabled

    private val _message = kotlinx.coroutines.flow.MutableSharedFlow<Int>()
    val message = _message.asSharedFlow()

    fun setTheme(theme: AppTheme) {
        settingsManager.setTheme(theme)
    }

    fun setStrategy(strategy: AllocationStrategy) {
        settingsManager.setStrategy(strategy)
    }

    fun setLanguage(language: AppLanguage) {
        settingsManager.setLanguage(language)
    }

    fun setAppLockEnabled(enabled: Boolean) {
        settingsManager.setAppLockEnabled(enabled)
        viewModelScope.launch {
            _message.emit(if (enabled) com.awd.driverouter.R.string.app_lock_enabled else com.awd.driverouter.R.string.app_lock_disabled)
        }
    }

    fun setDownloadLocation(uri: android.net.Uri?, name: String?) {
        settingsManager.setDownloadLocation(uri?.toString(), name)
    }
}
