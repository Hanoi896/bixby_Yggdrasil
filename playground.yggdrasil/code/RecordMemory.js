/**
 * @file RecordMemory.js
 * @description Munin 의 Niflheim 암호화 저장소에 새 기억을 기록합니다.
 * 암호화·서명은 로컬 데몬이 수행하며, 미연결 시 저장되지 않았음을 명시한 PENDING 레코드를 반환합니다.
 */
import { postJson } from './lib/YggdrasilClient.js';
import { API_PATH } from './lib/YggdrasilConfig.js';
import {
  MEMORY_CATEGORIES,
  DEFAULT_MEMORY_CATEGORY,
  MAX_MEMORY_CONTENT_LENGTH,
  UNSIGNED_HASH
} from './lib/YggdrasilConstants.js';
import { isPlainObject, toText, toBool, toAllowed } from './lib/Normalize.js';

const PENDING_ID_PREFIX = 'PENDING-';

export default function recordMemory(input) {
  const content = toText(input.content, '', MAX_MEMORY_CONTENT_LENGTH);
  const category = toAllowed(input.category, MEMORY_CATEGORIES, DEFAULT_MEMORY_CATEGORY);

  const result = postJson(API_PATH.MEMORY, { content: content, category: category });
  if (result.ok && isPlainObject(result.data)) {
    return normalizeRecord(result.data, content, category);
  }
  return buildPendingRecord(content, category);
}

/**
 * 데몬이 반환한 레코드를 정규화합니다.
 * id 또는 서명이 없으면 저장 성공으로 간주하지 않습니다.
 */
function normalizeRecord(data, content, category) {
  const id = toText(data.id, null, 100);
  const signatureHash = toText(data.signatureHash, null, 256);
  if (!id || !signatureHash) {
    return buildPendingRecord(content, category);
  }

  return {
    id: id,
    category: toAllowed(data.category, MEMORY_CATEGORIES, category),
    content: toText(data.content, content, MAX_MEMORY_CONTENT_LENGTH),
    timestamp: toText(data.timestamp, new Date().toISOString(), 40),
    isEncrypted: toBool(data.isEncrypted, false),
    signatureHash: signatureHash,
    appLaunchUri: 'yggdrasil://archive'
  };
}

function buildPendingRecord(content, category) {
  return {
    id: PENDING_ID_PREFIX + Date.now(),
    category: category,
    content: content,
    timestamp: new Date().toISOString(),
    isEncrypted: false,
    signatureHash: UNSIGNED_HASH,
    appLaunchUri: 'yggdrasil://archive'
  };
}
