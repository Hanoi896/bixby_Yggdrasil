#include "ExaoneRuntime.h"
#include "llama.h"

#include <algorithm>
#include <android/log.h>
#include <chrono>
#include <iostream>
#include <mutex>
#include <sstream>
#include <vector>

#define TAG "ExaoneRuntime"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

/**
 * [ExaoneRuntime::Impl]
 * llama.cpp 네이티브 C API 기반 LG EXAONE 3.0 / 3.5 GGUF 온디바이스 NPU 추론
 * 엔진
 */
class ExaoneRuntime::Impl {
public:
  ExaoneConfig config;
  struct llama_model *model = nullptr;
  struct llama_context *ctx = nullptr;
  const struct llama_vocab *vocab = nullptr;
  struct llama_sampler *smpl = nullptr;

  bool loaded = false;
  float tokensPerSec = 0.0f;
  size_t ramUsageMb = 0;
  std::mutex inferenceMutex;

  ~Impl() { release(); }

  int64_t initialize(const ExaoneConfig &cfg) {
    std::lock_guard<std::mutex> lock(inferenceMutex);
    auto startTime = std::chrono::high_resolution_clock::now();
    this->config = cfg;

    LOGI("온디바이스 EXAONE 모델 로딩 시작: %s", cfg.modelPath.c_str());

    // 1. llama.cpp 글로벌 백엔드 초기화
    llama_backend_init();

    // 2. 모델 파라미터 설정 (NPU/GPU 오프로딩 및 mmap 고속 매핑)
    struct llama_model_params mparams = llama_model_default_params();
    mparams.n_gpu_layers = cfg.gpuLayers;
    mparams.use_mmap = cfg.useMmap;

    // 3. GGUF 가중치 모델 로드
    model = llama_model_load_from_file(cfg.modelPath.c_str(), mparams);
    if (!model) {
      LOGE("🚨 GGUF 모델 로드 실패: %s", cfg.modelPath.c_str());
      return -1;
    }

    // 4. 모델 어휘(Vocab) 획득
    vocab = llama_model_get_vocab(model);
    if (!vocab) {
      LOGE("🚨 모델 어휘(Vocab) 파싱 실패");
      llama_model_free(model);
      model = nullptr;
      return -1;
    }

    // 5. 컨텍스트 파라미터 설정 (멀티스레딩 & 컨텍스트 크기)
    struct llama_context_params cparams = llama_context_default_params();
    cparams.n_ctx = cfg.contextLength;
    cparams.n_threads = cfg.threadCount;
    cparams.n_threads_batch = cfg.threadCount;

    // 6. 실행 컨텍스트 생성
    ctx = llama_init_from_model(model, cparams);
    if (!ctx) {
      LOGE("🚨 llama_context 생성 실패");
      llama_model_free(model);
      model = nullptr;
      return -1;
    }

    // 7. 샘플러 체인 초기화 (Top-K, Top-P, Temperature)
    struct llama_sampler_chain_params sparams =
        llama_sampler_chain_default_params();
    smpl = llama_sampler_chain_init(sparams);
    llama_sampler_chain_add(smpl, llama_sampler_init_top_k(50));
    llama_sampler_chain_add(smpl, llama_sampler_init_top_p(cfg.topP, 1));
    llama_sampler_chain_add(smpl, llama_sampler_init_temp(cfg.temperature));
    llama_sampler_chain_add(smpl, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));

    this->loaded = true;

    // RAM 사용량 추정 (모델 크기 약 1.4~1.6GB + KV 캐시 0.5GB)
    this->ramUsageMb = 2100;

    auto endTime = std::chrono::high_resolution_clock::now();
    int64_t elapsedMs = std::chrono::duration_cast<std::chrono::milliseconds>(
                            endTime - startTime)
                            .count();
    LOGI("✅ 온디바이스 EXAONE 추론 엔진 로드 완료! (소요 시간: %lld ms)",
         (long long)elapsedMs);
    return elapsedMs;
  }

  void release() {
    std::lock_guard<std::mutex> lock(inferenceMutex);
    if (smpl) {
      llama_sampler_free(smpl);
      smpl = nullptr;
    }
    if (ctx) {
      llama_free(ctx);
      ctx = nullptr;
    }
    if (model) {
      llama_model_free(model);
      model = nullptr;
    }
    llama_backend_free();
    loaded = false;
    vocab = nullptr;
    LOGI("EXAONE 추론 엔진 메모리 자원 완전 해제 완료");
  }

  std::string formatExaonePrompt(const std::vector<ChatMessage> &messages) {
    std::stringstream ss;
    for (const auto &msg : messages) {
      if (msg.role == "system") {
        ss << "[|system|]\n" << msg.content << "[|endofturn|]\n";
      } else if (msg.role == "user") {
        ss << "[|user|]\n" << msg.content << "[|endofturn|]\n";
      } else if (msg.role == "assistant") {
        ss << "[|assistant|]\n" << msg.content << "[|endofturn|]\n";
      }
    }
    ss << "[|assistant|]\n";
    return ss.str();
  }

  std::string generateStream(
      const std::vector<ChatMessage> &messages,
      std::function<void(const std::string &token)> onTokenReceived) {
    std::lock_guard<std::mutex> lock(inferenceMutex);
    if (!loaded || !ctx || !model || !vocab || !smpl) {
      LOGE("추론 실패: 엔진이 로드되지 않았습니다.");
      return "Error: On-device engine not initialized";
    }

    std::string prompt = formatExaonePrompt(messages);

    // 1. 텍스트 프롬프트 토큰화 (llama_tokenize)
    const int n_prompt_max = static_cast<int>(prompt.length() + 128);
    std::vector<llama_token> prompt_tokens(n_prompt_max);
    int n_tokens = llama_tokenize(
        vocab, prompt.c_str(), static_cast<int32_t>(prompt.length()),
        prompt_tokens.data(), static_cast<int32_t>(prompt_tokens.size()),
        true, // add_special
        true  // parse_special
    );

    if (n_tokens < 0) {
      prompt_tokens.resize(-n_tokens);
      n_tokens = llama_tokenize(
          vocab, prompt.c_str(), static_cast<int32_t>(prompt.length()),
          prompt_tokens.data(), static_cast<int32_t>(prompt_tokens.size()),
          true, true);
    }

    if (n_tokens <= 0) {
      LOGE("토큰화 결과 유효 토큰 없음");
      return "Error: failed to tokenize prompt";
    }
    prompt_tokens.resize(n_tokens);

    // 2. 컨텍스트 크기 보호
    const uint32_t max_ctx = llama_n_ctx(ctx);
    if (static_cast<uint32_t>(n_tokens) >= max_ctx - 128) {
      LOGW("프롬프트가 컨텍스트 윈도우 한계에 근접했습니다 (%d / %u)", n_tokens,
           max_ctx);
      int trim_count = n_tokens - static_cast<int>(max_ctx - 256);
      if (trim_count > 0 && trim_count < n_tokens) {
        prompt_tokens.erase(prompt_tokens.begin(),
                            prompt_tokens.begin() + trim_count);
        n_tokens = static_cast<int>(prompt_tokens.size());
      }
    }

    // 3. KV 캐시 메모리 초기화
    llama_memory_t mem = llama_get_memory(ctx);
    if (mem) {
      llama_memory_clear(mem, true);
    }

    // 4. 초기 프롬프트 배치 디코딩 (Prefill)
    struct llama_batch batch =
        llama_batch_get_one(prompt_tokens.data(), n_tokens);
    if (llama_decode(ctx, batch) != 0) {
      LOGE("프롬프트 llama_decode 사전 처리 실패");
      return "Error: failed to decode prompt into KV cache";
    }

    // 5. 자기회귀(Autoregressive) 토큰 생성 루프
    std::stringstream fullResponse;
    int n_cur = n_tokens;
    const int n_max_predict = 1024; // 모바일 최대 생성 토큰 수
    int n_generated = 0;
    auto startTime = std::chrono::high_resolution_clock::now();

    while (n_generated < n_max_predict &&
           static_cast<uint32_t>(n_cur) < max_ctx) {
      // 샘플링 실행
      llama_token new_token_id = llama_sampler_sample(smpl, ctx, -1);
      llama_sampler_accept(smpl, new_token_id);

      // 종료 토큰 검사 (EOG, EOS, EOT)
      if (llama_vocab_is_eog(vocab, new_token_id) ||
          new_token_id == llama_vocab_eot(vocab) ||
          new_token_id == llama_vocab_eos(vocab)) {
        break;
      }

      // 토큰을 UTF-8 문자열 조각으로 변환
      char piece_buf[256];
      int n_piece = llama_token_to_piece(vocab, new_token_id, piece_buf,
                                         sizeof(piece_buf), 0, false);
      if (n_piece > 0) {
        std::string piece(piece_buf, n_piece);
        fullResponse << piece;
        if (onTokenReceived) {
          onTokenReceived(piece);
        }
      }

      // 생성된 단일 토큰 디코딩
      batch = llama_batch_get_one(&new_token_id, 1);
      if (llama_decode(ctx, batch) != 0) {
        LOGE("단일 토큰 llama_decode 실패 (token=%d)", new_token_id);
        break;
      }

      n_cur++;
      n_generated++;
    }

    auto endTime = std::chrono::high_resolution_clock::now();
    int64_t elapsedMs = std::chrono::duration_cast<std::chrono::milliseconds>(
                            endTime - startTime)
                            .count();
    if (elapsedMs > 0 && n_generated > 0) {
      tokensPerSec = (n_generated * 1000.0f) / elapsedMs;
      LOGI("온디바이스 NPU 추론 완료: %d 토큰 생성, %lld ms 소요 (%.1f t/s)",
           n_generated, (long long)elapsedMs, tokensPerSec);
    }

    return fullResponse.str();
  }
};

ExaoneRuntime::ExaoneRuntime() : pImpl(std::make_unique<Impl>()) {}
ExaoneRuntime::~ExaoneRuntime() = default;

int64_t ExaoneRuntime::initialize(const ExaoneConfig &config) {
  return pImpl->initialize(config);
}

void ExaoneRuntime::release() { pImpl->release(); }

std::string ExaoneRuntime::generateStream(
    const std::vector<ChatMessage> &messages,
    std::function<void(const std::string &token)> onTokenReceived) {
  return pImpl->generateStream(messages, onTokenReceived);
}

std::string
ExaoneRuntime::formatExaonePrompt(const std::vector<ChatMessage> &messages) {
  return pImpl->formatExaonePrompt(messages);
}

bool ExaoneRuntime::isLoaded() const { return pImpl->loaded; }

float ExaoneRuntime::getInferenceTokensPerSec() const {
  return pImpl->tokensPerSec;
}

size_t ExaoneRuntime::getEstimatedRamUsageMb() const {
  return pImpl->ramUsageMb;
}
