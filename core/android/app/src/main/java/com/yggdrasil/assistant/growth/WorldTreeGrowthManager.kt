package com.yggdrasil.assistant.growth

import android.content.Context
import android.content.SharedPreferences
import android.util.Log

/**
 * [WorldTreeGrowthManager]
 * 세계수 위그드라실(Yggdrasil) 온디바이스 실시간 성장 엔진
 *
 * 사용자와의 상호작용(대화, 장기 기억 축적, 화면 자동 조작 완수, 화자 인증 등)을 실시간 감지하여
 * 지혜 포인트(Wisdom Points, WP)를 영구 누적하고, 성장 단계와 잠금 해제 능력을 동적으로 진화시킵니다.
 */
class WorldTreeGrowthManager private constructor(context: Context) {

    companion object {
        private const val TAG = "WorldTreeGrowth"
        private const val PREFS_NAME = "yggdrasil_tree_growth"

        private const val KEY_WISDOM_POINTS = "wisdom_points"
        private const val KEY_DIALOGUE_COUNT = "dialogue_count"
        private const val KEY_MEMORY_COUNT = "memory_count"
        private const val KEY_ACTION_COUNT = "action_count"
        private const val KEY_CALIBRATION_COUNT = "calibration_count"
        private const val KEY_SELF_CORRECTION_COUNT = "self_correction_count"

        @Volatile
        private var instance: WorldTreeGrowthManager? = null

        fun getInstance(context: Context): WorldTreeGrowthManager {
            return instance ?: synchronized(this) {
                instance ?: WorldTreeGrowthManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    data class GrowthStatus(
        val level: Int,
        val stageCode: String,
        val stageName: String,
        val guardian: String,
        val growthPercent: Float,
        val wisdomPoints: Int,
        val nextStageWp: Int,
        val unlockedAbilities: List<String>,
        val dialogueCount: Int,
        val memoryCount: Int,
        val actionCount: Int
    )

    @Synchronized
    fun getGrowthStatus(): GrowthStatus {
        val wp = prefs.getInt(KEY_WISDOM_POINTS, 120) // 초기 기본 활성화 120 WP
        val dialogues = prefs.getInt(KEY_DIALOGUE_COUNT, 0)
        val memories = prefs.getInt(KEY_MEMORY_COUNT, 0)
        val actions = prefs.getInt(KEY_ACTION_COUNT, 0)

        val (level, stageCode, stageName, guardian, nextWp, prevWp, abilities) = when {
            wp < 500 -> StageInfo(
                level = 1,
                stageCode = "Roots",
                stageName = "뿌리 (데이터 수집)",
                guardian = "Munin",
                nextWp = 500,
                prevWp = 0,
                abilities = listOf(
                    "Munin SQLCipher AES-256 로컬 암호화 기억",
                    "Gungnir L1/L2 시스템 제어 (볼륨/플래시/화면)",
                    "자유 음성 발화 1-Shot 텍스트 변환"
                )
            )
            wp < 2000 -> StageInfo(
                level = 2,
                stageCode = "Trunk",
                stageName = "줄기 (맥락 인지)",
                guardian = "Hugin",
                nextWp = 2000,
                prevWp = 500,
                abilities = listOf(
                    "대용량 대화 히스토리 기반 맥락 회상(RAG)",
                    "카카오톡/시스템 메시지 인텐트 자동 추출",
                    "Acoustic Barge-in 실시간 말 끊기 지원",
                    "뿌리 단계 전 기능 영구 유지"
                )
            )
            wp < 5000 -> StageInfo(
                level = 3,
                stageCode = "Branches",
                stageName = "가지 (자율 행동)",
                guardian = "Odin",
                nextWp = 5000,
                prevWp = 2000,
                abilities = listOf(
                    "Gungnir L3 앱 내부 심층 제어(스크롤/자동입력)",
                    "선제적 Doze 모드 지능형 알림 브리핑",
                    "고유 화자 음성 지문 생체 보안 인가",
                    "줄기 단계 전 기능 완전 가동"
                )
            )
            wp < 10000 -> StageInfo(
                level = 4,
                stageCode = "Canopy",
                stageName = "수관 (완숙 지능)",
                guardian = "Pantheon Core",
                nextWp = 10000,
                prevWp = 5000,
                abilities = listOf(
                    "복합 다중 멀티스텝 자동화 체인 가동",
                    "온디바이스 검색 그라운딩 심층 결합",
                    "자가 교정(Self-Correction) 선호도 자동 강화"
                )
            )
            else -> StageInfo(
                level = 5,
                stageCode = "Ancient",
                stageName = "고대 세계수 (신화적 자율체)",
                guardian = "Yggdrasil Sovereign",
                nextWp = wp,
                prevWp = 10000,
                abilities = listOf(
                    "완전 무인 온디바이스 자율 비서",
                    "모든 판테온 6대 신격 풀 파워 인가",
                    "초저전력 영구 백그라운드 무결성 수호"
                )
            )
        }

        val range = (nextWp - prevWp).coerceAtLeast(1)
        val progress = if (level >= 5) 100.0f else {
            (((wp - prevWp).toFloat() / range.toFloat()) * 100f).coerceIn(0f, 100f)
        }

        return GrowthStatus(
            level = level,
            stageCode = stageCode,
            stageName = stageName,
            guardian = guardian,
            growthPercent = Math.round(progress * 10f) / 10f,
            wisdomPoints = wp,
            nextStageWp = nextWp,
            unlockedAbilities = abilities,
            dialogueCount = dialogues,
            memoryCount = memories,
            actionCount = actions
        )
    }

    private data class StageInfo(
        val level: Int,
        val stageCode: String,
        val stageName: String,
        val guardian: String,
        val nextWp: Int,
        val prevWp: Int,
        val abilities: List<String>
    )

    /**
     * 대화 발생 시 지혜 포인트 누적 (+5 WP)
     */
    @Synchronized
    fun recordDialogue(points: Int = 5) {
        val currentWp = prefs.getInt(KEY_WISDOM_POINTS, 120)
        val currentCount = prefs.getInt(KEY_DIALOGUE_COUNT, 0)
        prefs.edit()
            .putInt(KEY_WISDOM_POINTS, currentWp + points)
            .putInt(KEY_DIALOGUE_COUNT, currentCount + 1)
            .apply()
        Log.d(TAG, "🌱 대화 지혜 포인트 획득: +$points WP (총 ${currentWp + points} WP)")
    }

    /**
     * Munin DB 기억 저장 시 지혜 포인트 누적 (+10 WP)
     */
    @Synchronized
    fun recordMemory(points: Int = 10) {
        val currentWp = prefs.getInt(KEY_WISDOM_POINTS, 120)
        val currentCount = prefs.getInt(KEY_MEMORY_COUNT, 0)
        prefs.edit()
            .putInt(KEY_WISDOM_POINTS, currentWp + points)
            .putInt(KEY_MEMORY_COUNT, currentCount + 1)
            .apply()
        Log.d(TAG, "🪶 기억 아카이빙 지혜 포인트 획득: +$points WP (총 ${currentWp + points} WP)")
    }

    /**
     * 자율 조작 액션 완수 시 지혜 포인트 누적 (+15 WP)
     */
    @Synchronized
    fun recordAction(points: Int = 15) {
        val currentWp = prefs.getInt(KEY_WISDOM_POINTS, 120)
        val currentCount = prefs.getInt(KEY_ACTION_COUNT, 0)
        prefs.edit()
            .putInt(KEY_WISDOM_POINTS, currentWp + points)
            .putInt(KEY_ACTION_COUNT, currentCount + 1)
            .apply()
        Log.d(TAG, "⚡ 자율 액션 완수 지혜 포인트 획득: +$points WP (총 ${currentWp + points} WP)")
    }

    /**
     * 화자 인증 캘리브레이션 등록 시 지혜 포인트 누적 (+50 WP)
     */
    @Synchronized
    fun recordVoiceprintCalibration(points: Int = 50) {
        val currentWp = prefs.getInt(KEY_WISDOM_POINTS, 120)
        val currentCount = prefs.getInt(KEY_CALIBRATION_COUNT, 0)
        prefs.edit()
            .putInt(KEY_WISDOM_POINTS, currentWp + points)
            .putInt(KEY_CALIBRATION_COUNT, currentCount + 1)
            .apply()
        Log.d(TAG, "🎙️ 음성 지문 등록 지혜 포인트 획득: +$points WP (총 ${currentWp + points} WP)")
    }

    /**
     * 실시간 LLM 프롬프트 주입 및 팩트 응답용 현황 문자열 생성
     */
    fun getGrowthFactString(): String {
        val s = getGrowthStatus()
        val nextDesc = if (s.level >= 5) "최고 성장 단계 도달" else "${s.nextStageWp - s.wisdomPoints} WP 추가 필요 (목표: ${s.nextStageWp} WP)"
        val abilityStr = s.unlockedAbilities.joinToString("\n  • ")
        return """
            [실시간 세계수 성장 팩트]
            - 현재 단계: ${s.stageName} (Level ${s.level})
            - 수호 신격: ${s.guardian}
            - 축적 지혜 포인트: ${s.wisdomPoints} WP (성장 진행률: ${s.growthPercent}%)
            - 다음 승급 요건: $nextDesc
            - 누적 활동 통계: 대화 ${s.dialogueCount}회, 기억 보관 ${s.memoryCount}건, 액션 완수 ${s.actionCount}건
            - 활성 능력:
              • $abilityStr
        """.trimIndent()
    }
}
