/**
 * @file ProcessAutonomousDialogue.js
 * @description 갤럭시 S26 울트라 기기 내 온디바이스 EXAONE 3.0 (llama.cpp) 서버와 통신하여
 * 사용자의 자유 발화를 자율적으로 추론하고 답변을 생성합니다.
 */
import http from 'http';

export default function processAutonomousDialogue(input) {
  const utterance = input.utterance;
  const startTime = Date.now();

  // 온디바이스 로컬 llama.cpp HTTP 엔드포인트 (기기 내부 루프백)
  const localLlmUrl = 'http://127.0.0.1:8080/v1/chat/completions';

  // Hugin 페르소나 및 시스템 프롬프트 정의
  const systemPrompt = 
    "당신은 갤럭시 S26 울트라 온디바이스에서 동작하는 개인 AI 비서 '이그드라실(Yggdrasil)'의 생각하는 두뇌 '휴긴(Hugin)'입니다.\n" +
    "- 사용자의 의도를 정확히 파악하고 간결하면서도 친절한 한국어로 답변하십시오.\n" +
    "- 일정, 결제, IoT 제어 등의 행동이 필요한 경우 필요한 작업과 함께 답변을 제시하십시오.\n" +
    "- 외부 서버가 아닌 사용자의 폰 안에서 안전하게 처리 중임을 신뢰감 있게 전달하십시오.";

  const requestPayload = {
    model: 'EXAONE-3.0-2.4B-Instruct',
    messages: [
      { role: 'system', content: systemPrompt },
      { role: 'user', content: utterance }
    ],
    temperature: 0.7,
    max_tokens: 512
  };

  try {
    const httpOptions = {
      format: 'json',
      headers: {
        'Content-Type': 'application/json'
      },
      // 폰 내부 루프백 통신이므로 타임아웃 10초 설정
      timeout: 10000
    };

    const response = http.postUrl(localLlmUrl, requestPayload, httpOptions);
    const latencyMs = Date.now() - startTime;

    if (response && response.choices && response.choices.length > 0) {
      const generatedText = response.choices[0].message.content.trim();

      return {
        utterance: utterance,
        speechResponse: generatedText,
        displayText: generatedText,
        thoughtProcess: "온디바이스 EXAONE 3.0 모델이 의도를 분석하여 답변을 도출했습니다.",
        tierName: "Hugin",
        actionName: "AutonomousInference",
        isLocalInference: true,
        latencyMs: latencyMs
      };
    }
  } catch (error) {
    // 로컬 데몬이 미실행 상태이거나 개발/테스트 중일 때의 Fallback 안내
    const latencyMs = Date.now() - startTime;
    const fallbackMessage = `"${utterance}" 명령을 전달받았습니다. 현재 온디바이스 로컬 AI 데몬(127.0.0.1:8080) 구동 대기 상태입니다. 모델이 실행되면 EXAONE 3.0이 완벽히 자율 응답합니다.`;

    return {
      utterance: utterance,
      speechResponse: fallbackMessage,
      displayText: fallbackMessage,
      thoughtProcess: "로컬 LLM 서버(127.0.0.1:8080) 연결 상태 대기 중",
      tierName: "Hugin",
      actionName: "OfflineFallback",
      isLocalInference: true,
      latencyMs: latencyMs
    };
  }

  // 기본 반환값
  return {
    utterance: utterance,
    speechResponse: "죄송합니다. 답변을 생성하는 도중 오류가 발생했습니다.",
    displayText: "답변 생성 실패",
    tierName: "Hugin",
    isLocalInference: true,
    latencyMs: Date.now() - startTime
  };
}
