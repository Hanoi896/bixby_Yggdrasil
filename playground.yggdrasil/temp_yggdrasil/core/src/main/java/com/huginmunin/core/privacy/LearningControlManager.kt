package com.huginmunin.core.privacy

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LearningControlManager @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs: SharedPreferences = context.getSharedPreferences("learning_control_prefs", Context.MODE_PRIVATE)

    private val _isLearningPaused = MutableStateFlow(prefs.getBoolean("is_learning_paused", false))
    val isLearningPaused: StateFlow<Boolean> = _isLearningPaused.asStateFlow()

    private val _pauseEndTime = MutableStateFlow(prefs.getLong("pause_end_time", 0L))
    val pauseEndTime: StateFlow<Long> = _pauseEndTime.asStateFlow()

    fun setLearningPaused(paused: Boolean, durationMs: Long = 0) {
        val currentTime = System.currentTimeMillis()
        val endTime = if (paused && durationMs > 0) currentTime + durationMs else 0L

        prefs.edit()
            .putBoolean("is_learning_paused", paused)
            .putLong("pause_end_time", endTime)
            .apply()

        _isLearningPaused.value = paused
        _pauseEndTime.value = endTime
    }

    fun isCategoryAllowed(category: String): Boolean {
        if (isLearningPaused.value) {
            val endTime = pauseEndTime.value
            if (endTime > 0 && System.currentTimeMillis() > endTime) {
                // Pause expired
                setLearningPaused(false)
                return prefs.getBoolean("cat_$category", true)
            }
            return false
        }
        return prefs.getBoolean("cat_$category", true)
    }

    fun setCategoryAllowed(category: String, allowed: Boolean) {
        prefs.edit().putBoolean("cat_$category", allowed).apply()
    }
}
