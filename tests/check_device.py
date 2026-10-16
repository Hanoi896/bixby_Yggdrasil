import subprocess
import os
import sys

# UTF-8 출력 보장
if hasattr(sys.stdout, 'reconfigure'):
    sys.stdout.reconfigure(encoding='utf-8')

# 1. 모델 파일 검사
model_path = os.path.abspath("models/exaone-3.5-2.4b-instruct-q4_k_m.gguf")
if os.path.exists(model_path):
    size_gb = os.path.getsize(model_path) / (1024**3)
    print(f"[1] 모델 파일: exaone-3.5-2.4b-instruct-q4_k_m.gguf ({size_gb:.2f} GB) 준비 완료!")
else:
    print("[1] 모델 파일 없음")

# 2. Windows PnP 장치 검색
ps_cmd = "[Console]::OutputEncoding = [System.Text.Encoding]::UTF8; Get-CimInstance Win32_PnPEntity | Where-Object { $_.Caption -like '*Samsung*' -or $_.Caption -like '*Galaxy*' -or $_.Caption -like '*Android*' -or $_.PNPClass -eq 'WPD' } | Select-Object Caption, PNPClass, Status | Format-Table -AutoSize | Out-String"

res = subprocess.run(["powershell", "-NoProfile", "-Command", ps_cmd], capture_output=True, text=True, encoding="utf-8", errors="replace")
print("\n[2] 연결된 모바일 장치 (USB / MTP / ADB):")
if res.stdout and res.stdout.strip():
    print(res.stdout.strip())
else:
    print("   -> 현재 USB로 인식된 모바일 장치가 없습니다.")

# 3. ADB 위치 및 장치 목록
adb_candidates = [
    os.path.expandvars(r"%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe"),
    r"C:\Program Files\Android\platform-tools\adb.exe",
    r"C:\platform-tools\adb.exe",
    "adb"
]

found_adb = None
for path in adb_candidates:
    try:
        r = subprocess.run([path, "version"], capture_output=True, text=True, encoding="utf-8", errors="replace")
        if r.returncode == 0:
            found_adb = path
            break
    except Exception:
        continue

print("\n[3] ADB 디버깅 도구:")
if found_adb:
    print(f"   ADB 위치: {found_adb}")
    dev_r = subprocess.run([found_adb, "devices"], capture_output=True, text=True, encoding="utf-8", errors="replace")
    print(f"   연결 기기:\n{dev_r.stdout.strip()}")
else:
    print("   시스템에 ADB 실행 파일이 아직 등록되어 있지 않습니다.")
