/**
 * @file YggdrasilConfig.js
 * @description 로컬 Yggdrasil 데몬 연결 설정 및 API 경로 정의
 */
import config from 'config';

// capsule.properties 미설정 시 사용할 기본 루프백 주소 (레거시 호환용)
export const DEFAULT_BASE_URL = 'http://127.0.0.1:8080';

// 프로덕션 빌드에서는 반드시 false 유지 (디버그 로그 차단)
export const IS_DEBUG = false;

export const LLM_MODEL = 'EXAONE-3.0-2.4B-Instruct';

// Android Native Intent 딥링크 스키마 설정 (100% 네이티브 직결)
export const DEEP_LINK_SCHEME = 'yggdrasil';

export function buildActionUri(action, payload) {
  const query = encodeURIComponent(typeof payload === 'string' ? payload : JSON.stringify(payload || {}));
  return `${DEEP_LINK_SCHEME}://action?name=${encodeURIComponent(action)}&payload=${query}`;
}

export function buildChatUri(query) {
  return `${DEEP_LINK_SCHEME}://chat?query=${encodeURIComponent(query || '')}`;
}

/** 로컬 데몬 REST API 경로 (폴백 호환 유지) */
export const API_PATH = Object.freeze({
  CHAT: '/v1/chat/completions',
  GROWTH: '/v1/yggdrasil/growth',
  PANTHEON: '/v1/pantheon/overview',
  SECURITY: '/v1/security/status',
  MEMORY: '/v1/memory/records',
  GUNGNIR: '/v1/gungnir/execute'
});

/**
 * capsule.properties 의 yggdrasil.baseUrl 값을 반환합니다.
 * @returns {string} 데몬 Base URL
 */
export function getBaseUrl() {
  try {
    const value = config.get('yggdrasil.baseUrl');
    if (typeof value === 'string' && value.length > 0) {
      return value;
    }
  } catch (e) {
    // 설정 키가 없으면 기본값 사용
  }
  return DEFAULT_BASE_URL;
}
