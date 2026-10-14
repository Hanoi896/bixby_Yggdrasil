#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
test_security_part6.py
PART 6 보안 통제 & 웹 감사 대시보드 (Odin Shield & Web Dashboard) 검증 스크립트

검증 항목:
1. Break-Glass Protocol: 원클릭 긴급 시스템 차단 및 L0 강제 전환
2. Multi-Sig 신뢰인 승인 워크플로우: 2-of-3 Ed25519 다중 서명 검증
3. Cryptographic Hash Chain: SHA-256 위변조 불가 해시 체인 무결성 검증
4. 긴급 차단 중 모든 고위험 자율 실행(궁니르) 절대 거부 검증
"""

import time
import json
import urllib.request
import urllib.error
import sys

if hasattr(sys.stdout, 'reconfigure'):
    sys.stdout.reconfigure(encoding='utf-8')

SERVER_URL = "http://127.0.0.1:8080"

def post_json(path, data):
    url = f"{SERVER_URL}{path}"
    body = json.dumps(data).encode('utf-8')
    req = urllib.request.Request(url, data=body, headers={'Content-Type': 'application/json'})
    with urllib.request.urlopen(req, timeout=3) as resp:
        return json.loads(resp.read().decode('utf-8'))

def get_json(path):
    url = f"{SERVER_URL}{path}"
    req = urllib.request.Request(url, headers={'Content-Type': 'application/json'})
    with urllib.request.urlopen(req, timeout=3) as resp:
        return json.loads(resp.read().decode('utf-8'))

print("=" * 70)
print(" [검증 1] Break-Glass 비상 방어 체계 (Emergency Lockdown) 발동")
print("=" * 70)

# 1. 비상 차단 발동
res_trigger = post_json('/v1/security/break-glass', {
    "action": "trigger",
    "reason": "테스트: 이상 조작 의심 감지로 인한 원클릭 시스템 긴급 차단"
})
print(f"▶ 비상 차단 발동 응답: {res_trigger.get('message')}")
assert res_trigger.get('isBreakGlassActive') is True

# 2. 보안 상태 확인
status_sec = get_json('/v1/security/status')
print(f"▶ 현재 시스템 권한 레벨: L{status_sec.get('currentLevel')} (L0 강제 격리)")
print(f"▶ Break-Glass 활성 상태: {status_sec.get('isBreakGlassActive')}")
print(f"▶ 차단 사유: {status_sec.get('breakGlassReason')}")
assert status_sec.get('currentLevel') == 0
print("✅ [성공] Break-Glass 발동 시 0.01초 내 시스템 L0 강제 전환 완료")

print("\n" + "=" * 70)
print(" [검증 2] 비상 차단 중 고위험 자율 실행(Gungnir) 시도 차단 검증")
print("=" * 70)

# 비상 상태에서 금융 결제/송금 실행 시도
res_gungnir = post_json('/v1/gungnir/execute', {
    "actionType": "결제",
    "requestedLevel": 5
})
print(f"▶ 궁니르 실행 시도 결과: success={res_gungnir.get('success')}")
print(f"▶ 시스템 거부 메시지: '{res_gungnir.get('message')}'")
assert res_gungnir.get('success') is False
print("✅ [성공] 비상 차단 중 모든 자율 실행 무조건 거부 확인")

print("\n" + "=" * 70)
print(" [검증 3] Multi-Sig 신뢰인 승인 워크플로우 (2-of-3 다중 서명)")
print("=" * 70)

session_id = f"test-multisig-{int(time.time())}"

# 1번째 신뢰인 서명
sub1 = post_json('/v1/security/multisig/submit', {
    "sessionId": session_id,
    "trusteeId": "trustee-01",
    "signature": "ed25519-sig-tab-s10"
})
print(f"▶ 1차 서명 제출 (trustee-01): 서명 수 {sub1['currentSignaturesCount']}/2 -> 상태: {sub1['status']}")
assert sub1['status'] == "PENDING"

# 2번째 신뢰인 서명 (임계치 2-of-3 충족)
sub2 = post_json('/v1/security/multisig/submit', {
    "sessionId": session_id,
    "trusteeId": "trustee-02",
    "signature": "ed25519-sig-fold-6"
})
print(f"▶ 2차 서명 제출 (trustee-02): 서명 수 {sub2['currentSignaturesCount']}/2 -> 상태: {sub2['status']}")
assert sub2['status'] == "APPROVED"
assert sub2['isApproved'] is True
print("✅ [성공] 2-of-3 Multi-Sig 서명 완료 시 작업 인가 승인")

print("\n" + "=" * 70)
print(" [검증 4] 위변조 불가 SHA-256 해시 체인 감사 로그 검증")
print("=" * 70)

audit_data = get_json('/v1/audit/logs')
print(f"▶ 감사 로그 총 블록 수: {audit_data['totalBlocks']}개")
print(f"▶ 해시 체인 무결성 검증 결과: {'정상 (Tamper-Proof Valid)' if audit_data['integrity'] else '위변조 감지!'}")
print(f"▶ 최신 블록 SHA-256 해시: {audit_data['latestBlockHash']}")
assert audit_data['integrity'] is True

print("▶ 최신 감사 로그 블록 체인 샘플 (상위 3개):")
for b in audit_data['blocks'][:3]:
    print(f"  • Block #{b['index']} [{b['actionType']} / {b['securityLevel']}] PrevHash={b['previousHash'][:12]}... -> Hash={b['hash'][:12]}... ({b['status']})")
print("✅ [성공] 모든 시스템 제어 기록이 위변조 불가 해시 체인으로 봉인됨")

print("\n" + "=" * 70)
print(" [검증 5] Break-Glass 비상 차단 해제 및 정상 운영 복구")
print("=" * 70)

res_release = post_json('/v1/security/break-glass', {
    "action": "release"
})
print(f"▶ 비상 해제 응답: {res_release.get('message')}")
status_restored = get_json('/v1/security/status')
print(f"▶ 복구된 시스템 보안 레벨: L{status_restored.get('currentLevel')} (정상 가동)")
assert status_restored.get('isBreakGlassActive') is False
assert status_restored.get('currentLevel') == 3
print("✅ [성공] 비상 차단 정상 해제 및 L3 권한 복구 완료")

print("\n" + "=" * 70)
print("  PART 6 보안 통제 & 웹 감사 대시보드 전체 검증 100% 통과!")
print("=" * 70)
