import subprocess
import os
import sys
import glob

if hasattr(sys.stdout, 'reconfigure'):
    sys.stdout.reconfigure(encoding='utf-8')

# WinGet으로 설치된 adb.exe 탐색
pattern = os.path.expandvars(r"%LOCALAPPDATA%\Microsoft\WinGet\Packages\Google.PlatformTools_*\platform-tools\adb.exe")
matches = glob.glob(pattern)

adb_bin = None
if matches and os.path.exists(matches[0]):
    adb_bin = matches[0]
else:
    # 일반 경로 탐색
    for candidate in [
        os.path.expandvars(r"%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe"),
        r"C:\platform-tools\adb.exe",
        "adb"
    ]:
        try:
            r = subprocess.run([candidate, "version"], capture_output=True)
            if r.returncode == 0:
                adb_bin = candidate
                break
        except Exception:
            continue

if not adb_bin:
    print("❌ adb.exe를 찾을 수 없습니다.")
    sys.exit(1)

print(f"✅ ADB 실행 도구 발견: {adb_bin}")
cmd = [adb_bin] + sys.argv[1:]
res = subprocess.run(cmd, text=True, encoding="utf-8", errors="replace")
sys.exit(res.returncode)
