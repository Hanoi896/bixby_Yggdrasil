#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
test_local_server.py
갤럭시 S26 울트라의 llama.cpp + Yggdrasil 로컬 데몬(127.0.0.1:8080)과 동일한 규격으로 동작하는 테스트용 서버입니다.
빅스비 스튜디오(Bixby Studio) 시뮬레이터에서 캡슐을 테스트할 때 실행해 두면 실제 데몬처럼 동작합니다.

지원 API:
  POST /v1/chat/completions    - EXAONE 채팅 (ProcessAutonomousDialogue, ConsultHugin)
  GET  /v1/yggdrasil/growth    - 세계수 성장 (GetYggdrasilGrowth)
  GET  /v1/pantheon/overview   - 판테온 관제 (GetPantheonOverview)
  GET  /v1/security/status     - 보안 점검 (InspectSecurity)
  POST /v1/memory/records      - 기억 기록 (RecordMemory)
  GET  /v1/memory/records?q=   - 기억 검색 (QueryMemory)
  POST /v1/gungnir/execute     - 궁니르 실행 (ExecuteGungnirAction)
"""

from http.server import HTTPServer, BaseHTTPRequestHandler
from urllib.parse import urlparse, parse_qs
from datetime import datetime, timezone
import hashlib
import json
import time
import sys
import os

# Windows 콘솔 인코딩 안전 처리
if sys.platform == 'win32':
    sys.stdout.reconfigure(encoding='utf-8')

PORT = 8080
MOCK_CURRENT_LEVEL = 3          # 테스트용 현재 권한 레벨
DEFAULT_QUERY_LIMIT = 10
MAX_QUERY_LIMIT = 50

# Gungnir 작업별 필요 권한 (실제 데몬에서는 Odin 이 판정)
GUNGNIR_POLICIES = [
    (('iot', '조명', '전등', '에어컨', '보일러', '스마트홈'), 3, 'Nidavellir'),
    (('결제', '송금', '이체', 'payment'), 5, 'Nidavellir'),
    (('비상', '차단', 'lockdown'), 2, 'Asgard'),
    (('계정', 'oauth'), 6, 'Jotunheim'),
]
DEFAULT_POLICY = (5, 'Nidavellir')

# 인메모리 기억 저장소 (서버 재시작 시 초기화)
memory_store = [
    {"id": "mem-0001", "category": "Routine", "content": "평일 기상 시간은 오전 7시", "timestamp": "2026-10-13T07:00:00Z"},
    {"id": "mem-0002", "category": "Work", "content": "내일 오전 10시 디자인 회의", "timestamp": "2026-10-13T18:20:00Z"},
]

# PART 6 보안 통제 상태 (Break-Glass 및 Multi-Sig)
is_break_glass_active = False
break_glass_reason = None
multisig_sessions = {}

# PART 7 온디바이스 프라이버시 & 시크릿 모드 (학습 잠시 끄는 버튼)
is_incognito_active = False

def check_sensitive_privacy_content(text):
    """성생활/19금, 민감 의료, 개인 식별 키워드 자동 필터링"""
    lower = text.lower()
    sensitive_keywords = [
        "성인용품", "성생활", "피임", "콘돔", "성관계", "자위", "야동", "포르노", "adult", "sex", "porn",
        "비뇨기과", "산부인과", "정신과", "우울증", "성병", "임신", "낙태", "에이즈", "hiv",
        "주민번호", "주민등록번호", "보안카드", "cvc"
    ]
    for kw in sensitive_keywords:
        if kw in lower:
            return True, kw
    return False, None

# PART 6 위변조 불가 SHA-256 감사 로그 해시 체인
audit_hash_chain = []

def init_genesis_block():
    if not audit_hash_chain:
        genesis_prev = "0" * 64
        genesis_raw = f"0|2026-10-14T00:00:00Z|SYSTEM_GENESIS|L0|Asgard|Yggdrasil 보안 감사 로그 해시 체인 가동|{genesis_prev}|SUCCESS"
        genesis_hash = hashlib.sha256(genesis_raw.encode('utf-8')).hexdigest()
        audit_hash_chain.append({
            "index": 0,
            "timestamp": "2026-10-14T00:00:00Z",
            "actionType": "SYSTEM_GENESIS",
            "securityLevel": "L0",
            "realm": "Asgard",
            "details": "Yggdrasil 보안 감사 로그 해시 체인 가동",
            "previousHash": genesis_prev,
            "hash": genesis_hash,
            "status": "SUCCESS"
        })

init_genesis_block()

def append_audit_block(action_type, level, realm, details, status="SUCCESS"):
    prev_hash = audit_hash_chain[-1]["hash"] if audit_hash_chain else "0" * 64
    idx = len(audit_hash_chain)
    ts = now_iso()
    raw = f"{idx}|{ts}|{action_type}|{level}|{realm}|{details}|{prev_hash}|{status}"
    cur_hash = hashlib.sha256(raw.encode('utf-8')).hexdigest()
    block = {
        "index": idx,
        "timestamp": ts,
        "actionType": action_type,
        "securityLevel": level,
        "realm": realm,
        "details": details,
        "previousHash": prev_hash,
        "hash": cur_hash,
        "status": status
    }
    audit_hash_chain.append(block)
    return block


def now_iso():
    return datetime.now(timezone.utc).strftime('%Y-%m-%dT%H:%M:%SZ')


def mock_sign(payload):
    """Ed25519 서명 대신 SHA-256 해시로 서명을 흉내냅니다 (테스트 전용)."""
    return 'mock-ed25519:' + hashlib.sha256(payload.encode('utf-8')).hexdigest()[:32]


def find_policy(action_type):
    lower = action_type.lower()
    for keywords, level, realm in GUNGNIR_POLICIES:
        if any(k in lower for k in keywords):
            return level, realm
    return DEFAULT_POLICY


def with_signature(record):
    signed = dict(record)
    signed['isEncrypted'] = True
    signed['signatureHash'] = mock_sign(record['id'] + record['content'])
    return signed


class MockYggdrasilHandler(BaseHTTPRequestHandler):
    # ---------- 공통 유틸 ----------
    def read_json_body(self):
        length = int(self.headers.get('Content-Length', 0))
        raw = self.rfile.read(length) if length > 0 else b''
        try:
            return json.loads(raw.decode('utf-8')) if raw else {}
        except (ValueError, UnicodeDecodeError):
            return None

    def send_json(self, status, data):
        body = json.dumps(data, ensure_ascii=False).encode('utf-8')
        self.send_response(status)
        self.send_header('Content-Type', 'application/json; charset=utf-8')
        self.send_header('Content-Length', str(len(body)))
        self.send_header('Access-Control-Allow-Origin', '*')
        self.end_headers()
        self.wfile.write(body)

    # ---------- 라우팅 ----------
    def do_GET(self):
        parsed = urlparse(self.path)
        routes = {
            '/v1/yggdrasil/growth': self.handle_growth,
            '/v1/pantheon/overview': self.handle_pantheon,
            '/v1/security/status': self.handle_security,
            '/v1/tools': self.handle_tools,
            '/v1/audit/logs': self.handle_audit_logs,
            '/v1/privacy/status': self.handle_privacy_status,
            '/v1/memory/records': lambda: self.handle_memory_query(parse_qs(parsed.query)),
        }
        handler = routes.get(parsed.path)
        if handler:
            handler()
        else:
            self.send_json(404, {"error": "not found"})

    def do_POST(self):
        path = urlparse(self.path).path
        body = self.read_json_body()
        if body is None:
            self.send_json(400, {"error": "invalid json"})
            return

        routes = {
            '/v1/chat/completions': self.handle_chat,
            '/v1/memory/records': self.handle_memory_record,
            '/v1/gungnir/execute': self.handle_gungnir,
            '/v1/security/break-glass': self.handle_break_glass,
            '/v1/security/multisig/submit': self.handle_multisig_submit,
            '/v1/privacy/toggle': self.handle_privacy_toggle,
        }
        handler = routes.get(path)
        if handler:
            handler(body)
        else:
            self.send_json(404, {"error": "not found"})

    # ---------- 핸들러 ----------
    def handle_chat(self, body):
        messages = body.get('messages', [])
        user_msg = messages[-1].get('content', '') if messages else ''
        system_msg = messages[0].get('content', '') if messages and messages[0].get('role') == 'system' else ''
        print(f"[RECV] 사용자 발화: {user_msg}")

        # ConsultHugin 은 JSON 형식 응답을 요구함
        if 'JSON' in system_msg:
            reply_text = json.dumps({
                "intent": f"'{user_msg}' 관련 브리핑 요청",
                "thoughtProcess": "Munin 의 일정/루틴 기억을 참조하여 우선순위를 정리했습니다.",
                "recommendation": "오전 10시 디자인 회의 전에 자료를 미리 검토하는 것을 권장합니다.",
                "actionPlan": ["09:30 회의 자료 알림 설정", "회의실 조명 켜기 (Gungnir L3)"],
                "confidence": 82
            }, ensure_ascii=False)
        else:
            reply_text = f"온디바이스 EXAONE 3.0입니다. '{user_msg}'에 대한 작업을 성공적으로 분석했습니다. 오늘 하루도 좋은 시간 되세요!"

        self.send_json(200, {
            "id": "chatcmpl-mock-s26u",
            "object": "chat.completion",
            "created": int(time.time()),
            "model": "EXAONE-3.0-2.4B-Instruct",
            "choices": [{
                "index": 0,
                "message": {"role": "assistant", "content": reply_text},
                "finish_reason": "stop"
            }],
            "usage": {"prompt_tokens": 30, "completion_tokens": 25, "total_tokens": 55}
        })
        print(f"[SEND] EXAONE 발화: {reply_text}\n")

    def handle_growth(self):
        self.send_json(200, {
            "stage": "Trunk",
            "growthPercent": 64,
            "wisdomPoints": 1280,
            "guardianAgent": "Hugin",
            "unlockedAbilities": ["생활 패턴 수집", "출퇴근 맥락 인지", "일정 선제 알림"],
            "nextStageRequirement": "지혜 포인트 2,000 달성 및 신뢰인 2명 등록 시 Branches 단계로 진화"
        })

    def handle_pantheon(self):
        self.send_json(200, {
            "overallStatus": "정상",
            "securityLevel": f"L{MOCK_CURRENT_LEVEL}",
            "tiers": [
                {"name": "Odin", "role": "시스템 감독관", "status": "가동", "description": "권한 승인 대기열 0건", "realm": "Asgard"},
                {"name": "Hugin", "role": "생각하는 두뇌", "status": "가동", "description": "EXAONE 3.0 2.4B 로드 완료", "realm": "Vanaheim"},
                {"name": "Munin", "role": "기억하는 서기관", "status": "가동", "description": f"기억 {len(memory_store)}건 보관", "realm": "Niflheim"},
                {"name": "Draupnir", "role": "자원 관리자", "status": "가동", "description": "RAM 2.1GB / NPU 12%", "realm": "Muspelheim"},
                {"name": "Gungnir", "role": "치명적 실행자", "status": "대기", "description": "실행 대기 작업 없음", "realm": "Nidavellir"},
                {"name": "Sleipnir", "role": "고속 실행자", "status": "대기", "description": "네트워크 오프라인 모드", "realm": "Alfheim"},
            ],
            "realms": [
                {"name": "Asgard", "accessLevel": "System Only", "description": "시스템 코어", "isSecure": True, "healthScore": 98},
                {"name": "Vanaheim", "accessLevel": "Internal", "description": "AI 추론 엔진", "isSecure": True, "healthScore": 95},
                {"name": "Alfheim", "accessLevel": "Internet", "description": "고속 통신망", "isSecure": True, "healthScore": 90},
                {"name": "Midgard", "accessLevel": "User", "description": "사용자 인터페이스", "isSecure": True, "healthScore": 97},
                {"name": "Jotunheim", "accessLevel": "API Key", "description": "외부 API 연동", "isSecure": False, "healthScore": 72},
                {"name": "Nidavellir", "accessLevel": "Biometric", "description": "금융/IoT 실행", "isSecure": True, "healthScore": 99},
                {"name": "Niflheim", "accessLevel": "Encrypted", "description": "암호화 기억 저장소", "isSecure": True, "healthScore": 100},
                {"name": "Muspelheim", "accessLevel": "System Only", "description": "CPU/RAM 자원", "isSecure": True, "healthScore": 88},
                {"name": "Hel", "accessLevel": "Debug", "description": "에러 로그", "isSecure": True, "healthScore": 93},
            ],
            "activeOdinMessage": "모든 영역이 격리된 상태로 안정적으로 운영 중입니다."
        })

    def handle_security(self):
        self.send_json(200, {
            "currentLevel": 0 if is_break_glass_active else MOCK_CURRENT_LEVEL,
            "isBreakGlassActive": is_break_glass_active,
            "breakGlassReason": break_glass_reason,
            "isLearningBlocked": False,
            "multiSigRequired": True,
            "activeTrusteesCount": 3,
            "multiSigThreshold": "2-of-3"
        })

    def handle_audit_logs(self):
        # 전체 해시 체인의 무결성 검증
        integrity_ok = True
        for i in range(1, len(audit_hash_chain)):
            if audit_hash_chain[i]["previousHash"] != audit_hash_chain[i-1]["hash"]:
                integrity_ok = False
                break

        self.send_json(200, {
            "integrity": integrity_ok,
            "totalBlocks": len(audit_hash_chain),
            "latestBlockHash": audit_hash_chain[-1]["hash"] if audit_hash_chain else None,
            "blocks": list(reversed(audit_hash_chain[-30:])) # 최신 30개 반환
        })

    def handle_break_glass(self, body):
        global is_break_glass_active, break_glass_reason
        action = str(body.get('action', 'trigger')).lower()
        if action == 'trigger':
            is_break_glass_active = True
            break_glass_reason = str(body.get('reason', '웹 대시보드/사용자 원클릭 비상 차단'))
            append_audit_block("BREAK_GLASS_TRIGGER", "L10", "Asgard", f"비상 차단 발동: {break_glass_reason}", status="BREAK_GLASS")
            print(f"[BREAK-GLASS] 🚨 긴급 차단 발동! 이유: {break_glass_reason}")
            self.send_json(200, {"success": True, "isBreakGlassActive": True, "message": "시스템 비상 차단 완료. 모든 실행 정지 (L0 강등)"})
        elif action == 'release':
            is_break_glass_active = False
            break_glass_reason = None
            append_audit_block("BREAK_GLASS_RELEASE", "L5", "Asgard", "비상 차단 정상 해제", status="SUCCESS")
            print("[BREAK-GLASS] 🛡️ 비상 차단 해제됨. 정상 운영 복귀.")
            self.send_json(200, {"success": True, "isBreakGlassActive": False, "message": "비상 차단이 성공적으로 해제되었습니다."})
        else:
            self.send_json(400, {"error": "invalid break glass action"})

    def handle_multisig_submit(self, body):
        session_id = body.get('sessionId', 'default-session')
        trustee_id = body.get('trusteeId', 'trustee-01')
        sig = body.get('signature', 'mock-ed25519-signature')

        if session_id not in multisig_sessions:
            multisig_sessions[session_id] = {"signatures": {}}

        multisig_sessions[session_id]["signatures"][trustee_id] = sig
        count = len(multisig_sessions[session_id]["signatures"])
        approved = count >= 2

        status_str = "APPROVED" if approved else "PENDING"
        append_audit_block("MULTI_SIG_SUBMIT", "L8", "Nidavellir", f"신뢰인 {trustee_id} 서명 제출 (현재 {count}/2)", status=status_str)

        self.send_json(200, {
            "sessionId": session_id,
            "trusteeId": trustee_id,
            "currentSignaturesCount": count,
            "requiredSignatures": 2,
            "isApproved": approved,
            "status": status_str
        })

    def handle_privacy_status(self):
        self.send_json(200, {
            "isIncognitoActive": is_incognito_active,
            "mode": "시크릿 모드 (휘발성 처리, DB 저장 차단)" if is_incognito_active else "일반 모드 (개인화 학습 활성화)",
            "sensitiveCategoriesBlocked": ["성생활/19금", "민감 의료/질병", "주민등록번호/금융"]
        })

    def handle_privacy_toggle(self, body):
        global is_incognito_active
        requested_state = body.get('incognito')
        if requested_state is None:
            is_incognito_active = not is_incognito_active
        else:
            is_incognito_active = bool(requested_state)

        mode_str = "시크릿 모드 ON (학습 일시정지)" if is_incognito_active else "시크릿 모드 OFF (학습 재개)"
        append_audit_block("PRIVACY_TOGGLE", "L2", "Midgard", f"프라이버시 모드 전환: {mode_str}", status="SUCCESS")
        print(f"[PRIVACY] 🔒 {mode_str}")
        self.send_json(200, {
            "success": True,
            "isIncognitoActive": is_incognito_active,
            "message": f"성공적으로 {mode_str} 상태로 전환되었습니다."
        })

    def handle_tools(self):
        schema_path = os.path.join(os.path.dirname(__file__), 'core', 'tools', 'tools_schema.json')
        if os.path.exists(schema_path):
            with open(schema_path, 'r', encoding='utf-8') as f:
                self.send_json(200, json.load(f))
        else:
            self.send_json(404, {"error": "tools schema not found"})

    def handle_memory_record(self, body):
        content = str(body.get('content', '')).strip()
        if not content:
            self.send_json(400, {"error": "content required"})
            return

        # 1. 시크릿 모드 활성화 시 기억 DB 저장 거부 (완전 휘발성 처리)
        if is_incognito_active:
            print(f"[PRIVACY] 🔒 시크릿 모드 동작 중: 기억 저장 거부 (휘발성 세션 유지) -> '{content[:20]}...'")
            self.send_json(200, {
                "id": "ephemeral-temp",
                "status": "SKIPPED_DUE_TO_INCOGNITO",
                "message": "시크릿 모드(학습 끄기) 활성화 중으로 DB에 저장되지 않고 휘발성 처리되었습니다."
            })
            return

        # 2. 민감 카테고리(성생활, 의료, 주민번호) 자동 감지 시 저장 원천 차단
        is_sensitive, keyword = check_sensitive_privacy_content(content)
        if is_sensitive:
            print(f"[PRIVACY] 🚨 민감 카테고리 감지('{keyword}'): 프라이버시 보호를 위해 DB 저장 원천 차단")
            self.send_json(200, {
                "id": "blocked-sensitive",
                "status": "AUTO_EXCLUDED_FROM_LEARNING",
                "message": f"민감 개인 프라이버시('{keyword}') 감지로 인해 학습 및 저장이 원천 차단되었습니다."
            })
            return
            self.send_json(400, {"error": "content required"})
            return
        record = {
            "id": f"mem-{len(memory_store) + 1:04d}",
            "category": body.get('category', 'General'),
            "content": content,
            "timestamp": now_iso(),
        }
        memory_store.append(record)
        print(f"[MUNIN] 기억 저장: {record['content']}")
        self.send_json(200, with_signature(record))

    def handle_memory_query(self, params):
        keyword = params.get('q', [''])[0].strip()
        try:
            limit = min(int(params.get('limit', [DEFAULT_QUERY_LIMIT])[0]), MAX_QUERY_LIMIT)
        except ValueError:
            limit = DEFAULT_QUERY_LIMIT

        matched = [r for r in reversed(memory_store) if not keyword or keyword in r['content']]
        self.send_json(200, {
            "records": [with_signature(r) for r in matched[:limit]],
            "totalCount": len(matched)
        })

    def handle_gungnir(self, body):
        action_type = str(body.get('actionType', '')).strip()
        required_level, realm = find_policy(action_type)

        # Break-Glass 활성화 시 모든 궁니르 실행 강제 차단 (L0 강등)
        if is_break_glass_active:
            append_audit_block(action_type, f"L{required_level}", realm, f"Break-Glass 비상 차단으로 실행 거부: {action_type}", status="BLOCKED")
            print(f"[GUNGNIR] 🚨 Break-Glass 발동 중: {action_type} 실행 차단")
            self.send_json(200, {
                "success": False,
                "actionType": action_type,
                "requiredLevel": required_level,
                "realm": realm,
                "message": "비상 방어(Break-Glass) 활성화 상태입니다. 모든 자율 실행이 즉시 차단되었습니다."
            })
            return

        allowed = MOCK_CURRENT_LEVEL >= required_level
        status_str = "SUCCESS" if allowed else "BLOCKED"
        append_audit_block(action_type, f"L{required_level}", realm, f"궁니르 작업 실행: {action_type}", status=status_str)

        result = {
            "success": allowed,
            "actionType": action_type,
            "requiredLevel": required_level,
            "realm": realm,
            "message": (f"Odin 승인 완료: '{action_type}' 작업을 실행했습니다."
                        if allowed else
                        f"권한 부족: 현재 L{MOCK_CURRENT_LEVEL}, 필요 L{required_level}"),
        }
        if allowed:
            result["signedHash"] = mock_sign(action_type + now_iso())
        print(f"[GUNGNIR] {action_type} -> {'승인' if allowed else '거부'}")
        self.send_json(200, result)


def run():
    httpd = HTTPServer(('127.0.0.1', PORT), MockYggdrasilHandler)
    print("=" * 60)
    print("[START] 온디바이스 Yggdrasil 로컬 데몬 테스트 서버 구동 완료!")
    print(f"   주소: http://127.0.0.1:{PORT}")
    print("   빅스비 스튜디오 시뮬레이터에서 자유롭게 테스트해 보세요.")
    print("=" * 60)
    try:
        httpd.serve_forever()
    except KeyboardInterrupt:
        print("\n서버를 종료합니다.")


if __name__ == '__main__':
    run()
