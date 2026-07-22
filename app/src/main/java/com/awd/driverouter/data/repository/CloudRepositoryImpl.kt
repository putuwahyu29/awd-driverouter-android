package com.awd.driverouter.data.repository

import android.net.Uri
import android.util.Log
import com.awd.driverouter.data.local.*
import com.awd.driverouter.domain.manager.AllocationManager
import com.awd.driverouter.domain.model.CloudAccount
import com.awd.driverouter.domain.model.CloudFile
import com.awd.driverouter.domain.provider.CloudProvider
import com.awd.driverouter.domain.repository.CloudRepository
import com.awd.driverouter.domain.repository.TransferRepository
import androidx.documentfile.provider.DocumentFile
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class CloudRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: android.content.Context,
    private val dao: CloudFileDao,
    private val accountDao: AccountDao,
    private val allocationManager: AllocationManager,
    private val transferRepository: TransferRepository,
    private val providers: List<@JvmSuppressWildcards CloudProvider>
) : CloudRepository {

    override fun getAllFiles(folderId: String?): Flow<List<CloudFile>> {
        return dao.getFilesByFolderWithAccount(folderId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun searchFiles(query: String): Flow<List<CloudFile>> {
        return dao.searchFilesWithAccount(query).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getStarredFiles(): Flow<List<CloudFile>> {
        return dao.getStarredFilesWithAccount().map { entities -> entities.map { it.toDomain() } }
    }

    override fun getRecentFiles(): Flow<List<CloudFile>> {
        return dao.getRecentFilesWithAccount().map { entities -> entities.map { it.toDomain() } }
    }

    override fun getSharedFiles(): Flow<List<CloudFile>> {
        return dao.getSharedFilesWithAccount().map { entities -> entities.map { it.toDomain() } }
    }

    override fun getTrashedFiles(): Flow<List<CloudFile>> {
        return dao.getTrashedFilesWithAccount().map { entities -> entities.map { it.toDomain() } }
    }

    override suspend fun syncFiles(folderId: String?): Result<List<CloudFile>> = syncAggregation(overriddenParentId = folderId, mode = "files") { provider, account, onPartial ->
        provider.listFiles(account, folderId, onPartial)
    }

    override suspend fun syncStarred(): Result<List<CloudFile>> = syncAggregation(mode = "starred") { provider, account, onPartial ->
        provider.listStarred(account, onPartial)
    }

    override suspend fun syncRecent(): Result<List<CloudFile>> = syncAggregation(mode = "recent") { provider, account, onPartial ->
        provider.listRecent(account, onPartial)
    }

    override suspend fun syncShared(): Result<List<CloudFile>> = syncAggregation(mode = "shared") { provider, account, onPartial ->
        provider.listShared(account, onPartial)
    }

    override suspend fun syncTrash(): Result<List<CloudFile>> = syncAggregation(mode = "trash") { provider, account, onPartial ->
        provider.listTrashed(account, onPartial)
    }

    override suspend fun getFilesByAccount(folderId: String?, accountId: String): Result<List<CloudFile>> = withContext(Dispatchers.IO) {
        try {
            val accountEntity = accountDao.getAllAccounts().first().find { it.id == accountId }
                ?: return@withContext Result.failure(Exception("Account not found"))
            val account = accountEntity.toDomain()
            val provider = providers.find { it.providerId == account.provider }
                ?: return@withContext Result.failure(Exception("Provider not found"))
            
            val result = provider.listFiles(account, folderId) { files ->
                // Partial insert if needed
                dao.insertFiles(files.map { it.toEntity() })
            }
            result.onSuccess { files ->
                dao.insertFiles(files.map { it.toEntity() })
            }
            result
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun syncQuota(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val accounts = accountDao.getAllAccounts().first()
            accounts.map { accountEntity ->
                async {
                    val account = accountEntity.toDomain()
                    val provider = providers.find { it.providerId == account.provider }
                    try {
                        withTimeoutOrNull(15000L) { // 15s timeout for quota
                            provider?.getQuota(account)?.onSuccess { quota ->
                                accountDao.insertAccount(accountEntity.copy(
                                    usedSpace = quota.usedSpace,
                                    totalSpace = quota.totalSpace
                                ))
                            }
                        }
                    } catch (e: Exception) {
                        Log.w("CloudRepository", "Quota sync failed for ${account.email}: ${e.message}")
                    }
                }
            }.awaitAll()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun syncAggregation(
        overriddenParentId: String? = null,
        mode: String = "files",
        fetcher: suspend (CloudProvider, CloudAccount, onPartial: suspend (List<CloudFile>) -> Unit) -> Result<List<CloudFile>>
    ): Result<List<CloudFile>> = withContext(Dispatchers.IO) {
        try {
            var accounts = accountDao.getAllAccounts().first()
            if (accounts.isEmpty()) return@withContext Result.success(emptyList())

            if (overriddenParentId != null && mode == "files") {
                val parentFile = dao.getFileById(overriddenParentId)
                if (parentFile != null) {
                    accounts = accounts.filter { it.id == parentFile.accountId }
                }
            }

            val results = accounts.map { accountEntity ->
                async {
                    val account = accountEntity.toDomain()
                    val provider = providers.find { it.providerId == account.provider }
                    if (provider != null) {
                        try {
                            var hasClearedCache = false
                            
                            val files = withTimeoutOrNull(45000L) { // Increased timeout for multi-page sync
                                val fetchedResult = fetcher(provider, account) { partialFiles ->
                                    // Incremental Insertion - make it synchronous with the fetching process
                                    if (!hasClearedCache) {
                                        // Reset flags or clear folder once before first batch
                                        when (mode) {
                                            "starred" -> dao.resetStarredStatus(account.id)
                                            "shared" -> dao.resetSharedStatus(account.id)
                                            "trash" -> dao.resetTrashedStatus(account.id)
                                        }
                                        if (mode == "files") {
                                            dao.deleteFilesByAccountAndFolder(overriddenParentId, account.id)
                                        }
                                        hasClearedCache = true
                                    }

                                    if (partialFiles.isNotEmpty()) {
                                        val entities = if (mode == "files") {
                                            partialFiles.map { it.toEntity().copy(parentId = overriddenParentId) }
                                        } else {
                                            partialFiles.map { it.toEntity() }
                                        }
                                        // Synchronous call as the lambda is now suspend
                                        dao.insertFiles(entities)
                                    }
                                }
                                
                                if (fetchedResult.isSuccess) {
                                    fetchedResult.getOrDefault(emptyList())
                                } else {
                                    Log.e("CloudRepository", "Fetch failed for ${account.email}: ${fetchedResult.exceptionOrNull()?.message}")
                                    null 
                                }
                            }
                            
                            if (files == null) {
                                Log.e("CloudRepository", "Sync failed or timeout for ${account.email} (${account.provider})")
                            }
                            
                            files ?: emptyList<CloudFile>()
                        } catch (e: Exception) {
                            Log.e("CloudRepository", "Sync error for ${account.email}: ${e.message}")
                            emptyList<CloudFile>()
                        }
                    } else emptyList<CloudFile>()
                }
            }.awaitAll()
            Result.success(results.flatten())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun createFolder(name: String, parentFolder: CloudFile?, accountId: String?): Result<CloudFile> = withContext(Dispatchers.IO) {
        try {
            val account: CloudAccount
            val provider: CloudProvider
            
            if (accountId != null) {
                val accounts = accountDao.getAllAccounts().first()
                val accountEntity = accounts.find { it.id == accountId }
                    ?: return@withContext Result.failure(Exception("Account $accountId not found"))
                account = accountEntity.toDomain()
                provider = providers.find { it.providerId == account.provider }
                    ?: return@withContext Result.failure(Exception("Provider not found for account $accountId"))
            } else if (parentFolder == null) {
                provider = allocationManager.getBestProvider() ?: return@withContext Result.failure(Exception("No provider available"))
                val accounts = accountDao.getAllAccounts().first()
                val accountEntity = accounts.find { it.providerId == provider.providerId }
                    ?: return@withContext Result.failure(Exception("No account found for provider ${provider.providerId}"))
                account = accountEntity.toDomain()
            } else {
                val accounts = accountDao.getAllAccounts().first()
                val accountEntity = accounts.find { it.id == parentFolder.accountId }
                    ?: return@withContext Result.failure(Exception("Owner account not found"))
                account = accountEntity.toDomain()
                provider = providers.find { it.providerId == account.provider }
                    ?: return@withContext Result.failure(Exception("Provider not found"))
            }

            val result = provider.createFolder(account, name, parentFolder?.id)
            result.onSuccess {
                dao.insertFiles(listOf(it.toEntity(parentFolder?.id)))
            }
            result
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun uploadFile(uri: Uri, parentFolder: CloudFile?, accountId: String?): Result<Unit> = withContext(Dispatchers.IO) {
        if (accountId != null) {
            transferRepository.startUpload(uri, accountId, parentFolder?.id)
        } else if (parentFolder != null) {
            transferRepository.startUpload(uri, parentFolder.accountId, parentFolder.id)
        } else {
            val accountIds = allocationManager.getTargetAccountIds()
            if (accountIds.isEmpty()) return@withContext Result.failure(Exception("No active cloud accounts connected"))
            
            accountIds.forEach { targetId ->
                transferRepository.startUpload(uri, targetId, null)
            }
        }
        Result.success(Unit)
    }

    override suspend fun uploadFiles(uris: List<Uri>, parentFolder: CloudFile?, accountId: String?): Result<Unit> = withContext(Dispatchers.IO) {
        uris.forEach { uri ->
            uploadFile(uri, parentFolder, accountId)
        }
        Result.success(Unit)
    }

    override suspend fun uploadFolder(uri: Uri, parentFolder: CloudFile?, accountId: String?): Result<Unit> = withContext(Dispatchers.IO) {
        val rootFolder = DocumentFile.fromTreeUri(context, uri)
        if (rootFolder == null || !rootFolder.exists()) {
            return@withContext Result.failure(Exception("Could not open folder"))
        }
        
        uploadDocumentFolder(rootFolder, parentFolder, accountId)
        Result.success(Unit)
    }

    private suspend fun uploadDocumentFolder(folder: DocumentFile, parentCloudFolder: CloudFile?, accountId: String?) {
        val cloudFolderResult = createFolder(folder.name ?: "Folder", parentCloudFolder, accountId)
        val cloudFolder = cloudFolderResult.getOrNull() ?: return
        
        folder.listFiles().forEach { file ->
            if (file.isDirectory) {
                uploadDocumentFolder(file, cloudFolder, accountId)
            } else {
                uploadFile(file.uri, cloudFolder, accountId)
            }
        }
    }

    override suspend fun downloadFile(file: CloudFile): Result<Unit> {
        transferRepository.startDownload(file)
        return Result.success(Unit)
    }

    override suspend fun deleteFile(file: CloudFile): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val accounts = accountDao.getAllAccounts().first()
            val accountEntity = accounts.find { it.id == file.accountId }
                ?: return@withContext Result.failure(Exception("Account not found"))
                
            val provider = providers.find { it.providerId == accountEntity.providerId }
            
            provider?.deleteFile(accountEntity.toDomain(), file.id)?.onSuccess {
                dao.deleteFileById(file.id)
            } ?: Result.failure(Exception("Provider not found"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun renameFile(file: CloudFile, newName: String): Result<CloudFile> = withContext(Dispatchers.IO) {
        try {
            val accounts = accountDao.getAllAccounts().first()
            val accountEntity = accounts.find { it.id == file.accountId }
                ?: return@withContext Result.failure(Exception("Account not found"))
                
            val provider = providers.find { it.providerId == accountEntity.providerId }
                ?: return@withContext Result.failure(Exception("Provider not found"))
            
            val result = provider.renameFile(accountEntity.toDomain(), file.id, newName)
            result.onSuccess { updatedFile ->
                dao.deleteFileById(file.id)
                dao.insertFiles(listOf(updatedFile.toEntity(file.parentId)))
            }
            result
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updateGeneralAccess(file: CloudFile, isPublic: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val accounts = accountDao.getAllAccounts().first()
            val accountEntity = accounts.find { it.id == file.accountId }
                ?: return@withContext Result.failure(Exception("Account not found"))
            
            val provider = providers.find { it.providerId == accountEntity.providerId }
                ?: return@withContext Result.failure(Exception("Provider not found"))
                
            val result = provider.setGeneralAccess(accountEntity.toDomain(), file.id, isPublic)
            result.onSuccess {
                // Update local DB
                val updatedFile = file.copy(isPublic = isPublic)
                dao.insertFiles(listOf(updatedFile.toEntity()))
            }
            result
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getAccountById(accountId: String): CloudAccount? = withContext(Dispatchers.IO) {
        accountDao.getAllAccounts().first().find { it.id == accountId }?.toDomain()
    }

    override fun getProviderById(providerId: String): CloudProvider? {
        return providers.find { it.providerId == providerId }
    }
}
