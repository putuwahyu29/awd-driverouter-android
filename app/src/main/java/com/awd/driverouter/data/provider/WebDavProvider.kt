package com.awd.driverouter.data.provider

import android.webkit.MimeTypeMap
import com.awd.driverouter.data.local.CredentialManager
import com.awd.driverouter.domain.model.CloudAccount
import com.awd.driverouter.domain.model.CloudFile
import com.awd.driverouter.domain.model.QuotaInfo
import com.awd.driverouter.domain.provider.CloudProvider
import com.thegrizzlylabs.sardineandroid.Sardine
import com.thegrizzlylabs.sardineandroid.impl.OkHttpSardine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

class WebDavProvider @Inject constructor(
    private val credentialManager: CredentialManager
) : CloudProvider {
    override val providerId: String = "webdav"

    private fun getSardine(account: CloudAccount): Sardine {
        val user = credentialManager.getAccountCredential(account.id, CredentialManager.WEBDAV_USER)
        val pass = credentialManager.getAccountCredential(account.id, CredentialManager.WEBDAV_PASS)
        return OkHttpSardine().apply {
            setCredentials(user, pass)
        }
    }

    private fun getBaseUrl(account: CloudAccount): String {
        var url = credentialManager.getAccountCredential(account.id, CredentialManager.WEBDAV_URL)
        if (!url.endsWith("/")) url += "/"
        return url
    }

    override suspend fun listFiles(
        account: CloudAccount, 
        folderId: String?,
        onPartialResult: (suspend (List<CloudFile>) -> Unit)?
    ): Result<List<CloudFile>> = withContext(Dispatchers.IO) {
        try {
            val sardine = getSardine(account)
            val path = folderId ?: getBaseUrl(account)
            val resources = sardine.list(path)
            
            val files = resources.drop(1).map { res ->
                val extension = res.name?.substringAfterLast('.', "")?.lowercase()
                val guessedMime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
                
                CloudFile(
                    id = res.href.toString(),
                    name = res.displayName ?: res.name ?: "Unknown",
                    size = res.contentLength,
                    mimeType = when {
                        res.isDirectory -> "application/vnd.google-apps.folder"
                        guessedMime != null -> guessedMime
                        else -> "application/octet-stream"
                    },
                    provider = providerId,
                    accountId = account.id,
                    path = res.path,
                    isFolder = res.isDirectory,
                    modifiedTime = res.modified?.time,
                    thumbnailLink = null,
                    webViewLink = res.href.toString()
                )
            }
            onPartialResult?.invoke(files)
            Result.success(files)
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }

    override suspend fun downloadFile(
        account: CloudAccount, 
        fileId: String, 
        destination: File,
        onProgress: ((Float) -> Unit)?
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val sardine = getSardine(account)
            val resources = sardine.list(fileId)
            val total = resources.firstOrNull()?.contentLength ?: 0L
            
            sardine.get(fileId).use { inputStream ->
                destination.outputStream().use { outputStream ->
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
            Result.success(Unit)
        } catch (e: Throwable) {
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
        try {
            val sardine = getSardine(account)
            val targetUrl = (folderId ?: getBaseUrl(account)) + fileName
            val total = source.length()
            
            sardine.put(targetUrl, source, "application/octet-stream")
            onProgress?.invoke(1.0f)
            
            Result.success(CloudFile(
                id = targetUrl,
                name = fileName,
                size = total,
                mimeType = "application/octet-stream",
                provider = providerId,
                accountId = account.id,
                path = targetUrl,
                isFolder = false,
                modifiedTime = System.currentTimeMillis()
            ))
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }

    override suspend fun deleteFile(account: CloudAccount, fileId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            getSardine(account).delete(fileId)
            Result.success(Unit)
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }

    override suspend fun renameFile(account: CloudAccount, fileId: String, newName: String): Result<CloudFile> = withContext(Dispatchers.IO) {
        try {
            val parentUrl = fileId.substringBeforeLast("/", "")
            val newUrl = if (parentUrl.isEmpty()) getBaseUrl(account) + newName else "$parentUrl/$newName"
            getSardine(account).move(fileId, newUrl)
            Result.success(CloudFile(
                id = newUrl,
                name = newName,
                size = null,
                mimeType = "application/octet-stream",
                provider = providerId,
                accountId = account.id,
                path = newUrl,
                isFolder = false,
                modifiedTime = System.currentTimeMillis()
            ))
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }

    override suspend fun createFolder(account: CloudAccount, name: String, parentId: String?): Result<CloudFile> = withContext(Dispatchers.IO) {
        try {
            var targetUrl = (parentId ?: getBaseUrl(account)) + name
            if (!targetUrl.endsWith("/")) targetUrl += "/"
            getSardine(account).createDirectory(targetUrl)
            Result.success(CloudFile(
                id = targetUrl,
                name = name,
                size = null,
                mimeType = "application/vnd.google-apps.folder",
                provider = providerId,
                accountId = account.id,
                path = targetUrl,
                isFolder = true,
                modifiedTime = System.currentTimeMillis()
            ))
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }

    override suspend fun getQuota(account: CloudAccount): Result<QuotaInfo> = withContext(Dispatchers.IO) {
        Result.failure(Exception("Quota not supported on this WebDAV server"))
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
    ): Result<List<CloudFile>> = Result.success(emptyList())
}
