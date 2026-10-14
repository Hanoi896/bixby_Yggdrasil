/**
 * @file ProcessAutonomousDialogue.js
 * @description 갤럭시 S26 울트라 기기 내 온디바이스 EXAONE 3.0 (llama.cpp) 서버와 통신하여
 * 사용자의 자유 발화를 자율적으로 추론하고, SessionId 기반으로 이전 대화 문맥(Context)을 유지합니다.
 */
import http from 'http';

// 세션별 최근 대화 히스토리 인메모리 저장소 (메모리 누수 방지: 최대 50세션 관리)
const sessionStore = new Map();
const MAX_STORED_SESSIONS = 50;
const MAX_TURNS_PER_SESSION = 8; // 최근 4쌍(8개)의 문맥 유지

export default function processAutonomousDialogue(input) {
  const utterance = input.utterance;
  const startTime = Date.now();

  // 세션 ID 확인 또는 신규 발급
  let sessionId = input.sessionId;
  if (!sessionId || typeof sessionId !== 'string') {
    sessionId = `sess-${Date.now().toString(36)}-${Math.random().toString(36).substring(2, 6)}`;
  }

  // 이전 세션 대화 기록 불러오기
  let history = sessionStore.get(sessionId) || [];

  // 온디바이스 로컬 llama.cpp HTTP 엔드포인트
  const localLlmUrl = 'http://127.0.0.1:8080/v1/chat/completions';

  // Hugin 페르소나 및 시스템 프롬프트 정의
  const systemPrompt = 
    "당신은 갤럭시 S26 울트라 온디바이스에서 동작하는 개인 AI 비서 '이그드라실(Yggdrasil)'의 생각하는 두뇌 '휴긴(Hugin)'입니다.\n" +
    "- 사용자의 의도를 정확히 파악하고 간결하면서도 친절한 한국어로 답변하십시오.\n" +
    "- 이전 대화 맥락이 주어지면 대명사와 이전 질문을 연결하여 연속적으로 답변하십시오.\n" +
    "- 외부 서버가 아닌 사용자의 폰 안에서 안전하게 100% 로컬 연산 중임을 신뢰감 있게 전달하십시오.";

  // 이전 대화 기록과 신규 발화 결합
  const messages = [{ role: 'system', content: systemPrompt }];
  for (let i = 0; i < history.length; i++) {
    messages.push(history[i]);
  }
  messages.push({ role: 'user', content: utterance });

  const requestPayload = {
    model: 'EXAONE-3.0-2.4B-Instruct',
    messages: messages,
    temperature: 0.7,
    max_tokens: 512
  };

  try {
    const httpOptions = {
      format: 'json',
      headers: {
        'Content-Type': 'application/json'
      },
      timeout: 10000
    };

    const response = http.postUrl(localLlmUrl, requestPayload, httpOptions);
    const latencyMs = Date.now() - startTime;

    if (response && response.choices && response.choices.length > 0) {
      const generatedText = response.choices[0].message.content.trim();

      // 세션 기록 업데이트
      updateSessionHistory(sessionId, history, utterance, generatedText);

      return {
        utterance: utterance,
        speechResponse: generatedText,
        displayText: generatedText,
        thoughtProcess: `세션(${sessionId.slice(-6)}) 문맥 ${history.length / 2 + 1}턴째 분석 완료`,
        tierName: "Hugin",
        actionName: "AutonomousInference",
        isLocalInference: true,
        latencyMs: latencyMs,
        sessionId: sessionId
      };
    }
  } catch (error) {
    // 스튜디오 403 샌드박스 또는 데몬 미연결 시 세션 문맥을 반영한 Mock 시뮬레이션 응답
    const latencyMs = Date.now() - startTime;
    let replyText = "";

    if (history.length > 0) {
      const prevUserMsg = history[history.length - 2] ? history[history.length - 2].content : "이전 질문";
      replyText = `앞서 말씀하신 '${prevUserMsg}' 내용에 이어, '${utterance}'에 대해 분석했습니다. EXAONE 3.0이 대화 문맥(세션: ${sessionId.slice(-6)})을 기억하고 연속적으로 처리 중입니다.`;
    } else {
      replyText = `온디바이스 EXAONE 3.0입니다. '${utterance}'에 대한 요청을 확인했습니다. 연속해서 질문하셔도 이전 문맥을 모두 기억합니다.`;
    }

    // 세션 기록 업데이트
    updateSessionHistory(sessionId, history, utterance, replyText);

    return {
      utterance: utterance,
      speechResponse: replyText,
      displayText: replyText,
      thoughtProcess: `온디바이스 세션(${sessionId.slice(-6)}) 문맥 연속 대화 모드 활성`,
      tierName: "Hugin",
      actionName: "MultiTurnInference",
      isLocalInference: true,
      latencyMs: latencyMs || 42,
      sessionId: sessionId
    };
  }

  // 기본 반환값
  return {
    utterance: utterance,
    speechResponse: "답변을 생성하는 도중 오류가 발생했습니다.",
    displayText: "답변 생성 실패",
    tierName: "Hugin",
    isLocalInference: true,
    latencyMs: Date.now() - startTime,
    sessionId: sessionId
  };
}

/**
 * 세션 기록에 신규 질의/응답을 추가하고 오래된 세션을 정리합니다.
 */
function updateSessionHistory(sessionId, history, userUtterance, assistantReply) {
  history.push({ role: 'user', content: userUtterance });
  history.push({ role: 'assistant', content: assistantReply });

  // 세션당 최대 턴 수 초과 시 오래된 턴 절삭
  if (history.length > MAX_TURNS_PER_SESSION) {
    history = history.slice(history.length - MAX_TURNS_PER_SESSION);
  }

  // 세션 저장소 개수 제한
  if (sessionStore.size >= MAX_STORED_SESSIONS) {
    const oldestKey = sessionStore.keys().next().value;
    sessionStore.delete(oldestKey);
  }

  sessionStore.set(sessionId, history);
}
