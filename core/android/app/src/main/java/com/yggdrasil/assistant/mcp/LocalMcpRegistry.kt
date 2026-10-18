package com.yggdrasil.assistant.mcp

import android.content.Context
import com.yggdrasil.assistant.integration.CalendarAndContactsManager
import com.yggdrasil.assistant.operator.AutoOperatorManager
import com.yggdrasil.assistant.rag.MuninRagManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * [LocalMcpTool]
 * 모바일 샌드박스 내부에서 안전하게 실행되는 인메모리 MCP 도구 인터페이스.
 * 데스크톱 stdio CLI 프로세스 포크를 배제하고 코루틴 기반으로 경량 실행됩니다.
 */
interface LocalMcpTool {
    val name: String
    val description: String
    val keywords: List<String>
    val schemaJson: String // 최소화된 초경량 JSON 스키마
    suspend fun execute(args: Map<String, Any?>): McpResult
}

data class McpResult(
    val success: Boolean,
    val output: String,
    val error: String? = null
)

/**
 * [LocalMcpRegistry]
 * 온디바이스 MCP 도구 등록소 및 싱글톤 매니저 (Zero-Mock 실제 시스템 직결)
 */
class LocalMcpRegistry private constructor(private val context: Context) {

    private val tools = java.util.concurrent.ConcurrentHashMap<String, LocalMcpTool>()
    @Volatile
    private var cachedToolsList: List<LocalMcpTool> = emptyList()

    companion object {
        @Volatile
        private var instance: LocalMcpRegistry? = null

        fun getInstance(context: Context? = null): LocalMcpRegistry {
            return instance ?: synchronized(this) {
                val appCtx = context?.applicationContext 
                    ?: throw IllegalStateException("LocalMcpRegistry 최초 초기화 시 Context가 필요합니다.")
                instance ?: LocalMcpRegistry(appCtx).also {
                    it.registerDefaultTools()
                    instance = it
                }
            }
        }
    }

    @Synchronized
    fun registerTool(tool: LocalMcpTool) {
        tools[tool.name] = tool
        cachedToolsList = java.util.Collections.unmodifiableList(tools.values.toList())
    }

    fun getTool(name: String): LocalMcpTool? = tools[name]

    fun getAllTools(): List<LocalMcpTool> = cachedToolsList

    private fun registerDefaultTools() {
        // 1. 캘린더 일정 조회/등록 도구 (실제 CalendarAndContactsManager 직결)
        registerTool(object : LocalMcpTool {
            override val name = "calendar"
            override val description = "단말기 캘린더 일정 조회 및 등록"
            override val keywords = listOf("일정", "캘린더", "약속", "스케줄", "회의")
            override val schemaJson = """{"name":"calendar","params":{"action":"get|add","title":"string","time":"string"}}"""
            override suspend fun execute(args: Map<String, Any?>): McpResult = withContext(Dispatchers.IO) {
                try {
                    val calendarManager = CalendarAndContactsManager.getInstance(context)
                    val action = (args["action"] as? String)?.lowercase() ?: "get"
                    if (action == "add") {
                        val title = args["title"] as? String ?: "새 일정"
                        val timeStr = args["time"] as? String ?: ""
                        val now = System.currentTimeMillis()
                        val success = calendarManager.addCalendarEvent(
                            title = title,
                            description = "이그드라실 온디바이스 MCP 등록 일정",
                            startTimeMs = now + 3600_000L,
                            endTimeMs = now + 7200_000L
                        )
                        McpResult(success, if (success) "캘린더에 '$title' 일정을 정상 등록했습니다." else "캘린더 일정 등록 실패 (권한 필요)")
                    } else {
                        val events = calendarManager.getTodayEvents()
                        val output = if (events.isNotEmpty()) {
                            "오늘의 일정 목록:\n" + events.joinToString("\n") { "• ${it.title} (${it.timeString})" }
                        } else {
                            "오늘 등록된 일정이 없습니다."
                        }
                        McpResult(true, output)
                    }
                } catch (e: Exception) {
                    McpResult(false, "", e.message)
                }
            }
        })

        // 2. 메모 및 노트 도구 (실제 Munin SQLCipher 암호화 RAG 직결)
        registerTool(object : LocalMcpTool {
            override val name = "memo"
            override val description = "로컬 암호화 볼트에 빠른 메모 기록 및 검색"
            override val keywords = listOf("메모", "기록", "노트", "적어줘")
            override val schemaJson = """{"name":"memo","params":{"action":"save|read","content":"string"}}"""
            override suspend fun execute(args: Map<String, Any?>): McpResult = withContext(Dispatchers.IO) {
                try {
                    val ragManager = MuninRagManager.getInstance(context)
                    val action = (args["action"] as? String)?.lowercase() ?: "save"
                    val content = args["content"] as? String ?: ""
                    if (action == "save") {
                        if (content.isBlank()) return@withContext McpResult(false, "저장할 메모 내용이 비어있습니다.")
                        val success = ragManager.recordMemory(content, "MEMO")
                        McpResult(success, if (success) "암호화 볼트에 메모('$content')를 안전하게 보관했습니다." else "메모 저장 실패")
                    } else {
                        val records = ragManager.searchRelevantMemories(content, 5)
                        val output = if (records.isNotEmpty()) {
                            "메모 검색 결과:\n" + records.joinToString("\n") { "• ${it.content}" }
                        } else {
                            "검색된 관련 메모가 없습니다."
                        }
                        McpResult(true, output)
                    }
                } catch (e: Exception) {
                    McpResult(false, "", e.message)
                }
            }
        })

        // 3. 화면 제어 및 접근성 터치 도구 (실제 AutoOperatorManager 직결)
        registerTool(object : LocalMcpTool {
            override val name = "screen_action"
            override val description = "화면 인식 및 탭/스크롤 자동 제스처 집행"
            override val keywords = listOf("화면", "눌러줘", "클릭", "스크롤", "앱 켜줘", "종료")
            override val schemaJson = """{"name":"screen_action","params":{"target":"string","action":"click|scroll"}}"""
            override suspend fun execute(args: Map<String, Any?>): McpResult = withContext(Dispatchers.Default) {
                try {
                    val operator = AutoOperatorManager.getInstance(context)
                    val target = args["target"] as? String ?: ""
                    val report = operator.executeNaturalCommand(target)
                    McpResult(report.success, report.message)
                } catch (e: Exception) {
                    McpResult(false, "", e.message)
                }
            }
        })

        // 4. 시스템 설정 제어 도구 (Wi-Fi, 배터리, 볼륨 - 실제 시스템 연동)
        registerTool(object : LocalMcpTool {
            override val name = "system_settings"
            override val description = "단말기 Wi-Fi, 블루투스, 소리, 밝기, 절전모드 제어"
            override val keywords = listOf("와이파이", "블루투스", "절전", "볼륨", "밝기", "설정")
            override val schemaJson = """{"name":"system_settings","params":{"setting":"string","value":"string"}}"""
            override suspend fun execute(args: Map<String, Any?>): McpResult = withContext(Dispatchers.Default) {
                try {
                    val operator = AutoOperatorManager.getInstance(context)
                    val setting = args["setting"] as? String ?: ""
                    val value = args["value"] as? String ?: ""
                    val cmd = "$setting $value".trim()
                    val report = operator.executeNaturalCommand(cmd)
                    McpResult(report.success, report.message)
                } catch (e: Exception) {
                    McpResult(false, "", e.message)
                }
            }
        })
    }
}
