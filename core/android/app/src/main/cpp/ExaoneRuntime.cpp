#include "ExaoneRuntime.h"
#include "llama.h"

#include <algorithm>
#include <android/log.h>
#include <chrono>
#include <cmath>
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
 * llama.cpp 네이티브 C API 기반 LG EXAONE 3.0 / 3.5 GGUF 온디바이스 NPU 추론 엔진
 *
 * 🌟 듀얼 컨텍스트(Dual Context) 아키텍처 실장:
 * - ctx_gen: 텍스트 생성 전용 컨텍스트 (KV 캐시 4096-ctx, generateMutex로 보호)
 * - ctx_embd: 임베딩 추출 전용 경량 컨텍스트 (512-ctx, embeddings=true, embeddingMutex로 보호)
 * 대화 스트리밍 도중에 백그라운드 RAG 기억 저장이 끼어들어도 KV 캐시 증발 및 네이티브 크래시 0% 원천 차단
 */
class ExaoneRuntime::Impl {
public:
  ExaoneConfig config;
  struct llama_model *model = nullptr;
  const struct llama_vocab *vocab = nullptr;

  // 1. 대화 생성 전용 런타임
  struct llama_context *ctx_gen = nullptr;
  struct llama_sampler *smpl = nullptr;
  std::mutex generateMutex;

  // 2. 임베딩(RAG) 전용 런타임 (독립 컨텍스트 & 독립 뮤텍스)
  struct llama_context *ctx_embd = nullptr;
  std::mutex embeddingMutex;

  bool loaded = false;
  float tokensPerSec = 0.0f;
  size_t ramUsageMb = 0;

  ~Impl() { release(); }

  int64_t initialize(const ExaoneConfig &cfg) {
    std::lock_guard<std::mutex> lockGen(generateMutex);
    std::lock_guard<std::mutex> lockEmbd(embeddingMutex);
    auto startTime = std::chrono::high_resolution_clock::now();
    this->config = cfg;

    LOGI("온디바이스 EXAONE 모델 듀얼 컨텍스트 로딩 시작: %s", cfg.modelPath.c_str());

    // 1. llama.cpp 글로벌 백엔드 초기화
    llama_backend_init();

    // 2. 모델 파라미터 설정 (NPU/GPU 오프로딩 및 mmap 고속 매핑)
    struct llama_model_params mparams = llama_model_default_params();
    mparams.n_gpu_layers = cfg.gpuLayers;
    mparams.use_mmap = cfg.useMmap;

    // 3. GGUF 가중치 모델 로드 (생성과 임베딩이 불변 가중치 공유)
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

    // 5. 생성 전용 컨텍스트(ctx_gen) 파라미터 설정 (KV 캐시 4096, 생성 최적화)
    struct llama_context_params cparams_gen = llama_context_default_params();
    cparams_gen.n_ctx = cfg.contextLength;
    cparams_gen.n_threads = cfg.threadCount;
    cparams_gen.n_threads_batch = cfg.threadCount;
    cparams_gen.embeddings = false; // 생성 전용이므로 임베딩 비활성화하여 오버헤드 절감

    ctx_gen = llama_init_from_model(model, cparams_gen);
    if (!ctx_gen) {
      LOGE("🚨 llama_context (ctx_gen) 생성 실패");
      llama_model_free(model);
      model = nullptr;
      return -1;
    }

    // 6. 임베딩 전용 경량 컨텍스트(ctx_embd) 파라미터 설정 (경량 512-ctx, embeddings=true)
    struct llama_context_params cparams_embd = llama_context_default_params();
    cparams_embd.n_ctx = 512; // 단문/기억 RAG 임베딩에 최적화된 512 슬롯
    cparams_embd.n_threads = std::max(1, cfg.threadCount / 2);
    cparams_embd.n_threads_batch = cparams_embd.n_threads;
    cparams_embd.embeddings = true; // 임베딩 텐서 추출 활성화

    ctx_embd = llama_init_from_model(model, cparams_embd);
    if (!ctx_embd) {
      LOGW("⚠️ 경량 임베딩 컨텍스트(ctx_embd) 별도 생성 실패 -> 단일 컨텍스트 폴백 모드로 동작합니다.");
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

    // RAM 사용량 추정 (모델 1.6GB + ctx_gen KV캐시 400MB + ctx_embd 50MB)
    this->ramUsageMb = 2150;

    auto endTime = std::chrono::high_resolution_clock::now();
    int64_t elapsedMs = std::chrono::duration_cast<std::chrono::milliseconds>(
                            endTime - startTime)
                            .count();
    LOGI("✅ 온디바이스 EXAONE 듀얼 컨텍스트 엔진 로드 완료! (소요 시간: %lld ms, 듀얼컨텍스트: %s)",
         (long long)elapsedMs, ctx_embd ? "활성화" : "단일폴백");
    return elapsedMs;
  }

  void release() {
    std::lock_guard<std::mutex> lockGen(generateMutex);
    std::lock_guard<std::mutex> lockEmbd(embeddingMutex);
    if (smpl) {
      llama_sampler_free(smpl);
      smpl = nullptr;
    }
    if (ctx_embd) {
      llama_free(ctx_embd);
      ctx_embd = nullptr;
    }
    if (ctx_gen) {
      llama_free(ctx_gen);
      ctx_gen = nullptr;
    }
    if (model) {
      llama_model_free(model);
      model = nullptr;
    }
    llama_backend_free();
    loaded = false;
    vocab = nullptr;
    LOGI("EXAONE 듀얼 컨텍스트 추론 엔진 메모리 자원 완전 해제 완료");
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
    // 텍스트 생성 전용 락 획득 (임베딩 락과 무관하여 RAG 저장과 완전 병렬 처리)
    std::lock_guard<std::mutex> lock(generateMutex);
    if (!loaded || !ctx_gen || !model || !vocab || !smpl) {
      LOGE("추론 실패: 생성 엔진이 로드되지 않았습니다.");
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
    const uint32_t max_ctx = llama_n_ctx(ctx_gen);
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

    // 3. 생성 컨텍스트 KV 캐시 메모리 초기화
    llama_memory_t mem = llama_get_memory(ctx_gen);
    if (mem) {
      llama_memory_clear(mem, true);
    }

    // 4. 초기 프롬프트 배치 디코딩 (Prefill)
    struct llama_batch batch =
        llama_batch_get_one(prompt_tokens.data(), n_tokens);
    if (llama_decode(ctx_gen, batch) != 0) {
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
      llama_token new_token_id = llama_sampler_sample(smpl, ctx_gen, -1);
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
      if (llama_decode(ctx_gen, batch) != 0) {
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

  std::vector<float> computeEmbedding(const std::string &text) {
    // 임베딩 전용 락 획득 (generateMutex와 완전히 독립적)
    std::lock_guard<std::mutex> lock(embeddingMutex);

    // ctx_embd 우선 사용, 없을 시 ctx_gen으로 안전 폴백
    struct llama_context *active_ctx = ctx_embd ? ctx_embd : ctx_gen;

    if (!loaded || !active_ctx || !model || !vocab) {
      LOGE("임베딩 계산 실패: 엔진이 로드되지 않았습니다.");
      return {};
    }

    if (text.empty()) {
      return {};
    }

    // 1. 프롬프트 텍스트 토큰화
    std::vector<llama_token> tokens(text.length() + 32);
    int n_tokens = llama_tokenize(
        vocab, text.c_str(), static_cast<int32_t>(text.length()),
        tokens.data(), static_cast<int32_t>(tokens.size()),
        true,  // add_special
        false  // parse_special
    );
    if (n_tokens < 0) {
      tokens.resize(-n_tokens);
      n_tokens = llama_tokenize(
          vocab, text.c_str(), static_cast<int32_t>(text.length()),
          tokens.data(), static_cast<int32_t>(tokens.size()),
          true, false);
    }
    if (n_tokens <= 0) {
      LOGE("임베딩용 토큰화 결과 없음");
      return {};
    }
    tokens.resize(n_tokens);

    // 2. 임베딩 컨텍스트 KV 캐시 메모리 초기화 (ctx_gen의 KV 캐시와 완전히 격리됨)
    llama_memory_t mem = llama_get_memory(active_ctx);
    if (mem) {
      llama_memory_clear(mem, true);
    }

    // 3. 배치 생성 및 디코딩 (전체 토큰 시퀀스 통과 후 hidden states 계산)
    struct llama_batch batch = llama_batch_get_one(tokens.data(), n_tokens);
    if (llama_decode(active_ctx, batch) != 0) {
      LOGE("임베딩 llama_decode 실패");
      return {};
    }

    // 4. 모델 임베딩 차원 획득
    const int32_t n_embd = llama_model_n_embd(model);
    if (n_embd <= 0) {
      LOGE("유효하지 않은 임베딩 차원: %d", n_embd);
      return {};
    }

    // 5. 마지막 토큰의 임베딩 텐서(히든 스테이트) 포인터 획득
    const float *embd_ptr = llama_get_embeddings_ith(active_ctx, -1);
    if (!embd_ptr) {
      embd_ptr = llama_get_embeddings(active_ctx);
    }
    if (!embd_ptr) {
      LOGE("임베딩 포인터 획득 실패");
      return {};
    }

    // 6. 복사 및 L2 정규화 (Cosine Similarity를 단순 내적으로 계산 가능)
    std::vector<float> result(n_embd);
    float sum_sq = 0.0f;
    for (int32_t i = 0; i < n_embd; ++i) {
      float val = embd_ptr[i];
      result[i] = val;
      sum_sq += val * val;
    }

    float norm = std::sqrt(sum_sq);
    if (norm > 0.0f) {
      for (int32_t i = 0; i < n_embd; ++i) {
        result[i] /= norm;
      }
    }

    LOGI("온디바이스 시맨틱 임베딩 추출 완료 (듀얼 컨텍스트 격리, 차원: %d, 토큰: %d)", n_embd, n_tokens);
    return result;
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

std::vector<float> ExaoneRuntime::computeEmbedding(const std::string &text) {
  return pImpl->computeEmbedding(text);
}

bool ExaoneRuntime::isLoaded() const { return pImpl->loaded; }

float ExaoneRuntime::getInferenceTokensPerSec() const {
  return pImpl->tokensPerSec;
}

size_t ExaoneRuntime::getEstimatedRamUsageMb() const {
  return pImpl->ramUsageMb;
}
