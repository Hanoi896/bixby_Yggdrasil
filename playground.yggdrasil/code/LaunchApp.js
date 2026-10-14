/**
 * @file LaunchApp.js
 * @description 이그드라실 안드로이드 네이티브 앱 실행을 위한 App-Launch 페이로드 생성
 */
export default function launchApp() {
  const launchUri = "yggdrasil://launch";
  return {
    launchUri: launchUri,
    displayText: "🦅 100% 온디바이스 개인 AI 비서 이그드라실 앱을 실행합니다.",
    speechResponse: "이그드라실 앱을 엽니다."
  };
}
