# 🦅 Hugin-Munin: The Norse AI Assistant

> **"Thought and Memory, Power and Speed."**
> 북유럽 신화의 위계와 특성을 완벽하게 구현한 차세대 안드로이드 AI 에이전트.

![Hugin & Munin Logo](app/src/main/res/drawable/ic_launcher_foreground.xml)

## 🏛️ Architecture: The Pantheon (6-Tier)

이 프로젝트는 단순한 MVC/MVVM을 넘어, 신화적 역할에 기반한 **6티어 아키텍처**를 채택했습니다.

### 👑 1. Odin (The All-Father) - **Kernel**
*   **Role**: 시스템의 절대자. 중앙 제어 및 오케스트레이션.
*   **Responsibility**: 앱 생명주기 관리, 최상위 보안 승인, 컴포넌트 조율.
*   **Package**: `com.huginmunin.app.odin`

### 🦅 2. Hugin (The Thought) - **Intelligence**
*   **Role**: 생각하는 두뇌.
*   **Responsibility**: AI 추론, 자연어 이해(NLU), 사용자 의도 파악, 실행 계획 수립.
*   **Package**: `com.huginmunin.app.hugin`

### 🦅 3. Munin (The Memory) - **Repository**
*   **Role**: 기억하는 서기관.
*   **Responsibility**: 모든 로그 기록, 알림/센서 데이터 수집, 문맥(Context) 제공.
*   **Package**: `com.huginmunin.app.munin`

### 💍 4. Draupnir (The Multiplier) - **Resource Manager**
*   **Role**: 자원을 불리는 황금 팔찌.
*   **Responsibility**: 스레드/메모리 관리, 작업 스케줄링, 배터리 최적화.
*   **Package**: `com.huginmunin.app.draupnir`

### 🔱 5. Gungnir (The Spear) - **Critical Executor**
*   **Role**: 필중의 창. 실패해선 안 되는 중요 실행.
*   **Responsibility**: **금융/결제**, 보안 설정, IoT 제어. (Atomic & Transactional)
*   **Package**: `com.huginmunin.app.gungnir`

### 🐎 6. Sleipnir (The 8-Legged Horse) - **Fast Executor**
*   **Role**: 가장 빠른 말. 고속/병렬 처리.
*   **Responsibility**: **네트워크 통신**, 파일 다운로드, 대량 데이터 처리. (Async & Parallel)
*   **Package**: `com.huginmunin.app.sleipnir`

---

## 🌳 Yggdrasil: The Learning System (Growth Stages)

Hugin-Munin은 **Yggdrasil(세계수)**의 성장 단계에 따라 지능이 진화합니다.

### 🌱 Stage 1: Roots (Niflheim) - **Data Collection**
*   **담당**: Munin
*   **기능**: 사용자의 단순 패턴(기상 시간, 앱 사용량, 이동 경로)을 수집하고 저장합니다.
*   **상태**: *Passive Observer*

### 🪵 Stage 2: Trunk (Midgard) - **Context Awareness**
*   **담당**: Hugin + Munin
*   **기능**: 수집된 데이터에서 '맥락'을 이해합니다. ("출근 중이시군요", "배고플 시간이네요")
*   **상태**: *Active Assistant*

### 🌿 Stage 3: Branches (Asgard) - **Autonomous Agency**
*   **담당**: Odin + Gungnir
*   **기능**: 사용자의 승인 하에 스스로 판단하고 행동합니다. (자동 송금, 스마트홈 제어)
*   **상태**: *Autonomous Agent*

---

## 🌍 The 9 Realms: Security & Domain Model

시스템의 각 영역은 신화 속 **9개의 세계**로 구분되어 철저한 보안 격리(Isolation)를 유지합니다.

| Realm | Tier | Description | Access Level |
|:---|:---:|:---|:---|
| **Asgard** | **Kernel** | **Odin**이 거주하는 시스템 코어. 생명주기 및 최상위 권한. | `System Only` |
| **Vanaheim** | **AI** | **Hugin**의 추론 엔진. 자연어 처리 및 학습. | `Internal` |
| **Alfheim** | **Network** | **Sleipnir**가 활동하는 고속 통신망. | `Internet` |
| **Midgard** | **UI** | 사용자와 만나는 인터페이스 (Compose UI). | `User` |
| **Jotunheim** | **External** | 외부 API (Google, GitHub) 및 서드파티 연동. | `API Key` |
| **Nidavellir** | **Critical** | **Gungnir**가 제작/실행하는 금융/IoT 영역. | `Biometric` |
| **Niflheim** | **Storage** | **Munin**의 차가운 데이터 저장소 (Cold Storage). | `Encrypted` |
| **Muspelheim** | **Resource** | **Draupnir**가 관리하는 뜨거운 자원(CPU/RAM). | `System Only` |
| **Hel** | **Logs** | 죽은 프로세스와 에러 로그가 모이는 곳. | `Debug` |.

---

## 🚀 Key Features

*   **Wake Word Detection**: "휴긴", "뮤닌" 호출 감지 (Always-on)
*   **Smart Notification Analysis**: Munin이 알림을 분석하여 중요 정보만 Hugin에게 전달
*   **Secure Execution**: Odin의 승인 하에 Gungnir가 안전하게 결제 수행
*   **High Performance**: Draupnir의 자원 관리와 Sleipnir의 병렬 처리로 끊김 없는 경험

---

## 📱 System Requirements

*   **Minimum**: Android 10 (API 29) - *For NNAPI acceleration*
*   **Recommended**: Android 12+ (API 31)

---

## 🛠️ Tech Stack

*   **Language**: Kotlin
*   **UI**: Jetpack Compose (Material 3)
*   **DI**: Dagger Hilt
*   **Async**: Coroutines & Flow
*   **Local DB**: Room (SQLCipher Encrypted)
*   **AI**: TensorFlow Lite, Gemini API

---

## 📜 License

Copyright 2024 Hugin-Munin Project. All rights reserved.
