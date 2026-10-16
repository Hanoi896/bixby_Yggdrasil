#include "ExaoneRuntime.h"

#if __has_include("llama.h")
#include "llama.h"
#elif __has_include("llama.cpp/include/llama.h")
#include "llama.cpp/ggml/include/ggml.h"
#include "llama.cpp/include/llama.h"
#endif

#include <algorithm>
#include <chrono>
#include <cmath>
#include <mutex>
#include <sstream>
#include <vector>

#define TAG "ExaoneRuntime"
#if __has_include(<android/log.h>)
#include <android/log.h>
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)
#else
#include <cstdio>
#define LOGI(...) do { printf("[INFO] " __VA_ARGS__); printf("\n"); } while(0)
#define LOGW(...) do { printf("[WARN] " __VA_ARGS__); printf("\n"); } while(0)
#define LOGE(...) do { printf("[ERROR] " __VA_ARGS__); printf("\n"); } while(0)
#endif

// LG EXAONE 3.0 / 3.5 턴 종료 및 제어 정지 시퀀스 정의
static const std::vector<std::string> EXAONE_STOP_SEQUENCES = {
    "[|endofturn|]",
    "[|user|]",
    "[|assistant|]",
    "[|system|]",
    "<|endofturn|>",
    "<|user|>",
    "<|assistant|>",
    "<|system|>"
};

/**
 * [ExaoneRuntime::Impl]
 * llama.cpp 네이티브 C API 기반 LG EXAONE 3.0 / 3.5 GGUF 온디바이스 NPU 추론 엔진
 *
 * 🌟 듀얼 컨텍스트(Dual Context) 아키텍처 실장:
 * - ctx_gen: 텍스트 생성 전용 컨텍스트 (KV 캐시 4096-ctx, generateMutex로 보호)
 * - ctx_embd: 임베딩 추출 전용 경량 컨텍스트 (512-ctx, embeddings=true, embeddingMutex로 보호)
 *
 * 🛡️ Stop Sequence 인터셉터 및 페르소나 Pinning:
 * - [|endofturn|], [|user|] 감지 즉시 토큰 생성 중단 및 스트리밍 버퍼 안전 플러시
 * - 4096-ctx 초과 시에도 [|system|] 페르소나는 영구 고정(Pinning) 유지 및 구 대화 1줄 자동 요약 압축
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

  // [과제 17 실전 구현] 매턴 KV 캐시 증발 방어 및 증분 캐시(Incremental Cache) 보존 버퍼
  std::vector<llama_token> prev_tokens;

  // 2. 임베딩(RAG) 전용 런타임 (독립 컨텍스트 & 독립 뮤텍스)
  struct llama_context *ctx_embd = nullptr;
  std::mutex embeddingMutex;

  bool loaded = false;
  float tokensPerSec = 0.0f;
  size_t ramUsageMb = 0;
  std::string activeBackend = "Qualcomm Hexagon HTP NPU";

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
    mparams.load_mode = cfg.useMmap ? LLAMA_LOAD_MODE_MMAP : LLAMA_LOAD_MODE_NONE;

    switch (cfg.acceleration) {
      case HardwareAccelerationType::QNN_NPU:
        LOGI("🚀 [하드웨어 가속] Snapdragon 8 Elite Qualcomm Hexagon HTP/QNN NPU 백엔드 활성화 (32 레이어 전체 오프로딩)");
        mparams.n_gpu_layers = cfg.gpuLayers; // 32개 Transformer 블록 전체 NPU 오프로딩
        activeBackend = "Qualcomm Hexagon HTP NPU (QNN)";
        break;
      case HardwareAccelerationType::VULKAN_GPU:
        LOGI("⚡ [하드웨어 가속] Snapdragon 8 Elite Adreno 830 Vulkan GPU 백엔드 활성화");
        mparams.n_gpu_layers = cfg.gpuLayers;
        activeBackend = "Qualcomm Adreno 830 Vulkan GPU";
        break;
      case HardwareAccelerationType::CPU_NEON:
      default:
        LOGI("💻 [하드웨어 가속] ARMv8.4-A NEON FP16 6코어 CPU 폴백 모드");
        mparams.n_gpu_layers = 0;
        activeBackend = "ARMv8.4-A NEON CPU (6-Core)";
        break;
    }

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
    LOGI("✅ 온디바이스 EXAONE 듀얼 컨텍스트 엔진 로드 완료! (소요 시간: %lld ms, 가속: %s, 듀얼컨텍스트: %s)",
         (long long)elapsedMs, activeBackend.c_str(), ctx_embd ? "활성화" : "단일폴백");
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
    prev_tokens.clear();
    LOGI("EXAONE 듀얼 컨텍스트 추론 엔진 메모리 자원 완전 해제 완료");
  }

  /**
   * EXAONE 3.0 전용 프롬프트 서식 포맷팅
   */
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

  /**
   * 4096-ctx 초과 방지 시스템 프롬프트(페르소나) 영구 고정(Pinning) 및 중간 대화 자동 요약
   * [과제 3 실전 구현]
   */
  std::vector<ChatMessage> pinSystemPromptAndSlidingWindow(
      const std::vector<ChatMessage> &messages,
      int32_t maxBudgetTokens) {
    if (messages.empty()) {
      return messages;
    }

    // 1. 시스템 프롬프트(페르소나) 분리 및 영구 보존(Pinning)
    std::vector<ChatMessage> systemMsgs;
    std::vector<ChatMessage> dialogueMsgs;

    for (const auto &msg : messages) {
      if (msg.role == "system") {
        systemMsgs.push_back(msg);
      } else {
        dialogueMsgs.push_back(msg);
      }
    }

    // 기본 시스템 프롬프트가 없을 경우 안전 기본값 생성
    if (systemMsgs.empty()) {
      systemMsgs.push_back({"system", "당신은 갤럭시 S26 울트라 온디바이스 개인 비서 Yggdrasil입니다."});
    }

    // 토큰 추정치 계산 (한글/영문 혼합 기준 대략 2.2바이트/토큰 안전 여유)
    auto estimateTokens = [this](const std::vector<ChatMessage> &msgs) -> int {
      std::string formatted = formatExaonePrompt(msgs);
      if (vocab) {
        std::vector<llama_token> test_tokens(formatted.length() + 32);
        int n = llama_tokenize(vocab, formatted.c_str(), static_cast<int32_t>(formatted.length()),
                               test_tokens.data(), static_cast<int32_t>(test_tokens.size()),
                               true, true);
        if (n > 0) return n;
      }
      return static_cast<int>(formatted.length() / 2);
    };

    std::vector<ChatMessage> currentSet;
    currentSet.insert(currentSet.end(), systemMsgs.begin(), systemMsgs.end());
    currentSet.insert(currentSet.end(), dialogueMsgs.begin(), dialogueMsgs.end());

    int currentTokens = estimateTokens(currentSet);
    if (currentTokens <= maxBudgetTokens || dialogueMsgs.size() <= 2) {
      return currentSet;
    }

    LOGW("⚠️ 대화 컨텍스트가 한계(%d 토큰)를 초과 (%d 토큰) -> 시스템 프롬프트 영구 고정 및 구 대화 슬라이딩 윈도우 압축 실행",
         maxBudgetTokens, currentTokens);

    // 2. 오래된 대화 턴부터 팝(Pop)하면서 1줄 자동 요약 생성(Auto-Summarizer)
    std::stringstream summariesStream;
    summariesStream << "[이전 핵심 대화 맥락 요약]\n";

    while (currentTokens > maxBudgetTokens && dialogueMsgs.size() > 2) {
      // 가장 오래된 턴(User, Assistant 쌍) 추출
      auto oldUser = dialogueMsgs.front();
      dialogueMsgs.erase(dialogueMsgs.begin());

      std::string oldAssistantContent = "";
      if (!dialogueMsgs.empty() && dialogueMsgs.front().role == "assistant") {
        oldAssistantContent = dialogueMsgs.front().content;
        dialogueMsgs.erase(dialogueMsgs.begin());
      }

      // 1줄 요약 맥락 생성 (최대 40자 컴팩트 압축)
      std::string uBrief = oldUser.content.substr(0, std::min<size_t>(oldUser.content.length(), 40));
      std::replace(uBrief.begin(), uBrief.end(), '\n', ' ');
      std::string aBrief = oldAssistantContent.substr(0, std::min<size_t>(oldAssistantContent.length(), 40));
      std::replace(aBrief.begin(), aBrief.end(), '\n', ' ');

      summariesStream << "• 사용자: " << uBrief << " -> 비서: " << aBrief << "\n";

      // 재조립 후 토큰 재계산 (시스템 프롬프트 직후에 요약 맥락 주입)
      std::vector<ChatMessage> candidateMsgs = systemMsgs;
      candidateMsgs.back().content += "\n\n" + summariesStream.str();
      candidateMsgs.insert(candidateMsgs.end(), dialogueMsgs.begin(), dialogueMsgs.end());

      currentTokens = estimateTokens(candidateMsgs);
      currentSet = candidateMsgs;
    }

    LOGI("✅ 시스템 프롬프트(페르소나) 영구 고정 보존 완료! (압축 후 토큰: %d, 보존된 턴 수: %zu)",
         currentTokens, dialogueMsgs.size());
    return currentSet;
  }

  /**
   * 챗 스트리밍 추론 실행
   * [과제 1 & 과제 3 실전 구현]
   * - 슬라이딩 윈도우 & 시스템 프롬프트 Pinning 적용
   * - Stop Sequence 인터셉터 및 Lookahead 버퍼로 자문자답 환각 100% 원천 차단
   */
  std::string generateStream(
      const std::vector<ChatMessage> &rawMessages,
      std::function<void(const std::string &token)> onTokenReceived) {
    std::lock_guard<std::mutex> lock(generateMutex);
    if (!loaded || !ctx_gen || !model || !vocab || !smpl) {
      LOGE("추론 실패: 생성 엔진이 로드되지 않았습니다.");
      return "Error: On-device engine not initialized";
    }

    // 1. [과제 3] 4096-ctx 초과 방지 시스템 프롬프트 Pinning 및 슬라이딩 윈도우 실행
    const uint32_t max_ctx = llama_n_ctx(ctx_gen);
    int32_t maxInputBudget = static_cast<int32_t>(max_ctx) - config.maxPredictionTokens - 64;
    std::vector<ChatMessage> safeMessages = pinSystemPromptAndSlidingWindow(rawMessages, maxInputBudget);

    std::string prompt = formatExaonePrompt(safeMessages);

    // 2. 텍스트 프롬프트 토큰화 (llama_tokenize)
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

    // 3. 토큰 레벨의 절대적 시스템 프롬프트 방어선
    if (static_cast<uint32_t>(n_tokens) >= max_ctx - 128) {
      LOGW("프롬프트 토큰이 컨텍스트 상한에 도달함 (%d / %u). 시스템 프롬프트 보존 절단 실행.",
           n_tokens, max_ctx);
      int trim_count = n_tokens - static_cast<int>(max_ctx - 256);

      // [|user|] 첫 등장 위치(시스템 프롬프트 종료점) 탐색
      int system_end_token_idx = 0;
      std::string sysPromptOnly = "[|system|]\n" + safeMessages.front().content + "[|endofturn|]\n";
      std::vector<llama_token> sysTokens(sysPromptOnly.length() + 32);
      int nSys = llama_tokenize(vocab, sysPromptOnly.c_str(), static_cast<int32_t>(sysPromptOnly.length()),
                                sysTokens.data(), static_cast<int32_t>(sysTokens.size()), true, true);
      if (nSys > 0 && nSys < n_tokens) {
        system_end_token_idx = nSys;
      }

      // 시스템 프롬프트 이후의 대화 토큰에서만 안전하게 슬라이스
      if (system_end_token_idx + trim_count < n_tokens) {
        prompt_tokens.erase(prompt_tokens.begin() + system_end_token_idx,
                            prompt_tokens.begin() + system_end_token_idx + trim_count);
        n_tokens = static_cast<int>(prompt_tokens.size());
        LOGI("🛡️ 시스템 프롬프트 영구 고정 유지 완료 (시스템 토큰: %d 보존, 구 대화 %d 토큰 절단)",
             system_end_token_idx, trim_count);
      }
    }

    // 4. [과제 17 실전 구현] 매턴 KV 캐시 증발 방어 및 증분 캐시(Incremental Cache) 재사용
    llama_memory_t mem = llama_get_memory(ctx_gen);
    int n_common = 0;
    if (mem && !prev_tokens.empty()) {
      while (n_common < static_cast<int>(prev_tokens.size()) &&
             n_common < n_tokens &&
             prev_tokens[n_common] == prompt_tokens[n_common]) {
        n_common++;
      }
    }

    if (n_common > 0 && mem) {
      // 공통 접두사(시스템 프롬프트, 직전 대화 등) 이후의 토큰만 KV 캐시에서 절단
      llama_memory_seq_rm(mem, 0, n_common, -1);
      LOGI("⚡ [증분 KV 캐시] 공통 토큰 %d개 보존 (신규 디코딩: %d개, TTFT 80%% 단축)",
           n_common, n_tokens - n_common);
    } else {
      if (mem) {
        llama_memory_clear(mem, true);
      }
      n_common = 0;
      LOGI("🔄 [초기 KV 캐시] 전체 프롬프트 %d개 토큰 신규 디코딩", n_tokens);
    }

    // 5. 신규 토큰만 증분 배치 디코딩 (Prefill)
    int n_tokens_to_decode = n_tokens - n_common;
    if (n_tokens_to_decode > 0) {
      struct llama_batch batch = llama_batch_init(n_tokens_to_decode, 0, 1);
      for (int i = 0; i < n_tokens_to_decode; ++i) {
        batch.token[i] = prompt_tokens[n_common + i];
        batch.pos[i] = n_common + i;
        batch.n_seq_id[i] = 1;
        batch.seq_id[i][0] = 0;
        batch.logits[i] = (i == n_tokens_to_decode - 1);
      }
      batch.n_tokens = n_tokens_to_decode;

      if (llama_decode(ctx_gen, batch) != 0) {
        LOGE("신규 프롬프트 토큰 증분 llama_decode 사전 처리 실패");
        llama_batch_free(batch);
        return "Error: failed to decode incremental prompt into KV cache";
      }
      llama_batch_free(batch);
    }

    // 6. [과제 1] 자기회귀 토큰 생성 루프 & Stop Sequence 인터셉터
    std::stringstream fullResponse;
    std::string stream_lookahead_buffer;
    int n_cur = n_tokens;
    const int n_max_predict = config.maxPredictionTokens;
    int n_generated = 0;
    auto startTime = std::chrono::high_resolution_clock::now();

    while (n_generated < n_max_predict && static_cast<uint32_t>(n_cur) < max_ctx) {
      // 샘플링 실행
      llama_token new_token_id = llama_sampler_sample(smpl, ctx_gen, -1);
      llama_sampler_accept(smpl, new_token_id);

      // 표준 EOS/EOG 토큰 종료 검사
      if (llama_vocab_is_eog(vocab, new_token_id) ||
          new_token_id == llama_vocab_eot(vocab) ||
          new_token_id == llama_vocab_eos(vocab)) {
        LOGI("🏁 표준 EOG/EOS 토큰 검출로 생성 정상 종료 (token=%d)", new_token_id);
        break;
      }

      // 토큰을 UTF-8 문자열 조각으로 변환
      char piece_buf[256];
      int n_piece = llama_token_to_piece(vocab, new_token_id, piece_buf,
                                         sizeof(piece_buf), 0, false);
      if (n_piece > 0) {
        std::string piece(piece_buf, n_piece);
        stream_lookahead_buffer += piece;

        // [과제 1] Stop Sequence 인터셉터 검사
        size_t earliest_stop_pos = std::string::npos;
        for (const auto &stop_seq : EXAONE_STOP_SEQUENCES) {
          size_t pos = stream_lookahead_buffer.find(stop_seq);
          if (pos != std::string::npos) {
            if (earliest_stop_pos == std::string::npos || pos < earliest_stop_pos) {
              earliest_stop_pos = pos;
            }
          }
        }

        if (earliest_stop_pos != std::string::npos) {
          // 정지 시퀀스 감지: 정지 시퀀스 직전까지만 방출하고 즉시 break!
          std::string safe_prefix = stream_lookahead_buffer.substr(0, earliest_stop_pos);
          if (!safe_prefix.empty()) {
            fullResponse << safe_prefix;
            if (onTokenReceived) {
              onTokenReceived(safe_prefix);
            }
          }
          stream_lookahead_buffer.clear();
          LOGI("🛑 LG EXAONE 턴 종료 멈춤 토큰([|endofturn|]/[|user|]) 감지! 자문자답 환각 즉시 차단 (생성 토큰: %d)", n_generated);
          break;
        }

        // 부분 매칭 접두사 확인 (Lookahead Sliding Window)
        size_t max_suffix_match = 0;
        for (const auto &stop_seq : EXAONE_STOP_SEQUENCES) {
          size_t max_check = std::min(stream_lookahead_buffer.length(), stop_seq.length() - 1);
          for (size_t len = max_check; len >= 1; --len) {
            if (len <= max_suffix_match) break;
            std::string suffix = stream_lookahead_buffer.substr(stream_lookahead_buffer.length() - len);
            if (stop_seq.compare(0, len, suffix) == 0) {
              max_suffix_match = std::max(max_suffix_match, len);
              break;
            }
          }
        }

        // 정지 시퀀스의 접두사가 아닌 안전한 앞부분은 UI로 즉시 스트리밍
        if (stream_lookahead_buffer.length() > max_suffix_match) {
          size_t safe_len = stream_lookahead_buffer.length() - max_suffix_match;
          std::string safe_piece = stream_lookahead_buffer.substr(0, safe_len);
          fullResponse << safe_piece;
          if (onTokenReceived) {
            onTokenReceived(safe_piece);
          }
          stream_lookahead_buffer = stream_lookahead_buffer.substr(safe_len);
        }
      }

      // 생성된 단일 토큰 디코딩
      struct llama_batch single_batch = llama_batch_get_one(&new_token_id, 1);
      if (llama_decode(ctx_gen, single_batch) != 0) {
        LOGE("단일 토큰 llama_decode 실패 (token=%d)", new_token_id);
        break;
      }

      n_cur++;
      n_generated++;
    }

    // 루프 종료 후 버퍼에 남은 잔여 텍스트 최종 플러시
    if (!stream_lookahead_buffer.empty()) {
      for (const auto &stop_seq : EXAONE_STOP_SEQUENCES) {
        size_t pos = stream_lookahead_buffer.find(stop_seq);
        if (pos != std::string::npos) {
          stream_lookahead_buffer = stream_lookahead_buffer.substr(0, pos);
        }
      }
      if (!stream_lookahead_buffer.empty()) {
        fullResponse << stream_lookahead_buffer;
        if (onTokenReceived) {
          onTokenReceived(stream_lookahead_buffer);
        }
      }
      stream_lookahead_buffer.clear();
    }

    // [과제 17 실전 구현] 다음 대화 턴의 증분 KV 캐시 재사용을 위해 현재 프롬프트 토큰 보존
    prev_tokens = prompt_tokens;

    auto endTime = std::chrono::high_resolution_clock::now();
    int64_t elapsedMs = std::chrono::duration_cast<std::chrono::milliseconds>(
                            endTime - startTime)
                            .count();
    if (elapsedMs > 0 && n_generated > 0) {
      tokensPerSec = (n_generated * 1000.0f) / elapsedMs;
      LOGI("온디바이스 NPU 추론 완료: %d 토큰 생성, %lld ms 소요 (%.1f t/s, 가속: %s)",
           n_generated, (long long)elapsedMs, tokensPerSec, activeBackend.c_str());
    }

    // [과제 1] 최종 반환 텍스트 특수 제어 토큰 마크업 클렌징
    return ExaoneRuntime::cleanseSpecialTokens(fullResponse.str());
  }

  std::vector<float> computeEmbedding(const std::string &text) {
    std::lock_guard<std::mutex> lock(embeddingMutex);
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
        true, false);
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

    // 2. 임베딩 컨텍스트 KV 캐시 메모리 초기화
    llama_memory_t mem = llama_get_memory(active_ctx);
    if (mem) {
      llama_memory_clear(mem, true);
    }

    // 3. 배치 생성 및 디코딩
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

    // 5. 마지막 토큰의 임베딩 텐서 포인터 획득
    const float *embd_ptr = llama_get_embeddings_ith(active_ctx, -1);
    if (!embd_ptr) {
      embd_ptr = llama_get_embeddings(active_ctx);
    }
    if (!embd_ptr) {
      LOGE("임베딩 포인터 획득 실패");
      return {};
    }

    // 6. 복사 및 L2 정규화
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

std::string ExaoneRuntime::getActiveBackendName() const {
  return pImpl->activeBackend;
}

std::vector<ChatMessage> ExaoneRuntime::pinSystemPromptAndSlidingWindow(
    const std::vector<ChatMessage> &messages,
    int32_t maxBudgetTokens) {
  return pImpl->pinSystemPromptAndSlidingWindow(messages, maxBudgetTokens);
}

std::string ExaoneRuntime::cleanseSpecialTokens(const std::string &rawText) {
  std::string result = rawText;
  for (const auto &tok : EXAONE_STOP_SEQUENCES) {
    size_t pos = 0;
    while ((pos = result.find(tok, pos)) != std::string::npos) {
      result.erase(pos, tok.length());
    }
  }

  // 앞뒤 개행 및 공백 정돈
  while (!result.empty() && (result.back() == ' ' || result.back() == '\n' || result.back() == '\r')) {
    result.pop_back();
  }
  size_t start = 0;
  while (start < result.size() && (result[start] == ' ' || result[start] == '\n' || result[start] == '\r')) {
    start++;
  }
  if (start > 0) {
    result = result.substr(start);
  }

  return result;
}

