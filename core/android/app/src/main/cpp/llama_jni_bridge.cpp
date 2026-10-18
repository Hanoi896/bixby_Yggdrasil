#if defined(__ANDROID__) || defined(ANDROID)
#include <jni.h>
#elif __has_include(<jni.h>)
#include <jni.h>
#else
// Host IDE(VS Code clangd) 정적 분석용 JNI 타입 스텁
#ifndef JNIEXPORT
#define JNIEXPORT
#endif
#ifndef JNICALL
#define JNICALL
#endif
typedef void* jobject;
typedef void* jclass;
typedef void* jstring;
typedef void* jarray;
typedef void* jobjectArray;
typedef void* jfloatArray;
typedef void* jmethodID;
typedef unsigned char jboolean;
typedef int jint;
typedef int jsize;
#define JNI_TRUE 1
#define JNI_FALSE 0

struct JNIEnv {
    const char* GetStringUTFChars(jstring, jboolean*) { return ""; }
    void ReleaseStringUTFChars(jstring, const char*) {}
    jstring NewStringUTF(const char*) { return nullptr; }
    jfloatArray NewFloatArray(jsize) { return nullptr; }
    void SetFloatArrayRegion(jfloatArray, jsize, jsize, const float*) {}
    jsize GetArrayLength(jarray) { return 0; }
    jobject GetObjectArrayElement(jobjectArray, jsize) { return nullptr; }
    void DeleteLocalRef(jobject) {}
    bool ExceptionCheck() { return false; }
    void ExceptionClear() {}
    jclass GetObjectClass(jobject) { return nullptr; }
    jmethodID GetMethodID(jclass, const char*, const char*) { return nullptr; }
    void CallVoidMethod(jobject, jmethodID, ...) {}
};
#endif

#include <string>
#include <memory>
#include <cstdio>
#if defined(__ANDROID__) || defined(ANDROID)
#include <android/log.h>
#define LOG_TAG "YggdrasilJni"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#else
#define LOGI(...) printf(__VA_ARGS__)
#define LOGE(...) fprintf(stderr, __VA_ARGS__)
#endif
#include "ExaoneRuntime.h"

static std::shared_ptr<ExaoneRuntime> g_runtime = nullptr;

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_yggdrasil_assistant_llm_LocalLlmService_initEngineNative(
    JNIEnv *env,
    jobject /* this */,
    jstring modelPath_,
    jint accelerationType
) {
    if (!modelPath_) {
        LOGE("JNI initEngineNative: modelPath_ is null");
        return JNI_FALSE;
    }

    const char *modelPath = env->GetStringUTFChars(modelPath_, nullptr);
    if (!modelPath) {
        LOGE("JNI initEngineNative: GetStringUTFChars returned null");
        return JNI_FALSE;
    }

    ExaoneConfig config;
    config.modelPath = modelPath;
    config.acceleration = static_cast<HardwareAccelerationType>(accelerationType);
    config.useMmap = true;
    config.threadCount = 6;
    config.contextLength = 4096;

    g_runtime = std::make_shared<ExaoneRuntime>();
    int64_t bootTimeMs = g_runtime->initialize(config);

    env->ReleaseStringUTFChars(modelPath_, modelPath);
    return (bootTimeMs >= 0) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_yggdrasil_assistant_llm_LocalLlmService_releaseEngineNative(
    JNIEnv * /* env */,
    jobject /* this */
) {
    if (g_runtime) {
        g_runtime->release();
        g_runtime.reset();
        LOGI("JNI: 온디바이스 NPU 추론 엔진 메모리 자원 완전 해제");
    }
}

JNIEXPORT void JNICALL
Java_com_yggdrasil_assistant_llm_LocalLlmService_trimMemoryNative(
    JNIEnv * /* env */,
    jobject /* this */
) {
    if (g_runtime && g_runtime->isLoaded()) {
        g_runtime->trimMemoryPages();
        LOGI("JNI: LMK 대응 mmap 가중치 슬림화 및 KV 캐시 회수 완료");
    }
}

JNIEXPORT jstring JNICALL
Java_com_yggdrasil_assistant_llm_LocalLlmService_generateTextNative(
    JNIEnv *env,
    jobject /* this */,
    jstring prompt_
) {
    if (!g_runtime || !g_runtime->isLoaded()) {
        return env->NewStringUTF("Error: Model not loaded");
    }

    if (!prompt_) {
        return env->NewStringUTF("Error: Null prompt");
    }

    const char *prompt = env->GetStringUTFChars(prompt_, nullptr);
    if (!prompt) {
        return env->NewStringUTF("Error: Invalid prompt memory");
    }

    std::vector<ChatMessage> messages = {
        {"system", "당신은 갤럭시 S26 울트라 온디바이스 개인 비서 Yggdrasil입니다."},
        {"user", prompt}
    };

    std::string response;
    try {
        response = g_runtime->generateStream(messages, nullptr);
    } catch (const std::exception &e) {
        LOGE("generateTextNative C++ 예외 발생: %s", e.what());
        env->ReleaseStringUTFChars(prompt_, prompt);
        return env->NewStringUTF("Error: Native text inference failed");
    }

    env->ReleaseStringUTFChars(prompt_, prompt);

    if (env->ExceptionCheck()) {
        env->ExceptionClear();
        LOGE("generateTextNative JNI 경계 예외 감지 -> ExceptionClear() 호출로 네이티브 크래시 차단");
    }

    return env->NewStringUTF(response.c_str());
}

JNIEXPORT jstring JNICALL
Java_com_yggdrasil_assistant_llm_LocalLlmService_generateChatNative(
    JNIEnv *env,
    jobject /* this */,
    jstring systemPrompt_,
    jobjectArray historyRoles_,
    jobjectArray historyContents_,
    jstring currentPrompt_
) {
    if (!g_runtime || !g_runtime->isLoaded()) {
        return env->NewStringUTF("Error: Model not loaded");
    }

    std::vector<ChatMessage> messages;

    // 1. 고정될 시스템 프롬프트(페르소나) 주입
    if (systemPrompt_ != nullptr) {
        const char *sysPrompt = env->GetStringUTFChars(systemPrompt_, nullptr);
        if (sysPrompt) {
            messages.push_back({"system", sysPrompt});
            env->ReleaseStringUTFChars(systemPrompt_, sysPrompt);
        }
    }
    if (messages.empty()) {
        messages.push_back({"system", "당신은 갤럭시 S26 울트라 온디바이스 개인 비서 Yggdrasil입니다."});
    }

    // 2. 대화 히스토리 주입
    if (historyRoles_ != nullptr && historyContents_ != nullptr) {
        jsize roleCount = env->GetArrayLength(historyRoles_);
        jsize contentCount = env->GetArrayLength(historyContents_);
        jsize count = (roleCount < contentCount) ? roleCount : contentCount;

        for (jsize i = 0; i < count; ++i) {
            jstring rStr = (jstring)env->GetObjectArrayElement(historyRoles_, i);
            jstring cStr = (jstring)env->GetObjectArrayElement(historyContents_, i);

            if (rStr && cStr) {
                const char *rChars = env->GetStringUTFChars(rStr, nullptr);
                const char *cChars = env->GetStringUTFChars(cStr, nullptr);

                if (rChars && cChars) {
                    messages.push_back({rChars, cChars});
                }

                if (rChars) env->ReleaseStringUTFChars(rStr, rChars);
                if (cChars) env->ReleaseStringUTFChars(cStr, cChars);
            }

            if (rStr) env->DeleteLocalRef(rStr);
            if (cStr) env->DeleteLocalRef(cStr);
        }
    }

    // 3. 현재 사용자 프롬프트 주입
    if (currentPrompt_ != nullptr) {
        const char *curPrompt = env->GetStringUTFChars(currentPrompt_, nullptr);
        if (curPrompt) {
            messages.push_back({"user", curPrompt});
            env->ReleaseStringUTFChars(currentPrompt_, curPrompt);
        }
    }

    std::string response;
    try {
        response = g_runtime->generateStream(messages, nullptr);
    } catch (const std::exception &e) {
        LOGE("generateStream C++ 예외 발생: %s", e.what());
        return env->NewStringUTF("Error: Native inference failed");
    }

    if (env->ExceptionCheck()) {
        env->ExceptionClear();
        LOGE("JNI 경계 예외 감지 -> ExceptionClear() 호출로 네이티브 SIGSEGV 크래시 차단");
    }

    return env->NewStringUTF(response.c_str());
}

JNIEXPORT jstring JNICALL
Java_com_yggdrasil_assistant_llm_LocalLlmService_generateChatStreamNative(
    JNIEnv *env,
    jobject /* this */,
    jstring systemPrompt_,
    jobjectArray historyRoles_,
    jobjectArray historyContents_,
    jstring currentPrompt_,
    jobject callback_
) {
    if (!g_runtime || !g_runtime->isLoaded()) {
        return env->NewStringUTF("Error: Model not loaded");
    }

    std::vector<ChatMessage> messages;

    // 1. 고정 시스템 프롬프트(페르소나) 주입
    if (systemPrompt_ != nullptr) {
        const char *sysPrompt = env->GetStringUTFChars(systemPrompt_, nullptr);
        if (sysPrompt) {
            messages.push_back({"system", sysPrompt});
            env->ReleaseStringUTFChars(systemPrompt_, sysPrompt);
        }
    }
    if (messages.empty()) {
        messages.push_back({"system", "당신은 갤럭시 S26 울트라 온디바이스 개인 비서 Yggdrasil입니다."});
    }

    // 2. 대화 히스토리 주입
    if (historyRoles_ != nullptr && historyContents_ != nullptr) {
        jsize roleCount = env->GetArrayLength(historyRoles_);
        jsize contentCount = env->GetArrayLength(historyContents_);
        jsize count = (roleCount < contentCount) ? roleCount : contentCount;

        for (jsize i = 0; i < count; ++i) {
            jstring rStr = (jstring)env->GetObjectArrayElement(historyRoles_, i);
            jstring cStr = (jstring)env->GetObjectArrayElement(historyContents_, i);

            if (rStr && cStr) {
                const char *rChars = env->GetStringUTFChars(rStr, nullptr);
                const char *cChars = env->GetStringUTFChars(cStr, nullptr);

                if (rChars && cChars) {
                    messages.push_back({rChars, cChars});
                }

                if (rChars) env->ReleaseStringUTFChars(rStr, rChars);
                if (cChars) env->ReleaseStringUTFChars(cStr, cChars);
            }

            if (rStr) env->DeleteLocalRef(rStr);
            if (cStr) env->DeleteLocalRef(cStr);
        }
    }

    // 3. 현재 사용자 프롬프트 주입
    if (currentPrompt_ != nullptr) {
        const char *curPrompt = env->GetStringUTFChars(currentPrompt_, nullptr);
        if (curPrompt) {
            messages.push_back({"user", curPrompt});
            env->ReleaseStringUTFChars(currentPrompt_, curPrompt);
        }
    }

    // 4. Java/Kotlin 토큰 콜백 메서드 탐색
    jmethodID onTokenMethod = nullptr;
    if (callback_ != nullptr) {
        jclass cbClass = env->GetObjectClass(callback_);
        if (cbClass != nullptr) {
            onTokenMethod = env->GetMethodID(cbClass, "onToken", "(Ljava/lang/String;)V");
            env->DeleteLocalRef(cbClass);
        }
    }

    std::string response;
    try {
        response = g_runtime->generateStream(messages, [&](const std::string& token) {
            if (callback_ != nullptr && onTokenMethod != nullptr && !token.empty()) {
                jstring tokenStr = env->NewStringUTF(token.c_str());
                if (tokenStr != nullptr) {
                    env->CallVoidMethod(callback_, onTokenMethod, tokenStr);
                    env->DeleteLocalRef(tokenStr);
                    if (env->ExceptionCheck()) {
                        env->ExceptionClear();
                    }
                }
            }
        });
    } catch (const std::exception &e) {
        LOGE("generateChatStreamNative C++ 예외 발생: %s", e.what());
        return env->NewStringUTF("Error: Native streaming inference failed");
    }

    if (env->ExceptionCheck()) {
        env->ExceptionClear();
        LOGE("generateChatStreamNative JNI 경계 예외 감지 -> ExceptionClear() 호출");
    }

    return env->NewStringUTF(response.c_str());
}

JNIEXPORT jstring JNICALL
Java_com_yggdrasil_assistant_llm_LocalLlmService_getEngineStatsNative(
    JNIEnv *env,
    jobject /* this */
) {
    if (!g_runtime || !g_runtime->isLoaded()) {
        return env->NewStringUTF("{\"loaded\": false}");
    }

    char buf[512];
    snprintf(buf, sizeof(buf),
             "{\"loaded\": true, \"ramMb\": %zu, \"tokensPerSec\": %.1f, \"backend\": \"%s\"}",
             g_runtime->getEstimatedRamUsageMb(),
             g_runtime->getInferenceTokensPerSec(),
             g_runtime->getActiveBackendName().c_str());

    return env->NewStringUTF(buf);
}

static jfloatArray computeEmbeddingInternal(JNIEnv *env, jstring text_) {
    if (!g_runtime || !g_runtime->isLoaded() || text_ == nullptr) {
        return nullptr;
    }

    const char *text = env->GetStringUTFChars(text_, nullptr);
    if (!text) {
        return nullptr;
    }
    std::string textStr(text);
    env->ReleaseStringUTFChars(text_, text);

    if (textStr.empty()) {
        return nullptr;
    }

    std::vector<float> emb;
    try {
        emb = g_runtime->computeEmbedding(textStr);
    } catch (const std::exception &e) {
        LOGE("computeEmbeddingInternal C++ 예외 발생: %s", e.what());
        return nullptr;
    }

    if (emb.empty()) {
        return nullptr;
    }

    if (env->ExceptionCheck()) {
        env->ExceptionClear();
        LOGE("computeEmbeddingInternal JNI 경계 예외 감지 -> ExceptionClear() 호출로 네이티브 크래시 차단");
    }

    jfloatArray result = env->NewFloatArray(static_cast<jsize>(emb.size()));
    if (!result) {
        return nullptr;
    }

    env->SetFloatArrayRegion(result, 0, static_cast<jsize>(emb.size()), emb.data());
    return result;
}

JNIEXPORT jfloatArray JNICALL
Java_com_yggdrasil_assistant_llm_LocalLlmService_computeEmbeddingNative(
    JNIEnv *env,
    jobject /* this */,
    jstring text_
) {
    return computeEmbeddingInternal(env, text_);
}

JNIEXPORT jfloatArray JNICALL
Java_com_yggdrasil_assistant_rag_MuninEmbeddingEngine_computeEmbeddingNative(
    JNIEnv *env,
    jobject /* this */,
    jstring text_
) {
    return computeEmbeddingInternal(env, text_);
}

JNIEXPORT void JNICALL
Java_com_yggdrasil_assistant_llm_LocalLlmService_clearKvCacheNative(
    JNIEnv * /* env */,
    jobject /* this */
) {
    if (g_runtime) {
        g_runtime->clearKvCache();
    }
}

JNIEXPORT void JNICALL
Java_com_yggdrasil_assistant_llm_LocalLlmService_pruneKvCacheNative(
    JNIEnv * /* env */,
    jobject /* this */,
    jint p0,
    jint p1
) {
    if (g_runtime) {
        g_runtime->pruneKvCache(static_cast<int32_t>(p0), static_cast<int32_t>(p1));
    }
}

JNIEXPORT jstring JNICALL
Java_com_yggdrasil_assistant_llm_LocalLlmService_getActiveBackendNameNative(
    JNIEnv *env,
    jobject /* this */
) {
    if (g_runtime && g_runtime->isLoaded()) {
        std::string name = g_runtime->getActiveBackendName();
        return env->NewStringUTF(name.c_str());
    }
    return env->NewStringUTF("미탑재 / 언로드 상태");
}

JNIEXPORT jboolean JNICALL
Java_com_yggdrasil_assistant_llm_LocalLlmService_isModelLoadedNative(
    JNIEnv * /* env */,
    jobject /* this */
) {
    return (g_runtime && g_runtime->isLoaded()) ? JNI_TRUE : JNI_FALSE;
}

}

