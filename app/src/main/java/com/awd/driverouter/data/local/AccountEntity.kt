package com.awd.driverouter.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.awd.driverouter.domain.model.CloudAccount

@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey val id: String,
    val email: String?,
    val displayName: String?,
    val providerId: String,
    val usedSpace: Long,
    val totalSpace: Long,
    val isConnected: Boolean,
    val isMainAccount: Boolean = false
)

fun AccountEntity.toDomain() = CloudAccount(
    id = id,
    name = displayName ?: email ?: "Unknown Account",
    email = email,
    provider = providerId,
    usedSpace = usedSpace,
    totalSpace = totalSpace,
    isConnected = isConnected,
    isMainAccount = isMainAccount
)
