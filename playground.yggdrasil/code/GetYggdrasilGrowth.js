/**
 * @file GetYggdrasilGrowth.js
 * @description 세계수 성장 단계(Roots → Trunk → Branches) 및 지혜 포인트를 로컬 데몬에서 조회합니다.
 */
import { getJson } from './lib/YggdrasilClient.js';
import { API_PATH } from './lib/YggdrasilConfig.js';
import { TREE_STAGES, STAGE_KOREAN_NAME, STAGE_GUARDIAN } from './lib/YggdrasilConstants.js';
import { isPlainObject, toText, toInt, toArray, toAllowed } from './lib/Normalize.js';

const MAX_PERCENT = 100;
const MAX_WISDOM_POINTS = 1000000000;

export default function getYggdrasilGrowth() {
  const result = getJson(API_PATH.GROWTH);
  if (result.ok && isPlainObject(result.data)) {
    return normalizeGrowth(result.data);
  }
  return buildOfflineGrowth(result.error);
}

// 데몬 응답을 신뢰하지 않고 범위/허용값 검증
function normalizeGrowth(data) {
  const stage = toAllowed(data.stage, TREE_STAGES, TREE_STAGES[0]);

  const abilities = [];
  const rawAbilities = toArray(data.unlockedAbilities);
  for (let i = 0; i < rawAbilities.length; i++) {
    const ability = toText(rawAbilities[i], null, 100);
    if (ability) abilities.push(ability);
  }
  if (abilities.length === 0) {
    abilities.push('기본 패턴 수집');
  }

  return {
    stage: stage,
    koreanStageName: STAGE_KOREAN_NAME[stage],
    growthPercent: toInt(data.growthPercent, 0, MAX_PERCENT, 0),
    wisdomPoints: toInt(data.wisdomPoints, 0, MAX_WISDOM_POINTS, 0),
    guardianAgent: toText(data.guardianAgent, STAGE_GUARDIAN[stage], 30),
    unlockedAbilities: abilities,
    nextStageRequirement: toText(data.nextStageRequirement, '다음 단계 조건 정보 없음', 200),
    appLaunchUri: 'yggdrasil://status'
  };
}

// 데몬 미연결 시 최초 단계 기준 안내
function buildOfflineGrowth(errorMessage) {
  const stage = TREE_STAGES[0];
  const reqMsg = errorMessage 
    ? '통신 실패 원인: ' + errorMessage
    : '로컬 Yggdrasil 데몬(127.0.0.1:8080)을 실행하면 실제 성장 상태가 표시됩니다.';

  return {
    stage: stage,
    koreanStageName: STAGE_KOREAN_NAME[stage],
    growthPercent: 0,
    wisdomPoints: 0,
    guardianAgent: STAGE_GUARDIAN[stage],
    unlockedAbilities: ['오프라인 - 성장 데이터 미수신'],
    nextStageRequirement: reqMsg,
    appLaunchUri: 'yggdrasil://status'
  };
}
