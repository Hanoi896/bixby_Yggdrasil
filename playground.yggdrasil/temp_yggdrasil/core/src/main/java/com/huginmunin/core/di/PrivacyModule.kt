package com.huginmunin.core.di

import android.content.Context
import com.huginmunin.core.privacy.LearningControlManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object PrivacyModule {

    @Provides
    @Singleton
    fun provideLearningControlManager(@ApplicationContext context: Context): LearningControlManager {
        return LearningControlManager(context)
    }
}
