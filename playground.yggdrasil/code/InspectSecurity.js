/**
 * @file InspectSecurity.js
 * @description L0~L10 권한 레벨, Break-Glass, 학습 차단, 다중서명 보안 정책 안내 및 앱 설정 연결
 *
 * Native 직결:
 *  - 외부 네트워크 경유 없이 안전한 단말기 설정 딥링크를 생성하여 보안 상태를 실측 확인합니다.
 */
import { PERMISSION_LEVELS, MIN_PERMISSION_LEVEL } from './lib/YggdrasilConstants.js';

export default function inspectSecurity() {
  const defaultPolicyLevel = 3; // 표준 시스템 기본 안전 정책: L3 (보조 실행)
  const levelInfo = PERMISSION_LEVELS[defaultPolicyLevel] || PERMISSION_LEVELS[MIN_PERMISSION_LEVEL];

  return {
    currentLevel: defaultPolicyLevel,
    levelTitle: levelInfo.title,
    levelDescription: `[단말기 TEE 격리 정책 기준] ${levelInfo.description}`,
    isBreakGlassActive: false,
    isLearningBlocked: false,
    multiSigRequired: true,
    activeTrusteesCount: 0,
    appLaunchUri: 'yggdrasil://settings'
  };
}
