# 릴리스 빌드 난독화 규칙 (현재 isMinifyEnabled = false)

# JNI 네이티브 메서드 이름 보존 (Java_com_yggdrasil_assistant_llm_LocalLlmService_*)
-keepclasseswithmembernames class * {
    native <methods>;
}

# SQLCipher
-keep class net.sqlcipher.** { *; }
