package com.yggdrasil.assistant.integration

import android.content.Context
import android.content.Intent
import android.net.Uri
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * [SleipnirMessengerClient]
 * 고속 통신자 Sleipnir 기반 범용 메신저 연동 엔진
 * 
 * 1. 텔레그램 Bot API 기반 실제 텍스트 메시지 및 대용량 압축 파일(ZIP) 전송
 * 2. 카카오톡 및 안드로이드 시스템 공유(System Share Intent) 브릿지
 * 3. Hugin messenger_send 도구 파이프라인과 1:1 연동
 */
class SleipnirMessengerClient(private val context: Context) {

    companion object {
        private const val PREFS_NAME = "yggdrasil_messenger_config"
        private const val KEY_TELEGRAM_BOT_TOKEN = "telegram_bot_token"
        private const val KEY_TELEGRAM_CHAT_ID = "telegram_default_chat_id"

        @Volatile
        private var instance: SleipnirMessengerClient? = null

        fun getInstance(context: Context): SleipnirMessengerClient {
            return instance ?: synchronized(this) {
                instance ?: SleipnirMessengerClient(context.applicationContext).also { instance = it }
            }
        }
    }

    data class SendResult(
        val success: Boolean,
        val platform: String,
        val messageId: String?,
        val detailMessage: String
    )

    /**
     * 텔레그램 인증 설정 저장
     */
    fun saveTelegramConfig(botToken: String, defaultChatId: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_TELEGRAM_BOT_TOKEN, botToken.trim())
            .putString(KEY_TELEGRAM_CHAT_ID, defaultChatId.trim())
            .apply()
    }

    /**
     * 텔레그램 인증 설정 조회
     */
    fun getTelegramConfig(): Pair<String?, String?> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val token = prefs.getString(KEY_TELEGRAM_BOT_TOKEN, null)?.takeIf { it.isNotBlank() }
        val chatId = prefs.getString(KEY_TELEGRAM_CHAT_ID, null)?.takeIf { it.isNotBlank() }
        return Pair(token, chatId)
    }

    /**
     * 텔레그램 연동 테스트 메시지 1건 발송
     */
    fun testTelegramConnection(): SendResult {
        val (token, chatId) = getTelegramConfig()
        if (token.isNullOrBlank() || chatId.isNullOrBlank()) {
            return SendResult(
                success = false,
                platform = "telegram",
                messageId = null,
                detailMessage = "Bot Token 또는 Chat ID가 설정되지 않았습니다."
            )
        }
        val timestamp = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.KOREA).format(java.util.Date())
        return sendTelegram(
            chatId = chatId,
            textMessage = "🌲 [Yggdrasil Assistant] 텔레그램 연동 테스트 성공!\n일시: $timestamp\n정상적으로 봇 메시지가 수신됩니다.",
            attachmentFilePath = null
        )
    }

    /**
     * 범용 메신저 전송 디스패처
     */
    fun sendMessage(
        platform: String,
        recipient: String?,
        textMessage: String?,
        attachmentFilePath: String?
    ): SendResult {
        return when (platform.lowercase()) {
            "telegram", "텔레그램" -> sendTelegram(recipient, textMessage, attachmentFilePath)
            "kakaotalk", "카카오톡" -> sendKakaoTalk(textMessage, attachmentFilePath)
            "sms", "문자" -> sendSmsIntent(recipient, textMessage)
            else -> sendSystemShareIntent(textMessage, attachmentFilePath)
        }
    }

    /**
     * 1. 텔레그램 Bot API 직접 전송 (sendDocument / sendMessage)
     */
    private fun sendTelegram(
        chatId: String?,
        textMessage: String?,
        attachmentFilePath: String?
    ): SendResult {
        val (savedToken, savedChatId) = getTelegramConfig()
        val botToken = savedToken
        val targetChatId = chatId?.takeIf { it.isNotBlank() } ?: savedChatId

        // 토큰이 등록되어 있지 않으면 정직하게 에러 반환 (가짜 성공 차단)
        if (botToken.isNullOrBlank()) {
            return SendResult(
                success = false,
                platform = "telegram",
                messageId = null,
                detailMessage = "텔레그램 Bot Token이 설정되지 않았습니다. 앱 설정에서 토큰을 등록해 주세요."
            )
        }

        if (targetChatId.isNullOrBlank()) {
            return SendResult(
                success = false,
                platform = "telegram",
                messageId = null,
                detailMessage = "전송 대상 Chat ID가 지정되지 않았습니다."
            )
        }

        // 파일 첨부 전송인 경우 (sendDocument)
        if (!attachmentFilePath.isNullOrBlank()) {
            val file = File(attachmentFilePath)
            if (!file.exists()) {
                return SendResult(false, "telegram", null, "첨부 파일이 존재하지 않습니다: $attachmentFilePath")
            }

            return try {
                val boundary = "==YggdrasilBoundary==" + System.currentTimeMillis()
                val url = URL("https://api.telegram.org/bot$botToken/sendDocument")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15000
                    readTimeout = 30000
                    useCaches = false
                    doOutput = true
                    doInput = true
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
                }

                conn.outputStream.use { out ->
                    PrintWriter(OutputStreamWriter(out, "UTF-8"), true).use { writer ->
                        // chat_id 파라미터
                        writer.append("--$boundary").append("\r\n")
                        writer.append("Content-Disposition: form-data; name=\"chat_id\"").append("\r\n\r\n")
                        writer.append(targetChatId).append("\r\n")

                        // caption 파라미터
                        if (!textMessage.isNullOrBlank()) {
                            writer.append("--$boundary").append("\r\n")
                            writer.append("Content-Disposition: form-data; name=\"caption\"").append("\r\n\r\n")
                            writer.append(textMessage).append("\r\n")
                        }

                        // document 파일 파라미터
                        writer.append("--$boundary").append("\r\n")
                        writer.append("Content-Disposition: form-data; name=\"document\"; filename=\"${file.name}\"").append("\r\n")
                        writer.append("Content-Type: application/octet-stream").append("\r\n\r\n")
                        writer.flush()

                        FileInputStream(file).use { fileIn ->
                            val buffer = ByteArray(8192)
                            var bytesRead: Int
                            while (fileIn.read(buffer).also { bytesRead = it } != -1) {
                                out.write(buffer, 0, bytesRead)
                            }
                            out.flush()
                        }

                        writer.append("\r\n").append("--$boundary--").append("\r\n")
                        writer.flush()
                    }
                }

                val responseCode = conn.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    SendResult(true, "telegram", "tg-doc-${System.currentTimeMillis()}", "텔레그램 파일 전송 완료: ${file.name}")
                } else {
                    val errorMsg = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $responseCode"
                    SendResult(false, "telegram", null, "텔레그램 서버 응답 오류 ($responseCode): $errorMsg")
                }
            } catch (e: Exception) {
                // 실패 시 실제 에러 사유를 정직하게 반환 (가짜 성공 속임수 제거)
                SendResult(false, "telegram", null, "텔레그램 전송 실패: ${e.localizedMessage ?: e.message}")
            }
        }

        // 일반 텍스트 전송인 경우 (sendMessage)
        return try {
            val url = URL("https://api.telegram.org/bot$botToken/sendMessage")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 10000
                readTimeout = 15000
                requestMethod = "POST"
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            }

            val jsonBody = JSONObject().apply {
                put("chat_id", targetChatId)
                put("text", textMessage ?: "")
            }

            conn.outputStream.use { os ->
                os.write(jsonBody.toString().toByteArray(Charsets.UTF_8))
                os.flush()
            }

            val responseCode = conn.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                SendResult(true, "telegram", "tg-msg-${System.currentTimeMillis()}", "텔레그램 텍스트 메시지 전송 성공")
            } else {
                val errorMsg = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $responseCode"
                SendResult(false, "telegram", null, "텔레그램 전송 실패 ($responseCode): $errorMsg")
            }
        } catch (e: Exception) {
            SendResult(false, "telegram", null, "텔레그램 네트워크 오류: ${e.localizedMessage ?: e.message}")
        }
    }

    /**
     * 2. 카카오톡 인텐트 연동 (FileProvider 안전 가드레일 적용)
     */
    private fun sendKakaoTalk(textMessage: String?, attachmentFilePath: String?): SendResult {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = if (attachmentFilePath != null) "application/zip" else "text/plain"
            setPackage("com.kakao.talk")
            putExtra(Intent.EXTRA_TEXT, textMessage ?: "")
            if (attachmentFilePath != null) {
                val file = File(attachmentFilePath)
                putExtra(Intent.EXTRA_STREAM, getSharableUri(file))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }

        return try {
            context.startActivity(intent)
            SendResult(true, "kakaotalk", null, "카카오톡 공유 실행 완료")
        } catch (e: Exception) {
            sendSystemShareIntent(textMessage, attachmentFilePath)
        }
    }

    /**
     * 3. 안드로이드 SMS 문자 인텐트
     */
    private fun sendSmsIntent(recipient: String?, textMessage: String?): SendResult {
        val uri = Uri.parse("smsto:${recipient ?: ""}")
        val intent = Intent(Intent.ACTION_SENDTO, uri).apply {
            putExtra("sms_body", textMessage ?: "")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
        return SendResult(true, "sms", null, "문자메시지 발송 화면 호출 완료")
    }

    /**
     * 4. 시스템 표준 공유 인텐트 (FileProvider 안전 가드레일 적용)
     */
    private fun sendSystemShareIntent(textMessage: String?, attachmentFilePath: String?): SendResult {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = if (attachmentFilePath != null) "*/*" else "text/plain"
            putExtra(Intent.EXTRA_TEXT, textMessage ?: "")
            if (attachmentFilePath != null) {
                val file = File(attachmentFilePath)
                putExtra(Intent.EXTRA_STREAM, getSharableUri(file))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        val chooser = Intent.createChooser(intent, "공유할 앱 선택").apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        context.startActivity(chooser)
        return SendResult(true, "system_share", null, "시스템 공유 창 호출 완료")
    }

    private fun getSharableUri(file: File): Uri {
        return try {
            androidx.core.content.FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
        } catch (e: Exception) {
            Uri.fromFile(file)
        }
    }
}
