/**
 * @file ProcessAutonomousDialogue.js
 * @description 빅스비 캡슐과 안드로이드 네이티브 앱 간 App-Launch 딥링크 연동 브릿지.
 * 캡슐은 사용자의 자연어 발화를 파싱하여 앱 실행 딥링크(yggdrasil://chat?query=...)를 생성하고,
 * 실제 대화 추론 및 도구 실행은 안드로이드 네이티브 앱(온디바이스 EXAONE 3.0 NPU)에서 전담 처리합니다.
 */
import { planTask } from './lib/TaskPlanner.js';
import { interceptPlan, GATEKEEPER_STATUS } from './lib/OdinGatekeeper.js';

export default function processAutonomousDialogue(input) {
  const utterance = (input.utterance || '').trim();
  const startTime = Date.now();

  // 세션 ID 확인 또는 신규 발급
  let sessionId = input.sessionId;
  if (!sessionId || typeof sessionId !== 'string') {
    sessionId = `sess-${Date.now().toString(36)}-${Math.random().toString(36).substring(2, 6)}`;
  }

  // 앱 실행 딥링크 URI 생성 (안드로이드 MainActivity.kt의 handleDeepLink에서 수신)
  const encodedQuery = encodeURIComponent(utterance);
  const appLaunchUri = `yggdrasil://chat?query=${encodedQuery}`;

  // 1. 복합 명령 여부 사전 분석 (Hugin TaskPlanner)
  const plan = planTask(utterance);
  if (plan && plan.isCompound && plan.steps.length > 1) {
    const gateVerdict = interceptPlan(plan, 3);

    const stepDescriptions = [];
    for (let s = 0; s < gateVerdict.evaluatedSteps.length; s++) {
      const step = gateVerdict.evaluatedSteps[s];
      let statusTag = '[✅ 자동 승인]';
      if (step.status === GATEKEEPER_STATUS.REQUIRES_APPROVAL) {
        statusTag = '[🛡️ 오딘 승인 필요]';
      } else if (step.status === GATEKEEPER_STATUS.BLOCKED) {
        statusTag = '[⛔ 보안 차단]';
      }
      stepDescriptions.push(`• ${statusTag} ${step.stepIndex}단계: ${step.description}`);
    }

    const plannerReply = `Hugin 복합 작업 분해기 분석 결과 (${plan.steps.length}단계):\n` +
      stepDescriptions.join('\n') +
      `\n\n🦅 안전한 온디바이스 NPU 실행을 위해 이그드라실 앱으로 연결합니다.`;

    const speechText = gateVerdict.pausedAtStep
      ? `보안 승인이 필요한 복합 작업이 포함되어 있어, 이그드라실 앱에서 상세 내용을 확인하고 실행합니다.`
      : `요청하신 ${plan.steps.length}단계 작업을 온디바이스에서 실행하기 위해 이그드라실 앱을 엽니다.`;

    return {
      utterance: utterance,
      speechResponse: speechText,
      displayText: plannerReply,
      thoughtProcess: `Hugin 플래너 & Odin 게이트키퍼: ${plan.planDescription}`,
      tierName: "Hugin",
      actionName: "TaskDecompositionPlanner",
      isLocalInference: true,
      latencyMs: Date.now() - startTime,
      sessionId: sessionId,
      appLaunchUri: appLaunchUri
    };
  }

  // 2. 일반 자유 발화: 앱 런치를 통해 네이티브 온디바이스 NPU 대화로 즉시 핸드오프
  const speechText = utterance
    ? `'${utterance}' 요청을 처리하기 위해 이그드라실 앱을 실행합니다.`
    : `이그드라실 온디바이스 AI 비서를 실행합니다.`;

  const displayText = utterance
    ? `💬 "${utterance}"\n\n🦅 100% 온디바이스 EXAONE 3.0 NPU 엔진을 연결하여 앱에서 대화를 이어갑니다.`
    : `🦅 100% 온디바이스 EXAONE 3.0 NPU 엔진을 탑재한 이그드라실 앱을 실행합니다.`;

  return {
    utterance: utterance,
    speechResponse: speechText,
    displayText: displayText,
    thoughtProcess: `App-Launch 패턴: 온디바이스 NPU 대화 룸으로 핸드오프 (query=${utterance})`,
    tierName: "Hugin",
    actionName: "AppLaunchDialogueHandover",
    isLocalInference: true,
    latencyMs: Date.now() - startTime,
    sessionId: sessionId,
    appLaunchUri: appLaunchUri
  };
}
