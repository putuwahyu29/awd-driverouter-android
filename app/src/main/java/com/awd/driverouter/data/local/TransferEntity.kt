package com.awd.driverouter.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.awd.driverouter.domain.model.Transfer
import com.awd.driverouter.domain.model.TransferStatus
import com.awd.driverouter.domain.model.TransferType

@Entity(tableName = "transfers")
data class TransferEntity(
    @PrimaryKey val id: String,
    val fileId: String,
    val fileName: String,
    val type: TransferType,
    val status: TransferStatus,
    val progress: Float,
    val totalSize: Long,
    val provider: String,
    val accountId: String,
    val accountEmail: String? = null,
    val localPath: String? = null
)

fun TransferEntity.toDomain() = Transfer(
    id = id,
    fileId = fileId,
    fileName = fileName,
    type = type,
    status = status,
    progress = progress,
    totalSize = totalSize,
    provider = provider,
    accountId = accountId,
    accountEmail = accountEmail
)

fun Transfer.toEntity(localPath: String? = null) = TransferEntity(
    id = id,
    fileId = fileId,
    fileName = fileName,
    type = type,
    status = status,
    progress = progress,
    totalSize = totalSize,
    provider = provider,
    accountId = accountId,
    accountEmail = accountEmail,
    localPath = localPath
)
