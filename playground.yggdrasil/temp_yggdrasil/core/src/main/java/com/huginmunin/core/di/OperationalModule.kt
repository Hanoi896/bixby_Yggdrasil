package com.huginmunin.core.di

import com.huginmunin.core.repository.LogRepository
import com.huginmunin.core.repository.TrusteeRepository
import com.huginmunin.core.security.BreakGlassManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object OperationalModule {

    @Provides
    @Singleton
    fun provideBreakGlassManager(
        logRepository: LogRepository,
        trusteeRepository: TrusteeRepository
    ): BreakGlassManager {
        return BreakGlassManager(logRepository, trusteeRepository)
    }
}
