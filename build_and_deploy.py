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

# 3. 빅스비 캡슐 동기화 안내
print("\n3. 삼성 빅스비 캡슐 동기화...")
capsule_dir = os.path.abspath("playground.yggdrasil")
print(f"   📁 캡슐 경로: {capsule_dir}")
print("   💡 Bixby Developer Studio에서 위 폴더를 열고 [On-Device Testing]을 누르면 폰으로 즉시 동기화됩니다.")

print("\n" + "=" * 65)
print("📋 상세 설치 단계는 [설치_빌드.md] 파일에 친절하게 정리되어 있습니다!")
print("=" * 65)
