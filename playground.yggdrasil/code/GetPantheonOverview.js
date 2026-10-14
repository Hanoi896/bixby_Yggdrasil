/**
 * @file GetPantheonOverview.js
 * @description 판테온 6대 에이전트와 9대 세계 영역의 종합 관제 상태를 로컬 데몬에서 조회합니다.
 */
import { getJson } from './lib/YggdrasilClient.js';
import { API_PATH } from './lib/YggdrasilConfig.js';
import { PANTHEON_TIERS, REALMS } from './lib/YggdrasilConstants.js';
import { isPlainObject, toText, toInt, toBool, toArray } from './lib/Normalize.js';

const MAX_HEALTH = 100;
const OFFLINE_STATUS = '오프라인';

export default function getPantheonOverview() {
  const result = getJson(API_PATH.PANTHEON);
  if (result.ok && isPlainObject(result.data)) {
    return normalizeOverview(result.data);
  }
  return buildOfflineOverview();
}

function normalizeOverview(data) {
  const tiers = normalizeTiers(toArray(data.tiers));
  const realms = normalizeRealms(toArray(data.realms));

  return {
    overallStatus: toText(data.overallStatus, '알 수 없음', 50),
    securityLevel: toText(data.securityLevel, 'Unknown', 50),
    // tiers/realms 는 Required(Many) 이므로 비어 있으면 기본 정의로 대체
    tiers: tiers.length > 0 ? tiers : buildDefaultTiers(OFFLINE_STATUS),
    realms: realms.length > 0 ? realms : buildDefaultRealms(),
    activeOdinMessage: toText(data.activeOdinMessage, '오딘의 메시지가 없습니다.', 300)
  };
}

function normalizeTiers(rawTiers) {
  const tiers = [];
  for (let i = 0; i < rawTiers.length; i++) {
    const raw = rawTiers[i];
    if (!isPlainObject(raw)) continue;
    const name = toText(raw.name, null, 30);
    if (!name) continue;

    const tier = {
      name: name,
      role: toText(raw.role, '역할 미정', 50),
      status: toText(raw.status, '알 수 없음', 30),
      description: toText(raw.description, '', 200)
    };
    const realm = toText(raw.realm, null, 30);
    if (realm) tier.realm = realm;
    tiers.push(tier);
  }
  return tiers;
}

function normalizeRealms(rawRealms) {
  const realms = [];
  for (let i = 0; i < rawRealms.length; i++) {
    const raw = rawRealms[i];
    if (!isPlainObject(raw)) continue;
    const name = toText(raw.name, null, 30);
    if (!name) continue;

    realms.push({
      name: name,
      accessLevel: toText(raw.accessLevel, 'Unknown', 30),
      description: toText(raw.description, '', 200),
      // 보안 여부는 명시적 true 일 때만 인정
      isSecure: toBool(raw.isSecure, false),
      healthScore: toInt(raw.healthScore, 0, MAX_HEALTH, 0)
    });
  }
  return realms;
}

function buildDefaultTiers(status) {
  const tiers = [];
  for (let i = 0; i < PANTHEON_TIERS.length; i++) {
    const def = PANTHEON_TIERS[i];
    tiers.push({
      name: def.name,
      role: def.role,
      status: status,
      description: def.description,
      realm: def.realm
    });
  }
  return tiers;
}

// 미연결 상태에서는 측정값이 없으므로 보안/건전성을 보수적으로 표기
function buildDefaultRealms() {
  const realms = [];
  for (let i = 0; i < REALMS.length; i++) {
    const def = REALMS[i];
    realms.push({
      name: def.name,
      accessLevel: def.accessLevel,
      description: def.description,
      isSecure: false,
      healthScore: 0
    });
  }
  return realms;
}

function buildOfflineOverview() {
  return {
    overallStatus: OFFLINE_STATUS,
    securityLevel: 'Unknown',
    tiers: buildDefaultTiers(OFFLINE_STATUS),
    realms: buildDefaultRealms(),
    activeOdinMessage: '로컬 Yggdrasil 데몬과 연결되지 않아 영역 상태를 측정할 수 없습니다.'
  };
}
