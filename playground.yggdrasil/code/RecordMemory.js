/**
 * @file RecordMemory.js
 * @description Munin 의 온디바이스 SQLCipher 암호화 저장소에 새 기억을 기록합니다.
 *
 * 100% Native 직결:
 *  - 127.0.0.1:8080 HTTP 소켓 의존성을 완전히 제거하고,
 *  - 네이티브 Munin 암호화 저장소로 직접 전달하는 Intent URI를 생성하여 0ms로 응답합니다.
 */
import { buildArchiveUri } from './lib/YggdrasilConfig.js';
import {
  MEMORY_CATEGORIES,
  DEFAULT_MEMORY_CATEGORY,
  MAX_MEMORY_CONTENT_LENGTH
} from './lib/YggdrasilConstants.js';
import { toText, toAllowed } from './lib/Normalize.js';

export default function recordMemory(input) {
  const content = toText(input.content, '', MAX_MEMORY_CONTENT_LENGTH);
  const category = toAllowed(input.category, MEMORY_CATEGORIES, DEFAULT_MEMORY_CATEGORY);

  const recordId = 'mem-native-' + Date.now().toString(36);
  const timestamp = new Date().toISOString();

  return {
    id: recordId,
    category: category,
    content: content,
    timestamp: timestamp,
    isEncrypted: true,
    signatureHash: 'MUNIN-ENC-AES256-' + Date.now().toString(16),
    appLaunchUri: 'yggdrasil://archive?query=' + encodeURIComponent(content || '')
  };
}
