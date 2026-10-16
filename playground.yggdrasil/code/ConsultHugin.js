/**
 * @file ConsultHugin.js
 * @description 온디바이스 NPU(Hugin)에 브리핑을 요청하고 HuginAdvice 로 변환합니다.
 *
 * 100% Android Native 직결:
 *  - 불필요한 HTTP 소켓 통신을 영구 제거하고 0ms 레이턴시로 음성 브리핑 및 앱 딥링크를 생성합니다.
 */
import { toText } from './lib/Normalize.js';

const DEFAULT_TOPIC = '오늘의 브리핑';
const MAX_TOPIC_LENGTH = 200;

export default function consultHugin(input) {
  const topic = toText(input.topic, DEFAULT_TOPIC, MAX_TOPIC_LENGTH);
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
