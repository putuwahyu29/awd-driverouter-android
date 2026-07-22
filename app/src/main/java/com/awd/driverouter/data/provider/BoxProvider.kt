package com.awd.driverouter.data.provider

import com.awd.driverouter.data.remote.BoxAuthManager
import com.awd.driverouter.domain.model.CloudAccount
import com.awd.driverouter.domain.model.CloudFile
import com.awd.driverouter.domain.model.QuotaInfo
import com.awd.driverouter.domain.provider.CloudProvider
import com.box.sdk.BoxFile
import com.box.sdk.BoxFolder
import com.box.sdk.BoxItem
import com.box.sdk.BoxUser
import com.box.sdk.ProgressListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import javax.inject.Inject

class BoxProvider @Inject constructor(
    private val authManager: BoxAuthManager
) : CloudProvider {
    override val providerId: String = "box"

    override suspend fun listFiles(
        account: CloudAccount,
        folderId: String?,
        onPartialResult: (suspend (List<CloudFile>) -> Unit)?
    ): Result<List<CloudFile>> = withContext(Dispatchers.IO) {
        val api = authManager.getApiConnection(account.id)
            ?: return@withContext Result.failure(Exception("Not logged in"))
        val targetId = folderId ?: "0"
        return@withContext try {
            val allFiles = mutableListOf<CloudFile>()
            val folder = BoxFolder(api, targetId)
            val items = folder.getChildren("id", "name", "type", "size", "modified_at")

            for (item in items) {
                val cloudFile = item.toCloudFile(account)
                allFiles.add(cloudFile)
                if (allFiles.size % 50 == 0) {
                    onPartialResult?.invoke(allFiles.toList())
                }
            }
            onPartialResult?.invoke(allFiles)
            Result.success(allFiles)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun listStarred(
        account: CloudAccount,
        onPartialResult: (suspend (List<CloudFile>) -> Unit)?
    ): Result<List<CloudFile>> = Result.success(emptyList())

    override suspend fun listRecent(
        account: CloudAccount,
        onPartialResult: (suspend (List<CloudFile>) -> Unit)?
    ): Result<List<CloudFile>> = Result.success(emptyList())

    override suspend fun listShared(
        account: CloudAccount,
        onPartialResult: (suspend (List<CloudFile>) -> Unit)?
    ): Result<List<CloudFile>> = Result.success(emptyList())

    override suspend fun listTrashed(
        account: CloudAccount,
        onPartialResult: (suspend (List<CloudFile>) -> Unit)?
    ): Result<List<CloudFile>> = withContext(Dispatchers.IO) {
        val api = authManager.getApiConnection(account.id)
            ?: return@withContext Result.failure(Exception("Not logged in"))
        return@withContext try {
            // Box API: trash folder items
            val folder = BoxFolder(api, "trash")
            val items = folder.getChildren("id", "name", "type", "size", "modified_at")
            val trashFiles = items.map { it.toCloudFile(account).copy(isTrashed = true) }
            onPartialResult?.invoke(trashFiles)
            Result.success(trashFiles)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Konversi BoxItem.Info dari Box Java SDK ke CloudFile domain model.
     */
    private fun BoxItem.Info.toCloudFile(account: CloudAccount): CloudFile {
        val isFolder = this is BoxFolder.Info
        return CloudFile(
            id = this.id,
            name = this.name ?: "",
            size = if (this is BoxFile.Info) this.size else null,
            mimeType = if (isFolder) "application/vnd.google-apps.folder" else "application/octet-stream",
            provider = "box",
            accountId = account.id,
            path = "",
            isFolder = isFolder,
            modifiedTime = this.modifiedAt?.time,
            isTrashed = false,
            thumbnailLink = null,
            webViewLink = "https://app.box.com/${if (isFolder) "folder" else "file"}/${this.id}"
        )
    }

    override suspend fun downloadFile(
        account: CloudAccount,
        fileId: String,
        destination: File,
        onProgress: ((Float) -> Unit)?
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val api = authManager.getApiConnection(account.id)
            ?: return@withContext Result.failure(Exception("Not logged in"))
        return@withContext try {
            FileOutputStream(destination).use { outputStream ->
                val boxFile = BoxFile(api, fileId)
                if (onProgress != null) {
                    // Gunakan explicit ProgressListener interface untuk SAM conversion yang tepat
                    val listener = ProgressListener { numBytes, totalBytes ->
                        if (totalBytes > 0) onProgress(numBytes.toFloat() / totalBytes)
                    }
                    boxFile.download(outputStream, listener)
                } else {
                    boxFile.download(outputStream)
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun uploadFile(
        account: CloudAccount,
        source: File,
        fileName: String,
        folderId: String?,
        onProgress: ((Float) -> Unit)?
    ): Result<CloudFile> = withContext(Dispatchers.IO) {
        val api = authManager.getApiConnection(account.id)
            ?: return@withContext Result.failure(Exception("Not logged in"))
        val targetFolderId = folderId ?: "0"
        return@withContext try {
            FileInputStream(source).use { inputStream ->
                val folder = BoxFolder(api, targetFolderId)
                // Box Java SDK: uploadFile(InputStream, String, long, ProgressListener)
                val uploadedFile: BoxFile.Info = if (onProgress != null) {
                    val listener = ProgressListener { numBytes, totalBytes ->
                        if (totalBytes > 0) onProgress(numBytes.toFloat() / totalBytes)
                    }
                    folder.uploadFile(inputStream, fileName, source.length(), listener)
                } else {
                    folder.uploadFile(inputStream, fileName)
                }
                Result.success(uploadedFile.toCloudFile(account))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteFile(account: CloudAccount, fileId: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            val api = authManager.getApiConnection(account.id)
                ?: return@withContext Result.failure(Exception("Not logged in"))
            return@withContext try {
                BoxFile(api, fileId).delete()
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    override suspend fun renameFile(
        account: CloudAccount,
        fileId: String,
        newName: String
    ): Result<CloudFile> = withContext(Dispatchers.IO) {
        val api = authManager.getApiConnection(account.id)
            ?: return@withContext Result.failure(Exception("Not logged in"))
        return@withContext try {
            val boxFile = BoxFile(api, fileId)
            val info = boxFile.getInfo("id", "name", "size", "modified_at")
            info.setName(newName)
            boxFile.updateInfo(info)
            Result.success(info.toCloudFile(account))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun createFolder(
        account: CloudAccount,
        name: String,
        parentId: String?
    ): Result<CloudFile> = withContext(Dispatchers.IO) {
        val api = authManager.getApiConnection(account.id)
            ?: return@withContext Result.failure(Exception("Not logged in"))
        val targetParentId = parentId ?: "0"
        return@withContext try {
            // Box Java SDK v4.11.1: BoxFolder.createFolder(String name)
            val newFolderInfo = BoxFolder(api, targetParentId).createFolder(name)
            Result.success(newFolderInfo.toCloudFile(account))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getQuota(account: CloudAccount): Result<QuotaInfo> =
        withContext(Dispatchers.IO) {
            val api = authManager.getApiConnection(account.id)
                ?: return@withContext Result.failure(Exception("Not logged in"))
            return@withContext try {
                val userInfo = BoxUser.getCurrentUser(api)
                    .getInfo("space_used", "space_amount")
                Result.success(
                    QuotaInfo(
                        usedSpace = userInfo.spaceUsed,
                        totalSpace = userInfo.spaceAmount
                    )
                )
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
}
