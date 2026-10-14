/**
 * @file Normalize.js
 * @description 외부(데몬/사용자) 입력값을 신뢰하지 않고 안전한 타입으로 정규화하는 유틸리티
 */

/** @returns {boolean} 배열이 아닌 일반 객체 여부 */
export function isPlainObject(value) {
  return value !== null && typeof value === 'object' && !Array.isArray(value);
}

/**
 * 공백 제거 후 비어있지 않은 문자열이면 반환, 아니면 fallback
 * @param {*} value
 * @param {string} fallback
 * @param {number} [maxLength] 최대 길이 (초과 시 절삭)
 */
export function toText(value, fallback, maxLength) {
  if (typeof value === 'string') {
    const trimmed = value.trim();
    if (trimmed.length > 0) {
      return maxLength && trimmed.length > maxLength ? trimmed.substring(0, maxLength) : trimmed;
    }
  }
  return fallback;
}

/**
 * 정수로 변환 후 [min, max] 범위로 제한
 */
export function toInt(value, min, max, fallback) {
  const num = Number(value);
  if (value === null || value === undefined || !Number.isFinite(num)) {
    return fallback;
  }
  const rounded = Math.round(num);
  if (rounded < min) return min;
  if (rounded > max) return max;
  return rounded;
}

/** boolean 타입일 때만 그대로 사용 (문자열 "true" 등은 신뢰하지 않음) */
export function toBool(value, fallback) {
  return typeof value === 'boolean' ? value : fallback;
}

/** 배열이 아니면 빈 배열 반환 */
export function toArray(value) {
  return Array.isArray(value) ? value : [];
}

/**
 * 허용 목록에 포함된 값만 반환 (대소문자 무시)
 * @param {*} value
 * @param {string[]} allowList
 * @param {string} fallback
 */
export function toAllowed(value, allowList, fallback) {
  if (typeof value !== 'string') {
    return fallback;
  }
  const lower = value.trim().toLowerCase();
  for (let i = 0; i < allowList.length; i++) {
    if (allowList[i].toLowerCase() === lower) {
      return allowList[i];
    }
  }
  return fallback;
}
