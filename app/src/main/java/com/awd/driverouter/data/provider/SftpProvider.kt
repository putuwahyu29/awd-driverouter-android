package com.awd.driverouter.data.provider

import com.awd.driverouter.data.local.CredentialManager
import com.awd.driverouter.domain.model.CloudAccount
import com.awd.driverouter.domain.model.CloudFile
import com.awd.driverouter.domain.model.QuotaInfo
import com.awd.driverouter.domain.provider.CloudProvider
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.sftp.SFTPClient
import net.schmizz.sshj.sftp.FileMode
import net.schmizz.sshj.transport.verification.PromiscuousVerifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

class SftpProvider @Inject constructor(
    private val credentialManager: CredentialManager
) : CloudProvider {
    override val providerId: String = "sftp"

    private suspend fun <T> useSftp(account: CloudAccount, block: suspend (SFTPClient) -> T): T = withContext(Dispatchers.IO) {
        val client = SSHClient()
        try {
            val host = credentialManager.getAccountCredential(account.id, CredentialManager.SFTP_HOST)
            val portStr = credentialManager.getAccountCredential(account.id, CredentialManager.SFTP_PORT)
            val port = if (portStr.isEmpty()) 22 else portStr.toInt()
            val user = credentialManager.getAccountCredential(account.id, CredentialManager.SFTP_USER)
            val pass = credentialManager.getAccountCredential(account.id, CredentialManager.SFTP_PASS)

            client.addHostKeyVerifier(PromiscuousVerifier())
            client.connect(host, port)
            client.authPassword(user, pass)
            
            val sftp = client.newSFTPClient()
            try {
                block(sftp)
            } finally {
                sftp.close()
            }
        } finally {
            client.disconnect()
        }
    }

    override suspend fun listFiles(account: CloudAccount, folderId: String?): List<CloudFile> = try {
        useSftp(account) { sftp ->
            val path = folderId ?: "."
            val entries = sftp.ls(path)
            entries.map { entry ->
                val isDir = entry.attributes.type == FileMode.Type.DIRECTORY
                CloudFile(
                    id = entry.path,
                    name = entry.name,
                    size = if (isDir) null else entry.attributes.size,
                    mimeType = if (isDir) "application/vnd.google-apps.folder" else "application/octet-stream",
                    provider = providerId,
                    accountId = account.id,
                    path = entry.path,
                    isFolder = isDir,
                    modifiedTime = entry.attributes.mtime * 1000L
                )
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
        emptyList()
    }

    override suspend fun downloadFile(
        account: CloudAccount, 
        fileId: String, 
        destination: File,
        onProgress: ((Float) -> Unit)?
    ): Result<Unit> = try {
        useSftp(account) { sftp ->
            sftp.fileTransfer.transferListener = object : net.schmizz.sshj.xfer.TransferListener {
                override fun directory(name: String?) = this
                override fun file(name: String?, size: Long) = net.schmizz.sshj.common.StreamCopier.Listener { transferred ->
                    if (size > 0) onProgress?.invoke(transferred.toFloat() / size)
                }
            }
            sftp.get(fileId, destination.absolutePath)
            Result.success(Unit)
        }
    } catch (e: Exception) {
        Result.failure(e)
    }

    override suspend fun uploadFile(
        account: CloudAccount, 
        source: File, 
        fileName: String, 
        folderId: String?,
        onProgress: ((Float) -> Unit)?
    ): Result<CloudFile> = try {
        useSftp(account) { sftp ->
            sftp.fileTransfer.transferListener = object : net.schmizz.sshj.xfer.TransferListener {
                override fun directory(name: String?) = this
                override fun file(name: String?, size: Long) = net.schmizz.sshj.common.StreamCopier.Listener { transferred ->
                    if (size > 0) onProgress?.invoke(transferred.toFloat() / size)
                }
            }
            val targetPath = (if (folderId != null) "$folderId/" else "") + fileName
            sftp.put(source.absolutePath, targetPath)
            Result.success(CloudFile(
                id = targetPath,
                name = fileName,
                size = source.length(),
                mimeType = "application/octet-stream",
                provider = providerId,
                accountId = account.id,
                path = targetPath,
                isFolder = false,
                modifiedTime = System.currentTimeMillis()
            ))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }

    override suspend fun deleteFile(account: CloudAccount, fileId: String): Result<Unit> = try {
        useSftp(account) { sftp ->
            val attrs = sftp.stat(fileId)
            if (attrs.type == FileMode.Type.DIRECTORY) sftp.rmdir(fileId) else sftp.rm(fileId)
            Result.success(Unit)
        }
    } catch (e: Exception) {
        Result.failure(e)
    }

    override suspend fun renameFile(account: CloudAccount, fileId: String, newName: String): Result<CloudFile> = try {
        useSftp(account) { sftp ->
            val parentPath = fileId.substringBeforeLast("/", "")
            val newPath = if (parentPath.isEmpty()) newName else "$parentPath/$newName"
            sftp.rename(fileId, newPath)
            Result.success(CloudFile(
                id = newPath,
                name = newName,
                size = null,
                mimeType = "application/octet-stream",
                provider = providerId,
                accountId = account.id,
                path = newPath,
                isFolder = false,
                modifiedTime = System.currentTimeMillis()
            ))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }

    override suspend fun createFolder(account: CloudAccount, name: String, parentId: String?): Result<CloudFile> = try {
        useSftp(account) { sftp ->
            val targetPath = (if (parentId != null) "$parentId/" else "") + name
            sftp.mkdir(targetPath)
            Result.success(CloudFile(
                id = targetPath,
                name = name,
                size = null,
                mimeType = "application/vnd.google-apps.folder",
                provider = providerId,
                accountId = account.id,
                path = targetPath,
                isFolder = true,
                modifiedTime = System.currentTimeMillis()
            ))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }

    override suspend fun getQuota(account: CloudAccount): Result<QuotaInfo> = withContext(Dispatchers.IO) {
        Result.failure(Exception("SFTP does not support native quota reporting"))
    }

    override suspend fun listStarred(account: CloudAccount): List<CloudFile> = emptyList()
    override suspend fun listRecent(account: CloudAccount): List<CloudFile> = emptyList()
    override suspend fun listShared(account: CloudAccount): List<CloudFile> = emptyList()
}
