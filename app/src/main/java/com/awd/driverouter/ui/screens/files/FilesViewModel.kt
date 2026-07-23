package com.awd.driverouter.ui.screens.files

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.awd.driverouter.R
import com.awd.driverouter.domain.model.CloudFile
import com.awd.driverouter.domain.model.SharePermission
import com.awd.driverouter.domain.model.Transfer
import com.awd.driverouter.domain.repository.CloudRepository
import com.awd.driverouter.domain.repository.TransferRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class FolderInfo(val id: String?, val name: String, val cloudFile: CloudFile? = null)

enum class FileCategory { ALL, IMAGE, VIDEO, DOCUMENT, AUDIO }
enum class SortOrder { NAME, DATE, SIZE }
enum class SearchScope { ALL, CURRENT_FOLDER }

@HiltViewModel
class FilesViewModel @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
    private val repository: CloudRepository,
    private val transferRepository: TransferRepository
) : ViewModel() {

    private val _rawFilesState = MutableStateFlow<FilesUiState>(FilesUiState.Loading)
    
    private val _categoryFilter = MutableStateFlow(FileCategory.ALL)
    val categoryFilter: StateFlow<FileCategory> = _categoryFilter.asStateFlow()

    private val _sortOrder = MutableStateFlow(SortOrder.NAME)
    val sortOrder: StateFlow<SortOrder> = _sortOrder.asStateFlow()

    private val _selectedAccountId = MutableStateFlow<String?>(null)
    val selectedAccountId: StateFlow<String?> = _selectedAccountId.asStateFlow()

    val uiState: StateFlow<FilesUiState> = combine(
        _rawFilesState, 
        _categoryFilter, 
        _sortOrder,
        _selectedAccountId
    ) { state, category, sort, selectedAccId ->
        if (state is FilesUiState.Success) {
            var filtered = state.files

            // Filter by Account
            if (selectedAccId != null) {
                filtered = filtered.filter { it.accountId == selectedAccId }
            }

            // Filter by Category
            filtered = when (category) {
                FileCategory.ALL -> filtered
                FileCategory.IMAGE -> filtered.filter { it.mimeType.startsWith("image/") }
                FileCategory.VIDEO -> filtered.filter { it.mimeType.startsWith("video/") }
                FileCategory.AUDIO -> filtered.filter { it.mimeType.startsWith("audio/") }
                FileCategory.DOCUMENT -> filtered.filter { 
                    val mime = it.mimeType.lowercase()
                    mime.contains("pdf") || mime.contains("word") || mime.contains("spreadsheet") || 
                    mime.contains("presentation") || mime.contains("text/") || mime.endsWith(".doc") || mime.endsWith(".docx")
                }
            }

            // Apply Sorting
            filtered = when (sort) {
                SortOrder.NAME -> filtered.sortedBy { it.name.lowercase() }
                SortOrder.DATE -> filtered.sortedByDescending { it.modifiedTime ?: 0L }
                SortOrder.SIZE -> filtered.sortedByDescending { it.size ?: 0L }
            }
            FilesUiState.Success(filtered)
        } else {
            state
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), FilesUiState.Loading)

    private val _currentFolderId = MutableStateFlow<String?>(null)
    val currentFolderId: StateFlow<String?> = _currentFolderId.asStateFlow()

    private val _folderPath = MutableStateFlow(listOf(FolderInfo(null, context.getString(R.string.nav_home))))
    val folderPath: StateFlow<List<FolderInfo>> = _folderPath.asStateFlow()


    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedFileIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedFileIds: StateFlow<Set<String>> = _selectedFileIds.asStateFlow()

    private val _searchScope = MutableStateFlow(SearchScope.ALL)
    val searchScope: StateFlow<SearchScope> = _searchScope.asStateFlow()

    private val _errorEvent = MutableSharedFlow<String>()
    val errorEvent: SharedFlow<String> = _errorEvent.asSharedFlow()

    private val _permissions = MutableStateFlow<List<SharePermission>>(emptyList())
    val permissions: StateFlow<List<SharePermission>> = _permissions.asStateFlow()

    private val _isPermissionsLoading = MutableStateFlow(false)
    val isPermissionsLoading: StateFlow<Boolean> = _isPermissionsLoading.asStateFlow()

    private var loadJob: Job? = null
    private var syncJob: Job? = null
    private var currentMode: String = "files"

    init {
        // Initial load for files mode
        loadFiles(null, context.getString(R.string.nav_home))
    }

    fun selectAccount(accountId: String?) {
        _selectedAccountId.value = accountId
    }

    fun setMode(mode: String) {
        if (currentMode == mode && mode != "files") return
        currentMode = mode
        
        _selectedAccountId.value = null // Reset filter when mode changes
        
        // Reset breadcrumbs based on mode
        val rootTitle = when (mode) {
            "starred" -> context.getString(R.string.nav_starred)
            "shared" -> context.getString(R.string.nav_shared)
            "trash" -> context.getString(R.string.nav_trash)
            else -> context.getString(R.string.nav_home)
        }
        _folderPath.value = listOf(FolderInfo(null, rootTitle))
        
        if (mode == "files") {
            loadFiles(null, context.getString(R.string.nav_home))
        } else {
            loadSpecialList(mode)
        }
    }

    private fun loadSpecialList(mode: String) {

        loadJob?.cancel()
        _rawFilesState.value = FilesUiState.Loading
        loadJob = viewModelScope.launch {
            val flow = when (mode) {
                "recent" -> repository.getRecentFiles()
                "starred" -> repository.getStarredFiles()
                "shared" -> repository.getSharedFiles()
                "trash" -> repository.getTrashedFiles()
                else -> emptyFlow()
            }
            flow.collect { files ->
                _rawFilesState.value = FilesUiState.Success(files)
            }
        }
        refreshSpecial(mode)
    }

    private fun refreshSpecial(mode: String) {
        syncJob?.cancel()
        syncJob = viewModelScope.launch {
            _isRefreshing.value = true
            try {
                val result = when (mode) {
                    "recent" -> repository.syncRecent()
                    "starred" -> repository.syncStarred()
                    "shared" -> repository.syncShared()
                    "trash" -> repository.syncTrash()
                    else -> Result.success(emptyList())
                }
                result.onFailure { _errorEvent.emit(it.message ?: "Sync error") }
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun loadFiles(folderId: String?, folderName: String) {
        loadJob?.cancel()
        _currentFolderId.value = folderId
        _searchQuery.value = ""
        loadJob = viewModelScope.launch {
            repository.getAllFiles(folderId).collect { files ->
                _rawFilesState.value = FilesUiState.Success(files)
            }
        }
        refresh()
    }

    fun refresh() {
        if (currentMode != "files") {
            refreshSpecial(currentMode)
            return
        }
        if (syncJob?.isActive == true) return
        val folderId = _currentFolderId.value
        syncJob = viewModelScope.launch {
            _isRefreshing.value = true
            val currentState = _rawFilesState.value
            val isEmpty = currentState !is FilesUiState.Success || currentState.files.isEmpty()
            
            if (isEmpty) {
                _rawFilesState.value = FilesUiState.Loading
            }
            
            try {
                repository.syncFiles(folderId).onFailure { error ->
                    _errorEvent.emit(context.getString(R.string.sync_failed, error.message))
                    if (_rawFilesState.value is FilesUiState.Loading) {
                        _rawFilesState.value = FilesUiState.Error(error.message ?: context.getString(R.string.error))
                    }
                }
            } finally {
                _isRefreshing.value = false
                // Ensure we transition out of loading if we are still there and sync finished
                if (_rawFilesState.value is FilesUiState.Loading) {
                    // Fetch from local one last time to be sure
                    repository.getAllFiles(folderId).first().let { 
                        _rawFilesState.value = FilesUiState.Success(it)
                    }
                }
            }
        }
    }

    fun onSearchQueryChange(query: String) {
        val wasEmpty = _searchQuery.value.isEmpty()
        _searchQuery.value = query
        loadJob?.cancel() // Always cancel previous job when query changes
        
        if (query.isEmpty()) {
            _searchScope.value = SearchScope.ALL // Reset scope when clearing search
            if (currentMode == "files") {
                val lastFolder = _folderPath.value.last()
                // Reload current folder without resetting the path
                loadJob = viewModelScope.launch {
                    repository.getAllFiles(lastFolder.id).collect { files ->
                        _rawFilesState.value = FilesUiState.Success(files)
                    }
                }
            } else {
                // Restore special mode list
                loadJob = viewModelScope.launch {
                    val flow = when (currentMode) {
                        "recent" -> repository.getRecentFiles()
                        "starred" -> repository.getStarredFiles()
                        "shared" -> repository.getSharedFiles()
                        "trash" -> repository.getTrashedFiles()
                        else -> emptyFlow()
                    }
                    flow.collect { files ->
                        _rawFilesState.value = FilesUiState.Success(files)
                    }
                }
            }
        } else {
            // If just started searching and in a subfolder, default to current folder scope
            if (wasEmpty && _folderPath.value.size > 1) {
                _searchScope.value = SearchScope.CURRENT_FOLDER
            }

            _rawFilesState.value = FilesUiState.Loading
            loadJob = viewModelScope.launch {
                // Add a small delay for debouncing search
                kotlinx.coroutines.delay(300)
                
                val folderId = if (_searchScope.value == SearchScope.CURRENT_FOLDER) {
                    _folderPath.value.lastOrNull()?.id
                } else null
                
                repository.searchFiles(query, folderId).collect { files ->
                    _rawFilesState.value = FilesUiState.Success(files)
                }
            }
        }
    }

    fun setSearchScope(scope: SearchScope) {
        _searchScope.value = scope
        if (_searchQuery.value.isNotEmpty()) {
            onSearchQueryChange(_searchQuery.value)
        }
    }

    fun navigateToFolder(folder: CloudFile) {
        if (folder.isFolder) {
            currentMode = "files" // Switch to files mode for subfolders
            val newPath = _folderPath.value.toMutableList().apply {
                add(FolderInfo(folder.id, folder.name, folder))
            }
            _folderPath.value = newPath
            loadFiles(folder.id, folder.name)
        }
    }

    fun navigateBack(): Boolean {
        if (_folderPath.value.size > 1) {
            val newPath = _folderPath.value.toMutableList().apply { removeAt(size - 1) }
            _folderPath.value = newPath
            val last = newPath.last()
            loadFiles(last.id, last.name)
            return true
        }
        return false
    }

    fun navigateToBreadcrumb(index: Int) {
        val newPath = _folderPath.value.take(index + 1)
        _folderPath.value = newPath
        val last = newPath.last()
        loadFiles(last.id, last.name)
    }

    fun downloadFile(file: CloudFile) {
        viewModelScope.launch { repository.downloadFile(file) }
    }

    fun getTransferProgress(fileId: String): Flow<Transfer?> {
        return transferRepository.getTransferByFileId(fileId)
    }

    fun getLocalFile(file: CloudFile): File? {
        val downloadsDir = File(context.getExternalFilesDir(null), "Downloads")
        val localFile = File(downloadsDir, file.name)
        return if (localFile.exists()) localFile else null
    }

    fun deleteFile(file: CloudFile) {
        viewModelScope.launch {
            repository.deleteFile(file).onSuccess {
                _errorEvent.emit(context.getString(R.string.file_deleted))
                refresh()
            }.onFailure {
                _errorEvent.emit(context.getString(R.string.delete_failed, it.message))
            }
        }
    }

    fun renameFile(file: CloudFile, newName: String) {
        viewModelScope.launch {
            repository.renameFile(file, newName).onSuccess {
                _errorEvent.emit(context.getString(R.string.file_renamed))
                refresh()
            }.onFailure {
                _errorEvent.emit(it.message ?: "Rename failed")
            }
        }
    }

    fun createFolder(name: String) {
        viewModelScope.launch {
            val parentFolder = _folderPath.value.last().cloudFile
            repository.createFolder(name, parentFolder).onSuccess {
                _errorEvent.emit(context.getString(R.string.folder_created))
                refresh()
            }.onFailure {
                _errorEvent.emit(context.getString(R.string.folder_create_failed, it.message))
            }
        }
    }

    fun uploadFile(uri: Uri) {
        uploadFiles(listOf(uri))
    }

    fun uploadFiles(uris: List<Uri>) {
        viewModelScope.launch {
            try {
                val parentFolder = _folderPath.value.last().cloudFile
                repository.uploadFiles(uris, parentFolder).onSuccess {
                    _errorEvent.emit(context.getString(R.string.upload_started))
                }.onFailure {
                    _errorEvent.emit(it.message ?: "Upload failed to start")
                }
            } catch (e: Throwable) {
                _errorEvent.emit("System error starting upload: ${e.javaClass.simpleName}")
            }
        }
    }

    fun uploadFolder(uri: Uri) {
        viewModelScope.launch {
            try {
                val parentFolder = _folderPath.value.last().cloudFile
                repository.uploadFolder(uri, parentFolder).onSuccess {
                    _errorEvent.emit(context.getString(R.string.upload_started))
                }.onFailure {
                    _errorEvent.emit(it.message ?: "Upload failed to start")
                }
            } catch (e: Throwable) {
                _errorEvent.emit("System error starting upload: ${e.javaClass.simpleName}")
            }
        }
    }

    fun toggleSelection(fileId: String) {
        val current = _selectedFileIds.value
        if (current.contains(fileId)) {
            _selectedFileIds.value = current - fileId
        } else {
            _selectedFileIds.value = current + fileId
        }
    }

    fun clearSelection() {
        _selectedFileIds.value = emptySet()
    }

    fun deleteSelectedFiles() {
        val ids = _selectedFileIds.value.toList()
        if (ids.isEmpty()) return
        
        viewModelScope.launch {
            _rawFilesState.value.let { state ->
                if (state is FilesUiState.Success) {
                    val filesToDelete = state.files.filter { ids.contains(it.id) }
                    filesToDelete.forEach { file ->
                        repository.deleteFile(file)
                    }
                    _errorEvent.emit(context.getString(R.string.files_deleted_count, filesToDelete.size))
                    clearSelection()
                    refresh()
                }
            }
        }
    }

    fun shareFile(file: CloudFile, email: String, role: String) {
        viewModelScope.launch {
            val account = repository.getAccountById(file.accountId) ?: return@launch
            val provider = repository.getProviderById(file.provider) ?: return@launch
            
            provider.shareFile(account, file.id, email, role).onSuccess {
                _errorEvent.emit(context.getString(R.string.share_success, email))
                refresh()
            }.onFailure {
                _errorEvent.emit(context.getString(R.string.share_failed, it.message))
            }
        }
    }

    fun setGeneralAccess(file: CloudFile, isPublic: Boolean) {
        viewModelScope.launch {
            repository.updateGeneralAccess(file, isPublic).onSuccess {
                refresh()
                loadPermissions(file)
            }.onFailure {
                _errorEvent.emit(it.message ?: "Gagal mengubah akses umum")
            }
        }
    }

    fun loadPermissions(file: CloudFile) {
        viewModelScope.launch {
            _isPermissionsLoading.value = true
            repository.getPermissions(file).onSuccess {
                _permissions.value = it
            }.onFailure {
                _errorEvent.emit(it.message ?: "Gagal memuat izin")
            }
            _isPermissionsLoading.value = false
        }
    }

    fun setCategoryFilter(category: FileCategory) {
        _categoryFilter.value = category
    }

    fun setSortOrder(order: SortOrder) {
        _sortOrder.value = order
    }
}

sealed interface FilesUiState {
    object Loading : FilesUiState
    data class Success(val files: List<CloudFile>) : FilesUiState
    data class Error(val message: String) : FilesUiState
}
