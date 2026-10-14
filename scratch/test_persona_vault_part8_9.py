import sys
import os
import time
import json

# Windows UTF-8 콘솔 출력 지원
if hasattr(sys.stdout, 'reconfigure'):
    sys.stdout.reconfigure(encoding='utf-8')

print("=" * 60)
print("[PART 8 & 9 통합 시뮬레이션] 개인화 온보딩 및 앱 내부 격리 스토리지 볼트")
print("=" * 60)

# -------------------------------------------------------------
# 1. PART 8 시뮬레이션: UserPersonaManager (온보딩 및 프롬프트 주입)
# -------------------------------------------------------------
print("\n[PART 8] 1. 최초 실행(First-Run) 감지 및 온보딩 셋업 테스트")

class MockUserPersonaManager:
    def __init__(self):
        self.is_first_run = True
        self.user_call_name = "사용자님"
        self.ai_name = "이그드라실"
        self.tone = "secretary" # secretary, friend, witty
        self.interests = []

    def get_first_run_greeting(self):
        return (
            "반갑습니다! 저는 당신의 갤럭시 전용 AI 파트너 '이그드라실'입니다.\n"
            "원활한 맞춤형 보좌를 위해 몇 가지만 정해볼까요?\n"
            "1. 제가 당신을 어떻게 불러드릴까요? (예: 지훈이 형, 대표님)\n"
            "2. 저의 이름을 다르게 부르고 싶으신가요? (기본: 이그드라실)\n"
            "3. 어떤 말투가 편하신가요? (정중한 비서 모드 / 편안한 친구 모드 / 위트 모드)"
        )

    def complete_onboarding(self, user_call_name, ai_name="이그드라실", tone="secretary", interests=None):
        self.is_first_run = False
        self.user_call_name = user_call_name.strip() or "사용자님"
        self.ai_name = ai_name.strip() or "이그드라실"
        self.tone = tone
        self.interests = interests or []

    def build_personalized_system_prompt(self, base_prompt):
        tone_map = {
            "secretary": "- 말투 지침: 최고 수준의 예의를 갖춘 비서로서 정중하고 공손한 경어체('~하셨습니다', '~드리겠습니다', '~해요')를 일관되게 사용하십시오.",
            "friend": "- 말투 지침: 오랜 절친 또는 든든한 동생처럼 다정하고 편안한 반말('~했어?', '~알겠어!', '~해둘게')을 사용하십시오. 단, 무례하지 않고 신뢰감을 주어야 합니다.",
            "witty": "- 말투 지침: 명석하고 센스 있는 조수처럼 적절한 유머와 재치를 섞은 친절한 어투로 대화하십시오."
        }
        lines = [
            "[SYSTEM IDENTITY & USER PERSONA]",
            f"- 너의 이름: {self.ai_name}",
            f"- 대화 상대(사용자) 호칭: 반드시 '{self.user_call_name}'(으)로 부를 것.",
            tone_map.get(self.tone, tone_map["secretary"]),
        ]
        if self.interests:
            lines.append(f"- 사용자 관심 분야: {', '.join(self.interests)}")
        lines.append("- 기본 시스템 원칙: 사용자의 프라이버시를 절대 보호하며 기기 내 네이티브 리소스를 적극 활용할 것.\n")
        lines.append("[BASE INSTRUCTIONS]")
        lines.append(base_prompt)
        return "\n".join(lines)

persona_mgr = MockUserPersonaManager()
print(f"1) 앱 최초 기동 여부: is_first_run = {persona_mgr.is_first_run}")
assert persona_mgr.is_first_run is True

greeting = persona_mgr.get_first_run_greeting()
print(f"2) 빅스비 최초 호출 첫인사:\n{greeting}")

# 온보딩 완료 시뮬레이션: "지훈이 형", "자비스", "친근한 친구 모드"
persona_mgr.complete_onboarding(
    user_call_name="지훈이 형",
    ai_name="자비스",
    tone="friend",
    interests=["게임 개발", "삼성 스마트싱스", "인공지능"]
)
print("\n3) 온보딩 설정 완료:")
print(f"   - 호칭: {persona_mgr.user_call_name}")
print(f"   - AI 이름: {persona_mgr.ai_name}")
print(f"   - 어투: {persona_mgr.tone}")
print(f"   - 온보딩 완료 후 is_first_run: {persona_mgr.is_first_run}")
assert persona_mgr.is_first_run is False

# EXAONE 프롬프트 주입 테스트
base_instruction = "너는 갤럭시 S26 Ultra의 온디바이스 에이전트로서 사용자의 화면 제어 및 작업을 신속히 수행한다."
personalized_prompt = persona_mgr.build_personalized_system_prompt(base_instruction)
print("\n4) EXAONE 3.0 NPU 엔진에 주입되는 맞춤형 System Prompt:")
print("-" * 50)
print(personalized_prompt)
print("-" * 50)
assert "지훈이 형" in personalized_prompt
assert "자비스" in personalized_prompt
assert "다정하고 편안한 반말" in personalized_prompt

# -------------------------------------------------------------
# 2. PART 9 시뮬레이션: AppStorageVaultManager (격리 및 TTL 청소)
# -------------------------------------------------------------
print("\n[PART 9] 2. 앱 전용 격리 스토리지 볼트 & 가비지 컬렉터(GC) 테스트")

class MockAppStorageVaultManager:
    def __init__(self, base_dir="./mock_app_storage"):
        self.base_dir = base_dir
        self.vault_dir = os.path.join(base_dir, "files", "vault")
        self.models_dir = os.path.join(base_dir, "files", "models")
        self.sandbox_dir = os.path.join(base_dir, "files", "sandbox")
        self.screenshot_cache_dir = os.path.join(base_dir, "cache", "screenshots")
        self.archive_cache_dir = os.path.join(base_dir, "cache", "archives")
        self._init_dirs()

    def _init_dirs(self):
        for d in [self.vault_dir, self.models_dir, self.sandbox_dir, 
                  self.screenshot_cache_dir, self.archive_cache_dir]:
            os.makedirs(d, exist_ok=True)

    def run_garbage_collection(self, current_time=None):
        if current_time is None:
            current_time = time.time()
        
        ttl_screenshot_sec = 3600 # 1시간
        ttl_archive_sec = 86400   # 24시간
        freed_bytes = 0
        deleted_screenshots = 0
        deleted_archives = 0

        # 스크린샷 정리
        for fname in os.listdir(self.screenshot_cache_dir):
            fpath = os.path.join(self.screenshot_cache_dir, fname)
            mtime = os.path.getmtime(fpath)
            if current_time - mtime > ttl_screenshot_sec:
                freed_bytes += os.path.getsize(fpath)
                os.remove(fpath)
                deleted_screenshots += 1

        # 아카이브 정리
        for fname in os.listdir(self.archive_cache_dir):
            fpath = os.path.join(self.archive_cache_dir, fname)
            mtime = os.path.getmtime(fpath)
            if current_time - mtime > ttl_archive_sec:
                freed_bytes += os.path.getsize(fpath)
                os.remove(fpath)
                deleted_archives += 1

        return {
            "deleted_screenshots": deleted_screenshots,
            "deleted_archives": deleted_archives,
            "freed_bytes": freed_bytes
        }

vault_mgr = MockAppStorageVaultManager()
print(f"1) 5대 격리 영역 디렉터리 생성 완료:")
print(f"   - [보안 볼트]: {vault_mgr.vault_dir}")
print(f"   - [모델 가중치]: {vault_mgr.models_dir}")
print(f"   - [에이전트 샌드박스]: {vault_mgr.sandbox_dir}")
print(f"   - [스크린샷 캐시]: {vault_mgr.screenshot_cache_dir}")
print(f"   - [압축 아카이브 캐시]: {vault_mgr.archive_cache_dir}")

# 파일 생성 시뮬레이션
# 1. 2시간 전 생성된 스크린샷 (만료 대상)
expired_screenshot = os.path.join(vault_mgr.screenshot_cache_dir, "screen_cap_old.png")
with open(expired_screenshot, "w") as f:
    f.write("DUMMY_IMAGE_BYTES_12345" * 100)
past_time_2h = time.time() - 7200
os.utime(expired_screenshot, (past_time_2h, past_time_2h))

# 2. 방금 생성된 스크린샷 (보존 대상)
fresh_screenshot = os.path.join(vault_mgr.screenshot_cache_dir, "screen_cap_fresh.png")
with open(fresh_screenshot, "w") as f:
    f.write("FRESH_IMAGE_BYTES")

# 3. 25시간 전 생성된 ZIP 압축본 (만료 대상)
expired_archive = os.path.join(vault_mgr.archive_cache_dir, "backup_20261013.zip")
with open(expired_archive, "w") as f:
    f.write("DUMMY_ZIP_BYTES" * 200)
past_time_25h = time.time() - 90000
os.utime(expired_archive, (past_time_25h, past_time_25h))

print("\n2) 가비지 컬렉터(GC) 실행 전 캐시 상태:")
print(f"   - 스크린샷 폴더 내 파일: {os.listdir(vault_mgr.screenshot_cache_dir)}")
print(f"   - 아카이브 폴더 내 파일: {os.listdir(vault_mgr.archive_cache_dir)}")

# GC 실행
gc_result = vault_mgr.run_garbage_collection()
print("\n3) 가비지 컬렉터(GC) 실행 결과:")
print(f"   - 삭제된 만료 스크린샷: {gc_result['deleted_screenshots']}개")
print(f"   - 삭제된 만료 압축파일: {gc_result['deleted_archives']}개")
print(f"   - 회수된 용량: {gc_result['freed_bytes']} 바이트")

# 검증
assert gc_result['deleted_screenshots'] == 1
assert gc_result['deleted_archives'] == 1
assert not os.path.exists(expired_screenshot), "만료된 스크린샷이 삭제되지 않음"
assert os.path.exists(fresh_screenshot), "최신 스크린샷이 보존되지 않음"
assert not os.path.exists(expired_archive), "만료된 압축파일이 삭제되지 않음"

print("\n4) 가비지 컬렉터(GC) 실행 후 보존 상태:")
print(f"   - 스크린샷 폴더 잔여: {os.listdir(vault_mgr.screenshot_cache_dir)}")
print(f"   - 아카이브 폴더 잔여: {os.listdir(vault_mgr.archive_cache_dir)}")

# 정리 (mock 디렉터리 삭제)
import shutil
shutil.rmtree(vault_mgr.base_dir, ignore_errors=True)

print("\n" + "=" * 60)
print(">>> [SUCCESS] PART 8(개인화 온보딩) 및 PART 9(스토리지 격리 볼트) 시뮬레이션 통과! <<<")
print("=" * 60)
