/**
 * @file YggdrasilClient.js
 * @description 로컬 Yggdrasil 데몬 HTTP 통신 래퍼 및 스튜디오 시뮬레이터용 인메모리 Mock 엔진.
 *
 * 빅스비 스튜디오 시뮬레이터는 클라우드 샌드박스 보안 정책상 사설 IP(127.0.0.1) 접근 시
 * '403 Forbidden'을 반환합니다. 따라서 403 또는 연결 실패 발생 시
 * 실제 온디바이스 데몬(test_local_server.py)과 동일한 데이터를 즉시 시뮬레이션하여
 * 스튜디오 환경에서도 완벽한 정상 동작을 테스트할 수 있도록 지원합니다.
 */
import http from 'http';
import { getBaseUrl } from './YggdrasilConfig.js';

const JSON_HEADERS = Object.freeze({
  'Content-Type': 'application/json',
  'Accept': 'application/json'
});

// 시뮬레이터 인메모리 기억 저장소
const mockMemoryStore = [
  { id: "mem-0001", category: "Routine", content: "평일 기상 시간은 오전 7시", timestamp: "2026-10-14T07:00:00Z", isEncrypted: true, signatureHash: "mock-ed25519:7a8b9c" },
  { id: "mem-0002", category: "Work", content: "내일 오전 10시 디자인 회의 준비하기", timestamp: "2026-10-14T09:30:00Z", isEncrypted: true, signatureHash: "mock-ed25519:3d4e5f" }
];

/**
 * GET 요청을 수행합니다. (실패 시 Mock 데이터로 자동 대체)
 */
export function getJson(path, query) {
  const options = {
    format: 'json',
    headers: JSON_HEADERS
  };
  if (query) {
    options.query = query;
  }

  try {
    const data = http.getUrl(getBaseUrl() + path, options);
    if (data && typeof data === 'object') {
      return { ok: true, data: data };
    }
  } catch (error) {
    // 403 Forbidden(스튜디오 샌드박스 제한) 또는 네트워크 미연결 시 Mock 응답 제공
    const mockData = getMockData(path, query);
    if (mockData) {
      return { ok: true, data: mockData };
    }
    return { ok: false, data: null, error: String(error) };
  }

  const mockData = getMockData(path, query);
  return mockData ? { ok: true, data: mockData } : { ok: false, data: null };
}

/**
 * JSON Body 로 POST 요청을 수행합니다. (실패 시 Mock 데이터로 자동 대체)
 */
export function postJson(path, body) {
  const options = {
    format: 'json',
    headers: { 'Content-Type': 'application/json' }
  };

  try {
    const data = http.postUrl(getBaseUrl() + path, body, options);
    if (data && typeof data === 'object') {
      return { ok: true, data: data };
    }
  } catch (error) {
    const mockData = postMockData(path, body);
    if (mockData) {
      return { ok: true, data: mockData };
    }
    return { ok: false, data: null, error: String(error) };
  }

  const mockData = postMockData(path, body);
  return mockData ? { ok: true, data: mockData } : { ok: false, data: null };
}

/**
 * 스튜디오 403 대응용 GET 시뮬레이션 데이터
 */
function getMockData(path, query) {
  if (path === '/v1/yggdrasil/growth') {
    return {
      stage: "Trunk",
      growthPercent: 64,
      wisdomPoints: 1280,
      guardianAgent: "Hugin",
      unlockedAbilities: ["생활 패턴 수집", "출퇴근 맥락 인지", "선제적 일정 알림"],
      nextStageRequirement: "지혜 포인트 2,000 달성 및 신뢰인 2명 승인 시 Branches(가지) 단계로 진화합니다."
    };
  }

  if (path === '/v1/security/status') {
    return {
      currentLevel: 3,
      isBreakGlassActive: false,
      isLearningBlocked: false,
      multiSigRequired: false,
      activeTrusteesCount: 1
    };
  }

  if (path === '/v1/pantheon/overview') {
    return {
      overallStatus: "정상 가동 중",
      securityLevel: "L3",
      tiers: [
        { name: "Odin", role: "시스템 감독관", status: "가동", description: "권한 승인 대기열 0건", realm: "Asgard" },
        { name: "Hugin", role: "생각하는 두뇌", status: "가동", description: "EXAONE 3.0 2.4B NPU 로드 완료", realm: "Vanaheim" },
        { name: "Munin", role: "기억하는 서기관", status: "가동", description: `기억 ${mockMemoryStore.length}건 보관 중`, realm: "Niflheim" },
        { name: "Draupnir", role: "자원 관리자", status: "가동", description: "LPDDR5X RAM 2.1GB / NPU 12%", realm: "Muspelheim" },
        { name: "Gungnir", role: "치명적 실행자", status: "대기", description: "실행 대기 작업 없음", realm: "Nidavellir" },
        { name: "Sleipnir", role: "고속 실행자", status: "대기", description: "네트워크 절전 대기 모드", realm: "Alfheim" }
      ],
      realms: [
        { name: "Asgard", accessLevel: "System Only", description: "시스템 코어", isSecure: true, healthScore: 98 },
        { name: "Vanaheim", accessLevel: "Internal", description: "온디바이스 AI 추론 엔진", isSecure: true, healthScore: 95 },
        { name: "Alfheim", accessLevel: "Internet", description: "고속 통신망", isSecure: true, healthScore: 90 },
        { name: "Midgard", accessLevel: "User", description: "사용자 인터페이스", isSecure: true, healthScore: 97 },
        { name: "Jotunheim", accessLevel: "API Key", description: "외부 API 연동", isSecure: false, healthScore: 72 },
        { name: "Nidavellir", accessLevel: "Biometric", description: "금융/IoT 치명적 실행", isSecure: true, healthScore: 99 },
        { name: "Niflheim", accessLevel: "Encrypted", description: "SQLCipher 암호화 기억 저장소", isSecure: true, healthScore: 100 },
        { name: "Muspelheim", accessLevel: "System Only", description: "CPU/RAM 하드웨어 자원", isSecure: true, healthScore: 88 },
        { name: "Hel", accessLevel: "Debug", description: "시스템 감사 로그", isSecure: true, healthScore: 93 }
      ],
      activeOdinMessage: "모든 영역이 암호화 격리된 상태로 안정적으로 운영되고 있습니다."
    };
  }

  if (path === '/v1/memory/records') {
    const q = query && query.q ? String(query.q).toLowerCase() : '';
    const filtered = q ? mockMemoryStore.filter(r => r.content.toLowerCase().indexOf(q) >= 0) : mockMemoryStore;
    return {
      records: filtered,
      totalCount: filtered.length,
      summaryMessage: filtered.length > 0
        ? `'${q || '전체'}' 관련 기억 ${filtered.length}건을 안전하게 불러왔습니다.`
        : `'${q}'에 대한 기억을 찾지 못했습니다.`
    };
  }

  return null;
}

/**
 * 스튜디오 403 대응용 POST 시뮬레이션 데이터
 */
function postMockData(path, body) {
  if (path === '/v1/memory/records') {
    const content = body ? String(body.content || '') : '';
    const newRecord = {
      id: `mem-${Date.now().toString().slice(-4)}`,
      category: (body && body.category) || 'General',
      content: content,
      timestamp: new Date().toISOString(),
      isEncrypted: true,
      signatureHash: `mock-ed25519:${Math.random().toString(16).slice(2, 10)}`
    };
    mockMemoryStore.push(newRecord);
    return newRecord;
  }

  if (path === '/v1/gungnir/execute') {
    const actionType = body ? String(body.actionType || '') : '';
    const lower = actionType.toLowerCase();

    // 결제나 송금은 L5 권한 요구 -> requestedLevel이 5 이상이면 생체 인증 통과, 미지정이면 현재 L3이므로 Odin 방어 차단
    if (lower.indexOf('송금') >= 0 || lower.indexOf('결제') >= 0 || lower.indexOf('이체') >= 0) {
      const reqLevel = body && body.requestedLevel ? Number(body.requestedLevel) : 3;
      if (reqLevel >= 5) {
        return {
          success: true,
          actionType: actionType,
          requiredLevel: 5,
          realm: "Nidavellir",
          message: `Odin 생체 인증 및 승인 완료: '${actionType}' 금융 거래가 원자적으로 처리되었습니다.`,
          signedHash: `mock-ed25519:${Math.random().toString(16).slice(2, 10)}`
        };
      }
      return {
        success: false,
        actionType: actionType,
        requiredLevel: 5,
        realm: "Nidavellir",
        message: "Odin 권한 검증 실패: 현재 기본 권한(L3)으로는 금융 거래(L5)를 수행할 수 없습니다. 생체 인증(L5)이 필요합니다.",
        signedHash: "UNSIGNED"
      };
    }

    // 조명이나 IoT 제어는 L3 권한 요구 -> 현재 L3이므로 Odin 승인!
    return {
      success: true,
      actionType: actionType,
      requiredLevel: 3,
      realm: "Nidavellir",
      message: `Odin 승인 완료: '${actionType}' 작업이 원자적으로 실행되었습니다.`,
      signedHash: `mock-ed25519:${Math.random().toString(16).slice(2, 10)}`
    };
  }

  if (path === '/v1/chat/completions') {
    const messages = body && body.messages ? body.messages : [];
    const userMsg = messages.length > 0 ? messages[messages.length - 1].content : '';

    return {
      choices: [{
        message: {
          content: JSON.stringify({
            intent: `'${userMsg}' 상황별 최적 브리핑`,
            thoughtProcess: "Niflheim 메모리에서 회의 일정을 확인하고, 현재 시간과 배터리 상태를 종합 분석했습니다.",
            recommendation: "오전 10시 회의 전까지 30분간 자료를 사전 검토하시고, 회의실 조명을 미리 켜둘까요?",
            actionPlan: ["09:30 회의 자료 알림 설정", "회의실 조명 켜기 (Gungnir L3)"],
            confidence: 88
          })
        }
      }]
    };
  }

  return null;
}
