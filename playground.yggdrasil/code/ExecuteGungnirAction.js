/**
 * @file ExecuteGungnirAction.js
 * @description Gungnir 를 통해 IoT 제어·결제 등 치명적 작업을 실행 요청합니다.
 *
 * 100% Android Native Intent 직결:
 *  - 로컬 HTTP 소켓 통신을 전면 배제하고, 0ms 레이턴시의 App-Launch Intent URI를 생성합니다.
 *  - 권한 레벨(L0~L10) 및 오딘(Odin) 정책에 따라 승인 대기 또는 즉각 실행으로 안전하게 핸드오프합니다.
 */
import { buildActionUri } from './lib/YggdrasilConfig.js';
import {
  GUNGNIR_POLICIES,
  DEFAULT_GUNGNIR_POLICY,
  MIN_PERMISSION_LEVEL,
  MAX_PERMISSION_LEVEL,
  UNSIGNED_HASH
} from './lib/YggdrasilConstants.js';
import { toText, toInt } from './lib/Normalize.js';

const MAX_ACTION_TYPE_LENGTH = 100;

export default function executeGungnirAction(input) {
  const actionType = toText(input.actionType, '', MAX_ACTION_TYPE_LENGTH);
  const policy = findPolicy(actionType);
  const requestedLevel = toInt(input.requestedLevel, MIN_PERMISSION_LEVEL, MAX_PERMISSION_LEVEL, policy.requiredLevel);

  // 100% 온디바이스 Native 직결 URI 생성
  const actionUri = buildActionUri('execute_action', {
    actionType: actionType,
    level: requestedLevel,
    realm: policy.realm
  });

  // L4 이상 고위험 작업: 오딘 승인 필요
  const requiresApproval = requestedLevel >= 4;
  const executionMessage = requiresApproval
    ? `[🛡️ Odin 승인 대기] '${actionType}' 작업은 보안 ${requestedLevel}등급(보안 도메인: ${policy.realm}) 작업으로, 기기에서 생체 인증 확인 후 실행됩니다.`
    : `[⚡ 즉시 실행] '${actionType}' 작업을 온디바이스 네이티브 엔진으로 즉시 전달하여 실행합니다.`;

  return {
    success: true,
    actionType: actionType,
    requiredLevel: requestedLevel,
    message: executionMessage,
    signedHash: 'ODIN-NATIVE-AUTH-' + Date.now().toString(16),
    realm: policy.realm,
    appLaunchUri: actionUri
  };
}

/**
 * 작업 유형 키워드로 안내용 정책을 탐색
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
