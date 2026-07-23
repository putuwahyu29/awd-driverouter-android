package com.awd.driverouter.data.provider

import com.awd.driverouter.data.remote.DropboxAuthManager
import com.awd.driverouter.domain.model.CloudAccount
import com.awd.driverouter.domain.model.CloudFile
import com.awd.driverouter.domain.model.QuotaInfo
import com.awd.driverouter.domain.provider.CloudProvider
import com.dropbox.core.v2.files.FileMetadata
import com.dropbox.core.v2.files.FolderMetadata
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import javax.inject.Inject

class DropboxProvider @Inject constructor(
    private val authManager: DropboxAuthManager
) : CloudProvider {
    override val providerId: String = "dropbox"

    override suspend fun listFiles(
        account: CloudAccount, 
        folderId: String?,
        onPartialResult: (suspend (List<CloudFile>) -> Unit)?
    ): Result<List<CloudFile>> {
        val client = authManager.getClient(account.id) ?: return Result.failure(Exception("Not logged in"))
        val path = folderId ?: ""
        return try {
            val allFiles = mutableListOf<CloudFile>()
            
            var result = client.files().listFolderBuilder(path)
                .withIncludeMountedFolders(true)
                .start()
            
            while (true) {
                val currentBatch = result.entries.map { it.toCloudFile(account) }
                allFiles.addAll(currentBatch)
                onPartialResult?.invoke(currentBatch)
                
                if (!result.hasMore) break
                result = client.files().listFolderContinue(result.cursor)
            }
            
            Result.success(allFiles)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun listStarred(
        account: CloudAccount,
        onPartialResult: (suspend (List<CloudFile>) -> Unit)?
    ): Result<List<CloudFile>> {
        return Result.success(emptyList())
    }

    override suspend fun listRecent(
        account: CloudAccount,
        onPartialResult: (suspend (List<CloudFile>) -> Unit)?
    ): Result<List<CloudFile>> {
        return Result.success(emptyList())
    }

    override suspend fun listShared(
        account: CloudAccount,
        onPartialResult: (suspend (List<CloudFile>) -> Unit)?
    ): Result<List<CloudFile>> {
        return Result.success(emptyList())
    }

    private fun com.dropbox.core.v2.files.Metadata.toCloudFile(account: CloudAccount): CloudFile {
        val parentPath = this.pathLower?.substringBeforeLast("/", "") ?: ""
        val stableId = when (this) {
            is FileMetadata -> this.id
            is FolderMetadata -> this.id
            else -> this.pathLower ?: ""
        }
        return CloudFile(
            id = stableId, 
            name = this.name,
            size = if (this is FileMetadata) this.size else null,
            mimeType = if (this is FolderMetadata) "application/vnd.google-apps.folder" else "application/octet-stream",
            provider = "dropbox",
            accountId = account.id,
            path = this.pathDisplay ?: "",
            parentId = if (parentPath.isEmpty()) null else parentPath,
            isFolder = this is FolderMetadata,
            modifiedTime = if (this is FileMetadata) this.clientModified.time else null,
            isTrashed = false,
            ownerDisplayName = account.name,
            thumbnailLink = null,
            webViewLink = "https://www.dropbox.com/home${this.pathDisplay}",
            supportsNativeSharing = true,
            supportsMemberSharing = false
        )
    }

    override suspend fun downloadFile(
        account: CloudAccount, 
        fileId: String, 
        destination: File,
        onProgress: ((Float) -> Unit)?
    ): Result<Unit> {
        val client = authManager.getClient(account.id) ?: return Result.failure(Exception("Not logged in"))
        return try {
            client.files().download(fileId).use { downloader ->
                val total = downloader.result.size
                downloader.inputStream.use { inputStream ->
                    FileOutputStream(destination).use { outputStream ->
                        var bytesCopied = 0L
                        val buffer = ByteArray(8192)
                        var bytesRead = inputStream.read(buffer)
                        while (bytesRead != -1) {
                            outputStream.write(buffer, 0, bytesRead)
                            bytesCopied += bytesRead
                            if (total > 0) onProgress?.invoke(bytesCopied.toFloat() / total)
                            bytesRead = inputStream.read(buffer)
                        }
                    }
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
    ): Result<CloudFile> {
        val client = authManager.getClient(account.id) ?: return Result.failure(Exception("Not logged in"))
        return try {
            val path = if (folderId == null) "/$fileName" else "$folderId/$fileName"
            val total = source.length()
            
            FileInputStream(source).use { inputStream ->
                // Custom progress wrapper
                val progressStream = object : java.io.FilterInputStream(inputStream) {
                    private var bytesReadTotal = 0L
                    override fun read(): Int {
                        val b = super.read()
                        if (b != -1) updateProgress(1)
                        return b
                    }
                    override fun read(b: ByteArray, off: Int, len: Int): Int {
                        val n = super.read(b, off, len)
                        if (n != -1) updateProgress(n.toLong())
                        return n
                    }
                    private fun updateProgress(n: Long) {
                        bytesReadTotal += n
                        if (total > 0) onProgress?.invoke(bytesReadTotal.toFloat() / total)
                    }
                }
                val metadata = client.files().uploadBuilder(path).uploadAndFinish(progressStream)
                Result.success(metadata.toCloudFile(account))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteFile(account: CloudAccount, fileId: String): Result<Unit> {
        val client = authManager.getClient(account.id) ?: return Result.failure(Exception("Not logged in"))
        return try {
            client.files().deleteV2(fileId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun renameFile(account: CloudAccount, fileId: String, newName: String): Result<CloudFile> {
        val client = authManager.getClient(account.id) ?: return Result.failure(Exception("Not logged in"))
        return try {
            val currentPath = if (fileId.startsWith("/")) fileId else {
                client.files().getMetadata(fileId).pathLower
            }
            
            val parentPath = currentPath.substringBeforeLast("/", "")
            val newPath = if (parentPath.isEmpty()) "/$newName" else "$parentPath/$newName"
            val result = client.files().moveV2(currentPath, newPath)
            Result.success(result.metadata.toCloudFile(account))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun createFolder(account: CloudAccount, name: String, parentId: String?): Result<CloudFile> {
        val client = authManager.getClient(account.id) ?: return Result.failure(Exception("Not logged in"))
        return try {
            val parentPath = if (parentId == null) "" else if (parentId.startsWith("/")) parentId else {
                client.files().getMetadata(parentId).pathLower
            }
            
            val path = "$parentPath/$name"
            val metadata = client.files().createFolderV2(path).metadata
            Result.success(metadata.toCloudFile(account))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getQuota(account: CloudAccount): Result<QuotaInfo> {
        val client = authManager.getClient(account.id) ?: return Result.failure(Exception("Not logged in"))
        return try {
            val usage = client.users().getSpaceUsage()
            Result.success(QuotaInfo(usedSpace = usage.used, totalSpace = usage.allocation.individualValue.allocated))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun supportsSharing(): Boolean = true

    override suspend fun setGeneralAccess(account: CloudAccount, fileId: String, isPublic: Boolean): Result<Unit> {
        val client = authManager.getClient(account.id) ?: return Result.failure(Exception("Not logged in"))
        return try {
            val path = if (fileId.startsWith("id:")) fileId else "/$fileId"
            if (isPublic) {
                try {
                    client.sharing().createSharedLinkWithSettings(path)
                } catch (e: com.dropbox.core.v2.sharing.CreateSharedLinkWithSettingsErrorException) {
                    if (e.errorValue.isSharedLinkAlreadyExists) {
                        // Link already exists, nothing to do
                    } else throw e
                }
            } else {
                val links = client.sharing().listSharedLinksBuilder().withPath(path).withDirectOnly(true).start()
                links.links.forEach { link ->
                    client.sharing().revokeSharedLink(link.url)
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getShareLink(account: CloudAccount, fileId: String): Result<String> {
        val client = authManager.getClient(account.id) ?: return Result.failure(Exception("Not logged in"))
        return try {
            val path = if (fileId.startsWith("id:")) fileId else "/$fileId"
            val links = client.sharing().listSharedLinksBuilder().withPath(path).withDirectOnly(true).start()
            val link = links.links.firstOrNull()?.url ?: client.sharing().createSharedLinkWithSettings(path).url
            Result.success(link)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
