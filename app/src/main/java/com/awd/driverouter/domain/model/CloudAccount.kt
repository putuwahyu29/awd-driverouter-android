package com.awd.driverouter.domain.model

data class CloudAccount(
    val id: String,
    val name: String,
    val email: String?,
    val provider: String,
    val usedSpace: Long,
    val totalSpace: Long,
    val isConnected: Boolean,
    val isMainAccount: Boolean = false
) {
    val freeSpace: Long get() = totalSpace - usedSpace
    val usedPercentage: Float get() = if (totalSpace > 0) usedSpace.toFloat() / totalSpace else 0f
}

data class QuotaInfo(
    val usedSpace: Long,
    val totalSpace: Long
)
