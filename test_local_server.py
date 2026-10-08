#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
test_local_server.py
갤럭시 S26 울트라의 llama.cpp 로컬 서버(127.0.0.1:8080)와 동일한 규격으로 동작하는 테스트용 서버입니다.
빅스비 스튜디오(Bixby Studio) 시뮬레이터에서 캡슐을 테스트할 때 실행해 두면 실제 EXAONE 응답처럼 동작합니다.
"""

from http.server import HTTPServer, BaseHTTPRequestHandler
import json
import time

PORT = 8080

class MockLlmHandler(BaseHTTPRequestHandler):
    def do_POST(self):
        if self.path == '/v1/chat/completions':
            content_length = int(self.headers.get('Content-Length', 0))
            post_data = self.rfile.read(content_length)
            
            try:
                body = json.loads(post_data.decode('utf-8'))
                messages = body.get('messages', [])
                user_msg = messages[-1].get('content', '') if messages else ''
                print(f"[수신] 사용자 발화: {user_msg}")
            except Exception as e:
                user_msg = "알 수 없는 입력"

            # EXAONE 시뮬레이션 응답 생성
            reply_text = f"온디바이스 EXAONE 3.0입니다. '{user_msg}'에 대한 작업을 성공적으로 분석했습니다. 오늘 하루도 좋은 시간 되세요!"

            response_data = {
                "id": "chatcmpl-mock-s26u",
                "object": "chat.completion",
                "created": int(time.time()),
                "model": "EXAONE-3.0-2.4B-Instruct",
                "choices": [
                    {
                        "index": 0,
                        "message": {
                            "role": "assistant",
                            "content": reply_text
                        },
                        "finish_reason": "stop"
                    }
                ],
                "usage": {
                    "prompt_tokens": 30,
                    "completion_tokens": 25,
                    "total_tokens": 55
                }
            }

            self.send_response(200)
            self.send_header('Content-Type', 'application/json; charset=utf-8')
            self.send_header('Access-Control-Allow-Origin', '*')
            self.end_headers()
            self.wfile.write(json.dumps(response_data, ensure_ascii=False).encode('utf-8'))
            print(f"[응답] EXAONE 발화: {reply_text}\n")
        else:
            self.send_response(404)
            self.end_headers()

def run():
    server_address = ('127.0.0.1', PORT)
    httpd = HTTPServer(server_address, MockLlmHandler)
    print("=" * 60)
    print(f"🚀 온디바이스 로컬 LLM 테스트 서버 구동 완료!")
    print(f"   주소: http://127.0.0.1:{PORT}/v1/chat/completions")
    print(f"   빅스비 스튜디오 시뮬레이터에서 자유롭게 발화해 보세요.")
    print("=" * 60)
    try:
        httpd.serve_forever()
    except KeyboardInterrupt:
        print("\n서버를 종료합니다.")

if __name__ == '__main__':
    run()
