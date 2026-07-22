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

    override suspend fun syncFiles(folderId: String?): Result<List<CloudFile>> = syncAggregation { provider, account ->
        provider.listFiles(account, folderId)
    }

    override suspend fun syncStarred(): Result<List<CloudFile>> = syncAggregation { provider, account ->
        provider.listStarred(account)
    }

    override suspend fun syncRecent(): Result<List<CloudFile>> = syncAggregation { provider, account ->
        provider.listRecent(account)
    }

    override suspend fun syncShared(): Result<List<CloudFile>> = syncAggregation { provider, account ->
        provider.listShared(account)
    }

    override suspend fun getFilesByAccount(folderId: String?, accountId: String): Result<List<CloudFile>> = withContext(Dispatchers.IO) {
        try {
            val accountEntity = accountDao.getAllAccounts().first().find { it.id == accountId }
                ?: return@withContext Result.failure(Exception("Account not found"))
            val account = accountEntity.toDomain()
            val provider = providers.find { it.providerId == account.provider }
                ?: return@withContext Result.failure(Exception("Provider not found"))
            
            val files = provider.listFiles(account, folderId)
            dao.insertFiles(files.map { it.toEntity() })
            Result.success(files)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun syncQuota(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val accounts = accountDao.getAllAccounts().first()
            accounts.forEach { accountEntity ->
                val account = accountEntity.toDomain()
                val provider = providers.find { it.providerId == account.provider }
                provider?.getQuota(account)?.onSuccess { quota ->
                    accountDao.insertAccount(accountEntity.copy(
                        usedSpace = quota.usedSpace,
                        totalSpace = quota.totalSpace
                    ))
                } ?: run {
                    Log.w("CloudRepository", "Quota sync failed or not supported for ${account.provider}")
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun syncAggregation(fetcher: suspend (CloudProvider, CloudAccount) -> List<CloudFile>): Result<List<CloudFile>> = withContext(Dispatchers.IO) {
        try {
            val accounts = accountDao.getAllAccounts().first()
            if (accounts.isEmpty()) return@withContext Result.success(emptyList())

            val results = accounts.map { accountEntity ->
                async {
                    val account = accountEntity.toDomain()
                    val provider = providers.find { it.providerId == account.provider }
                    if (provider != null) {
                        try {
                            val files = fetcher(provider, account)
                            dao.insertFiles(files.map { it.toEntity() })
                            files
                        } catch (e: Exception) {
                            Log.e("CloudRepository", "Sync failed for ${account.email}: ${e.message}")
                            emptyList()
                        }
                    } else emptyList()
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
