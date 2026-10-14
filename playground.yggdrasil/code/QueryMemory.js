/**
 * @file QueryMemory.js
 * @description Munin 이 저장한 과거 기억을 로컬 데몬(RAG 검색)에서 조회합니다.
 */
import { getJson } from './lib/YggdrasilClient.js';
import { API_PATH } from './lib/YggdrasilConfig.js';
import {
  MEMORY_CATEGORIES,
  DEFAULT_MEMORY_CATEGORY,
  MAX_MEMORY_CONTENT_LENGTH,
  DEFAULT_MEMORY_QUERY_LIMIT,
  MAX_MEMORY_QUERY_LIMIT,
  UNSIGNED_HASH
} from './lib/YggdrasilConstants.js';
import { isPlainObject, toText, toInt, toBool, toArray, toAllowed } from './lib/Normalize.js';

const MAX_QUERY_LENGTH = 200;

export default function queryMemory(input) {
  const query = toText(input.query, null, MAX_QUERY_LENGTH);

  const params = { limit: DEFAULT_MEMORY_QUERY_LIMIT };
  if (query) params.q = query;

  const result = getJson(API_PATH.MEMORY, params);
  if (result.ok && isPlainObject(result.data)) {
    return normalizeQueryResult(result.data, query);
  }
  return buildOfflineResult(query);
}

function normalizeQueryResult(data, query) {
  const rawRecords = toArray(data.records);
  const records = [];
  // 데몬이 limit 을 무시하더라도 최대 개수 제한
  const count = Math.min(rawRecords.length, MAX_MEMORY_QUERY_LIMIT);
  for (let i = 0; i < count; i++) {
    const record = normalizeRecord(rawRecords[i]);
    if (record) records.push(record);
  }

  const totalCount = toInt(data.totalCount, records.length, Number.MAX_SAFE_INTEGER, records.length);
  const response = {
    records: records,
    totalCount: totalCount,
    summaryMessage: toText(data.summaryMessage, buildSummary(query, totalCount), 300)
  };
  if (query) response.query = query;
  return response;
}

function normalizeRecord(raw) {
  if (!isPlainObject(raw)) return null;
  const id = toText(raw.id, null, 100);
  const content = toText(raw.content, null, MAX_MEMORY_CONTENT_LENGTH);
  if (!id || !content) return null;

  return {
    id: id,
    category: toAllowed(raw.category, MEMORY_CATEGORIES, DEFAULT_MEMORY_CATEGORY),
    content: content,
    timestamp: toText(raw.timestamp, '', 40),
    isEncrypted: toBool(raw.isEncrypted, false),
    signatureHash: toText(raw.signatureHash, UNSIGNED_HASH, 256)
  };
}

function buildSummary(query, totalCount) {
  if (totalCount === 0) {
    return query ? '"' + query + '"에 대한 기억을 찾지 못했습니다.' : '저장된 기억이 없습니다.';
  }
  return query
    ? '"' + query + '" 관련 기억 ' + totalCount + '건을 찾았습니다.'
    : '최근 기억 ' + totalCount + '건입니다.';
}

function buildOfflineResult(query) {
  const response = {
    records: [],
    totalCount: 0,
    summaryMessage: '로컬 Yggdrasil 데몬과 연결되지 않아 Munin 의 기억을 불러올 수 없습니다.'
  };
  if (query) response.query = query;
  return response;
}
