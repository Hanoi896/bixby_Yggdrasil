---
trigger: always_on
description: 안드로이드 네이티브 NPU 및 시스템 제어 실전 개발 표준 규칙
---

# 📱 Android On-Device NPU & System Automation Master Rules

본 규칙은 Android OS, C++ JNI, 온디바이스 NPU, 접근성 서비스 기반 화면 제어 코드 작성 시 반드시 준수해야 하는 필수 엔지니어링 표준입니다.

---

## 1. JNI & C++ 네이티브 메모리 안전성 (Native Stability)

- **JNI 핸들 즉시 해제**: `GetStringUTFChars`, `GetByteArrayElements` 호출 후 작업이 완료되면 동일 스코프 내에서 반드시 `ReleaseStringUTFChars`, `ReleaseByteArrayElements`를 호출하여 네이티브 힙 메모리 누수를 원천 차단한다.
- **예외 발생 검사 (`ExceptionCheck`)**: JNI 경계를 넘나드는 모든 호출 직후 `env->ExceptionCheck()`를 검사하여 보류 중인 JVM 예외가 네이티브 SIGSEGV 크래시로 번지지 않도록 `ExceptionClear()` 및 안전한 에러 반환을 수행한다.
- **포인터 널 체크**: 네이티브 `llama_context*`, `llama_model*` 인스턴스 참조 시 반드시 `nullptr` 검증을 선행한다.

---

## 2. 메인 스레드 보호 및 ANR 원천 방지 (Zero-ANR Policy)

- **UI 스레드 NPU/IO 작업 절대 금지**: NPU 텐서 연산, GGUF 모델 로딩, Google ML Kit OCR 파싱, SQLCipher DB 쿼리는 절대 `Dispatchers.Main`에서 실행하지 않는다.
- **코루틴 디스패처 엄격 분리**:
  - 파일 I/O, 암호화 DB 쿼리, HTTP 통신: `Dispatchers.IO`
  - NPU 임베딩 추출, 벡터 내적 연산, 이미지 비트맵 처리: `Dispatchers.Default`
- **타임아웃 안전망**: 모든 비동기 NPU 추론 및 액션 실행에는 `withTimeoutOrNull(15_000)` 등의 상한선을 두어 무한 블로킹을 방지한다.

---

## 3. 접근성 서비스 & 화면 자동화 안전 가드레일 (Safety & Fail-Safe)

- **사용자 우선권 (User Preemption)**: AI가 화면 조작을 수행하는 도중 사용자의 물리적 화면 터치가 감지되면 즉시 모든 자동 조작 제스처를 캔슬하고 제어권을 사용자에게 반환한다.
- **보안 화면 (`FLAG_SECURE`) 감지 즉시 중단**: 토스, 뱅킹, OTT 등 보안 캡처 방지 플래그가 적용된 검은 화면(Blackout) 감지 시 캡처 시도를 즉각 중단하고 음성 TTS로 사용자 직접 입력을 유도한다.
- **1-Shot 온디맨드 캡처**: 가상 디스플레이(VirtualDisplay)를 24시간 켜두지 않고, 화면 인식이 필요한 순간에만 1프레임 캡처 후 즉시 리소스를 해제한다.

---

## 4. 24시간 초저전력 방어 코딩 원칙 (Sub-Watt Architecture)

- **무한 폴링 루프 금지**: 코루틴 `while(true) { delay(1000) }` 같은 상시 백그라운드 폴링을 전면 금지하며, Android 순정 `BroadcastReceiver` 및 이벤트 콜백으로만 리스너를 유지한다.
- **Wakelock 최소화**: 시스템 슬립(Doze Mode)을 방해하는 `PARTIAL_WAKE_LOCK`의 남발을 금지하며, NPU 연산 시에만 일시 획득 후 연산 종료 즉시(`finally`) 해제한다.
- **Race-to-Sleep 준수**: 연산은 하드웨어 가속(NPU/NEON)을 통해 최대 속도로 빠르게 완료한 뒤, 단말기를 즉각 저전력 대기 상태로 복귀시킨다.
