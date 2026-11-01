package com.jntoolz.data

import android.accounts.AccountManager
import android.Manifest
import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import java.util.Calendar

class CalendarRepository(private val context: Context) {

    private val prefs = WidgetPreferences(context)

    fun hasCalendarPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CALENDAR
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun requestGoogleCalendarSync() {
        try {
            val accountManager = AccountManager.get(context)
            val accounts = accountManager.getAccountsByType("com.google")
            val bundle = Bundle().apply {
                putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true)
                putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true)
            }
            for (account in accounts) {
                ContentResolver.requestSync(account, CalendarContract.AUTHORITY, bundle)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Retrieves all Google Calendars on the device with their account name and Google visibility status.
     */
    fun getAvailableCalendars(): List<CalendarInfo> {
        if (!hasCalendarPermission()) return emptyList()

        val list = mutableListOf<CalendarInfo>()
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.VISIBLE,
            CalendarContract.Calendars.CALENDAR_COLOR
        )

        try {
            context.contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                projection,
                null,
                null,
                "${CalendarContract.Calendars.ACCOUNT_NAME} ASC, ${CalendarContract.Calendars.CALENDAR_DISPLAY_NAME} ASC"
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndex(CalendarContract.Calendars._ID)
                val accIdx = cursor.getColumnIndex(CalendarContract.Calendars.ACCOUNT_NAME)
                val dispIdx = cursor.getColumnIndex(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME)
                val visIdx = cursor.getColumnIndex(CalendarContract.Calendars.VISIBLE)
                val colIdx = cursor.getColumnIndex(CalendarContract.Calendars.CALENDAR_COLOR)

                while (cursor.moveToNext()) {
                    val id = if (idIdx != -1) cursor.getLong(idIdx) else 0L
                    val acc = if (accIdx != -1) cursor.getString(accIdx) ?: "" else ""
                    val disp = if (dispIdx != -1) cursor.getString(dispIdx) ?: acc else acc
                    val vis = if (visIdx != -1) cursor.getInt(visIdx) == 1 else true
                    val color = if (colIdx != -1) cursor.getInt(colIdx) else 0

                    list.add(
                        CalendarInfo(
                            id = id,
                            accountName = acc,
                            displayName = disp,
                            isVisibleInGoogle = vis,
                            color = color
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    /**
     * Fetches all upcoming events respecting user-enabled or Google-default visible calendars.
     */
    fun getUpcomingEvents(daysAhead: Int = 180): List<CalendarEvent> {
        if (!hasCalendarPermission()) {
            return emptyList()
        }

        val availableCalendars = getAvailableCalendars()
        val enabledCalendarIds = availableCalendars
            .filter { prefs.isCalendarEnabled(it) }
            .map { it.id }
            .toSet()

        if (enabledCalendarIds.isEmpty()) {
            return emptyList()
        }

        val events = mutableListOf<CalendarEvent>()
        val seenEventKeys = HashSet<String>()

        val startCal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startTime = startCal.timeInMillis

        val endCal = Calendar.getInstance().apply {
            timeInMillis = startTime
            add(Calendar.DAY_OF_YEAR, daysAhead)
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
        }
        val endTime = endCal.timeInMillis

        val builder = CalendarContract.Instances.CONTENT_URI.buildUpon()
        ContentUris.appendId(builder, startTime)
        ContentUris.appendId(builder, endTime)

        val projection = arrayOf(
            CalendarContract.Instances._ID,
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.CALENDAR_ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.EVENT_LOCATION,
            CalendarContract.Instances.DISPLAY_COLOR,
            CalendarContract.Instances.CALENDAR_DISPLAY_NAME
        )

        val selection = "${CalendarContract.Instances.STATUS} != ${CalendarContract.Instances.STATUS_CANCELED}"
        val sortOrder = "${CalendarContract.Instances.BEGIN} ASC"

        try {
            context.contentResolver.query(
                builder.build(),
                projection,
                selection,
                null,
                sortOrder
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndex(CalendarContract.Instances._ID)
                val eventIdIdx = cursor.getColumnIndex(CalendarContract.Instances.EVENT_ID)
                val calIdIdx = cursor.getColumnIndex(CalendarContract.Instances.CALENDAR_ID)
                val titleIdx = cursor.getColumnIndex(CalendarContract.Instances.TITLE)
                val beginIdx = cursor.getColumnIndex(CalendarContract.Instances.BEGIN)
                val endIdx = cursor.getColumnIndex(CalendarContract.Instances.END)
                val allDayIdx = cursor.getColumnIndex(CalendarContract.Instances.ALL_DAY)
                val locationIdx = cursor.getColumnIndex(CalendarContract.Instances.EVENT_LOCATION)
                val colorIdx = cursor.getColumnIndex(CalendarContract.Instances.DISPLAY_COLOR)
                val calNameIdx = cursor.getColumnIndex(CalendarContract.Instances.CALENDAR_DISPLAY_NAME)

                while (cursor.moveToNext()) {
                    val calId = if (calIdIdx != -1) cursor.getLong(calIdIdx) else 0L
                    if (!enabledCalendarIds.contains(calId)) continue

                    val rawTitle = if (titleIdx != -1) cursor.getString(titleIdx) else null
                    if (rawTitle.isNullOrBlank()) continue

                    val begin = if (beginIdx != -1) cursor.getLong(beginIdx) else 0L
                    val end = if (endIdx != -1) cursor.getLong(endIdx) else begin

                    val dedupeKey = "$rawTitle-$begin"
                    if (seenEventKeys.contains(dedupeKey)) continue
                    seenEventKeys.add(dedupeKey)

                    val isAllDay = if (allDayIdx != -1) cursor.getInt(allDayIdx) == 1 else false
                    val location = if (locationIdx != -1) cursor.getString(locationIdx) else null
                    val color = if (colorIdx != -1) cursor.getInt(colorIdx) else 0
                    val calName = if (calNameIdx != -1) cursor.getString(calNameIdx) else null
                    val id = if (idIdx != -1) cursor.getLong(idIdx) else 0L
                    val eventId = if (eventIdIdx != -1) cursor.getLong(eventIdIdx) else 0L

                    events.add(
                        CalendarEvent(
                            id = id,
                            eventId = eventId,
                            calendarId = calId,
                            title = rawTitle.trim(),
                            startTime = begin,
                            endTime = end,
                            isAllDay = isAllDay,
                            location = location,
                            color = color,
                            calendarDisplayName = calName
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return events
    }
}
