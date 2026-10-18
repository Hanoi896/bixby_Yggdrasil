#ifndef EXAONE_RUNTIME_H
#define EXAONE_RUNTIME_H

#include <string>
#include <vector>
#include <functional>
#include <memory>

/**
 * HardwareAccelerationType
 * 퀄컴 스냅드래곤 8 엘리트 AP 가속 백엔드 열거형
 */
enum class HardwareAccelerationType {
    LITERT_NPU,      // Google LiteRT (구 TFLite / MediaPipe GenAI) NPU 표준 가속
    ONNX_RUNTIME_QNN,// ONNX Runtime Mobile + Qualcomm QNN Execution Provider 표준 가속
    QNN_NPU,         // Qualcomm Hexagon HTP/QNN NPU (초저전력, 최고 속도)
    VULKAN_GPU,      // Qualcomm Adreno 830 GPU Vulkan 백엔드
    CPU_NEON         // ARMv8.4-A NEON/FP16 6코어 CPU 폴백
};

struct ExaoneConfig {
    std::string modelPath;
    HardwareAccelerationType acceleration = HardwareAccelerationType::LITERT_NPU;
    int32_t contextLength = 4096;
    int32_t threadCount = 6;      // S26 Ultra 고성능 코어 수
    int32_t gpuLayers = 32;       // NPU/GPU 전체 32 레이어 오프로딩
    bool useMmap = true;          // 2초 내 고속 부팅을 위한 mmap 활성화
    float temperature = 0.2f;
    float topP = 0.9f;
    int32_t maxPredictionTokens = 1024; // 모바일 최대 생성 토큰 수
};

struct ChatMessage {
    std::string role;    // "system", "user", "assistant"
    std::string content;
};

/**
 * ExaoneRuntime
 * LG EXAONE 3.0 2.4B 온디바이스 NPU 추론 엔진
 */
class ExaoneRuntime {
public:
    ExaoneRuntime();
    ~ExaoneRuntime();

    /**
     * 모델 초기화 및 메모리 로드 (mmap & NPU/GPU 가속)
     * @return 로드 소요 시간(ms)
     */
    int64_t initialize(const ExaoneConfig& config);

    /**
     * 현재 로드된 모델 자원 해제
     */
    void release();

    /**
     * LMK(Low Memory Killer) 대비 mmap 가중치 페이지 및 KV 캐시 슬림화 (콜드스타트 0초 방어)
     */
    void trimMemoryPages();

    /**
     * 챗 스트리밍 추론 실행
     * (Stop Sequence 인터셉터 및 시스템 프롬프트 Pinning 적용)
     * @param messages 대화 히스토리 리스트
     * @param onTokenReceived 토큰 실시간 스트리밍 콜백
     * @return 전체 생성된 텍스트
     */
    std::string generateStream(
        const std::vector<ChatMessage>& messages,
        std::function<void(const std::string& token)> onTokenReceived
    );

    /**
     * EXAONE 3.0 특화 프롬프트 템플릿 포맷팅
     * [|system|]...[|endofturn|]\n[|user|]...[|endofturn|]\n[|assistant|]
     */
    std::string formatExaonePrompt(const std::vector<ChatMessage>& messages);

    /**
     * 텍스트의 고차원 시맨틱 임베딩 텐서 추출 (RAG 의미 검색용)
     */
    std::vector<float> computeEmbedding(const std::string& text);

    /**
     * 특수 제어 토큰 마크업 클렌징
     * [|endofturn|], [|user|], [|assistant|], [|system|] 등 필터링
     */
    static std::string cleanseSpecialTokens(const std::string& rawText);

    /**
     * 4096-ctx 초과 방지 시스템 프롬프트(페르소나) 영구 고정 및 중간 대화 자동 요약
     */
    std::vector<ChatMessage> pinSystemPromptAndSlidingWindow(
        const std::vector<ChatMessage>& messages,
        int32_t maxBudgetTokens
    );

    /**
     * 현재 엔진 상태 조회
     */
    bool isLoaded() const;
    float getInferenceTokensPerSec() const;
    size_t getEstimatedRamUsageMb() const;
    std::string getActiveBackendName() const;

    /**
     * KV 캐시 전체 초기화 (새 대화 세션 시작 시)
     */
    void clearKvCache();

    /**
     * KV 캐시 선택적 슬롯 프루닝 (주제 급변 시 특정 위치 이후 토큰 제거)
     * llama_kv_cache_seq_rm(ctx, 0, p0, p1) 연동
     */
    void pruneKvCache(int32_t p0, int32_t p1);

private:
    class Impl;
    std::unique_ptr<Impl> pImpl;
};

#endif // EXAONE_RUNTIME_H
