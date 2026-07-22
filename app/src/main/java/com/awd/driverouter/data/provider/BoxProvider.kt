package com.awd.driverouter.data.provider

import com.awd.driverouter.data.remote.BoxAuthManager
import com.awd.driverouter.domain.model.CloudAccount
import com.awd.driverouter.domain.model.CloudFile
import com.awd.driverouter.domain.model.QuotaInfo
import com.awd.driverouter.domain.provider.CloudProvider
import com.box.androidsdk.content.BoxApiFile
import com.box.androidsdk.content.BoxApiFolder
import com.box.androidsdk.content.models.BoxFile
import com.box.androidsdk.content.models.BoxFolder
import com.box.androidsdk.content.models.BoxItem
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import javax.inject.Inject

class BoxProvider @Inject constructor(
    private val authManager: BoxAuthManager
) : CloudProvider {
    override val providerId: String = "box"

    override suspend fun listFiles(account: CloudAccount, folderId: String?): List<CloudFile> {
        val session = authManager.getSession() ?: return emptyList()
        val targetId = folderId ?: "0" 
        return try {
            val items = BoxApiFolder(session).getItemsRequest(targetId).send()
            items.map { it.toCloudFile(account) }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    override suspend fun listStarred(account: CloudAccount): List<CloudFile> {
        return emptyList()
    }

    override suspend fun listRecent(account: CloudAccount): List<CloudFile> {
        return emptyList()
    }

    override suspend fun listShared(account: CloudAccount): List<CloudFile> {
        return emptyList()
    }

    private fun BoxItem.toCloudFile(account: CloudAccount): CloudFile {
        return CloudFile(
            id = this.id,
            name = this.name,
            size = if (this is BoxFile) this.size else null,
            mimeType = if (this is BoxFolder) "application/vnd.google-apps.folder" else "application/octet-stream",
            provider = "box",
            accountId = account.id,
            path = "", 
            isFolder = this is BoxFolder,
            modifiedTime = this.modifiedAt?.time,
            thumbnailLink = null,
            webViewLink = "https://app.box.com/file/${this.id}"
        )
    }

    override suspend fun downloadFile(
        account: CloudAccount, 
        fileId: String, 
        destination: File,
        onProgress: ((Float) -> Unit)?
    ): Result<Unit> {
        val session = authManager.getSession() ?: return Result.failure(Exception("Not logged in"))
        return try {
            FileOutputStream(destination).use { outputStream ->
                val request = BoxApiFile(session).getDownloadRequest(outputStream, fileId)
                request.setProgressListener { numBytes, totalBytes ->
                    if (totalBytes > 0) onProgress?.invoke(numBytes.toFloat() / totalBytes)
                }
                request.send()
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
    ): Result<CloudFile> {
        val session = authManager.getSession() ?: return Result.failure(Exception("Not logged in"))
        val targetFolderId = folderId ?: "0"
        return try {
            FileInputStream(source).use { inputStream ->
                val request = BoxApiFile(session).getUploadRequest(inputStream, fileName, targetFolderId)
                request.setProgressListener { numBytes, totalBytes ->
                    if (totalBytes > 0) onProgress?.invoke(numBytes.toFloat() / totalBytes)
                }
                val boxFile = request.send()
                Result.success(boxFile.toCloudFile(account))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteFile(account: CloudAccount, fileId: String): Result<Unit> {
        val session = authManager.getSession() ?: return Result.failure(Exception("Not logged in"))
        return try {
            BoxApiFile(session).getDeleteRequest(fileId).send()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun renameFile(account: CloudAccount, fileId: String, newName: String): Result<CloudFile> {
        val session = authManager.getSession() ?: return Result.failure(Exception("Not logged in"))
        return try {
            val boxFile = BoxApiFile(session).getRenameRequest(fileId, newName).send()
            Result.success(boxFile.toCloudFile(account))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun createFolder(account: CloudAccount, name: String, parentId: String?): Result<CloudFile> {
        val session = authManager.getSession() ?: return Result.failure(Exception("Not logged in"))
        val targetParentId = parentId ?: "0"
        return try {
            val boxFolder = BoxApiFolder(session).getCreateRequest(targetParentId, name).send()
            Result.success(boxFolder.toCloudFile(account))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getQuota(account: CloudAccount): Result<QuotaInfo> {
        return Result.failure(Exception("Not implemented"))
    }
}
