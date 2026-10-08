package com.huginmunin.core.di

import android.content.Context
import com.huginmunin.core.payment.PaymentManager
import com.huginmunin.core.security.HardwareKeyManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Security Module
 * 
 * 보안 관련 컴포넌트 의존성 주입 모듈
 * FIDO2 하드웨어 키 인증 및 Stripe 결제 관리자 제공
 */
@Module
@InstallIn(SingletonComponent::class)
object SecurityModule {

    /**
     * FIDO2 하드웨어 키 관리자 제공
     * 
     * YubiKey 등 외부 보안 키를 사용한 인증에 필요
     * L10 (Full Autonomy) 권한 활성화 시 사용됨
     */
    @Provides
    @Singleton
    fun provideHardwareKeyManager(
        @dagger.hilt.android.qualifiers.ApplicationContext context: Context
    ): HardwareKeyManager {
        return HardwareKeyManager(context)
    }

    /**
     * Stripe 결제 관리자 제공
     * 
     * 앱 내 결제 기능 (L5+ 권한 필요)
     * API 키는 ApiConfig에서 중앙 관리됨
     */
    @Provides
    @Singleton
    fun providePaymentManager(
        @dagger.hilt.android.qualifiers.ApplicationContext context: Context
    ): PaymentManager {
        return PaymentManager(context)
    }
}
