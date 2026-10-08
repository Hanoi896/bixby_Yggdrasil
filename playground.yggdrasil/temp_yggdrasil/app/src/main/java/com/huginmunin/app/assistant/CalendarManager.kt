package com.huginmunin.app.assistant

import android.content.Context
import android.provider.CalendarContract
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

sealed class CalendarResult {
    data class Success(val events: List<CalendarEvent>) : CalendarResult()
    data class Error(val message: String) : CalendarResult()
}

data class CalendarEvent(val id: Long, val title: String, val dtStart: Long, val dtEnd: Long)

@Singleton
class CalendarManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    fun getUpcomingEvents(): CalendarResult {
        val events = mutableListOf<CalendarEvent>()
        val projection = arrayOf(
            CalendarContract.Events._ID,
            CalendarContract.Events.TITLE,
            CalendarContract.Events.DTSTART,
            CalendarContract.Events.DTEND
        )

        val now = System.currentTimeMillis()
        val selection = "${CalendarContract.Events.DTSTART} >= ?"
        val selectionArgs = arrayOf(now.toString())
        val sortOrder = "${CalendarContract.Events.DTSTART} ASC LIMIT 5"

        try {
            val cursor = context.contentResolver.query(
                CalendarContract.Events.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                sortOrder
            ) ?: return CalendarResult.Error("Failed to query calendar")

            cursor.use {
                val idIdx = it.getColumnIndex(CalendarContract.Events._ID)
                val titleIdx = it.getColumnIndex(CalendarContract.Events.TITLE)
                val startIdx = it.getColumnIndex(CalendarContract.Events.DTSTART)
                val endIdx = it.getColumnIndex(CalendarContract.Events.DTEND)

                // Validate column indices
                if (idIdx == -1 || titleIdx == -1 || startIdx == -1 || endIdx == -1) {
                    return CalendarResult.Error("Invalid calendar schema")
                }

                while (it.moveToNext()) {
                    try {
                        events.add(
                            CalendarEvent(
                                id = it.getLong(idIdx),
                                title = it.getString(titleIdx) ?: "No Title",
                                dtStart = it.getLong(startIdx),
                                dtEnd = it.getLong(endIdx)
                            )
                        )
                    } catch (e: Exception) {
                        // Skip malformed event
                        continue
                    }
                }
            }
            return CalendarResult.Success(events)
        } catch (e: SecurityException) {
            return CalendarResult.Error("Calendar permission not granted")
        } catch (e: Exception) {
            return CalendarResult.Error("Failed to read calendar: ${e.message}")
        }
    }
}
