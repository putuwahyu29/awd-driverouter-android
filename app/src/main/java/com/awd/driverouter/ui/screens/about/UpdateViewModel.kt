package com.awd.driverouter.ui.screens.about

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.awd.driverouter.data.remote.GitHubRelease
import com.awd.driverouter.data.remote.GitHubUpdateService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface UpdateUiState {
    object Idle : UpdateUiState
    object Checking : UpdateUiState
    data class NewVersionAvailable(val release: GitHubRelease) : UpdateUiState
    object UpToDate : UpdateUiState
    data class Error(val message: String) : UpdateUiState
}

@HiltViewModel
class UpdateViewModel @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
    private val gitHubService: GitHubUpdateService
) : ViewModel() {

    private val _uiState = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)
    val uiState: StateFlow<UpdateUiState> = _uiState.asStateFlow()

    fun checkForUpdates(currentVersion: String) {
        viewModelScope.launch {
            _uiState.value = UpdateUiState.Checking
            try {
                val latest = gitHubService.getLatestRelease()
                val latestTag = latest.tag_name.replace("v", "")
                
                if (isNewer(latestTag, currentVersion)) {
                    _uiState.value = UpdateUiState.NewVersionAvailable(latest)
                } else {
                    _uiState.value = UpdateUiState.UpToDate
                }
            } catch (e: Exception) {
                _uiState.value = UpdateUiState.Error(
                    if (e is java.net.UnknownHostException) context.getString(com.awd.driverouter.R.string.no_internet) 
                    else e.message ?: context.getString(com.awd.driverouter.R.string.error_check_update)
                )
            }
        }
    }

    private fun isNewer(latest: String, current: String): Boolean {
        val latestParts = latest.split(".").map { it.toIntOrNull() ?: 0 }
        val currentParts = current.split(".").map { it.toIntOrNull() ?: 0 }
        
        for (i in 0 until minOf(latestParts.size, currentParts.size)) {
            if (latestParts[i] > currentParts[i]) return true
            if (latestParts[i] < currentParts[i]) return false
        }
        return latestParts.size > currentParts.size
    }

    fun resetState() {
        _uiState.value = UpdateUiState.Idle
    }
}
