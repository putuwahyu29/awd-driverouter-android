package com.awd.driverouter.data.local

import androidx.room.Embedded
import androidx.room.Relation
import com.awd.driverouter.domain.model.CloudFile

data class CloudFileWithAccount(
    @Embedded val file: CloudFileEntity,
    @Relation(
        parentColumn = "accountId",
        entityColumn = "id"
    )
    val account: AccountEntity?
)

fun CloudFileWithAccount.toDomain(): CloudFile {
    val domainFile = file.toDomain()
    val providerDisplayName = when(file.provider) {
        "google_drive" -> "Google Drive"
        "onedrive" -> "OneDrive"
        "dropbox" -> "Dropbox"
        "box" -> "Box"
        else -> file.provider
    }
    
    val ownerDisplayName = account?.displayName ?: account?.email ?: ""
    
    return domainFile.copy(
        ownerInfo = ownerDisplayName,
        providerDisplayName = providerDisplayName,
        ownerDisplayName = ownerDisplayName
    )
}

