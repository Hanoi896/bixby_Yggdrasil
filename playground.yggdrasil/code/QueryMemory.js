/**
 * @file QueryMemory.js
 * @description Munin 온디바이스 암호화 RAG 저장소에서 기억을 조회합니다.
 *
 * 100% Native 직결:
 *  - 죽은 HTTP 소켓 호출을 영구 제거하고,
 *  - Native Intent URI를 통해 안드로이드 네이티브 RAG 엔진으로 안전하게 연결합니다.
 */
import { buildArchiveUri } from './lib/YggdrasilConfig.js';
import { toText } from './lib/Normalize.js';

const MAX_QUERY_LENGTH = 200;

export default function queryMemory(input) {
  const query = toText(input.query, null, MAX_QUERY_LENGTH);

  const response = {
    records: [],
    totalCount: 0,
    summaryMessage: query
      ? `'${query}' 기억 검색을 위해 이그드라실 앱을 엽니다.`
      : '암호화 기억 저장소 열람을 위해 이그드라실 앱을 엽니다.',
    appLaunchUri: 'yggdrasil://archive?query=' + encodeURIComponent(query || '')
  };

  if (query) response.query = query;
  return response;
}
