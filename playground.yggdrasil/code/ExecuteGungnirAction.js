/**
 * @file ExecuteGungnirAction.js
 * @description Gungnir 를 통해 IoT 제어·결제 등 치명적 작업을 실행 요청합니다.
 *
 * 보안 원칙:
 *  - 권한 판정과 실행은 오직 로컬 데몬(Odin 승인)이 수행합니다. 캡슐은 요청만 전달합니다.
 *  - 데몬 응답이 success=true 이더라도 서명(signedHash)이 없으면 실패로 처리합니다.
 *  - 데몬 미연결 시 절대 성공으로 보고하지 않습니다.
 */
import { postJson } from './lib/YggdrasilClient.js';
import { API_PATH } from './lib/YggdrasilConfig.js';
import {
  GUNGNIR_POLICIES,
  DEFAULT_GUNGNIR_POLICY,
  MIN_PERMISSION_LEVEL,
  MAX_PERMISSION_LEVEL,
  UNSIGNED_HASH
} from './lib/YggdrasilConstants.js';
import { isPlainObject, toText, toInt, toBool } from './lib/Normalize.js';

const MAX_ACTION_TYPE_LENGTH = 100;

export default function executeGungnirAction(input) {
  const actionType = toText(input.actionType, '', MAX_ACTION_TYPE_LENGTH);
  const policy = findPolicy(actionType);

  const body = { actionType: actionType };
  const requestedLevel = toInt(input.requestedLevel, MIN_PERMISSION_LEVEL, MAX_PERMISSION_LEVEL, null);
  if (requestedLevel !== null) body.requestedLevel = requestedLevel;

  const result = postJson(API_PATH.GUNGNIR, body);
  if (result.ok && isPlainObject(result.data)) {
    return normalizeExecution(result.data, actionType, policy);
  }
  return buildRejected(actionType, policy, 'Odin 승인 불가: 로컬 데몬 미연결로 작업을 실행하지 않았습니다.');
}

/**
 * 작업 유형 키워드로 안내용 정책을 찾습니다. (실행 허가 판단에는 사용하지 않음)
 * 정책 수가 적어 선형 탐색으로 충분합니다.
 */
function findPolicy(actionType) {
  const lower = actionType.toLowerCase();
  for (let i = 0; i < GUNGNIR_POLICIES.length; i++) {
    const keywords = GUNGNIR_POLICIES[i].keywords;
    for (let k = 0; k < keywords.length; k++) {
      if (lower.indexOf(keywords[k]) >= 0) {
        return GUNGNIR_POLICIES[i];
      }
    }
  }
  return DEFAULT_GUNGNIR_POLICY;
}

function normalizeExecution(data, actionType, policy) {
  const signedHash = toText(data.signedHash, null, 256);
  const claimedSuccess = toBool(data.success, false);
  const requiredLevel = toInt(data.requiredLevel, MIN_PERMISSION_LEVEL, MAX_PERMISSION_LEVEL, policy.requiredLevel);
  const realm = toText(data.realm, policy.realm, 30);

  // 서명 없는 성공은 위변조 가능성이 있으므로 거부
  if (claimedSuccess && !signedHash) {
    return {
      success: false,
      actionType: actionType,
      requiredLevel: requiredLevel,
      message: '실행 결과에 Odin 서명이 없어 성공으로 인정할 수 없습니다.',
      signedHash: UNSIGNED_HASH,
      realm: realm
    };
  }

  return {
    success: claimedSuccess,
    actionType: toText(data.actionType, actionType, MAX_ACTION_TYPE_LENGTH),
    requiredLevel: requiredLevel,
    message: toText(data.message, claimedSuccess ? '작업이 실행되었습니다.' : '작업이 거부되었습니다.', 300),
    signedHash: signedHash || UNSIGNED_HASH,
    realm: realm
  };
}

function buildRejected(actionType, policy, message) {
  return {
    success: false,
    actionType: actionType,
    requiredLevel: policy.requiredLevel,
    message: message,
    signedHash: UNSIGNED_HASH,
    realm: policy.realm
  };
}
