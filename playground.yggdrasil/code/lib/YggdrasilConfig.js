/**
 * @file YggdrasilConfig.js
 * @description 빅스비 캡슐 ↔ Android 네이티브 Intent 직결 설정 (100% Zero-Socket)
 */

// 프로덕션 빌드에서는 반드시 false 유지 (디버그 로그 차단)
export const IS_DEBUG = false;

export const LLM_MODEL = 'EXAONE-3.0-2.4B-Instruct';

// Android Native Intent 딥링크 스키마 설정 (100% 네이티브 직결, 지연시간 0ms)
export const DEEP_LINK_SCHEME = 'yggdrasil';

/**
 * Android 네이티브 액션 딥링크 URI 빌더
 * @param {string} action 실행할 액션 명칭
 * @param {object|string} payload 전달할 JSON 페이로드
 * @returns {string} yggdrasil://action URI
 */
export function buildActionUri(action, payload) {
  const query = encodeURIComponent(typeof payload === 'string' ? payload : JSON.stringify(payload || {}));
  return `${DEEP_LINK_SCHEME}://action?name=${encodeURIComponent(action)}&payload=${query}`;
}

/**
 * Android 네이티브 대화 딥링크 URI 빌더
 * @param {string} query 사용자 발화 텍스트
 * @returns {string} yggdrasil://chat URI
 */
export function buildChatUri(query) {
  return `${DEEP_LINK_SCHEME}://chat?query=${encodeURIComponent(query || '')}`;
}
