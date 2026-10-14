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
            "currentLevel": MOCK_CURRENT_LEVEL,
            "isBreakGlassActive": False,
            "isLearningBlocked": False,
            "multiSigRequired": False,
            "activeTrusteesCount": 1
        })

    def handle_memory_record(self, body):
        content = str(body.get('content', '')).strip()
        if not content:
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
        # 서버(Odin)가 현재 권한 레벨로 판정 - 클라이언트 requestedLevel 은 참고만 함
        allowed = MOCK_CURRENT_LEVEL >= required_level
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
