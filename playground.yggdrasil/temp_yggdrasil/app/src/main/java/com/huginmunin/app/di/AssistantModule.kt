package com.huginmunin.app.di

import android.content.Context
import com.huginmunin.app.assistant.CalendarManager
import com.huginmunin.app.assistant.SpeechManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AssistantModule {

    @Provides
    @Singleton
    fun provideSpeechManager(@ApplicationContext context: Context): SpeechManager {
        return SpeechManager(context)
    }

    @Provides
    @Singleton
    fun provideCalendarManager(@ApplicationContext context: Context): CalendarManager {
        return CalendarManager(context)
    }
}
