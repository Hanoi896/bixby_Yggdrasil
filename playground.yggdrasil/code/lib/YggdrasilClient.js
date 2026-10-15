/**
 * @file YggdrasilClient.js
 * @description 로컬 Yggdrasil 온디바이스 데몬(127.0.0.1:8080)과의 실제 HTTP 통신 클라이언트.
 * 가짜 인메모리 Mock을 사용하지 않고 온디바이스 데몬과 직접 통신하며,
 * 연결 실패 시 정직한 에러를 반환하여 안전한 네이티브 App-Launch 딥링크 핸드오프를 지원합니다.
 */
import http from 'http';
import { getBaseUrl } from './YggdrasilConfig.js';

const JSON_HEADERS = Object.freeze({
  'Content-Type': 'application/json',
  'Accept': 'application/json'
});

/**
 * GET 요청을 수행합니다. (실제 로컬 데몬과 통신, 실패 시 정직한 에러 반환)
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
    return { ok: false, data: null, error: 'Empty or invalid JSON response' };
  } catch (error) {
    return { ok: false, data: null, error: String(error) };
  }
}

/**
 * JSON Body 로 POST 요청을 수행합니다. (실제 로컬 데몬과 통신, 실패 시 정직한 에러 반환)
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
    return { ok: false, data: null, error: 'Empty or invalid JSON response' };
  } catch (error) {
    return { ok: false, data: null, error: String(error) };
  }
}
