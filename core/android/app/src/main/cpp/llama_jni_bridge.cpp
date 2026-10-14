#include <jni.h>
#include <string>
#include <memory>
#include "ExaoneRuntime.h"
#include "LocalHttpServer.h"

static std::shared_ptr<ExaoneRuntime> g_runtime = nullptr;
static std::unique_ptr<LocalHttpServer> g_server = nullptr;

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_yggdrasil_assistant_llm_LocalLlmService_initEngineNative(
    JNIEnv *env,
    jobject /* this */,
    jstring modelPath_,
    jint accelerationType
) {
    const char *modelPath = env->GetStringUTFChars(modelPath_, nullptr);

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

JNIEXPORT jboolean JNICALL
Java_com_yggdrasil_assistant_llm_LocalLlmService_startDaemonServerNative(
    JNIEnv *env,
    jobject /* this */,
    jint port
) {
    if (!g_runtime || !g_runtime->isLoaded()) {
        return JNI_FALSE;
    }

    g_server = std::make_unique<LocalHttpServer>(g_runtime);
    bool started = g_server->start(port);
    return started ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_yggdrasil_assistant_llm_LocalLlmService_stopDaemonServerNative(
    JNIEnv *env,
    jobject /* this */
) {
    if (g_server) {
        g_server->stop();
        g_server.reset();
    }
    if (g_runtime) {
        g_runtime->release();
        g_runtime.reset();
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

    const char *prompt = env->GetStringUTFChars(prompt_, nullptr);
    std::vector<ChatMessage> messages = {
        {"system", "당신은 갤럭시 S26 울트라 온디바이스 개인 비서 Yggdrasil입니다."},
        {"user", prompt}
    };

    std::string response = g_runtime->generateStream(messages, nullptr);

    env->ReleaseStringUTFChars(prompt_, prompt);
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

    char buf[256];
    snprintf(buf, sizeof(buf),
             "{\"loaded\": true, \"ramMb\": %zu, \"tokensPerSec\": %.1f}",
             g_runtime->getEstimatedRamUsageMb(),
             g_runtime->getInferenceTokensPerSec());

    return env->NewStringUTF(buf);
}

}
