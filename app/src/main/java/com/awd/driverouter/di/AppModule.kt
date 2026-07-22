package com.awd.driverouter.di

import android.content.Context
import androidx.room.Room
import com.awd.driverouter.data.local.AccountDao
import com.awd.driverouter.data.local.AppDatabase
import com.awd.driverouter.data.local.BackupDao
import com.awd.driverouter.data.local.CloudFileDao
import com.awd.driverouter.data.local.TransferDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "cloud_hub_db"
        ).fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    fun provideCloudFileDao(db: AppDatabase): CloudFileDao {
        return db.cloudFileDao()
    }

    @Provides
    fun provideTransferDao(db: AppDatabase): TransferDao {
        return db.transferDao()
    }

    @Provides
    fun provideAccountDao(db: AppDatabase): AccountDao {
        return db.accountDao()
    }

    @Provides
    fun provideBackupDao(db: AppDatabase): BackupDao {
        return db.backupDao()
    }
}
