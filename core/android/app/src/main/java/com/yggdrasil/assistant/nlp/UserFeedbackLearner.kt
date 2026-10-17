package com.yggdrasil.assistant.nlp

import android.content.Context
import android.util.Log
import com.yggdrasil.assistant.voice.YggdrasilIntentPlanner
import java.util.concurrent.ConcurrentHashMap

/**
 * [UserFeedbackLearner]
 * 사용자의 실시간 피드백 및 부정 지적("아니", "그거 말고", "카톡 말고 문자로")을 감지하여
 * 선호 규칙을 동적으로 자가 학습(In-Context Adaptation)하고 기억하는 온디바이스 엔진.
 * 
 * - 모델 가중치를 건드리지 않고도 사용자의 습관/교정 내역을 영구 기억소에 반영하여
 *   다음번 명령부터 틀리지 않는 진정한 온디바이스 자가 진화를 달성한다.
 */
class UserFeedbackLearner private constructor(private val context: Context) {

    companion object {
        private const val TAG = "UserFeedbackLearner"

        @Volatile
        private var instance: UserFeedbackLearner? = null

        fun getInstance(context: Context): UserFeedbackLearner {
            return instance ?: synchronized(this) {
                instance ?: UserFeedbackLearner(context.applicationContext).also { instance = it }
            }
        }
    }

    data class ActionHistory(
        val intent: YggdrasilIntentPlanner.ActionIntent,
        val appName: String,
        val target: String,
        val content: String,
        val timestamp: Long = System.currentTimeMillis()
    )

    data class CorrectionResult(
        val message: String,
        val shouldReexecute: Boolean = false,
        val newAppName: String? = null,
        val target: String? = null,
        val content: String? = null
    )

    private val prefs = context.getSharedPreferences("yggdrasil_user_learning", Context.MODE_PRIVATE)
    private var lastAction: ActionHistory? = null

    // 사용자 교정 규칙: 대상/키워드 -> 선호 앱 (예: "엄마" -> "메시지")
    private val preferredAppOverrides = ConcurrentHashMap<String, String>()

    init {
        // 앱 재시작 시에도 영구 기억소(SharedPreferences)에서 학습된 사용자 선호 규칙 복원
        prefs.all.forEach { (key, value) ->
            if (value is String) {
                preferredAppOverrides[key] = value
                Log.d(TAG, "📦 [학습된 선호 규칙 복원]: $key -> $value")
            }
        }
    }

    /**
     * 액션 실행 직후 기록
     */
    fun recordExecutedAction(intent: YggdrasilIntentPlanner.ActionIntent, appName: String, target: String, content: String) {
        lastAction = ActionHistory(intent, appName, target, content)
        Log.i(TAG, "📝 직전 액션 기록: intent=$intent, app=$appName, target=$target")
    }

    /**
     * 사용자의 교정 발화 분석 및 선호도 자동 학습 + 즉시 재실행 데이터 패키지 반환
     * 예: "아니 카톡 말고 문자로 보내", "문자로 해줘"
     */
    fun learnFromCorrection(correctionSentence: String): CorrectionResult {
        val last = lastAction ?: return CorrectionResult("어떤 작업을 수정해 드릴까요?", false)
        val clean = correctionSentence.trim()

        Log.w(TAG, "🧠 [자가 교정 학습 발동]: 사용자 지적='$clean' (직전 작업: ${last.appName} -> ${last.target})")

        // 1. "A 말고 B로" 패턴 추출
        val insteadRegex = Regex("([가-힣a-zA-Z0-9]+)\\s*말고\\s*([가-힣a-zA-Z0-9]+)")
        val match = insteadRegex.find(clean)

        if (match != null) {
            val to = match.groupValues[2].trim()
            val canonicalTo = KoreanOntologyDatabase.getInstance(context).getCanonicalEntity(to) ?: to

            if (last.target.isNotBlank()) {
                persistOverride(last.target, canonicalTo)
                val msg = "${last.target}님께는 앞으로 ${canonicalTo}(으)로 보내겠습니다."
                return CorrectionResult(
                    message = msg,
                    shouldReexecute = true,
                    newAppName = canonicalTo,
                    target = last.target,
                    content = last.content
                )
            }
        }

        // 2. "문자로", "카톡으로" 등 단순 수단 변경 교정
        if (clean.contains("문자") && last.appName != "메시지") {
            if (last.target.isNotBlank()) persistOverride(last.target, "메시지")
            return CorrectionResult(
                message = "네, 카카오톡 대신 문자로 전송하겠습니다.",
                shouldReexecute = true,
                newAppName = "메시지",
                target = last.target,
                content = last.content
            )
        }
        if ((clean.contains("카톡") || clean.contains("카카오톡")) && last.appName != "카카오톡") {
            if (last.target.isNotBlank()) persistOverride(last.target, "카카오톡")
            return CorrectionResult(
                message = "네, 문자 대신 카카오톡으로 전송하겠습니다.",
                shouldReexecute = true,
                newAppName = "카카오톡",
                target = last.target,
                content = last.content
            )
        }

        return CorrectionResult("말씀하신 대로 바로잡겠습니다. 어떻게 변경해 드릴까요?", false)
    }

    private fun persistOverride(target: String, appName: String) {
        preferredAppOverrides[target] = appName
        prefs.edit().putString(target, appName).apply()
        Log.i(TAG, "💡 [영구 선호 규칙 각인]: '$target' ➔ 항상 '$appName' 우선 사용 (영구 보존 완료)")
    }

    /**
     * 타겟 대상에 대한 사용자 선호 앱 오버라이드 조회
     */
    fun getPreferredApp(target: String): String? {
        return preferredAppOverrides[target]
    }

    fun getLastAction(): ActionHistory? = lastAction
}
