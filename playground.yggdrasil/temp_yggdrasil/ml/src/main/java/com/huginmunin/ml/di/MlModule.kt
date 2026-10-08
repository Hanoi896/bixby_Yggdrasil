package com.huginmunin.ml.di

import android.content.Context
import com.huginmunin.ml.TextClassificationHelper
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object MlModule {

    @Provides
    @Singleton
    fun provideTextClassificationHelper(@ApplicationContext context: Context): TextClassificationHelper {
        return TextClassificationHelper(context)
    }
}
