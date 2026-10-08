package com.huginmunin.app.di

import android.content.Context
import androidx.room.Room
import com.huginmunin.app.munin.db.MuninDao
import com.huginmunin.app.munin.db.MuninDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object MuninModule {

    @Provides
    @Singleton
    fun provideMuninDatabase(@ApplicationContext context: Context): MuninDatabase {
        return Room.databaseBuilder(
            context,
            MuninDatabase::class.java,
            "munin_memory.db"
        )
            // ⚠️ 개발 중에만 사용 - 메인 스레드에서 쿼리 허용
            // Flow를 사용하므로 실제로는 백그라운드에서 실행됨
            .allowMainThreadQueries()
            // 스키마 변경 시 데이터 손실을 감수하고 재생성
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    fun provideMuninDao(database: MuninDatabase): MuninDao {
        return database.muninDao()
    }
}
