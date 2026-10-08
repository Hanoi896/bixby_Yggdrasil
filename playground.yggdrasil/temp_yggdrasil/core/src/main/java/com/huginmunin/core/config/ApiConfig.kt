package com.huginmunin.core.config

/**
 * API 키 및 설정 상수 중앙 관리
 * 
 * 모든 외부 서비스 API 키와 설정 값을 한 곳에서 관리합니다.
 * 프로덕션 배포 시 BuildConfig 또는 환경 변수로 대체 권장.
 */
object ApiConfig {
    
    // ==================== Stripe ====================
    /**
     * Stripe Publishable Key (테스트 환경)
     * 실제 결제에는 프로덕션 키 필요
     */
    const val STRIPE_PUBLISHABLE_KEY = "pk_test_TYooMQauvdEDq54NiTphI7jx"
    
    
    // ==================== MQTT ====================
    /**
     * HiveMQ Cloud MQTT 브로커 URL
     * ssl:// 프로토콜은 TLS 암호화 사용
     */
    const val MQTT_BROKER_URL = "ssl://85c482ddcc5470f6e30fac92cfc56de.s1.eu.hivemq.cloud:8883"
    
    /** MQTT 메시지 Quality of Service 레벨 (0, 1, 2) */
    const val MQTT_DEFAULT_QOS = 1
    
    /** MQTT Keep-Alive 간격 (초) */
    const val MQTT_KEEP_ALIVE_INTERVAL = 60
    
    /** MQTT 연결 타임아웃 (초) */
    const val MQTT_CONNECTION_TIMEOUT = 10
    
    
    // ==================== FIDO2 ====================
    /**
     * FIDO2 Relying Party ID
     * 앱의 도메인 또는 패키지명
     */
    const val FIDO2_RP_ID = "huginmunin.app"
    
    /** FIDO2 Relying Party 이름 */
    const val FIDO2_RP_NAME = "Hugin-Munin"
    
    /** FIDO2 등록 요청 코드 */
    const val FIDO2_REGISTER_REQUEST_CODE = 1001
    
    /** FIDO2 서명 요청 코드 */
    const val FIDO2_SIGN_REQUEST_CODE = 1002
    
    
    // ==================== Database ====================
    /** 암호화된 Room 데이터베이스 파일명 */
    const val DATABASE_NAME = "hugin_munin.db"
}
