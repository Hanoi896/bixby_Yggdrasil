package com.yggdrasil.assistant.security

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import java.util.regex.Pattern

/**
 * [OtpSmsAutoSniffer]
 * 2FA 2단계 인증 문자(SMS) 자동 스니퍼.
 * 수신된 SMS 본문에서 4~6자리 인증번호(OTP)를 정규식으로 즉시 파싱하여
 * 접근성 서비스 또는 Autofill 엔진에 안전하게 주입합니다.
 */
class OtpSmsAutoSniffer(
    private val onOtpExtracted: (String) -> Unit
) : BroadcastReceiver() {

    companion object {
        // [인증번호 123456], [인증코드: 1234] 등 4~6자리 연속 숫자 패턴
        private val OTP_PATTERN = Pattern.compile("\\b(\\d{4,6})\\b")
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        val fullBody = StringBuilder()
        for (msg in messages) {
            fullBody.append(msg.messageBody)
        }

        val text = fullBody.toString()
        if (text.contains("인증") || text.contains("코드") || text.contains("code") || text.contains("OTP")) {
            val matcher = OTP_PATTERN.matcher(text)
            if (matcher.find()) {
                val otp = matcher.group(1)
                if (!otp.isNullOrBlank()) {
                    android.util.Log.i("OtpSmsAutoSniffer", "🎯 2FA OTP 감지 및 자동 추출 성공: $otp")
                    onOtpExtracted(otp)
                }
            }
        }
    }
}
