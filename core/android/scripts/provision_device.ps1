# ======================================================================
# 📱 Yggdrasil 갤럭시 단말기 원클릭 물리 프로비저닝 (PowerShell)
# ======================================================================

$PackageName = "com.yggdrasil.assistant"
$ServiceName = "com.yggdrasil.assistant/.operator.YggdrasilAccessibilityService"

Write-Host "======================================================================" -ForegroundColor Cyan
Write-Host "🚀 Yggdrasil Standalone OS Assistant 단말기 프로비저닝 시작" -ForegroundColor Cyan
Write-Host "======================================================================" -ForegroundColor Cyan

# 1. 기기 연결 확인
Write-Host "`n[1/6] USB 연결 단말기 확인..." -ForegroundColor Yellow
$devices = adb devices
Write-Host $devices

# 2. 핵심 런타임 권한 일괄 부여
Write-Host "`n[2/6] 핵심 런타임 권한 부여..." -ForegroundColor Yellow
$permissions = @(
    "android.permission.RECORD_AUDIO",
    "android.permission.POST_NOTIFICATIONS",
    "android.permission.READ_PHONE_STATE",
    "android.permission.READ_CONTACTS",
    "android.permission.READ_CALENDAR",
    "android.permission.WRITE_CALENDAR"
)

foreach ($perm in $permissions) {
    adb shell pm grant $PackageName $perm 2>$null
}
adb shell appops set $PackageName SYSTEM_ALERT_WINDOW allow
Write-Host "   ✅ RECORD_AUDIO, 오버레이 권한 인가 완료" -ForegroundColor Green

# 3. 사용 통계 권한
Write-Host "`n[3/6] PACKAGE_USAGE_STATS 권한 인가..." -ForegroundColor Yellow
adb shell appops set $PackageName PACKAGE_USAGE_STATS allow
Write-Host "   ✅ PACKAGE_USAGE_STATS 인가 완료" -ForegroundColor Green

# 4. 삼성 One UI 절전 모드 예외
Write-Host "`n[4/6] 삼성 One UI 절전 모드 예외 등록 (배터리 최적화 화이트리스트)..." -ForegroundColor Yellow
adb shell dumpsys deviceidle whitelist +$PackageName
Write-Host "   ✅ 배터리 최적화 제외 영구 등록 완료" -ForegroundColor Green

# 5. 접근성 서비스 자동 활성화
Write-Host "`n[5/6] Gungnir 화면 자율 제어 접근성 서비스 활성화..." -ForegroundColor Yellow
adb shell settings put secure enabled_accessibility_services $ServiceName
adb shell settings put secure accessibility_enabled 1
Write-Host "   ✅ YggdrasilAccessibilityService 활성화 완료" -ForegroundColor Green

# 6. 모델 볼트 폴더 생성
Write-Host "`n[6/6] 앱 전용 모델 볼트 디렉터리 생성..." -ForegroundColor Yellow
adb shell mkdir -p "/sdcard/Android/data/$PackageName/files/models"
Write-Host "   ✅ /sdcard/Android/data/$PackageName/files/models 생성 완료" -ForegroundColor Green

Write-Host "`n🎉 단말기 프로비저닝이 성공적으로 완료되었습니다!" -ForegroundColor Cyan
