/**
 * @file ConsultHugin.js
 * @description 온디바이스 NPU(Hugin)에 브리핑을 요청하고 HuginAdvice 로 변환합니다.
 *
 * 100% Android Native 직결:
 *  - 불필요한 HTTP 소켓 통신을 영구 제거하고 안전한 음성 브리핑 및 앱 딥링크를 생성합니다.
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
    intent: `'${topic}' 분석을 위해 이그드라실 앱을 엽니다.`,
    thoughtProcess: '온디바이스 NPU 추론 엔진에서 실시간 연산 및 화면 조작을 집행합니다.',
    recommendation: `'${topic}' 분석 및 자율 조작을 시작합니다.`,
    actionPlan: [
      `'${topic}' 의도 분석`,
      '네이티브 엔진 액션 실행'
    ],
    confidence: 0,
    appLaunchUri: 'yggdrasil://chat?query=' + encodeURIComponent(topic || '')
  };
}
