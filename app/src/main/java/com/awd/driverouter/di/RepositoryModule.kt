package com.awd.driverouter.di

import com.awd.driverouter.data.provider.BoxProvider
import com.awd.driverouter.data.provider.DropboxProvider
import com.awd.driverouter.data.provider.GoogleDriveProvider
import com.awd.driverouter.data.provider.OneDriveProvider
import com.awd.driverouter.data.provider.WebDavProvider
import com.awd.driverouter.data.provider.SftpProvider
import com.awd.driverouter.data.repository.CloudRepositoryImpl
import com.awd.driverouter.data.repository.TransferRepositoryImpl
import com.awd.driverouter.domain.provider.CloudProvider
import com.awd.driverouter.domain.repository.CloudRepository
import com.awd.driverouter.domain.repository.TransferRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindCloudRepository(impl: CloudRepositoryImpl): CloudRepository

    @Binds
    @Singleton
    abstract fun bindTransferRepository(impl: TransferRepositoryImpl): TransferRepository

    @Binds
    @IntoSet
    abstract fun bindGoogleDriveProvider(impl: GoogleDriveProvider): CloudProvider

    @Binds
    @IntoSet
    abstract fun bindOneDriveProvider(impl: OneDriveProvider): CloudProvider

    @Binds
    @IntoSet
    abstract fun bindDropboxProvider(impl: DropboxProvider): CloudProvider

    @Binds
    @IntoSet
    abstract fun bindBoxProvider(impl: BoxProvider): CloudProvider

    @Binds
    @IntoSet
    abstract fun bindWebDavProvider(impl: WebDavProvider): CloudProvider

    @Binds
    @IntoSet
    abstract fun bindSftpProvider(impl: SftpProvider): CloudProvider
}

@Module
@InstallIn(SingletonComponent::class)
object ProviderModule {
    @Provides
    fun provideCloudProviders(providers: Set<@JvmSuppressWildcards CloudProvider>): List<CloudProvider> {
        return providers.toList()
    }
}
