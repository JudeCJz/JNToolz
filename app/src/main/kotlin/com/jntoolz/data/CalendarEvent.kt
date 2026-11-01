package com.jntoolz.data

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class CalendarInfo(
    val id: Long,
    val accountName: String,
    val displayName: String,
    val isVisibleInGoogle: Boolean,
    val color: Int,
    val ownerAccount: String = ""
)

data class CalendarEvent(
    val id: Long,
    val eventId: Long,
    val calendarId: Long = 0L,
    val title: String,
    val startTime: Long,
    val endTime: Long,
    val isAllDay: Boolean,
    val location: String?,
    val color: Int,
    val calendarDisplayName: String?,
    val labelId: String? = null,
    val googleId: String? = null
) {
    fun isToday(): Boolean {
        val today = Calendar.getInstance()
        val eventCal = Calendar.getInstance().apply { timeInMillis = startTime }
        return today.get(Calendar.YEAR) == eventCal.get(Calendar.YEAR) &&
               today.get(Calendar.DAY_OF_YEAR) == eventCal.get(Calendar.DAY_OF_YEAR)
    }

    /**
     * Returns days between now and the event start.
     */
    fun daysFromNow(): Int {
        val now = System.currentTimeMillis()
        if (startTime <= now) return 0
        val diffMillis = startTime - now
        return (diffMillis / (24 * 60 * 60 * 1000)).toInt()
    }

    /**
     * Determines the visual tier (0 = closest/most opaque, 3 = farthest/most faded).
     */
    fun getOpacityTier(): Int {
        val days = daysFromNow()
        return when {
            days <= 1 -> 0
            days <= 4 -> 1
            days <= 8 -> 2
            else -> 3
        }
    }

    fun getFormattedTime(): String {
        if (isAllDay) return "All day"
        val timeFormat = SimpleDateFormat("HH:mm", Locale.ENGLISH)
        val startStr = timeFormat.format(Date(startTime))
        val endStr = timeFormat.format(Date(endTime))
        return if (startStr == endStr || endTime <= startTime) startStr else "$startStr – $endStr"
    }

    fun getFormattedStartTime(): String {
        if (isAllDay) return "All day"
        val timeFormat = SimpleDateFormat("HH:mm", Locale.ENGLISH)
        return timeFormat.format(Date(startTime))
    }

    fun getFormattedUpcomingDateTime(): String {
        val date = Date(startTime)
        val dateStr = SimpleDateFormat("EEE, d MMM", Locale.ENGLISH).format(date)
        val timeStr = if (isAllDay) "All day" else SimpleDateFormat("HH:mm", Locale.ENGLISH).format(date)
        return "$dateStr • $timeStr"
    }
}
