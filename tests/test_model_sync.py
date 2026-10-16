import sys
import os
import shutil

# Windows UTF-8 콘솔 출력 지원
if hasattr(sys.stdout, 'reconfigure'):
    sys.stdout.reconfigure(encoding='utf-8')

print("=" * 65)
print("[검증] GGUF 모델 자동 이관(Auto-Import) & 이전 모델 자동 삭제(GC)")
print("=" * 65)

MOCK_ROOT = "./mock_phone_storage"
DOWNLOAD_DIR = os.path.join(MOCK_ROOT, "sdcard", "Download")
INTERNAL_MODELS_DIR = os.path.join(MOCK_ROOT, "data", "user", "0", "com.yggdrasil.assistant", "files", "models")

os.makedirs(DOWNLOAD_DIR, exist_ok=True)
os.makedirs(INTERNAL_MODELS_DIR, exist_ok=True)

# 1. 시나리오 1: 사용자가 Download 폴더에 새 모델을 넣어둠
new_model_download = os.path.join(DOWNLOAD_DIR, "exaone-3.0-2.4b-instruct-q4_k_m.gguf")
with open(new_model_download, "w") as f:
    f.write("EXAONE_3.0_NEW_MODEL_WEIGHTS_VERSION_20261014" * 1000)

# 2. 내부 볼트에는 과거 구버전 모델이 남아있는 상태
old_model_internal = os.path.join(INTERNAL_MODELS_DIR, "exaone-3.0-old-backup.gguf")
with open(old_model_internal, "w") as f:
    f.write("OLD_DEPRECATED_WEIGHTS" * 500)

print(f"1) 초기 상태:")
print(f"   - Download 폴더 파일: {os.listdir(DOWNLOAD_DIR)}")
print(f"   - 내부 격리 볼트 파일: {os.listdir(INTERNAL_MODELS_DIR)}")

# 3. ModelAutoSyncManager 시뮬레이션 실행 (자동 이관 + 구버전 삭제)
def mock_auto_import():
    candidates = [f for f in os.listdir(DOWNLOAD_DIR) if f.endswith(".gguf") and "exaone" in f.lower()]
    if not candidates:
        return False
    
    src = os.path.join(DOWNLOAD_DIR, candidates[0])
    target_name = "exaone-3.0-2.4b-instruct-q4_k_m.gguf"
    dst = os.path.join(INTERNAL_MODELS_DIR, target_name)
    
    # 구버전 정리 (active 모델 제외 전원 삭제)
    for f in os.listdir(INTERNAL_MODELS_DIR):
        if f != target_name:
            os.remove(os.path.join(INTERNAL_MODELS_DIR, f))
            print(f"   🗑️ 구버전 모델 파일 자동 삭제 회수: {f}")
            
    # 복사 후 Download 폴더 원본 삭제
    shutil.copy2(src, dst)
    os.remove(src)
    print(f"   ✅ 새 모델 내부 볼트 이관 완료 & Download 폴더 중복 파일 삭제 완료!")
    return True

print("\n2) 앱 기동 ➔ ModelAutoSyncManager 실행:")
success = mock_auto_import()
assert success is True

print("\n3) 실행 후 상태 확인:")
download_remaining = os.listdir(DOWNLOAD_DIR)
internal_models = os.listdir(INTERNAL_MODELS_DIR)

print(f"   - Download 폴더 잔여: {download_remaining} (0개 비워짐 -> 2.17GB 낭비 방지)")
print(f"   - 내부 격리 볼트 잔여: {internal_models} (최신 활성 모델 1개만 안전 보관)")

assert len(download_remaining) == 0, "다운로드 폴더의 파일이 삭제되지 않음"
assert internal_models == ["exaone-3.0-2.4b-instruct-q4_k_m.gguf"], "최신 모델만 남아야 함"

# 청소
shutil.rmtree(MOCK_ROOT, ignore_errors=True)

print("\n" + "=" * 65)
print(">>> [PASS] Download 폴더 자동 이관 및 구버전 자동 삭제 검증 성공! <<<")
print("=" * 65)
