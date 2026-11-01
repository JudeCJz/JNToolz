package com.jntoolz.ui.main

import com.jntoolz.data.CalendarEvent
import org.junit.Assert.assertEquals
import org.junit.Test

class CalendarEventTest {
    @Test
    fun formattedTime_allDayReturnsAllDay() {
        val event = CalendarEvent(
            id = 1,
            eventId = 1,
            title = "Holiday",
            startTime = 1000L,
            endTime = 2000L,
            isAllDay = true,
            location = null,
            color = 0,
            calendarDisplayName = "Holidays"
        )
        assertEquals("All day", event.getFormattedTime())
        assertEquals("All day", event.getFormattedStartTime())
    }
}
