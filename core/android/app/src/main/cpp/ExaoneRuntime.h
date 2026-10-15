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
    QNN_NPU,      // Qualcomm Hexagon NPU (초저전력, 최고 속도)
    VULKAN_GPU,   // Adreno GPU Vulkan 백엔드
    CPU_NEON      // ARMv8 NEON/FP16 폴백
};

struct ExaoneConfig {
    std::string modelPath;
    HardwareAccelerationType acceleration = HardwareAccelerationType::QNN_NPU;
    int32_t contextLength = 4096;
    int32_t threadCount = 6;      // S26 Ultra 고성능 코어 수
    int32_t gpuLayers = 32;       // NPU/GPU 전체 오프로딩
    bool useMmap = true;          // 2초 내 고속 부팅을 위한 mmap 활성화
    float temperature = 0.2f;
    float topP = 0.9f;
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
     * 모델 초기화 및 메모리 로드 (mmap & NPU 가속)
     * @return 로드 소요 시간(ms)
     */
    int64_t initialize(const ExaoneConfig& config);

    /**
     * 현재 로드된 모델 자원 해제
     */
    void release();

    /**
     * 챗 스트리밍 추론 실행
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
     * llama_decode 후 hidden states로부터 L2 정규화된 밀집 벡터(Dense Embedding) 반환
     * @param text 입력 문장 또는 단락
     * @return L2 정규화된 float 임베딩 벡터
     */
    std::vector<float> computeEmbedding(const std::string& text);

    /**
     * 현재 엔진 상태 조회
     */
    bool isLoaded() const;
    float getInferenceTokensPerSec() const;
    size_t getEstimatedRamUsageMb() const;

private:
    class Impl;
    std::unique_ptr<Impl> pImpl;
};

#endif // EXAONE_RUNTIME_H
