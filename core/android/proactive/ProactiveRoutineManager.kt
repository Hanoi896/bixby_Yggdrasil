package com.yggdrasil.core.proactive

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import java.util.Calendar

/**
 * [ProactiveRoutineManager]
 * 사용자가 부르지 않아도 스마트폰 상황(아침 기상, 배터리 부족, 퇴근 귀가 등)을 감지하여
 * 선제적으로 맞춤 브리핑을 제공하는 능동형 비서 엔진.
 * 앱 내 마스터 스위치 및 상황별 ON/OFF 세분화 설정을 완벽하게 지원함.
 */
class ProactiveRoutineManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "ProactiveRoutineManager"
        private const val PREFS_NAME = "yggdrasil_proactive_prefs"

        // 설정 키값
        private const val KEY_MASTER_ENABLED = "proactive_master_enabled"
        private const val KEY_MORNING_BRIEFING = "enable_morning_briefing"
        private const val KEY_LOW_BATTERY_CARE = "enable_low_battery_care"
        private const val KEY_COMMUTE_ROUTINE = "enable_commute_routine"
        private const val KEY_QUIET_HOURS_ENABLED = "enable_quiet_hours"
        private const val KEY_QUIET_START_HOUR = "quiet_start_hour" // 기본 23시
        private const val KEY_QUIET_END_HOUR = "quiet_end_hour"     // 기본 7시

        @Volatile
        private var instance: ProactiveRoutineManager? = null

        fun getInstance(context: Context): ProactiveRoutineManager {
            return instance ?: synchronized(this) {
                instance ?: ProactiveRoutineManager(context.applicationContext).also { instance = it }
            }
        }
    }

    /**
     * 능동 비서 설정 데이터 모델
     */
    data class ProactiveConfig(
        val isMasterEnabled: Boolean,
        val enableMorningBriefing: Boolean,
        val enableLowBatteryCare: Boolean,
        val enableCommuteRoutine: Boolean,
        val isQuietHoursEnabled: Boolean,
        val quietStartHour: Int,
        val quietEndHour: Int
    )

    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * 현재 전체 설정 조회
     */
    fun getConfig(): ProactiveConfig {
        return ProactiveConfig(
            isMasterEnabled = prefs.getBoolean(KEY_MASTER_ENABLED, true),
            enableMorningBriefing = prefs.getBoolean(KEY_MORNING_BRIEFING, true),
            enableLowBatteryCare = prefs.getBoolean(KEY_LOW_BATTERY_CARE, true),
            enableCommuteRoutine = prefs.getBoolean(KEY_COMMUTE_ROUTINE, true),
            isQuietHoursEnabled = prefs.getBoolean(KEY_QUIET_HOURS_ENABLED, true),
            quietStartHour = prefs.getInt(KEY_QUIET_START_HOUR, 23),
            quietEndHour = prefs.getInt(KEY_QUIET_END_HOUR, 7)
        )
    }

    /**
     * 마스터 ON/OFF 스위치 설정
     */
    fun setMasterEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_MASTER_ENABLED, enabled).apply()
        Log.i(TAG, "선제적 능동 비서 마스터 스위치 변경: $enabled")
    }

    /**
     * 세부 상황별 루틴 토글 설정
     */
    fun updateRoutineSettings(
        enableMorningBriefing: Boolean? = null,
        enableLowBatteryCare: Boolean? = null,
        enableCommuteRoutine: Boolean? = null,
        isQuietHoursEnabled: Boolean? = null,
        quietStartHour: Int? = null,
        quietEndHour: Int? = null
    ) {
        val editor = prefs.edit()
        enableMorningBriefing?.let { editor.putBoolean(KEY_MORNING_BRIEFING, it) }
        enableLowBatteryCare?.let { editor.putBoolean(KEY_LOW_BATTERY_CARE, it) }
        enableCommuteRoutine?.let { editor.putBoolean(KEY_COMMUTE_ROUTINE, it) }
        isQuietHoursEnabled?.let { editor.putBoolean(KEY_QUIET_HOURS_ENABLED, it) }
        quietStartHour?.let { editor.putInt(KEY_QUIET_START_HOUR, it) }
        quietEndHour?.let { editor.putInt(KEY_QUIET_END_HOUR, it) }
        editor.apply()
        Log.i(TAG, "능동 비서 세부 루틴 설정 갱신 완료")
    }

    /**
     * 현재 시간이 방해금지 시간대(Quiet Hours)인지 판정
     */
    fun isInQuietHours(calendar: Calendar = Calendar.getInstance()): Boolean {
        val config = getConfig()
        if (!config.isQuietHoursEnabled) return false

        val currentHour = calendar.get(Calendar.HOUR_OF_DAY)
        return if (config.quietStartHour > config.quietEndHour) {
            // 예: 23시 ~ 07시 (자정을 넘기는 경우)
            currentHour >= config.quietStartHour || currentHour < config.quietEndHour
        } else {
            currentHour in config.quietStartHour until config.quietEndHour
        }
    }

    /**
     * 1. 기상 모닝 브리핑 생성
     */
    fun buildMorningBriefing(userCallName: String, schedules: List<String>, weatherSummary: String): String? {
        val config = getConfig()
        if (!config.isMasterEnabled || !config.enableMorningBriefing) {
            Log.d(TAG, "기상 모닝 브리핑이 비활성화되어 있어 건너뜁니다.")
            return null
        }

        val sb = java.lang.StringBuilder()
        sb.append("${userCallName}, 좋은 아침이에요! ☀️\n")
        sb.append("오늘 날씨는 ${weatherSummary}입니다.\n")
        if (schedules.isNotEmpty()) {
            sb.append("오늘 주요 일정은 ${schedules.size}건 있어요:\n")
            schedules.forEachIndexed { idx, s -> sb.append("• ${s}\n") }
        } else {
            sb.append("오늘은 등록된 일정이 없어 여유로운 하루를 보내실 수 있어요.\n")
        }
        sb.append("오늘도 힘찬 하루 되세요!")
        return sb.toString()
    }

    /**
     * 2. 배터리 긴급 케어 제안 메시지 생성
     */
    fun buildBatteryCareMessage(userCallName: String, batteryPct: Int): String? {
        val config = getConfig()
        if (!config.isMasterEnabled || !config.enableLowBatteryCare) {
            Log.d(TAG, "배터리 케어가 비활성화되어 있어 건너뜁니다.")
            return null
        }

        // 방해금지 시간대에는 침묵
        if (isInQuietHours()) {
            Log.d(TAG, "방해금지 시간대이므로 배터리 케어 알림을 생략합니다.")
            return null
        }

        return "${userCallName}, 배터리가 ${batteryPct}% 남았습니다. 🔋\n" +
               "화면 밝기를 30%로 낮추고 백그라운드 앱을 정리해 배터리를 아껴둘까요?"
    }

    /**
     * 3. 퇴근/귀가 감지 시 스마트홈 제안 메시지 생성
     */
    fun buildCommuteRoutineMessage(userCallName: String): String? {
        val config = getConfig()
        if (!config.isMasterEnabled || !config.enableCommuteRoutine) {
            return null
        }
        if (isInQuietHours()) return null

        return "${userCallName}, 오늘 하루도 고생 많으셨어요! 🏠\n" +
               "집에 도착하시면 편안히 쉬실 수 있게 거실 조명과 에어컨을 미리 켜둘까요?"
    }
}
