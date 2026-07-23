package com.awd.driverouter.domain.repository

import com.awd.driverouter.domain.model.CloudFile
import com.awd.driverouter.domain.model.SharePermission
import kotlinx.coroutines.flow.Flow

interface CloudRepository {
    fun getAllFiles(folderId: String? = null): Flow<List<CloudFile>>
    
    fun searchFiles(query: String, folderId: String? = null): Flow<List<CloudFile>>

    fun getStarredFiles(): Flow<List<CloudFile>>
    
    fun getRecentFiles(): Flow<List<CloudFile>>
    
    fun getSharedFiles(): Flow<List<CloudFile>>

    fun getTrashedFiles(): Flow<List<CloudFile>>
    
    suspend fun syncFiles(folderId: String? = null): Result<List<CloudFile>>

    suspend fun syncStarred(): Result<List<CloudFile>>
    
    suspend fun syncRecent(): Result<List<CloudFile>>
    
    suspend fun syncShared(): Result<List<CloudFile>>

    suspend fun syncTrash(): Result<List<CloudFile>>

    suspend fun getFilesByAccount(folderId: String?, accountId: String): Result<List<CloudFile>>

    suspend fun syncQuota(): Result<Unit>
    
    suspend fun downloadFile(file: CloudFile): Result<Unit>
    
    suspend fun deleteFile(file: CloudFile): Result<Unit>

    suspend fun createFolder(name: String, parentFolder: CloudFile? = null, accountId: String? = null): Result<CloudFile>
    
    suspend fun uploadFile(uri: android.net.Uri, parentFolder: CloudFile? = null, accountId: String? = null): Result<Unit>

    suspend fun uploadFiles(uris: List<android.net.Uri>, parentFolder: CloudFile? = null, accountId: String? = null): Result<Unit>

    suspend fun uploadFolder(uri: android.net.Uri, parentFolder: CloudFile? = null, accountId: String? = null): Result<Unit>

    suspend fun renameFile(file: CloudFile, newName: String): Result<CloudFile>

    suspend fun updateGeneralAccess(file: CloudFile, isPublic: Boolean): Result<Unit>

    suspend fun getPermissions(file: CloudFile): Result<List<SharePermission>>

    suspend fun getAccountById(accountId: String): com.awd.driverouter.domain.model.CloudAccount?
    
    fun getProviderById(providerId: String): com.awd.driverouter.domain.provider.CloudProvider?
}
