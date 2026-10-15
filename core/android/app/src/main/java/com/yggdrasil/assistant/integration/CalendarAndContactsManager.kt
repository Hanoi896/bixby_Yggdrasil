package com.yggdrasil.assistant.integration

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.provider.CalendarContract
import android.provider.ContactsContract
import java.util.TimeZone

/**
 * CalendarAndContactsManager
 * 안드로이드 순정 CalendarContract & ContactsContract 기반 구글 계정 연동 매니저
 * 
 * 1. 구글 캘린더(Google Calendar) 일정 실시간 조회 및 신규 일정 추가
 * 2. 구글 주소록(Google Contacts) 이름 기반 전화번호/이메일 검색
 * 3. 100% 무료/순정 OS 프로바이더 연동 (외부 유료 API 불필요)
 */
class CalendarAndContactsManager(private val context: Context) {

    companion object {
        @Volatile
        private var instance: CalendarAndContactsManager? = null

        fun getInstance(context: Context): CalendarAndContactsManager {
            return instance ?: synchronized(this) {
                instance ?: CalendarAndContactsManager(context.applicationContext).also { instance = it }
            }
        }
    }

    data class CalendarEvent(
        val id: Long,
        val title: String,
        val startTimeMillis: Long,
        val endTimeMillis: Long,
        val location: String
    )

    data class ContactInfo(
        val name: String,
        val phoneNumber: String,
        val email: String?
    )

    // ==========================================
    // 1. 구글 캘린더 (CalendarContract) 연동
    // ==========================================

    /**
     * 오늘 00:00부터 23:59:59까지의 실제 구글/삼성 캘린더 일정 목록 조회
     */
    fun getTodayEvents(): List<CalendarEvent> {
        val calendar = java.util.Calendar.getInstance()
        calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
        calendar.set(java.util.Calendar.MINUTE, 0)
        calendar.set(java.util.Calendar.SECOND, 0)
        calendar.set(java.util.Calendar.MILLISECOND, 0)
        val startOfDay = calendar.timeInMillis

        calendar.set(java.util.Calendar.HOUR_OF_DAY, 23)
        calendar.set(java.util.Calendar.MINUTE, 59)
        calendar.set(java.util.Calendar.SECOND, 59)
        calendar.set(java.util.Calendar.MILLISECOND, 999)
        val endOfDay = calendar.timeInMillis

        return queryEvents(startOfDay, endOfDay)
    }

    /**
     * 일정 목록을 사용자 친화적 문자열 포맷(예: "오후 2:00 주간 회의 (회의실 A)")으로 변환
     */
    fun formatEventsForBriefing(events: List<CalendarEvent>): List<String> {
        val timeFormat = java.text.SimpleDateFormat("a h:mm", java.util.Locale.KOREAN)
        return events.map { event ->
            val timeStr = timeFormat.format(java.util.Date(event.startTimeMillis))
            if (event.location.isNotBlank()) {
                "$timeStr ${event.title} (${event.location})"
            } else {
                "$timeStr ${event.title}"
            }
        }
    }

    /**
     * 특정 시간 범위 내의 구글 캘린더 일정 목록 조회
     */
    fun queryEvents(startRangeMillis: Long, endRangeMillis: Long): List<CalendarEvent> {
        val events = mutableListOf<CalendarEvent>()
        val projection = arrayOf(
            CalendarContract.Events._ID,
            CalendarContract.Events.TITLE,
            CalendarContract.Events.DTSTART,
            CalendarContract.Events.DTEND,
            CalendarContract.Events.EVENT_LOCATION
        )

        val selection = "${CalendarContract.Events.DTSTART} >= ? AND ${CalendarContract.Events.DTEND} <= ?"
        val selectionArgs = arrayOf(startRangeMillis.toString(), endRangeMillis.toString())

        try {
            val cursor: Cursor? = context.contentResolver.query(
                CalendarContract.Events.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                "${CalendarContract.Events.DTSTART} ASC"
            )

            cursor?.use {
                val idIdx = it.getColumnIndexOrThrow(CalendarContract.Events._ID)
                val titleIdx = it.getColumnIndexOrThrow(CalendarContract.Events.TITLE)
                val startIdx = it.getColumnIndexOrThrow(CalendarContract.Events.DTSTART)
                val endIdx = it.getColumnIndexOrThrow(CalendarContract.Events.DTEND)
                val locIdx = it.getColumnIndexOrThrow(CalendarContract.Events.EVENT_LOCATION)

                while (it.moveToNext()) {
                    events.add(
                        CalendarEvent(
                            id = it.getLong(idIdx),
                            title = it.getString(titleIdx) ?: "(제목 없음)",
                            startTimeMillis = it.getLong(startIdx),
                            endTimeMillis = it.getLong(endIdx),
                            location = it.getString(locIdx) ?: ""
                        )
                    )
                }
            }
        } catch (e: SecurityException) {
            // 권한 미허용 시 빈 리스트 반환
        }

        return events
    }

    /**
     * 구글 캘린더에 새로운 일정 등록
     */
    fun insertEvent(
        title: String,
        startTimeMillis: Long,
        endTimeMillis: Long,
        location: String = "",
        description: String = ""
    ): Long {
        val values = ContentValues().apply {
            put(CalendarContract.Events.DTSTART, startTimeMillis)
            put(CalendarContract.Events.DTEND, endTimeMillis)
            put(CalendarContract.Events.TITLE, title)
            put(CalendarContract.Events.DESCRIPTION, description)
            put(CalendarContract.Events.EVENT_LOCATION, location)
            put(CalendarContract.Events.CALENDAR_ID, 1) // 기본 구글 캘린더 ID
            put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
        }

        return try {
            val uri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
            uri?.lastPathSegment?.toLongOrNull() ?: -1L
        } catch (e: Exception) {
            -1L
        }
    }

    // ==========================================
    // 2. 구글 연락처 (ContactsContract) 연동
    // ==========================================

    /**
     * 연락처 이름으로 전화번호 검색
     */
    fun findContactByName(targetName: String): ContactInfo? {
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )
        val selection = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?"
        val selectionArgs = arrayOf("%$targetName%")

        try {
            val cursor = context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                null
            )

            cursor?.use {
                if (it.moveToNext()) {
                    val name = it.getString(it.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME))
                    val number = it.getString(it.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER))
                    return ContactInfo(name = name, phoneNumber = number, email = null)
                }
            }
        } catch (e: SecurityException) {
        }

        return null
    }
}
