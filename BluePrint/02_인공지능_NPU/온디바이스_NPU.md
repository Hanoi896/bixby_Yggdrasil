# 🧠 온디바이스 NPU 추론 엔진 설계 청사진 (`온디바이스_NPU.md`)

> **핵심 과제**: 클라우드 의존 없는 100% 온디바이스 LLM 구동. LG EXAONE 3.0 2.4B 모델을 Snapdragon 8 Elite NPU 위에서 초당 35~45 토큰 속도로 스트리밍 추론.

---

## 📑 핵심 아키텍처

```text
┌────────────────────────────────────────────────────────────────────────┐
│                        Kotlin / Android App Layer                      │
│  - LocalLlmService.kt: 백그라운드 서비스 상주, ServiceConnection 바인딩   │
│  - ExaoneModelDownloader.kt: 1.6GB GGUF 이어받기 & SHA-256 검증기       │
└──────────────────────────────────┬─────────────────────────────────────┘
                                   │ (JNI Native Bridge)
┌──────────────────────────────────▼─────────────────────────────────────┐
│                    C++ Native Layer (ExaoneRuntime.cpp)                │
│                                                                        │
│   [ctx_gen: 텍스트 생성 전용]          [ctx_embd: 임베딩 추출 전용]     │
│   - n_ctx = 4096 (대화 윈도우)          - n_ctx = 512 (경량 슬롯)       │
│   - generateMutex 독립 보호            - embeddingMutex 독립 보호      │
│   - llama_sampler (Top-K/Top-P/Temp)   - cparams.embeddings = true     │
└──────────────────────────────────┬─────────────────────────────────────┘
                                   │ (GGML / Hardware Backends)
┌──────────────────────────────────▼─────────────────────────────────────┐
│             Qualcomm Snapdragon 8 Elite AP Hardware Accelerators       │
│   - Qualcomm Hexagon HTP NPU (QNN 가속)                                │
│   - Adreno 830 GPU (Vulkan Compute)                                    │
│   - ARMv8.4-A Cortex-X925 (NEON CPU 폴백)                              │
└────────────────────────────────────────────────────────────────────────┘
```

---

## 🔑 주요 구현 명세 및 핵심 파일

1. **듀얼 컨텍스트(Dual Context) 아키텍처**:
   - [ExaoneRuntime.cpp](file:///c:/Users/nhj23/bixby-workspace/bixby_Yggdrasil/core/android/app/src/main/cpp/ExaoneRuntime.cpp): 대화 생성 중 RAG 기억 저장 시 단일 컨텍스트의 KV 캐시가 날아가는 치명적 충돌을 `ctx_gen`과 `ctx_embd` 분리로 완전 해결.
2. **JNI 네이티브 인터페이스**:
   - [llama_jni_bridge.cpp](file:///c:/Users/nhj23/bixby-workspace/bixby_Yggdrasil/core/android/app/src/main/cpp/llama_jni_bridge.cpp): 코틀린 계층과 C++ 엔진 간 스트리밍 토큰 콜백 및 float 임베딩 벡터 직결.
3. **온보딩 모델 프로비저닝**:
   - [ExaoneModelDownloader.kt](file:///c:/Users/nhj23/bixby-workspace/bixby_Yggdrasil/core/android/app/src/main/java/com/yggdrasil/assistant/llm/ExaoneModelDownloader.kt): 1.6GB GGUF 모델 가중치 백그라운드 분할/이어받기 및 무결성 검증.
