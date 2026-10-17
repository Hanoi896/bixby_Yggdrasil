@echo off
chcp 65001 > nul
echo ======================================================================
echo 📱 Yggdrasil 갤럭시 단말기 원클릭 물리 프로비저닝 (Provisioning) 도구
echo ======================================================================
echo.

set PACKAGE_NAME=com.yggdrasil.assistant
set SERVICE_NAME=com.yggdrasil.assistant/.operator.YggdrasilAccessibilityService

echo [1/6] USB 연결 단말기 확인...
adb devices
if %ERRORLEVEL% NEQ 0 (
    echo ❌ ADB 실행 실패. ADB 환경변수 또는 설치 경로를 확인하세요.
    pause
    exit /b 1
)

echo.
echo [2/6] 핵심 런타임 권한 자동 부여 (RECORD_AUDIO, SYSTEM_ALERT_WINDOW)...
adb shell pm grant %PACKAGE_NAME% android.permission.RECORD_AUDIO
adb shell pm grant %PACKAGE_NAME% android.permission.POST_NOTIFICATIONS
adb shell pm grant %PACKAGE_NAME% android.permission.READ_PHONE_STATE
adb shell pm grant %PACKAGE_NAME% android.permission.READ_CONTACTS
adb shell pm grant %PACKAGE_NAME% android.permission.READ_CALENDAR
adb shell pm grant %PACKAGE_NAME% android.permission.WRITE_CALENDAR
adb shell appops set %PACKAGE_NAME% SYSTEM_ALERT_WINDOW allow
echo    ✅ 기본 권한 및 오버레이 권한 인가 완료

echo.
echo [3/6] 사용 통계(PACKAGE_USAGE_STATS) 및 앱 실행 감지 권한 부여...
adb shell appops set %PACKAGE_NAME% PACKAGE_USAGE_STATS allow
echo    ✅ PACKAGE_USAGE_STATS 인가 완료

echo.
echo [4/6] 삼성 One UI 절전 모드 예외 등록 (배터리 최적화 화이트리스트 영구 등록)...
adb shell dumpsys deviceidle whitelist +%PACKAGE_NAME%
echo    ✅ Doze 모드 및 절전 모드 예외 등록 완료

echo.
echo [5/6] 안드로이드 접근성 서비스(Gungnir UI 자율 조작) 자동 활성화...
adb shell settings put secure enabled_accessibility_services %SERVICE_NAME%
adb shell settings put secure accessibility_enabled 1
echo    ✅ YggdrasilAccessibilityService 활성화 완료

echo.
echo [6/6] 앱 격리 모델 전용 디렉터리 준비...
adb shell mkdir -p /sdcard/Android/data/%PACKAGE_NAME%/files/models
echo    ✅ /sdcard/Android/data/%PACKAGE_NAME%/files/models 생성 완료

echo.
echo ======================================================================
echo 🎉 단말기 프로비저닝 완료! 이제 100% 독립 자율 구동이 가능합니다.
echo ======================================================================
pause
