package com.awd.driverouter.domain.provider

import com.awd.driverouter.domain.model.CloudAccount
import com.awd.driverouter.domain.model.CloudFile
import com.awd.driverouter.domain.model.QuotaInfo
import java.io.File

interface CloudProvider {
    val providerId: String
    
    suspend fun listFiles(
        account: CloudAccount, 
        folderId: String? = null,
        onPartialResult: (suspend (List<CloudFile>) -> Unit)? = null
    ): Result<List<CloudFile>>
    
    suspend fun downloadFile(
        account: CloudAccount, 
        fileId: String, 
        destination: File,
        onProgress: ((Float) -> Unit)? = null
    ): Result<Unit>
    
    suspend fun uploadFile(
        account: CloudAccount, 
        source: File, 
        fileName: String, 
        folderId: String? = null,
        onProgress: ((Float) -> Unit)? = null
    ): Result<CloudFile>
    
    suspend fun deleteFile(account: CloudAccount, fileId: String): Result<Unit>
    
    suspend fun renameFile(account: CloudAccount, fileId: String, newName: String): Result<CloudFile>

    suspend fun createFolder(account: CloudAccount, name: String, parentId: String? = null): Result<CloudFile>

    suspend fun getQuota(account: CloudAccount): Result<QuotaInfo>

    // Sharing Methods
    fun supportsSharing(): Boolean = false
    
    suspend fun shareFile(account: CloudAccount, fileId: String, email: String, role: String): Result<Unit> {
        return Result.failure(Exception("Sharing not supported by this provider"))
    }

    suspend fun setGeneralAccess(account: CloudAccount, fileId: String, isPublic: Boolean): Result<Unit> {
        return Result.failure(Exception("General access management not supported"))
    }
    
    suspend fun getShareLink(account: CloudAccount, fileId: String): Result<String> {
        return Result.failure(Exception("Share link generation not supported"))
    }

    // Aggregation Methods
    suspend fun listStarred(
        account: CloudAccount,
        onPartialResult: (suspend (List<CloudFile>) -> Unit)? = null
    ): Result<List<CloudFile>>
    
    suspend fun listRecent(
        account: CloudAccount,
        onPartialResult: (suspend (List<CloudFile>) -> Unit)? = null
    ): Result<List<CloudFile>>
    
    suspend fun listShared(
        account: CloudAccount,
        onPartialResult: (suspend (List<CloudFile>) -> Unit)? = null
    ): Result<List<CloudFile>>

    suspend fun listTrashed(
        account: CloudAccount,
        onPartialResult: (suspend (List<CloudFile>) -> Unit)? = null
    ): Result<List<CloudFile>> = Result.success(emptyList())
}
