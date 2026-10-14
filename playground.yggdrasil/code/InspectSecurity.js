/**
 * @file InspectSecurity.js
 * @description L0~L10 권한 레벨, Break-Glass, 학습 차단, 다중서명 상태를 로컬 데몬에서 점검합니다.
 * 데몬 미연결 시에는 Fail-Safe 원칙에 따라 최소 권한(L0) 상태로 보고합니다.
 */
import { getJson } from './lib/YggdrasilClient.js';
import { API_PATH } from './lib/YggdrasilConfig.js';
import { PERMISSION_LEVELS, MIN_PERMISSION_LEVEL, MAX_PERMISSION_LEVEL } from './lib/YggdrasilConstants.js';
import { isPlainObject, toInt, toBool } from './lib/Normalize.js';

const MAX_TRUSTEES = 100;

export default function inspectSecurity() {
  const result = getJson(API_PATH.SECURITY);
  if (result.ok && isPlainObject(result.data)) {
    return normalizeSecurity(result.data);
  }
  return buildFailSafeSecurity();
}

function normalizeSecurity(data) {
  const level = toInt(data.currentLevel, MIN_PERMISSION_LEVEL, MAX_PERMISSION_LEVEL, MIN_PERMISSION_LEVEL);
  const levelInfo = PERMISSION_LEVELS[level];

  return {
    currentLevel: level,
    // 레벨 명칭/설명은 캡슐 정의를 사용하여 데몬 응답과 표기 불일치 방지
    levelTitle: levelInfo.title,
    levelDescription: levelInfo.description,
    isBreakGlassActive: toBool(data.isBreakGlassActive, false),
    // 불명확한 값은 안전한 쪽(차단/필수)으로 처리
    isLearningBlocked: toBool(data.isLearningBlocked, true),
    multiSigRequired: toBool(data.multiSigRequired, true),
    activeTrusteesCount: toInt(data.activeTrusteesCount, 0, MAX_TRUSTEES, 0)
  };
}

function buildFailSafeSecurity() {
  const levelInfo = PERMISSION_LEVELS[MIN_PERMISSION_LEVEL];
  return {
    currentLevel: MIN_PERMISSION_LEVEL,
    levelTitle: levelInfo.title,
    levelDescription: '로컬 데몬 미연결 - 안전 모드로 ' + levelInfo.description,
    isBreakGlassActive: false,
    isLearningBlocked: true,
    multiSigRequired: true,
    activeTrusteesCount: 0
  };
}
