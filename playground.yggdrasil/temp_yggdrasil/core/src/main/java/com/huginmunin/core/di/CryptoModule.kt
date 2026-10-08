package com.huginmunin.core.di

import android.content.Context
import com.huginmunin.core.crypto.CryptoManager
import com.huginmunin.core.crypto.TinkCryptoManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CryptoModule {

    @Provides
    @Singleton
    fun provideCryptoManager(@ApplicationContext context: Context): CryptoManager {
        return TinkCryptoManager(context)
    }
}
