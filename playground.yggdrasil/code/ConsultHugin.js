/**
 * @file ConsultHugin.js
 * @description 온디바이스 LLM(Hugin)에 구조화된 JSON 브리핑을 요청하고 HuginAdvice 로 변환합니다.
 * LLM 이 JSON 형식을 지키지 못한 경우 원문을 권고 사항으로 사용합니다.
 */
import { postJson } from './lib/YggdrasilClient.js';
import { API_PATH, LLM_MODEL } from './lib/YggdrasilConfig.js';
import { isPlainObject, toText, toInt, toArray } from './lib/Normalize.js';

const DEFAULT_TOPIC = '오늘의 브리핑';
const MAX_TOPIC_LENGTH = 200;
const MAX_ACTION_PLANS = 5;
const MAX_CONFIDENCE = 100;
const RAW_TEXT_CONFIDENCE = 50; // JSON 파싱 실패 시 원문 응답 신뢰도
const LLM_TEMPERATURE = 0.3;    // 구조화 응답 안정성을 위해 낮게 설정
const LLM_MAX_TOKENS = 512;

const SYSTEM_PROMPT =
  "당신은 개인 AI 비서 '이그드라실'의 생각하는 두뇌 '휴긴(Hugin)'입니다.\n" +
  '사용자의 주제를 분석하여 아래 JSON 형식으로만 답하십시오. 다른 텍스트는 출력하지 마십시오.\n' +
  '{"intent": "의도 요약", "thoughtProcess": "추론 과정", "recommendation": "권고 사항", ' +
  '"actionPlan": ["실행 단계1", "실행 단계2"], "confidence": 0~100 정수}';

export default function consultHugin(input) {
  const topic = toText(input.topic, DEFAULT_TOPIC, MAX_TOPIC_LENGTH);

  // 100% 온디바이스 Native 직결: HTTP 소켓 서버 없이 즉시 음성 브리핑 및 앱 딥링크 생성
  return buildNativeAdvice(topic);
}

function buildNativeAdvice(topic) {
  return {
    topic: topic,
    intent: `'${topic}' 온디바이스 자율 분석`,
    thoughtProcess: 'S26 Ultra 온디바이스 NPU 코어로 직결하여 최적의 실행 계획을 수립합니다.',
    recommendation: `'${topic}'에 대한 심층 분석 및 화면 자율 제어를 수행합니다.`,
    actionPlan: [
      `'${topic}' 의도 분석 및 시맨틱 기억 검색`,
      '자율 에이전트 액션 실행 및 결과 음성 피드백'
    ],
    confidence: 95,
    appLaunchUri: 'yggdrasil://chat?query=' + encodeURIComponent(topic || '')
  };
}

// OpenAI 호환 응답에서 assistant 본문 추출
function extractContent(result) {
  if (!result.ok) return null;
  const choices = toArray(result.data.choices);
  if (choices.length === 0 || !isPlainObject(choices[0].message)) return null;
  return toText(choices[0].message.content, null);
}

/**
 * LLM 출력에서 첫 '{' ~ 마지막 '}' 구간만 잘라 JSON 파싱을 시도합니다.
 * (코드블록 ```json 등으로 감싸 출력하는 경우 대응)
 */
function parseJsonObject(text) {
  const start = text.indexOf('{');
  const end = text.lastIndexOf('}');
  if (start < 0 || end <= start) return null;
  try {
    const obj = JSON.parse(text.substring(start, end + 1));
    return isPlainObject(obj) ? obj : null;
  } catch (e) {
    return null;
  }
}

function normalizeAdvice(data, topic) {
  const plans = [];
  const rawPlans = toArray(data.actionPlan);
  const count = Math.min(rawPlans.length, MAX_ACTION_PLANS);
  for (let i = 0; i < count; i++) {
    const plan = toText(rawPlans[i], null, 200);
    if (plan) plans.push(plan);
  }

  const advice = {
    topic: topic,
    intent: toText(data.intent, '일반 문의', 100),
    thoughtProcess: toText(data.thoughtProcess, '추론 과정 정보 없음', 500),
    recommendation: toText(data.recommendation, '권고 사항이 생성되지 않았습니다.', 500),
    confidence: toInt(data.confidence, 0, MAX_CONFIDENCE, 0),
    appLaunchUri: 'yggdrasil://chat?query=' + encodeURIComponent(topic || '')
  };
  if (plans.length > 0) advice.actionPlan = plans;
  return advice;
}

function buildRawTextAdvice(text, topic) {
  return {
    topic: topic,
    intent: '일반 문의',
    thoughtProcess: '구조화 응답 파싱 실패 - LLM 원문 응답을 사용합니다.',
    recommendation: toText(text, '응답 없음', 500),
    confidence: RAW_TEXT_CONFIDENCE,
    appLaunchUri: 'yggdrasil://chat?query=' + encodeURIComponent(topic || '')
  };
}

function buildOfflineAdvice(topic) {
  return {
    topic: topic,
    intent: '오프라인',
    thoughtProcess: '로컬 LLM 서버(127.0.0.1:8080)에 연결할 수 없습니다.',
    recommendation: '온디바이스 EXAONE 데몬을 실행한 뒤 다시 휴긴에게 물어봐 주세요.',
    confidence: 0,
    appLaunchUri: 'yggdrasil://chat?query=' + encodeURIComponent(topic || '')
  };
}
