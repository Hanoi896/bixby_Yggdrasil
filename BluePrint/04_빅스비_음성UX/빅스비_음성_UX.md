# 🎙️ 빅스비 음성 인터페이스 및 지능형 딥링크 연동 청사진 (`빅스비_음성_UX.md`)

> **핵심 과제**: 삼성 빅스비 캡슐을 통해 사용자의 자연어 음성 명령을 수신하고, 핸즈프리 음성 TTS 즉시 회신과 네이티브 4대 탭 딥링크 핸드오프를 매끄럽게 연결하는 하이브리드 UX 구현.

---

## 📑 핵심 아키텍처

```text
┌────────────────────────────────────────────────────────────────────────┐
│                   사용자 발화 (Voice & Touch Input)                   │
│        "오늘 일정 브리핑해줘" / "기억 검색해줘" / "이그드라실 열어줘"  │
└──────────────────────────────────┬─────────────────────────────────────┘
                                   │
┌──────────────────────────────────▼─────────────────────────────────────┐
│                 Bixby Capsule (playground.yggdrasil)                   │
│  - 56개 Concepts 모델링 & 8개 Actions 비즈니스 로직                     │
│  - t-1.training.bxb 구어체 발화 126건 훈련                              │
└──────────────────────────────────┬─────────────────────────────────────┘
                                   │ (지능형 하이브리드 분기)
                 ┌─────────────────┴─────────────────┐
                 │                                   │
    [단순 브리핑 / 단답형 질의]             [복합 대화 / 상세 작업 지시]
                 │                                   │
                 ▼                                   ▼
┌──────────────────────────────────┐┌────────────────────────────────────┐
│      Bixby Views + 음성 TTS      ││   App-Launch 딥링크 핸드오프       │
│  - speechResponse: 버즈 이어폰   ││  - yggdrasil://chat (대화 0번)     │
│    즉각 음성 낭독 (화면 꺼짐 유지)││  - yggdrasil://status (상태 1번)   │
│  - compound-card One UI 카드 렌더 ││  - yggdrasil://archive (보관소 2번)│
│  - attribution-link 앱 이동 버튼 ││  - yggdrasil://settings (설정 3번) │
└──────────────────────────────────┘└────────────────────────────────────┘
                                                     │
                                    ┌────────────────▼───────────────────┐
                                    │    Android Jetpack Compose 앱      │
                                    │  - MainActivity: onNewIntent 수신  │
                                    │  - MainAppScreen: 4대 탭 자동 전환 │
                                    └────────────────────────────────────┘
```

---

## 🔑 주요 구현 명세 및 핵심 파일

1. **지능형 하이브리드 라우팅 (Hybrid Routing)**:
   - [AutonomousDialogueResult.view.bxb](file:///c:/Users/nhj23/bixby-workspace/bixby_Yggdrasil/playground.yggdrasil/resources/ko-KR/views/AutonomousDialogueResult.view.bxb): 단순 일정/날씨/상태는 빅스비 화면과 `speechResponse` 음성 TTS로 즉각 읽어주고, 대화가 깊어지면 `attribution-link`로 네이티브 앱에 자연스럽게 핸드오프.
2. **빅스비 8대 액션 핸드오프 일원화**:
   - [GetPantheonOverview.js](file:///c:/Users/nhj23/bixby-workspace/bixby_Yggdrasil/playground.yggdrasil/code/GetPantheonOverview.js): `yggdrasil://status`
   - [QueryMemory.js](file:///c:/Users/nhj23/bixby-workspace/bixby_Yggdrasil/playground.yggdrasil/code/QueryMemory.js): `yggdrasil://archive`
   - [InspectSecurity.js](file:///c:/Users/nhj23/bixby-workspace/bixby_Yggdrasil/playground.yggdrasil/code/InspectSecurity.js): `yggdrasil://settings`
   - [ProcessAutonomousDialogue.js](file:///c:/Users/nhj23/bixby-workspace/bixby_Yggdrasil/playground.yggdrasil/code/ProcessAutonomousDialogue.js): `yggdrasil://chat?query=...`
3. **네이티브 인텐트 라우터**:
   - [AndroidManifest.xml](file:///c:/Users/nhj23/bixby-workspace/bixby_Yggdrasil/core/android/app/src/main/AndroidManifest.xml) & [MainActivity.kt](file:///c:/Users/nhj23/bixby-workspace/bixby_Yggdrasil/core/android/app/src/main/java/com/yggdrasil/assistant/MainActivity.kt): 인텐트 수신 즉시 목표 탭(0~3) 자동 전환 및 NPU 추론 실행.
