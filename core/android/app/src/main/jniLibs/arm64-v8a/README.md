# Qualcomm Hexagon HTP & Vulkan JNI Runtime Libraries (arm64-v8a)

Qualcomm Snapdragon 8 Elite (SM8750) Hexagon NPU 및 Vulkan 하드웨어 가속 라이브러리 디렉터리입니다.

## 필수 실기기 배포 바이너리

- `libhexagon_controller.so`: Snapdragon NPU 하드웨어 텐서 가속 제어기
- `libQnnHtp.so`: Qualcomm Neural Network HTP 백엔드 런타임
- `libQnnHtpV75Stub.so`: Hexagon v75 아키텍처 스텁
- `libQnnHtpV79Stub.so`: Hexagon v79 아키텍처 스텁 (Snapdragon 8 Elite)

해당 `.so` 파일들이 이 디렉터리에 위치하면 Gradle이 APK 빌드 시 `useLegacyPackaging = true`를 통해 압축 없이 직접 패키징하여, 앱 기동 시 CPU 폴백 없이 NPU 1.2W 초저전력 가속으로 즉시 마운트됩니다.
바이너리 부재 시 `LocalLlmService`가 즉시 감지하여 상단 노티피케이션으로 CPU 폴백 상태를 사용자에게 투명하게 경고합니다.
