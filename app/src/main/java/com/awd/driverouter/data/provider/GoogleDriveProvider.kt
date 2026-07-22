package com.awd.driverouter.data.provider

import android.accounts.Account
import com.awd.driverouter.data.remote.GoogleAuthManager
import com.awd.driverouter.domain.model.CloudAccount
import com.awd.driverouter.domain.model.CloudFile
import com.awd.driverouter.domain.model.QuotaInfo
import com.awd.driverouter.domain.provider.CloudProvider
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.FileContent
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.File as GoogleFile
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

class GoogleDriveProvider @Inject constructor(
    private val authManager: GoogleAuthManager
) : CloudProvider {
    override val providerId: String = "google_drive"

    private fun getDriveService(account: CloudAccount): Drive {
        val credential = GoogleAccountCredential.usingOAuth2(
            authManager.context, listOf(DriveScopes.DRIVE, DriveScopes.DRIVE_METADATA_READONLY)
        ).apply {
            selectedAccount = Account(account.email, "com.google")
        }

        return Drive.Builder(
            NetHttpTransport(),
            GsonFactory.getDefaultInstance(),
            credential
        ).setApplicationName("Awd DriveRouter").build()
    }

    override suspend fun listFiles(account: CloudAccount, folderId: String?): List<CloudFile> {
        val service = getDriveService(account)
        val query = if (folderId == null) "'root' in parents" else "'$folderId' in parents"
        return executeQuery(service, account, "$query and trashed = false")
    }

    override suspend fun listStarred(account: CloudAccount): List<CloudFile> {
        val service = getDriveService(account)
        return executeQuery(service, account, "starred = true and trashed = false")
    }

    override suspend fun listRecent(account: CloudAccount): List<CloudFile> {
        val service = getDriveService(account)
        return try {
            val result = service.files().list()
                .setQ("trashed = false")
                .setOrderBy("viewedByMeTime desc")
                .setPageSize(30)
                .setFields("files(id, name, size, mimeType, modifiedTime, thumbnailLink, webViewLink, starred, viewedByMeTime)")
                .execute()

            result.files?.map { it.toCloudFile(account) } ?: emptyList()

        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    override suspend fun listShared(account: CloudAccount): List<CloudFile> {
        val service = getDriveService(account)
        return executeQuery(service, account, "sharedWithMe = true and trashed = false")
    }

    private fun executeQuery(service: Drive, account: CloudAccount, query: String): List<CloudFile> {
        return try {
            val result = service.files().list()
                .setQ(query)
                .setFields("files(id, name, size, mimeType, modifiedTime, thumbnailLink, webViewLink, starred, shared, permissions)")
                .execute()

            result.files?.map { it.toCloudFile(account) } ?: emptyList()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    private fun GoogleFile.toCloudFile(account: CloudAccount): CloudFile {
        val isPublic = this.permissions?.any { it.type == "anyone" } ?: false
        return CloudFile(
            id = this.id,
            name = this.name,
            size = this.getSize(),
            mimeType = this.mimeType,
            provider = "google_drive",
            accountId = account.id,
            path = "",
            isFolder = this.mimeType == "application/vnd.google-apps.folder",
            modifiedTime = this.modifiedTime?.value,
            thumbnailLink = this.thumbnailLink,
            webViewLink = this.webViewLink,
            isStarred = this.starred ?: false,
            isShared = this.shared ?: false,
            isPublic = isPublic,
            lastAccessedTime = this.viewedByMeTime?.value,
            supportsNativeSharing = true
        )
    }


    override suspend fun downloadFile(
        account: CloudAccount, 
        fileId: String, 
        destination: File,
        onProgress: ((Float) -> Unit)?
    ): Result<Unit> {
        val service = getDriveService(account)
        return try {
            FileOutputStream(destination).use { outputStream ->
                val request = service.files().get(fileId)
                request.mediaHttpDownloader.setProgressListener { downloader ->
                    onProgress?.invoke(downloader.progress.toFloat())
                }
                request.executeMediaAndDownloadTo(outputStream)
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
        val service = getDriveService(account)
        return try {
            val metadata = GoogleFile().apply {
                name = fileName
                if (folderId != null) parents = listOf(folderId)
            }
            val content = FileContent(null, source)
            val request = service.files().create(metadata, content)
            
            request.mediaHttpUploader.setProgressListener { uploader ->
                onProgress?.invoke(uploader.progress.toFloat())
            }

            val uploadedFile = request.setFields("id, name, size, mimeType, modifiedTime")
                .execute()
                
            Result.success(uploadedFile.toCloudFile(account))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteFile(account: CloudAccount, fileId: String): Result<Unit> {
        val service = getDriveService(account)
        return try {
            service.files().delete(fileId).execute()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun renameFile(account: CloudAccount, fileId: String, newName: String): Result<CloudFile> {
        val service = getDriveService(account)
        return try {
            val metadata = GoogleFile().apply { name = newName }
            val updatedFile = service.files().update(fileId, metadata)
                .setFields("id, name, size, mimeType, modifiedTime")
                .execute()
            Result.success(updatedFile.toCloudFile(account))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun createFolder(account: CloudAccount, name: String, parentId: String?): Result<CloudFile> {
        val service = getDriveService(account)
        return try {
            val metadata = GoogleFile().apply {
                this.name = name
                mimeType = "application/vnd.google-apps.folder"
                if (parentId != null) parents = listOf(parentId)
            }
            val folder = service.files().create(metadata)
                .setFields("id, name, mimeType")
                .execute()
            Result.success(folder.toCloudFile(account))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getQuota(account: CloudAccount): Result<QuotaInfo> {
        val service = getDriveService(account)
        return try {
            val about = service.about().get().setFields("storageQuota").execute()
            val quota = about.storageQuota
            Result.success(QuotaInfo(usedSpace = quota.usage ?: 0L, totalSpace = quota.limit ?: 0L))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun supportsSharing(): Boolean = true

    override suspend fun shareFile(account: CloudAccount, fileId: String, email: String, role: String): Result<Unit> {
        val service = getDriveService(account)
        return try {
            val permission = com.google.api.services.drive.model.Permission().apply {
                type = "user"
                this.role = if (role.lowercase() == "editor") "writer" else "reader"
                emailAddress = email
            }
            service.permissions().create(fileId, permission).execute()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun setGeneralAccess(account: CloudAccount, fileId: String, isPublic: Boolean): Result<Unit> {
        val service = getDriveService(account)
        return try {
            if (isPublic) {
                val permission = com.google.api.services.drive.model.Permission().apply {
                    type = "anyone"
                    role = "reader"
                }
                service.permissions().create(fileId, permission).execute()
            } else {
                val permissions = service.permissions().list(fileId).execute().permissions
                val anyonePermission = permissions?.find { it.type == "anyone" }
                if (anyonePermission != null) {
                    service.permissions().delete(fileId, anyonePermission.id).execute()
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getShareLink(account: CloudAccount, fileId: String): Result<String> {
        val service = getDriveService(account)
        return try {
            // Ensure link sharing is on or at least get the link
            val file = service.files().get(fileId).setFields("webViewLink").execute()
            Result.success(file.webViewLink ?: "")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
