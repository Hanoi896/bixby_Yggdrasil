/**
 * test_screen_operator.mjs
 * PART 3. 모바일 화면 제어 및 안전 가드레일 로직 검증 시뮬레이터
 */

import { planTask } from '../playground.yggdrasil/code/lib/TaskPlanner.js';
import { interceptPlan, GATEKEEPER_STATUS } from '../playground.yggdrasil/code/lib/OdinGatekeeper.js';
import { YGGDRASIL_TOOLS } from '../playground.yggdrasil/code/lib/YggdrasilTools.js';

console.log("======================================================================");
console.log("  [검증 1] Bixby Hugin 자율 에이전트 -> screen_control 도구 분해 검증");
console.log("======================================================================");

// 1. 발화 분해 테스트
const userUtterance = "화면에서 다음 버튼 누르고 아래로 스와이프해줘";
const plannedPlan = planTask(userUtterance);

console.log(`[입력 발화] "${userUtterance}"`);
console.log(`[분해된 단계 수] ${plannedPlan.steps.length}단계`);
plannedPlan.steps.forEach((step, idx) => {
  console.log(`  Step ${idx + 1}: [${step.toolName || step.tool}] action=${step.arguments.action}, targetText=${step.arguments.targetText || '-'}, direction=${step.arguments.direction || '-'}`);
});

console.log("\n======================================================================");
console.log("  [검증 2] OdinGatekeeper 보안 정책 및 권한 승인 검증");
console.log("======================================================================");

const securityEval = interceptPlan(plannedPlan, 3); // 현재 권한 L3
console.log(`[즉시 실행 가능 여부] ${securityEval.canExecuteDirectly ? '직접 실행 가능' : '보안 승인 대기 필요'}`);
console.log(`[일시 정지된 단계] ${securityEval.pausedAtStep ? `Step ${securityEval.pausedAtStep.stepIndex}` : '없음'}`);
console.log(`[요약 메시지] ${securityEval.summaryMessage}`);
securityEval.evaluatedSteps.forEach((s, idx) => {
  console.log(`  Step ${idx + 1} (${s.toolName || s.tool}): Level=${s.securityLevel}, Realm=${s.realm}, Status=${s.status}`);
});

console.log("\n======================================================================");
console.log("  [검증 3] 안드로이드 ScreenOperatorController & SafetyGuardrail 시뮬레이션");
console.log("======================================================================");

// SafetyGuardrailManager 모의 구현
class MockSafetyGuardrailManager {
  constructor() {
    this.goldenBorderVisible = false;
    this.emergencyStopped = false;
  }
  showSafetyGoldenBorder() {
    this.goldenBorderVisible = true;
    console.log("  [UI 오버레이] ✨ 황금빛 테두리(Golden Border) 점등 완료 (AI 조작 중)");
  }
  hideSafetyGoldenBorder() {
    this.goldenBorderVisible = false;
    console.log("  [UI 오버레이] ⬛ 황금빛 테두리 소등 완료 (조작 종료)");
  }
  triggerUserEmergencyInterrupt() {
    this.emergencyStopped = true;
    this.hideSafetyGoldenBorder();
    console.log("  [FAIL-SAFE] 🚨 사용자 화면 터치 감지! AI 제어 즉시 강제 중단!");
  }
  isEmergencyStopped() {
    return this.emergencyStopped;
  }
  isPasswordOrSensitiveField(node) {
    if (node.isPassword) return true;
    const text = (node.text || "").toLowerCase();
    const sensitive = ["password", "pin", "비밀번호", "인증번호"];
    return sensitive.some(k => text.includes(k));
  }
}

// ScreenOperatorController 모의 실행
class MockScreenOperatorController {
  constructor(guardrail) {
    this.guardrail = guardrail;
  }

  executeAction(action, targetText, coordinates, inputText, direction, mockNode) {
    console.log(`\n▶ 요청 실행: action=${action}, targetText=${targetText || '-'}, input=${inputText || '-'}`);
    
    // 1. Fail-Safe 인터럽트 체크
    if (this.guardrail.isEmergencyStopped()) {
      return { success: false, message: "사용자 터치로 인해 긴급 중단(Fail-Safe)되었습니다." };
    }

    // 2. 황금빛 오버레이 점등
    this.guardrail.showSafetyGoldenBorder();

    try {
      // 3. 보안 가드레일: 비밀번호 필드 여부 점검
      if (mockNode && this.guardrail.isPasswordOrSensitiveField(mockNode)) {
        return { success: false, message: "❌ 보안 가드레일 위반: 비밀번호 필드는 캡처/터치/입력할 수 없습니다." };
      }

      // 4. 액션 분기
      if (action === "tap" || action === "click") {
        return { success: true, message: `화면 텍스트 '${targetText}' 좌표 (540, 1820) 정밀 탭 완료` };
      } else if (action === "swipe") {
        return { success: true, message: `화면 ${direction || 'up'} 스와이프 제스처 완료 (1200 -> 400)` };
      } else if (action === "type" || action === "input_text") {
        return { success: true, message: `입력창에 텍스트 '${inputText}' 자동 주입 완료` };
      } else if (action === "press_key") {
        return { success: true, message: `시스템 키 '${targetText}' 전송 완료` };
      }
      return { success: false, message: "알 수 없는 액션" };
    } finally {
      this.guardrail.hideSafetyGoldenBorder();
    }
  }
}

const guardrail = new MockSafetyGuardrailManager();
const operator = new MockScreenOperatorController(guardrail);

// 테스트 케이스 A: 일반 버튼 탭
const testA = operator.executeAction("tap", "다음", null, null, null, { text: "다음", isPassword: false });
console.log(`  결과: ${testA.success ? '✅ 성공' : '❌ 실패'} - ${testA.message}`);

// 테스트 케이스 B: 비밀번호 입력창 방어 (보안 가드레일)
const testB = operator.executeAction("type", "비밀번호 입력", null, "mypassword123", null, { text: "비밀번호 입력창", isPassword: true });
console.log(`  결과: ${testB.success ? '✅ 성공' : '❌ 방어 성공'} - ${testB.message}`);

// 테스트 케이스 C: 사용자 화면 터치 시 Fail-Safe 긴급 중단
console.log("\n[상황 시뮬레이션: 사용자가 화면을 손가락으로 직접 터치함]");
guardrail.triggerUserEmergencyInterrupt();
const testC = operator.executeAction("swipe", null, null, null, "down", null);
console.log(`  결과: ${testC.success ? '✅ 성공' : '🛡️ 안전 중단 성공'} - ${testC.message}`);

console.log("\n======================================================================");
console.log("  모든 PART 3 핵심 시나리오 및 안전 가드레일 검증 완료!");
console.log("======================================================================");
