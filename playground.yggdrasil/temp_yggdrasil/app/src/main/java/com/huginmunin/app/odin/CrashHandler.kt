package com.huginmunin.app.odin

import android.content.Context
import com.huginmunin.app.munin.MuninRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.PrintWriter
import java.io.StringWriter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Odin's Watchful Eye: Global Crash Handler
 * 모든 예외를 감지하고 Munin에게 기억하도록 명령합니다.
 */
@Singleton
class CrashHandler @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context,
    private val munin: MuninRepository
) : Thread.UncaughtExceptionHandler {

    private val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
    // private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO) // Not needed for blocking call

    fun initialize() {
        Thread.setDefaultUncaughtExceptionHandler(this)
        munin.log("Odin", "CrashHandler initialized - Odin is watching", "INFO")
    }

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        try {
            // 크래시 정보를 Munin에 기록
            val stackTrace = getStackTraceString(throwable)
            val crashMessage = """
                ⚠️ CRASH DETECTED ⚠️
                Thread: ${thread.name}
                Exception: ${throwable.javaClass.simpleName}
                Message: ${throwable.message}
                
                Stack Trace:
                $stackTrace
            """.trimIndent()

            // Munin에게 크래시 기록 (동기적으로 - runBlocking 사용)
            munin.logBlocking(
                tag = "CRASH",
                message = crashMessage,
                level = "CRITICAL"
            )

            // 크래시 로그가 저장될 때까지 아주 잠시 대기 (안전장치)
            Thread.sleep(200)
        } catch (e: Exception) {
            // 크래시 핸들러 자체에서 에러가 나면 안되므로 무시
            e.printStackTrace()
        } finally {
            // 원래 핸들러 호출하여 앱 종료 처리 위임
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    private fun getStackTraceString(throwable: Throwable): String {
        val sw = StringWriter()
        val pw = PrintWriter(sw)
        throwable.printStackTrace(pw)
        return sw.toString()
    }
}
