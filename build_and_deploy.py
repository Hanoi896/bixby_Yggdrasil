import sys
import os
import subprocess
import shutil

# Windows UTF-8 콘솔 출력 지원
if hasattr(sys.stdout, 'reconfigure'):
    sys.stdout.reconfigure(encoding='utf-8')

print("=" * 65)
print("🚀  Yggdrasil 스마트폰 원클릭 빌드 & 배포 도우미 (Deploy Assistant) 🚀")
print("=" * 65)

# 1. ADB 연결 확인
print("\n1. USB 연결된 갤럭시 스마트폰 탐색 중...")
try:
    result = subprocess.run(["adb", "devices"], capture_output=True, text=True, check=True)
    lines = result.stdout.strip().split("\n")[1:]
    devices = [line.split()[0] for line in lines if "device" in line]

    if devices:
        print(f"   ✅ 스마트폰 연결 확인: {devices[0]}")
    else:
        print("   ⚠️  연결된 기기가 없습니다. 스마트폰에서 [USB 디버깅]을 켜고 PC와 연결해 주세요.")
except FileNotFoundError:
    print("   ℹ️  PC에 adb 명령어가 없습니다. Android Studio를 통해 원클릭 Run을 권장합니다.")

# 2. 안드로이드 소스 구조 확인
print("\n2. 안드로이드 네이티브 앱 소스 구조 점검...")
android_dir = os.path.abspath("core/android")
manifest_path = os.path.join(android_dir, "AndroidManifest.xml")
gradle_path = os.path.join(android_dir, "build.gradle.kts")

if os.path.exists(manifest_path) and os.path.exists(gradle_path):
    print(f"   ✅ Manifest 및 Gradle 빌드 스크립트 완비: {android_dir}")
else:
    print("   ❌ 빌드 설정 파일이 누락되었습니다.")

# 3. 로컬 모델 파일 감지 및 스마트폰 자동 전송 (USB 주입)
print("\n3. 로컬 EXAONE 3.0 / 3.5 GGUF 모델 점검...")
model_dirs = [os.path.abspath("models"), os.path.abspath("core/android/app/src/main/assets/models")]

detected_model = None
for m_dir in model_dirs:
    if os.path.exists(m_dir):
        for f in os.listdir(m_dir):
            if f.endswith(".gguf") and "exaone" in f.lower():
                full_p = os.path.join(m_dir, f)
                if os.path.getsize(full_p) > 500 * 1024 * 1024:
                    detected_model = full_p
                    break
    if detected_model:
        break

if detected_model:
    size_mb = os.path.getsize(detected_model) / (1024 * 1024)
    print(f"   ✅ 프로젝트 내 모델 발견: {os.path.basename(detected_model)} ({size_mb:.1f} MB)")
    if 'devices' in locals() and devices:
        print("   📲 스마트폰 앱 전용 격리 폴더로 자동 전송을 시도합니다 (adb push)...")
        try:
            target_name = os.path.basename(detected_model)
            app_models_dir = "/sdcard/Android/data/com.yggdrasil.assistant/files/models"
            subprocess.run(["adb", "shell", f"mkdir -p {app_models_dir}"], capture_output=True, text=True)
            push_cmd = ["adb", "push", detected_model, f"{app_models_dir}/{target_name}"]
            res = subprocess.run(push_cmd, capture_output=True, text=True, check=True)
            print(f"   🎉 앱 전용 폴더({app_models_dir})로 {target_name} 전송 완료!")
            print("   👉 앱 실행 시 런타임 권한 요구 없이 0.1초 만에 내부 볼트로 자동 승격됩니다.")
        except Exception as e:
            print(f"   ⚠️ 전송 실패 ({e}). 스마트폰 저장공간이나 USB 연결을 확인하세요.")
    else:
        print("   💡 스마트폰이 USB로 연결되면 자동으로 앱 전용 모델 폴더로 전송할 수 있습니다.")
else:
    print("   ℹ️  현재 프로젝트 models/ 폴더에 .gguf 파일이 없습니다.")
    print("   👉 EXAONE 3.5 최신 모델 다운로드 링크:")
    print("      https://huggingface.co/Edge-Quant/EXAONE-3.5-2.4B-Instruct-Q4_K_M-GGUF")
    print("   👉 다운받은 파일을 아래 폴더 중 하나에 넣어두시면 자동으로 감지/배포됩니다:")
    print("      1) bixby_Yggdrasil/models/ (원클릭 USB 전송용)")
    print("      2) bixby_Yggdrasil/core/android/app/src/main/assets/models/ (APK 자체 내장용)")

# 4. 빅스비 캡슐 동기화 안내
print("\n4. 삼성 빅스비 캡슐 동기화...")
capsule_dir = os.path.abspath("playground.yggdrasil")
print(f"   📁 캡슐 경로: {capsule_dir}")
print("   💡 Bixby Developer Studio에서 위 폴더를 열고 [On-Device Testing]을 누르면 폰으로 즉시 동기화됩니다.")

print("\n" + "=" * 65)
print("📋 상세 설치 단계는 [설치_빌드.md] 파일에 친절하게 정리되어 있습니다!")
print("=" * 65)

