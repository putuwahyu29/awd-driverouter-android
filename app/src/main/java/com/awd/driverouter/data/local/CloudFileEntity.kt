package com.awd.driverouter.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.awd.driverouter.domain.model.CloudFile

@Entity(tableName = "cloud_files")
data class CloudFileEntity(
    @PrimaryKey val id: String,
    val name: String,
    val size: Long?,
    val mimeType: String,
    val provider: String,
    val accountId: String,
    val path: String,
    val isFolder: Boolean,
    val modifiedTime: Long?,
    val parentId: String?,
    val thumbnailLink: String?,
    val webViewLink: String?,
    val isStarred: Boolean,
    val isShared: Boolean,
    val isPublic: Boolean = false,
    val lastAccessedTime: Long?,
    val shareLink: String?,
    val supportsNativeSharing: Boolean = false,
    val supportsMemberSharing: Boolean = false,
    val isOwner: Boolean = true,
    val isTrashed: Boolean = false
)

fun CloudFileEntity.toDomain() = CloudFile(
    id = id,
    name = name,
    size = size,
    mimeType = mimeType,
    provider = provider,
    accountId = accountId,
    path = path,
    parentId = parentId,
    isFolder = isFolder,
    modifiedTime = modifiedTime,
    thumbnailLink = thumbnailLink,
    webViewLink = webViewLink,
    isStarred = isStarred,
    isShared = isShared,
    isPublic = isPublic,
    lastAccessedTime = lastAccessedTime,
    shareLink = shareLink,
    supportsNativeSharing = supportsNativeSharing,
    supportsMemberSharing = supportsMemberSharing,
    isOwner = isOwner,
    isTrashed = isTrashed
)

fun CloudFile.toEntity(overriddenParentId: String? = null) = CloudFileEntity(
    id = id,
    name = name,
    size = size,
    mimeType = mimeType,
    provider = provider,
    accountId = accountId,
    path = path,
    isFolder = isFolder,
    modifiedTime = modifiedTime,
    parentId = overriddenParentId ?: parentId,
    thumbnailLink = thumbnailLink,
    webViewLink = webViewLink,
    isStarred = isStarred,
    isShared = isShared,
    isPublic = isPublic,
    lastAccessedTime = lastAccessedTime,
    shareLink = shareLink,
    supportsNativeSharing = supportsNativeSharing,
    supportsMemberSharing = supportsMemberSharing,
    isOwner = isOwner,
    isTrashed = isTrashed
)
