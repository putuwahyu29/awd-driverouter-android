package com.awd.driverouter.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface CloudFileDao {
    @Transaction
    @Query("SELECT * FROM cloud_files WHERE (parentId = :folderId OR (:folderId IS NULL AND parentId IS NULL)) AND isTrashed = 0")
    fun getFilesByFolderWithAccount(folderId: String?): Flow<List<CloudFileWithAccount>>

    @Transaction
    @Query("SELECT * FROM cloud_files WHERE name LIKE '%' || :query || '%' AND isTrashed = 0")
    fun searchFilesWithAccount(query: String): Flow<List<CloudFileWithAccount>>

    @Transaction
    @Query("SELECT * FROM cloud_files WHERE isStarred = 1 AND isTrashed = 0 ORDER BY modifiedTime DESC")
    fun getStarredFilesWithAccount(): Flow<List<CloudFileWithAccount>>

    @Transaction
    @Query("SELECT * FROM cloud_files WHERE isShared = 1 AND isTrashed = 0 ORDER BY modifiedTime DESC")
    fun getSharedFilesWithAccount(): Flow<List<CloudFileWithAccount>>

    @Transaction
    @Query("SELECT * FROM cloud_files WHERE isTrashed = 0 ORDER BY CASE WHEN lastAccessedTime IS NOT NULL THEN lastAccessedTime ELSE modifiedTime END DESC LIMIT 50")
    fun getRecentFilesWithAccount(): Flow<List<CloudFileWithAccount>>

    @Transaction
    @Query("SELECT * FROM cloud_files WHERE isTrashed = 1 ORDER BY modifiedTime DESC")
    fun getTrashedFilesWithAccount(): Flow<List<CloudFileWithAccount>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFiles(files: List<CloudFileEntity>)

    @Query("DELETE FROM cloud_files WHERE (parentId = :folderId OR (:folderId IS NULL AND parentId IS NULL)) AND accountId = :accountId")
    suspend fun deleteFilesByAccountAndFolder(folderId: String?, accountId: String)

    @Query("UPDATE cloud_files SET isStarred = 0 WHERE accountId = :accountId")
    suspend fun resetStarredStatus(accountId: String)

    @Query("UPDATE cloud_files SET isShared = 0 WHERE accountId = :accountId")
    suspend fun resetSharedStatus(accountId: String)

    @Query("UPDATE cloud_files SET isTrashed = 0 WHERE accountId = :accountId")
    suspend fun resetTrashedStatus(accountId: String)

    @Query("DELETE FROM cloud_files WHERE id = :fileId")
    suspend fun deleteFileById(fileId: String)

    @Query("SELECT * FROM cloud_files WHERE id = :id")
    suspend fun getFileById(id: String): CloudFileEntity?

    @Query("DELETE FROM cloud_files WHERE accountId = :accountId")
    suspend fun deleteFilesByAccount(accountId: String)
}
