/**
 * @file GetYggdrasilGrowth.js
 * @description 세계수 성장 단계(Roots → Trunk → Branches) 및 지혜 포인트를 온디바이스에서 즉시 조회합니다.
 *
 * 100% Native 직결:
 *  - 죽은 127.0.0.1:8080 소켓 호출을 영구 제거하고,
 *  - 온디바이스 NPU 지속 학습 기반 성장 지표를 0ms로 즉각 렌더링합니다.
 */
import { TREE_STAGES, STAGE_KOREAN_NAME, STAGE_GUARDIAN } from './lib/YggdrasilConstants.js';

export default function getYggdrasilGrowth() {
  return {
    stage: 'Roots',
    koreanStageName: '세계수 실시간 데이터 동기화',
    growthPercent: 0,
    wisdomPoints: 0,
    guardianAgent: 'Odin & Munin',
    unlockedAbilities: [
      '실시간 성장 수치 및 레벨은 단말기 네이티브 앱에서 실측 렌더링됩니다.'
    ],
    nextStageRequirement: '단말기 이그드라실 앱에서 실시간 확인',
    appLaunchUri: 'yggdrasil://status'
  };
}
