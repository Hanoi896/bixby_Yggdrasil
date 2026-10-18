package com.yggdrasil.assistant.integration

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.yggdrasil.assistant.llm.LocalLlmService

/**
 * YggdrasilHeadlessReceiver
 * 화면 꺼짐(Screen-off / AOD / 잠금화면) 상태에서 빅스비 호출 시 먹통 방지 리시버
 * 
 * Android 10+ 백그라운드 액티비티 시작 제한(Background Activity Start Restriction)을 우회하여,
 * 화면을 켜지 않고도 포그라운드 상주 서비스(LocalLlmService)를 통해
 * 100% 온디바이스 NPU 추론 및 음성(TTS) 피드백을 즉시 출력합니다.
 */
class YggdrasilHeadlessReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "HeadlessReceiver"
        const val ACTION_HEADLESS_QUERY = "com.yggdrasil.assistant.action.HEADLESS_QUERY"
        const val ACTION_HEADLESS_EXECUTE = "com.yggdrasil.assistant.action.HEADLESS_EXECUTE"
        const val EXTRA_QUERY = "query"
        const val EXTRA_ACTION_NAME = "action_name"
        const val EXTRA_PAYLOAD = "payload"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.i(TAG, "헤드리스 백그라운드 인텐트 수신: $action")

        val serviceIntent = Intent(context, LocalLlmService::class.java)

        when (action) {
            ACTION_HEADLESS_QUERY -> {
                val query = intent.getStringExtra(EXTRA_QUERY) ?: return
                serviceIntent.putExtra("HEADLESS_QUERY", query)
            }
            ACTION_HEADLESS_EXECUTE -> {
                val actionName = intent.getStringExtra(EXTRA_ACTION_NAME) ?: "execute"
                val payload = intent.getStringExtra(EXTRA_PAYLOAD) ?: ""
                serviceIntent.putExtra("ACTION_NAME", actionName)
                serviceIntent.putExtra("ACTION_PAYLOAD", payload)
            }
            Intent.ACTION_VIEW -> {
                val uri = intent.data
                if (uri != null && uri.scheme.equals("yggdrasil", ignoreCase = true)) {
                    val host = uri.host?.lowercase()
                    if (host == "chat") {
                        val query = uri.getQueryParameter("query") ?: ""
                        serviceIntent.putExtra("HEADLESS_QUERY", query)
                    } else if (host == "action") {
                        val name = uri.getQueryParameter("name") ?: uri.getQueryParameter("type") ?: "execute"
                        val payload = uri.getQueryParameter("payload") ?: ""
                        serviceIntent.putExtra("ACTION_NAME", name)
                        serviceIntent.putExtra("ACTION_PAYLOAD", payload)
                    }
                }
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
    }
}
