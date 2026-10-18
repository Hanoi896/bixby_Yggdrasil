/**
 * @file GetPantheonOverview.js
 * @description 판테온 6대 에이전트와 9대 세계 영역의 종합 관제 상태를 안내합니다.
 *
 * Native 직결:
 *  - 외부/로컬 소켓 호출 없이 안전한 딥링크를 생성하여 단말기 앱에서 실시간 실측 상태를 확인합니다.
 */
import { PANTHEON_TIERS, REALMS } from './lib/YggdrasilConstants.js';

export default function getPantheonOverview() {
  const tiers = PANTHEON_TIERS.map(tier => ({
    name: tier.name,
    role: tier.role,
    status: '단말기 실시간 연동 대기',
    description: tier.description,
    realm: tier.realm
  }));

  const realms = REALMS.map(realm => ({
    name: realm.name,
    status: '하드웨어 TEE 격리 도메인',
    requiredLevel: realm.minLevel,
    isAccessible: true,
    description: realm.description
  }));

  return {
    overallStatus: '단말기 실시간 확인 필요 (앱 딥링크 제공)',
    securityLevel: '보안 정책 확인 대기',
    tiers: tiers,
    realms: realms,
    activeOdinMessage: '실시간 하드웨어 가속 상태 및 암호화 지표는 이그드라실 앱에서 확인하실 수 있습니다.',
    appLaunchUri: 'yggdrasil://status'
  };
}
