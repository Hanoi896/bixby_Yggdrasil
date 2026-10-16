package com.yggdrasil.assistant.llm

/**
 * ExaoneModelConfig
 * LG EXAONE 3.0 2.4B 온디바이스 NPU 모델 사양 및 런타임 제약 상수
 */
object ExaoneModelConfig {
    const val MODEL_NAME = "EXAONE-3.0-2.4B-Instruct"
    const val QUANTIZATION_TYPE = "Q4_K_M"
    const val MODEL_FILE_NAME = "exaone-3.0-2.4b-instruct-q4_k_m.gguf"
    const val ESTIMATED_MODEL_SIZE_BYTES = 1680000000L // 약 1.6GB

    // 하드웨어 가속 타입
    const val ACCELERATION_QNN_NPU = 0    // Qualcomm Snapdragon 8 Elite Hexagon NPU
    const val ACCELERATION_VULKAN_GPU = 1 // Adreno GPU Vulkan 백엔드
    const val ACCELERATION_CPU_NEON = 2   // ARMv8.4 NEON CPU 백엔드

    // 시스템 리소스 및 성능 한계점 (SLA)
    const val MAX_COLD_BOOT_TIME_MS = 2000L   // 모델 부팅 시간 2초 이내
    const val MAX_RAM_USAGE_MB = 2500         // 폰 백그라운드 RAM 점유 2.5GB 이하
    const val TARGET_MIN_TOKENS_PER_SEC = 30f // 초당 최소 30 토큰 이상 스트리밍
    const val TARGET_MAX_TOKENS_PER_SEC = 45f // 초당 최대 45 토큰

    // Android Native Intent 딥링크 스키마 설정
    const val INTENT_URI_SCHEME = "yggdrasil"
    const val INTENT_HOST_ACTION = "action"
    const val INTENT_HOST_CHAT = "chat"
}
