package com.awd.driverouter.domain.model

data class CloudFile(
    val id: String,
    val name: String,
    val size: Long?,
    val mimeType: String,
    val provider: String,
    val accountId: String,
    val ownerInfo: String? = null,
    val providerDisplayName: String? = null,
    val ownerDisplayName: String? = null,
    val path: String,
    val parentId: String? = null,
    val isFolder: Boolean,
    val modifiedTime: Long?,
    val thumbnailLink: String? = null,
    val webViewLink: String? = null,
    val isStarred: Boolean = false,
    val isShared: Boolean = false,
    val isPublic: Boolean = false,
    val lastAccessedTime: Long? = null,
    val shareLink: String? = null,
    val supportsNativeSharing: Boolean = false,
    val supportsMemberSharing: Boolean = false,
    val isOwner: Boolean = true,
    val isTrashed: Boolean = false
)
