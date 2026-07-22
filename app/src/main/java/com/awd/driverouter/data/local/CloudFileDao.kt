package com.awd.driverouter.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface CloudFileDao {
    @Transaction
    @Query("SELECT * FROM cloud_files WHERE parentId = :folderId OR (:folderId IS NULL AND parentId IS NULL)")
    fun getFilesByFolderWithAccount(folderId: String?): Flow<List<CloudFileWithAccount>>

    @Transaction
    @Query("SELECT * FROM cloud_files WHERE name LIKE '%' || :query || '%'")
    fun searchFilesWithAccount(query: String): Flow<List<CloudFileWithAccount>>

    @Transaction
    @Query("SELECT * FROM cloud_files WHERE isStarred = 1 ORDER BY modifiedTime DESC")
    fun getStarredFilesWithAccount(): Flow<List<CloudFileWithAccount>>

    @Transaction
    @Query("SELECT * FROM cloud_files WHERE isShared = 1 ORDER BY modifiedTime DESC")
    fun getSharedFilesWithAccount(): Flow<List<CloudFileWithAccount>>

    @Transaction
    @Query("SELECT * FROM cloud_files ORDER BY modifiedTime DESC LIMIT 50")
    fun getRecentFilesWithAccount(): Flow<List<CloudFileWithAccount>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFiles(files: List<CloudFileEntity>)

    @Query("DELETE FROM cloud_files WHERE (parentId = :folderId OR (:folderId IS NULL AND parentId IS NULL)) AND accountId = :accountId")
    suspend fun deleteFilesByAccountAndFolder(folderId: String?, accountId: String)

    @Query("DELETE FROM cloud_files WHERE id = :fileId")
    suspend fun deleteFileById(fileId: String)

    @Query("DELETE FROM cloud_files WHERE accountId = :accountId")
    suspend fun deleteFilesByAccount(accountId: String)
}
