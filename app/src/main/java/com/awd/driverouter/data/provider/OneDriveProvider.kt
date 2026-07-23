package com.awd.driverouter.data.provider

import com.awd.driverouter.data.remote.OneDriveAuthManager
import com.awd.driverouter.domain.model.CloudAccount
import com.awd.driverouter.domain.model.CloudFile
import com.awd.driverouter.domain.model.QuotaInfo
import com.awd.driverouter.domain.model.SharePermission
import com.awd.driverouter.domain.provider.CloudProvider
import com.microsoft.graph.authentication.IAuthenticationProvider
import com.microsoft.graph.models.DriveItem
import com.microsoft.graph.models.Folder
import com.microsoft.graph.requests.GraphServiceClient
import kotlinx.coroutines.*
import java.io.File
import java.net.URL
import java.util.concurrent.CompletableFuture
import javax.inject.Inject

class OneDriveProvider @Inject constructor(
    private val authManager: OneDriveAuthManager
) : CloudProvider {
    override val providerId: String = "onedrive"

    private val providerScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private fun getGraphClient(account: CloudAccount): GraphServiceClient<okhttp3.Request> {
        val authProvider = object : IAuthenticationProvider {
            override fun getAuthorizationTokenAsync(requestUrl: URL): CompletableFuture<String> {
                val future = CompletableFuture<String>()
                providerScope.launch {
                    try {
                        val token = authManager.getAccessTokenForAccount(account.id)
                        if (token != null) {
                            future.complete(token)
                        } else {
                            future.completeExceptionally(Exception("Token not found for account ${account.email}"))
                        }
                    } catch (e: Exception) {
                        future.completeExceptionally(e)
                    }
                }
                return future
            }
        }
        
        return GraphServiceClient.builder()
            .authenticationProvider(authProvider)
            .buildClient()
    }

    override suspend fun listFiles(
        account: CloudAccount, 
        folderId: String?,
        onPartialResult: (suspend (List<CloudFile>) -> Unit)?
    ): Result<List<CloudFile>> {
        val client = getGraphClient(account)
        return try {
            val requestBuilder = if (folderId == null) {
                client.me().drive().root().children()
            } else {
                client.me().drive().items(folderId).children()
            }
            
            val request = requestBuilder.buildRequest().expand("permissions")
            val allFiles = mutableListOf<CloudFile>()
            var page = request.get()
            
            while (page != null) {
                val currentBatch = page.currentPage.map { it.toCloudFile(account) }
                allFiles.addAll(currentBatch)
                onPartialResult?.invoke(currentBatch)
                
                val nextPageRequest = page.nextPage
                page = nextPageRequest?.buildRequest()?.expand("permissions")?.get()
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
        val client = getGraphClient(account)
        return try {
            val allFiles = mutableListOf<CloudFile>()
            var page = client.me().drive().following().buildRequest().expand("permissions").get()
            
            while (page != null) {
                val currentBatch = page.currentPage.map { it.toCloudFile(account) }
                allFiles.addAll(currentBatch)
                onPartialResult?.invoke(currentBatch)
                
                val nextPageRequest = page.nextPage
                page = nextPageRequest?.buildRequest()?.expand("permissions")?.get()
            }
            Result.success(allFiles)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun listRecent(
        account: CloudAccount,
        onPartialResult: (suspend (List<CloudFile>) -> Unit)?
    ): Result<List<CloudFile>> {
        val client = getGraphClient(account)
        return try {
            val allFiles = mutableListOf<CloudFile>()
            var page = client.me().drive().recent().buildRequest().expand("permissions").get()
            
            while (page != null) {
                val currentBatch = page.currentPage.map { it.toCloudFile(account) }
                allFiles.addAll(currentBatch)
                onPartialResult?.invoke(currentBatch)
                
                val nextPageRequest = page.nextPage
                page = nextPageRequest?.buildRequest()?.expand("permissions")?.get()
            }
            Result.success(allFiles)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun listShared(
        account: CloudAccount,
        onPartialResult: (suspend (List<CloudFile>) -> Unit)?
    ): Result<List<CloudFile>> {
        val client = getGraphClient(account)
        return try {
            val allFiles = mutableListOf<CloudFile>()
            var page = client.me().drive().sharedWithMe().buildRequest().expand("permissions").get()
            
            while (page != null) {
                val currentBatch = page.currentPage.map { it.toCloudFile(account) }
                allFiles.addAll(currentBatch)
                onPartialResult?.invoke(currentBatch)
                
                val nextPageRequest = page.nextPage
                page = nextPageRequest?.buildRequest()?.expand("permissions")?.get()
            }
            Result.success(allFiles)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun listTrashed(
        account: CloudAccount,
        onPartialResult: (suspend (List<CloudFile>) -> Unit)?
    ): Result<List<CloudFile>> {
        val client = getGraphClient(account)
        return try {
            val allFiles = mutableListOf<CloudFile>()
            var page = client.me().drive().special("trash").children().buildRequest().expand("permissions").get()
            
            while (page != null) {
                val currentBatch = page.currentPage.map { it.toCloudFile(account).copy(isTrashed = true) }
                allFiles.addAll(currentBatch)
                onPartialResult?.invoke(currentBatch)
                
                val nextPageRequest = page.nextPage
                page = nextPageRequest?.buildRequest()?.expand("permissions")?.get()
            }
            Result.success(allFiles)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun DriveItem.toCloudFile(account: CloudAccount): CloudFile {
        val isShared = this.shared != null
        val isPublic = this.permissions?.currentPage?.any { perm ->
            perm.link != null && perm.link?.scope == "anonymous"
        } ?: false
        
        return CloudFile(
            id = this.id ?: "",
            name = this.name ?: "",
            size = this.size,
            mimeType = this.file?.mimeType ?: "application/octet-stream",
            provider = "onedrive",
            accountId = account.id,
            path = "",
            parentId = this.parentReference?.id,
            isFolder = this.folder != null,
            modifiedTime = this.lastModifiedDateTime?.toInstant()?.toEpochMilli(),
            thumbnailLink = null,
            webViewLink = this.webUrl,
            isShared = isShared,
            isPublic = isPublic,
            isTrashed = false,
            lastAccessedTime = this.lastModifiedDateTime?.toInstant()?.toEpochMilli(),
            supportsNativeSharing = true,
            supportsMemberSharing = true,
            isOwner = this.remoteItem == null
        )
    }


    override suspend fun downloadFile(
        account: CloudAccount, 
        fileId: String, 
        destination: File,
        onProgress: ((Float) -> Unit)?
    ): Result<Unit> {
        val client = getGraphClient(account)
        return try {
            val item = client.me().drive().items(fileId).buildRequest().get()
            val total = item?.size ?: 0L
            
            client.me().drive().items(fileId).content().buildRequest().get()?.use { inputStream ->
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
        val client = getGraphClient(account)
        return try {
            val total = source.length()
            val byteContent = source.readBytes()
            val item = if (folderId == null) {
                client.me().drive().root().itemWithPath(fileName).content().buildRequest().put(byteContent)
            } else {
                client.me().drive().items(folderId).itemWithPath(fileName).content().buildRequest().put(byteContent)
            }
            onProgress?.invoke(1.0f)
            Result.success(item!!.toCloudFile(account))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteFile(account: CloudAccount, fileId: String): Result<Unit> {
        val client = getGraphClient(account)
        return try {
            client.me().drive().items(fileId).buildRequest().delete()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun renameFile(account: CloudAccount, fileId: String, newName: String): Result<CloudFile> {
        val client = getGraphClient(account)
        return try {
            val item = DriveItem()
            item.name = newName
            val updated = client.me().drive().items(fileId).buildRequest().patch(item)
            Result.success(updated!!.toCloudFile(account))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun createFolder(account: CloudAccount, name: String, parentId: String?): Result<CloudFile> {
        val client = getGraphClient(account)
        return try {
            val folderItem = DriveItem()
            folderItem.name = name
            folderItem.folder = Folder()
            val request = if (parentId == null) client.me().drive().root().children().buildRequest()
            else client.me().drive().items(parentId).children().buildRequest()
            val created = request.post(folderItem)
            Result.success(created!!.toCloudFile(account))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getQuota(account: CloudAccount): Result<QuotaInfo> {
        val client = getGraphClient(account)
        return try {
            val drive = client.me().drive().buildRequest().get()
            val quota = drive?.quota
            Result.success(QuotaInfo(usedSpace = quota?.used ?: 0L, totalSpace = quota?.total ?: 0L))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun supportsSharing(): Boolean = true
    override fun supportsMemberSharing(): Boolean = true

    override suspend fun shareFile(account: CloudAccount, fileId: String, email: String, role: String): Result<Unit> {
        val client = getGraphClient(account)
        return try {
            val rolesList = listOf(if (role.lowercase() == "editor") "write" else "read")
            val recipientsList = listOf(com.microsoft.graph.models.DriveRecipient().apply { this.email = email })
            
            val params = com.microsoft.graph.models.DriveItemInviteParameterSet.newBuilder()
                .withRecipients(recipientsList)
                .withRequireSignIn(true)
                .withRoles(rolesList)
                .withSendInvitation(true)
                .build()

            client.me().drive().items(fileId).invite(params).buildRequest().post()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun setGeneralAccess(account: CloudAccount, fileId: String, isPublic: Boolean): Result<Unit> {
        val client = getGraphClient(account)
        return try {
            if (isPublic) {
                val params = com.microsoft.graph.models.DriveItemCreateLinkParameterSet.newBuilder()
                    .withType("view")
                    .withScope("anonymous")
                    .build()
                client.me().drive().items(fileId).createLink(params).buildRequest().post()
            } else {
                val permissions = client.me().drive().items(fileId).permissions().buildRequest().get()
                permissions?.currentPage?.filter { it.link != null && it.link?.scope == "anonymous" }?.forEach { perm ->
                    perm.id?.let { pid ->
                        client.me().drive().items(fileId).permissions(pid).buildRequest().delete()
                    }
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getPermissions(account: CloudAccount, fileId: String): Result<List<SharePermission>> {
        val client = getGraphClient(account)
        return try {
            val permissions = client.me().drive().items(fileId).permissions().buildRequest().get()
            val domainPermissions = permissions?.currentPage?.map { p ->
                val type = if (p.link != null) "anyone" else "user"
                val email = p.invitation?.email ?: p.grantedToV2?.user?.additionalDataManager()?.get("email")?.asString
                val displayName = p.grantedToV2?.user?.displayName ?: p.invitation?.email ?: if (p.link != null) "Anyone with link" else "Unknown User"
                
                SharePermission(
                    id = p.id ?: "",
                    email = email,
                    role = p.roles?.firstOrNull() ?: "",
                    displayName = displayName,
                    photoLink = null,
                    type = type
                )
            } ?: emptyList()
            Result.success(domainPermissions)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getShareLink(account: CloudAccount, fileId: String): Result<String> {
        val client = getGraphClient(account)
        return try {
            val params = com.microsoft.graph.models.DriveItemCreateLinkParameterSet.newBuilder()
                .withType("view")
                .withScope("anonymous")
                .build()
            val link = client.me().drive().items(fileId).createLink(params).buildRequest().post()
            Result.success(link?.link?.webUrl ?: "")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
