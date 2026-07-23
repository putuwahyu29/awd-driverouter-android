package com.awd.driverouter.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [CloudFileEntity::class, TransferEntity::class, AccountEntity::class, BackupConfigEntity::class], version = 15, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun cloudFileDao(): CloudFileDao
    abstract fun transferDao(): TransferDao
    abstract fun accountDao(): AccountDao
    abstract fun backupDao(): BackupDao
}
