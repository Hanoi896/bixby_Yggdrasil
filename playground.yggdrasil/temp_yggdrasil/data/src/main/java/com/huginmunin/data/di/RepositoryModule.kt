package com.huginmunin.data.di

import com.huginmunin.core.repository.LogRepository
import com.huginmunin.core.repository.TrusteeRepository
import com.huginmunin.data.repository.LogRepositoryImpl
import com.huginmunin.data.repository.TrusteeRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindLogRepository(
        logRepositoryImpl: LogRepositoryImpl
    ): LogRepository

    @Binds
    @Singleton
    abstract fun bindTrusteeRepository(
        trusteeRepositoryImpl: TrusteeRepositoryImpl
    ): TrusteeRepository
}
