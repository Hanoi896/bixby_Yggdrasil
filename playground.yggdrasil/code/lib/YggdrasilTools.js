/**
 * @file YggdrasilTools.js
 * @description 온디바이스 EXAONE 3.0 NPU 모델이 자율 호출하는 6대 도구(Tools)의 JSON Schema 및 보안 정책 레지스트리
 */

export const YGGDRASIL_TOOLS = [
  {
    type: 'function',
    function: {
      name: 'compress_files',
      description: '지정된 경로의 파일 또는 디렉토리를 로컬 ZIP 압축 파일로 생성합니다.',
      securityPolicy: {
        requiredLevel: 3,
        realm: 'Alfheim',
        guardian: 'Sleipnir',
        requiresVoiceConfirmation: false
      },
      parameters: {
        type: 'object',
        properties: {
          sourcePaths: {
            type: 'array',
            items: { type: 'string' },
            description: '압축할 원본 파일 또는 폴더의 절대 경로 목록'
          },
          outputZipPath: {
            type: 'string',
            description: '저장할 ZIP 압축 파일의 절대 경로'
          },
          compressionLevel: {
            type: 'string',
            enum: ['fast', 'standard', 'maximum'],
            default: 'standard'
          }
        },
        required: ['sourcePaths', 'outputZipPath']
      }
    }
  },
  {
    type: 'function',
    function: {
      name: 'messenger_send',
      description: '다양한 메신저 및 공유 플랫폼(카카오톡, 텔레그램, 슬랙, 라인, 문자/SMS, 이메일 등)을 통해 특정 대상에게 텍스트 및 첨부 파일을 전송합니다.',
      securityPolicy: {
        requiredLevel: 4,
        realm: 'Jotunheim',
        guardian: 'Odin',
        requiresVoiceConfirmation: true
      },
      parameters: {
        type: 'object',
        properties: {
          platform: {
            type: 'string',
            enum: ['telegram', 'kakaotalk', 'slack', 'line', 'sms', 'email', 'system_share'],
            default: 'system_share',
            description: '전송할 메신저/공유 플랫폼 (예: kakaotalk, telegram, slack, sms 등)'
          },
          recipient: {
            type: 'string',
            description: '수신 대상 식별자 (채팅방 이름, 연락처 명칭, 전화번호, 이메일 등)'
          },
          message: {
            type: 'string',
            description: '전송할 텍스트 안내 메시지'
          },
          attachmentPath: {
            type: 'string',
            description: '외부로 전송할 첨부 파일의 절대 경로'
          }
        },
        required: ['attachmentPath']
      }
    }
  },
  {
    type: 'function',
    function: {
      name: 'screen_control',
      description: '안드로이드 Accessibility Service를 통해 스마트폰 화면을 자율 조작(터치, 스와이프, 키 입력)합니다.',
      securityPolicy: {
        requiredLevel: 4,
        realm: 'Nidavellir',
        guardian: 'Gungnir',
        requiresVoiceConfirmation: true
      },
      parameters: {
        type: 'object',
        properties: {
          action: {
            type: 'string',
            enum: ['click', 'swipe', 'input_text', 'press_key', 'screenshot']
          },
          targetText: {
            type: 'string',
            description: '화면에서 찾아서 클릭할 UI 텍스트'
          },
          coordinates: {
            type: 'object',
            properties: {
              x: { type: 'number' },
              y: { type: 'number' }
            }
          },
          inputText: {
            type: 'string'
          },
          direction: {
            type: 'string',
            enum: ['up', 'down', 'left', 'right']
          }
        },
        required: ['action']
      }
    }
  },
  {
    type: 'function',
    function: {
      name: 'launch_app',
      description: '스마트폰에 설치된 특정 앱을 패키지명 또는 앱 이름으로 포그라운드 실행합니다.',
      securityPolicy: {
        requiredLevel: 2,
        realm: 'Midgard',
        guardian: 'Odin',
        requiresVoiceConfirmation: false
      },
      parameters: {
        type: 'object',
        properties: {
          packageName: {
            type: 'string',
            description: '실행할 앱의 안드로이드 패키지명'
          },
          appName: {
            type: 'string',
            description: '앱 표시 이름'
          },
          extraUri: {
            type: 'string'
          }
        },
        required: ['packageName']
      }
    }
  },
  {
    type: 'function',
    function: {
      name: 'calendar_schedule',
      description: '스마트폰 내부 캘린더에 일정을 등록, 조회, 수정하는 도구입니다.',
      securityPolicy: {
        requiredLevel: 3,
        realm: 'Niflheim',
        guardian: 'Munin',
        requiresVoiceConfirmation: false
      },
      parameters: {
        type: 'object',
        properties: {
          action: {
            type: 'string',
            enum: ['create', 'query', 'update', 'delete']
          },
          title: {
            type: 'string'
          },
          startTime: {
            type: 'string'
          },
          endTime: {
            type: 'string'
          },
          location: {
            type: 'string'
          },
          description: {
            type: 'string'
          }
        },
        required: ['action', 'title', 'startTime']
      }
    }
  },
  {
    type: 'function',
    function: {
      name: 'smarthome_control',
      description: 'IoT 스마트홈 기기(조명, 에어컨, 스마트 플러그, 도어락, 밸브)의 상태를 제어합니다.',
      securityPolicy: {
        requiredLevel: 3,
        realm: 'Nidavellir',
        guardian: 'Gungnir',
        requiresVoiceConfirmation: false
      },
      parameters: {
        type: 'object',
        properties: {
          deviceType: {
            type: 'string',
            enum: ['light', 'airconditioner', 'plug', 'lock', 'valve']
          },
          deviceName: {
            type: 'string'
          },
          command: {
            type: 'string',
            enum: ['turn_on', 'turn_off', 'set_temperature', 'lock', 'unlock']
          },
          value: {
            type: 'string'
          }
        },
        required: ['deviceType', 'deviceName', 'command']
      }
    }
  }
];

/**
 * 도구 명칭으로 도구 스키마 및 보안 정책을 조회합니다.
 */
export function getToolSchema(toolName) {
  for (let i = 0; i < YGGDRASIL_TOOLS.length; i++) {
    if (YGGDRASIL_TOOLS[i].function.name === toolName) {
      return YGGDRASIL_TOOLS[i].function;
    }
  }
  return null;
}

/**
 * 도구 호출에 필요한 필수 인자(required parameters) 검증
 */
export function validateToolArguments(toolName, args) {
  const schema = getToolSchema(toolName);
  if (!schema) {
    return { valid: false, error: `미등록 도구입니다: ${toolName}` };
  }
  const required = schema.parameters.required || [];
  for (let i = 0; i < required.length; i++) {
    const key = required[i];
    if (args === null || args === undefined || args[key] === undefined || args[key] === null || args[key] === '') {
      return { valid: false, error: `필수 파라미터 누락: '${key}'` };
    }
  }
  return { valid: true, securityPolicy: schema.securityPolicy };
}
