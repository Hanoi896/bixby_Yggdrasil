/**
 * @file TaskPlanner.js
 * @description Hugin 두뇌 기반의 범용 복합 작업 분해기(Task Decomposition & Planner Engine)
 *
 * 주요 기능:
 *  1. 임의의 자연어 복합 발화를 독립된 원자적 실행 단위(SubTask Steps)로 분할
 *  2. 6대 도구(compress_files, messenger_send, screen_control 등) 및 기존 Bixby 원자적 Action 매핑
 *  3. 선행 작업의 결과값($output)을 후행 작업의 입력으로 연결하는 DAG 파이프라인 바인딩
 *  4. 단계별 오딘 보안 인가 등급(L0~L10) 및 음성 승인 플래그 태깅
 */

import { YGGDRASIL_TOOLS, getToolSchema, validateToolArguments } from './YggdrasilTools.js';
import { buildActionUri } from './YggdrasilConfig.js';

// 복합 작업을 나누는 한국어 구문 접속사 패턴
const CONJUNCTION_REGEX = /(?:그리고|한\s*다음(?:에)?|하고\s*나서|한\s*뒤(?:에)?|해서|하고|후에|(?<=[가-힣])고\s+)/g;

/**
 * 사용자 발화를 분석하여 실행 계획(Execution Plan)을 생성합니다.
 * (HTTP 소켓 서버 없이 100% Bixby 클라이언트 측 고속 한국어 구문 DAG 분해 및 Native Intent 직결)
 * @param {string} utterance 사용자의 원문 발화
 * @returns {object} 분해된 실행 계획 객체
 */
export function planTask(utterance) {
  if (!utterance || typeof utterance !== 'string') {
    return buildEmptyPlan('');
  }

  const cleanText = utterance.trim();

  // 1. 한국어 구문 접속사 기반 원자적 절(Clause) 분할
  const clauses = splitIntoClauses(cleanText);
  const isCompound = clauses.length > 1;

  const steps = [];
  for (let i = 0; i < clauses.length; i++) {
    const clause = clauses[i];
    const step = mapClauseToStep(clause, i + 1);
    if (step) {
      steps.push(step);
    }
  }

  // 매핑된 단계가 없으면 단일 자율 대화/브리핑으로 폴백
  if (steps.length === 0) {
    return {
      originalUtterance: cleanText,
      isCompound: false,
      planDescription: `'${cleanText}' 자연어 질의 분석 및 브리핑`,
      steps: [],
      appLaunchUri: buildActionUri('chat', { query: cleanText })
    };
  }

  // 2. 선행-후행 의존성 데이터 바인딩 ($output linking)
  linkStepDependencies(steps);

  const planDesc = generatePlanDescription(steps);
  return {
    originalUtterance: cleanText,
    isCompound: steps.length > 1,
    planDescription: planDesc,
    steps: steps,
    appLaunchUri: buildActionUri('execute_plan', {
      utterance: cleanText,
      isCompound: steps.length > 1,
      steps: steps
    })
  };
}

/**
 * 한국어 접속사를 기준으로 문장을 하위 절(Clause)들로 분할합니다.
 */
function splitIntoClauses(text) {
  const parts = text.split(CONJUNCTION_REGEX);
  const result = [];
  for (let i = 0; i < parts.length; i++) {
    const p = parts[i].trim();
    if (p.length > 1) {
      result.push(p);
    }
  }
  return result.length > 0 ? result : [text];
}

/**
 * 개별 절을 적합한 도구(Tool) 및 파라미터 Step으로 변환합니다.
 */
function mapClauseToStep(clause, stepIndex) {
  const lower = clause.toLowerCase();

  // 1) 파일 압축 (compress_files)
  if (lower.indexOf('압축') >= 0 || lower.indexOf('zip') >= 0) {
    const targetFolder = extractTargetFolder(clause) || '/storage/emulated/0/Download';
    return {
      stepIndex: stepIndex,
      toolName: 'compress_files',
      actionName: 'FileCompress',
      description: `'${targetFolder}' 경로 파일 ZIP 압축`,
      securityLevel: 3,
      requiresVoiceConfirmation: false,
      arguments: {
        sourcePaths: [targetFolder],
        outputZipPath: `${targetFolder}/archive_${Date.now().toString().slice(-4)}.zip`,
        compressionLevel: 'standard'
      },
      outputKey: 'outputZipPath'
    };
  }

  // 2) 외부 메신저 전송 (messenger_send)
  if (lower.indexOf('보내') >= 0 || lower.indexOf('올려') >= 0 || lower.indexOf('전송') >= 0 || lower.indexOf('공유') >= 0) {
    const platform = detectPlatform(lower);
    const recipient = detectRecipient(clause) || '기본 업무방';
    return {
      stepIndex: stepIndex,
      toolName: 'messenger_send',
      actionName: 'MessengerSend',
      description: `${platform.toUpperCase()} [${recipient}]으로 파일/메시지 전송`,
      securityLevel: 4,
      requiresVoiceConfirmation: true,
      arguments: {
        platform: platform,
        recipient: recipient,
        message: clause,
        attachmentPath: '$step_previous.outputZipPath' // 이전 스텝 결과 자동 바인딩 플레이스홀더
      }
    };
  }

  // 3) 화면 조작 (screen_control)
  if (lower.indexOf('화면') >= 0 || lower.indexOf('터치') >= 0 || lower.indexOf('눌러') >= 0 || lower.indexOf('클릭') >= 0 || lower.indexOf('스크롤') >= 0) {
    return {
      stepIndex: stepIndex,
      toolName: 'screen_control',
      actionName: 'ScreenControl',
      description: `'${clause}' 화면 자율 조작`,
      securityLevel: 4,
      requiresVoiceConfirmation: true,
      arguments: {
        action: lower.indexOf('스크롤') >= 0 ? 'swipe' : 'click',
        targetText: extractTargetLabel(clause) || clause.replace(/(?:화면|터치|눌러|클릭|스크롤|해줘)/g, '').trim() || '확인'
      }
    };
  }

  // 4) 캘린더 일정 (calendar_schedule)
  if (lower.indexOf('일정') >= 0 || lower.indexOf('캘린더') >= 0 || lower.indexOf('스케줄') >= 0 || lower.indexOf('약속') >= 0) {
    return {
      stepIndex: stepIndex,
      toolName: 'calendar_schedule',
      actionName: 'CalendarSchedule',
      description: `'${clause}' 캘린더 일정 관리`,
      securityLevel: 3,
      requiresVoiceConfirmation: false,
      arguments: {
        action: 'create',
        title: clause.replace(/(?:등록|저장|추가|해줘|일정|캘린더|약속|스케줄)/g, '').trim() || '새로운 일정',
        startTime: extractScheduleTime(clause)
      }
    };
  }

  // 5) 스마트홈 IoT 제어 (smarthome_control)
  if (lower.indexOf('조명') >= 0 || lower.indexOf('불') >= 0 || lower.indexOf('에어컨') >= 0 || lower.indexOf('청소기') >= 0 || lower.indexOf('플러그') >= 0) {
    const isOff = lower.indexOf('꺼') >= 0 || lower.indexOf('차단') >= 0;
    const deviceType = lower.indexOf('조명') >= 0 || lower.indexOf('불') >= 0 ? 'light' : 'airconditioner';
    return {
      stepIndex: stepIndex,
      toolName: 'smarthome_control',
      actionName: 'SmartHomeControl',
      description: `${deviceType === 'light' ? '조명' : '에어컨'} ${isOff ? '끄기' : '켜기'}`,
      securityLevel: 3,
      requiresVoiceConfirmation: false,
      arguments: {
        deviceType: deviceType,
        deviceName: clause,
        command: isOff ? 'turn_off' : 'turn_on'
      }
    };
  }

  // 6) 기억 저장 (RecordMemory)
  if (lower.indexOf('기억') >= 0 || lower.indexOf('메모') >= 0 || lower.indexOf('적어') >= 0) {
    return {
      stepIndex: stepIndex,
      toolName: 'record_memory',
      actionName: 'RecordMemory',
      description: `'${clause}' SQLCipher 암호화 기억 보관`,
      securityLevel: 3,
      requiresVoiceConfirmation: false,
      arguments: {
        content: clause
      }
    };
  }

  return null;
}

/**
 * 선행 작업의 출력값을 후행 작업의 입력 파라미터로 자동 연결(Data Dependency Linking)합니다.
 */
function linkStepDependencies(steps) {
  for (let i = 1; i < steps.length; i++) {
    const prev = steps[i - 1];
    const curr = steps[i];

    if (curr.toolName === 'messenger_send' && prev.toolName === 'compress_files') {
      const producedZip = prev.arguments.outputZipPath;
      curr.arguments.attachmentPath = producedZip;
      curr.boundDependency = `Step ${prev.stepIndex} (${prev.toolName})의 출력 파일 -> Step ${curr.stepIndex} 첨부파일로 바인딩`;
    }
  }
}

/**
 * 플랫폼 키워드 감지
 */
function detectPlatform(text) {
  if (text.indexOf('카톡') >= 0 || text.indexOf('카카오') >= 0) return 'kakaotalk';
  if (text.indexOf('텔레그램') >= 0) return 'telegram';
  if (text.indexOf('슬랙') >= 0) return 'slack';
  if (text.indexOf('라인') >= 0) return 'line';
  if (text.indexOf('문자') >= 0 || text.indexOf('sms') >= 0) return 'sms';
  if (text.indexOf('메일') >= 0 || text.indexOf('email') >= 0) return 'email';
  return 'system_share';
}

function detectRecipient(text) {
  const match = text.match(/([가-힣a-zA-Z0-9_]+)(?:에게|한테|방에|채널에)/);
  if (match) return match[1];
  const words = text.split(/\s+/);
  for (let i = 0; i < words.length; i++) {
    const w = words[i];
    if (w.indexOf('팀') >= 0 || w.indexOf('님') >= 0 || w.indexOf('방') >= 0) {
      return w.replace(/(?:에게|한테|으로|로|에|보내줘|올려줘)/g, '');
    }
  }
  return null;
}

function extractTargetFolder(text) {
  if (text.indexOf('다운로드') >= 0) return '/storage/emulated/0/Download';
  if (text.indexOf('문서') >= 0) return '/storage/emulated/0/Documents';
  if (text.indexOf('사진') >= 0 || text.indexOf('갤러리') >= 0) return '/storage/emulated/0/DCIM';
  return null;
}

function extractTargetLabel(text) {
  const match = text.match(/['"]([^'"]+)['"]/);
  if (match) return match[1];
  return null;
}

function generatePlanDescription(steps) {
  if (steps.length === 1) {
    return steps[0].description;
  }
  const titles = [];
  for (let i = 0; i < steps.length; i++) {
    titles.push(`[${i + 1}단계: ${steps[i].description}]`);
  }
  return `복합 파이프라인 ${steps.length}단계 실행: ${titles.join(' ➡️ ')}`;
}

function buildEmptyPlan(utterance) {
  return {
    originalUtterance: utterance,
    isCompound: false,
    planDescription: '단일 작업',
    steps: []
  };
}

function extractScheduleTime(text) {
  const now = new Date();
  if (text.indexOf('내일') >= 0) {
    now.setDate(now.getDate() + 1);
  } else if (text.indexOf('모레') >= 0) {
    now.setDate(now.getDate() + 2);
  }
  const hourMatch = text.match(/(\d{1,2})\s*시/);
  if (hourMatch) {
    let hour = parseInt(hourMatch[1], 10);
    if (text.indexOf('오후') >= 0 && hour < 12) hour += 12;
    if (text.indexOf('오전') >= 0 && hour === 12) hour = 0;
    now.setHours(hour, 0, 0, 0);
  } else {
    now.setHours(now.getHours() + 1, 0, 0, 0);
  }
  return now.toISOString();
}
