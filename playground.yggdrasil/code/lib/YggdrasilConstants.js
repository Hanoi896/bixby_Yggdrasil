/**
 * @file YggdrasilConstants.js
 * @description Yggdrasil 도메인 상수 (BluePrint/06_구현_로드맵/아카이브/초기_README.md 의 판테온·9대 영역·성장 단계 정의와 일치)
 */

/** 세계수 성장 단계 */
export const TREE_STAGES = Object.freeze(['Roots', 'Trunk', 'Branches']);

export const STAGE_KOREAN_NAME = Object.freeze({
  Roots: '뿌리 (데이터 수집)',
  Trunk: '줄기 (맥락 인지)',
  Branches: '가지 (자율 행동)'
});

export const STAGE_GUARDIAN = Object.freeze({
  Roots: 'Munin',
  Trunk: 'Hugin',
  Branches: 'Odin'
});

/** 판테온 6대 에이전트 */
export const PANTHEON_TIERS = Object.freeze([
  Object.freeze({ name: 'Odin', role: '시스템 감독관', realm: 'Asgard', description: '생명주기 관리 및 최상위 권한 승인' }),
  Object.freeze({ name: 'Hugin', role: '생각하는 두뇌', realm: 'Vanaheim', description: '온디바이스 LLM 기반 의도 추론 및 계획 수립' }),
  Object.freeze({ name: 'Munin', role: '기억하는 서기관', realm: 'Niflheim', description: '로그·센서·일상 기록 수집 및 문맥 제공' }),
  Object.freeze({ name: 'Draupnir', role: '자원 관리자', realm: 'Muspelheim', description: '스레드/메모리/배터리 최적화' }),
  Object.freeze({ name: 'Gungnir', role: '치명적 실행자', realm: 'Nidavellir', description: '결제·보안 설정·IoT 원자적 실행' }),
  Object.freeze({ name: 'Sleipnir', role: '고속 실행자', realm: 'Alfheim', description: '네트워크 통신 및 대량 병렬 처리' })
]);

/** 9대 세계 영역 */
export const REALMS = Object.freeze([
  Object.freeze({ name: 'Asgard', accessLevel: 'System Only', description: '시스템 코어 (Kernel)' }),
  Object.freeze({ name: 'Vanaheim', accessLevel: 'Internal', description: 'AI 추론 엔진' }),
  Object.freeze({ name: 'Alfheim', accessLevel: 'Internet', description: '고속 통신망' }),
  Object.freeze({ name: 'Midgard', accessLevel: 'User', description: '사용자 인터페이스' }),
  Object.freeze({ name: 'Jotunheim', accessLevel: 'API Key', description: '외부 API 연동' }),
  Object.freeze({ name: 'Nidavellir', accessLevel: 'Biometric', description: '금융/IoT 치명적 실행 영역' }),
  Object.freeze({ name: 'Niflheim', accessLevel: 'Encrypted', description: '암호화 기억 저장소' }),
  Object.freeze({ name: 'Muspelheim', accessLevel: 'System Only', description: 'CPU/RAM 자원' }),
  Object.freeze({ name: 'Hel', accessLevel: 'Debug', description: '에러 로그 보관소' })
]);

/** L0~L10 권한 레벨 정의 */
export const MIN_PERMISSION_LEVEL = 0;
export const MAX_PERMISSION_LEVEL = 10;

export const PERMISSION_LEVELS = Object.freeze([
  Object.freeze({ title: 'L0 관찰자', description: '읽기 전용. 어떠한 행동도 수행하지 않습니다.' }),
  Object.freeze({ title: 'L1 조회', description: '개인 데이터 조회만 허용됩니다.' }),
  Object.freeze({ title: 'L2 제안', description: '행동을 제안할 수 있으나 실행은 사용자가 합니다.' }),
  Object.freeze({ title: 'L3 보조 실행', description: '저위험 기기 제어(IoT 등)를 실행합니다.' }),
  Object.freeze({ title: 'L4 하드웨어 인증', description: '하드웨어 보안키 인증이 필요한 작업을 수행합니다.' }),
  Object.freeze({ title: 'L5 결제', description: '결제 및 금융 거래를 수행합니다.' }),
  Object.freeze({ title: 'L6 계정 연동', description: 'OAuth 외부 계정을 대신 사용합니다.' }),
  Object.freeze({ title: 'L7 대리 위임', description: '범위가 검증된 작업을 대리 수행합니다.' }),
  Object.freeze({ title: 'L8 조직', description: '팀/조직 단위 작업을 컴플라이언스 검증 후 수행합니다.' }),
  Object.freeze({ title: 'L9 법률/공공', description: '법률 문서 등 공공 영역 작업을 검토 후 수행합니다.' }),
  Object.freeze({ title: 'L10 완전 자율', description: '지속 모니터링 하에 최대 위험 작업까지 자율 수행합니다.' })
]);

/** Munin 기억 카테고리 */
export const MEMORY_CATEGORIES = Object.freeze(['Routine', 'Work', 'Health', 'Secret', 'General']);
export const DEFAULT_MEMORY_CATEGORY = 'General';
export const MAX_MEMORY_CONTENT_LENGTH = 2000;
export const DEFAULT_MEMORY_QUERY_LIMIT = 10;
export const MAX_MEMORY_QUERY_LIMIT = 50;

/**
 * Gungnir 작업 정책 (안내 표시용).
 * 실제 권한 판정은 반드시 로컬 데몬(Odin)이 수행하며, 캡슐은 이 값으로 실행을 허가하지 않습니다.
 */
export const GUNGNIR_POLICIES = Object.freeze([
  Object.freeze({ keywords: ['iot', '조명', '전등', '에어컨', '보일러', '스마트홈'], requiredLevel: 3, realm: 'Nidavellir' }),
  Object.freeze({ keywords: ['결제', '송금', '이체', 'payment'], requiredLevel: 5, realm: 'Nidavellir' }),
  Object.freeze({ keywords: ['비상', '차단', 'lockdown'], requiredLevel: 2, realm: 'Asgard' }),
  Object.freeze({ keywords: ['계정', 'oauth'], requiredLevel: 6, realm: 'Jotunheim' })
]);
export const DEFAULT_GUNGNIR_POLICY = Object.freeze({ requiredLevel: 5, realm: 'Nidavellir' });

/** 서명이 없는 결과를 표시하기 위한 값 */
export const UNSIGNED_HASH = 'UNSIGNED';
