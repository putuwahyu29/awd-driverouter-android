package com.awd.driverouter.data.repository

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.work.*
import com.awd.driverouter.data.local.AccountDao
import com.awd.driverouter.data.local.TransferDao
import com.awd.driverouter.data.local.TransferEntity
import com.awd.driverouter.data.local.toDomain
import com.awd.driverouter.data.worker.TransferWorker
import com.awd.driverouter.domain.manager.AllocationManager
import com.awd.driverouter.domain.model.CloudFile
import com.awd.driverouter.domain.model.Transfer
import com.awd.driverouter.domain.model.TransferStatus
import com.awd.driverouter.domain.model.TransferType
import com.awd.driverouter.domain.repository.TransferRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.*
import javax.inject.Inject

class TransferRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: TransferDao,
    private val accountDao: AccountDao,
    private val allocationManager: AllocationManager
) : TransferRepository {

    private val workManager = WorkManager.getInstance(context)

    override fun getAllTransfers(): Flow<List<Transfer>> {
        return dao.getAllTransfers().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getTransferByFileId(fileId: String): Flow<Transfer?> {
        return dao.getTransferByFileId(fileId).map { it?.toDomain() }
    }

    override suspend fun startDownload(file: CloudFile, destinationUri: String?) {
        val transferId = UUID.randomUUID().toString()
        val account = accountDao.getAllAccounts().first().find { it.id == file.accountId }
        
        val entity = TransferEntity(
            id = transferId,
            fileId = file.id,
            fileName = file.name,
            type = TransferType.DOWNLOAD,
            status = TransferStatus.PENDING,
            progress = 0f,
            totalSize = file.size ?: 0L,
            provider = file.provider,
            accountId = file.accountId,
            accountEmail = account?.email
        )
        dao.insertTransfer(entity)

        val data = workDataOf(
            "transfer_id" to transferId,
            "file_id" to file.id,
            "account_id" to file.accountId,
            "file_name" to file.name,
            "mime_type" to file.mimeType,
            "provider" to file.provider,
            "expected_size" to (file.size ?: 0L),
            "type" to "DOWNLOAD",
            "destination_uri" to destinationUri
        )

        enqueueWork(transferId, data)
    }

    override suspend fun startUpload(uri: Uri, accountId: String, targetFolderId: String?) {
        withContext(Dispatchers.IO) {
            val account = accountDao.getAllAccounts().first().find { it.id == accountId }
                ?: return@withContext
            
            val transferId = UUID.randomUUID().toString()
            val fileName = getFileName(uri) ?: "upload_file"
            val fileSize = getFileSize(uri)

            // CRITICAL: Preserve filename and extension in temp file
            val tempFile = File(context.cacheDir, "up_${System.currentTimeMillis()}_$fileName")
            try {

                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(tempFile).use { output ->
                        input.copyTo(output)
                    }
                }
            } catch (e: Throwable) {
                Log.e("TransferRepo", "CRITICAL: Failed to copy URI content (possible R8 issue)", e)
                return@withContext
            }

            val entity = TransferEntity(
                id = transferId,
                fileId = "", 
                fileName = fileName,
                type = TransferType.UPLOAD,
                status = TransferStatus.PENDING,
                progress = 0f,
                totalSize = fileSize,
                provider = account.providerId,
                accountId = accountId,
                accountEmail = account.email
            )
            dao.insertTransfer(entity)

            val data = workDataOf(
                "transfer_id" to transferId,
                "temp_file_path" to tempFile.absolutePath,
                "account_id" to accountId,
                "folder_id" to targetFolderId,
                "file_name" to fileName,
                "provider" to account.providerId,
                "type" to "UPLOAD"
            )

            enqueueWork(transferId, data)
        }
    }

    private fun enqueueWork(transferId: String, data: Data) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val workRequest = OneTimeWorkRequestBuilder<TransferWorker>()
            .setInputData(data)
            .setConstraints(constraints)
            .addTag(transferId)
            .build()

        workManager.enqueueUniqueWork(
            transferId, 
            ExistingWorkPolicy.REPLACE, // Using REPLACE to ensure fresh start if stuck
            workRequest
        )
    }


    override suspend fun cancelTransfer(transferId: String) {
        workManager.cancelAllWorkByTag(transferId)
        dao.deleteTransfer(transferId)
    }

    override suspend fun clearHistory() {
        dao.clearHistory()
    }

    private fun getFileName(uri: Uri): String? {
        return try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1 && cursor.moveToFirst()) cursor.getString(nameIndex) else null
            }
        } catch (e: Throwable) {
            Log.e("TransferRepo", "Failed to get filename from URI", e)
            null
        }
    }

    private fun getFileSize(uri: Uri): Long {
        return try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (sizeIndex != -1 && cursor.moveToFirst()) cursor.getLong(sizeIndex) else 0L
            } ?: 0L
        } catch (e: Throwable) {
            Log.e("TransferRepo", "Failed to get filesize from URI", e)
            0L
        }
    }
}
