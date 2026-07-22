package com.awd.driverouter.domain.model

enum class TransferType { UPLOAD, DOWNLOAD }
enum class TransferStatus { PENDING, RUNNING, COMPLETED, FAILED }

data class Transfer(
    val id: String,
    val fileId: String,
    val fileName: String,
    val type: TransferType,
    val status: TransferStatus,
    val progress: Float, // 0.0 to 1.0
    val totalSize: Long,
    val provider: String,
    val accountId: String,
    val accountEmail: String? = null
)
