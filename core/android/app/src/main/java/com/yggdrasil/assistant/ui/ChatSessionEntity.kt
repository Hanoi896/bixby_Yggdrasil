package com.yggdrasil.assistant.ui

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * [ChatSession]
 * 멀티 세션 스레드 데이터 모델
 */
data class ChatSession(
    val id: String = UUID.randomUUID().toString(),
    var title: String = "새 대화",
    val createdAt: Long = System.currentTimeMillis(),
    var updatedAt: Long = System.currentTimeMillis(),
    var isPinned: Boolean = false,
    var isFlagged: Boolean = false,
    var category: String = "일반"
)

/**
 * [ChatSessionManager]
 * 세션 스레드 영구 저장 및 목록 관리자
 */
class ChatSessionManager private constructor(private val context: Context) {

    private val sessionFile = File(context.filesDir, "chat_sessions.json")
    private val sessions = mutableListOf<ChatSession>()

    companion object {
        @Volatile
        private var instance: ChatSessionManager? = null

        fun getInstance(context: Context): ChatSessionManager {
            return instance ?: synchronized(this) {
                instance ?: ChatSessionManager(context.applicationContext).also {
                    it.loadSessions()
                    instance = it
                }
            }
        }
    }

    @Synchronized
    fun loadSessions(): List<ChatSession> {
        sessions.clear()
        if (sessionFile.exists()) {
            try {
                val jsonStr = sessionFile.readText(Charsets.UTF_8)
                val array = JSONArray(jsonStr)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    sessions.add(
                        ChatSession(
                            id = obj.getString("id"),
                            title = obj.getString("title"),
                            createdAt = obj.getLong("createdAt"),
                            updatedAt = obj.getLong("updatedAt"),
                            isPinned = obj.optBoolean("isPinned", false),
                            isFlagged = obj.optBoolean("isFlagged", false),
                            category = obj.optString("category", "일반")
                        )
                    )
                }
            } catch (_: Exception) {}
        }

        if (sessions.isEmpty()) {
            val defaultSession = ChatSession(title = "첫인사와 대화 시작", isPinned = true)
            sessions.add(defaultSession)
            saveSessions()
        }
        return getSortedSessions()
    }

    @Synchronized
    fun createNewSession(title: String = "새 대화"): ChatSession {
        val newSession = ChatSession(title = title)
        sessions.add(0, newSession)
        saveSessions()
        return newSession
    }

    @Synchronized
    fun togglePin(sessionId: String) {
        sessions.find { it.id == sessionId }?.let {
            it.isPinned = !it.isPinned
            saveSessions()
        }
    }

    @Synchronized
    fun toggleFlag(sessionId: String) {
        sessions.find { it.id == sessionId }?.let {
            it.isFlagged = !it.isFlagged
            saveSessions()
        }
    }

    @Synchronized
    fun updateTitle(sessionId: String, newTitle: String) {
        sessions.find { it.id == sessionId }?.let {
            it.title = newTitle
            it.updatedAt = System.currentTimeMillis()
            saveSessions()
        }
    }

    fun getSortedSessions(): List<ChatSession> {
        return sessions.sortedWith(
            compareByDescending<ChatSession> { it.isPinned }
                .thenByDescending { it.updatedAt }
        )
    }

    @Synchronized
    private fun saveSessions() {
        try {
            val array = JSONArray()
            for (s in sessions) {
                val obj = JSONObject().apply {
                    put("id", s.id)
                    put("title", s.title)
                    put("createdAt", s.createdAt)
                    put("updatedAt", s.updatedAt)
                    put("isPinned", s.isPinned)
                    put("isFlagged", s.isFlagged)
                    put("category", s.category)
                }
                array.put(obj)
            }
            sessionFile.writeText(array.toString(), Charsets.UTF_8)
        } catch (_: Exception) {}
    }
}
