package com.awd.driverouter.domain.model

data class SharePermission(
    val id: String,
    val email: String?,
    val role: String,
    val displayName: String?,
    val photoLink: String? = null,
    val type: String // "user", "group", "domain", "anyone"
)
