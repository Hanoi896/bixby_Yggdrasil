package com.huginmunin.data.di

import android.content.Context
import com.huginmunin.core.crypto.CryptoManager
import com.huginmunin.data.backup.BackupManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object BackupModule {

    @Provides
    @Singleton
    fun provideBackupManager(
        @ApplicationContext context: Context,
        cryptoManager: CryptoManager
    ): BackupManager {
        return BackupManager(context, cryptoManager)
    }
}
