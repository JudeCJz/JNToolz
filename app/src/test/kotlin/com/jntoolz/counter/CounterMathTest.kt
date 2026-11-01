package com.jntoolz.counter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class CounterMathTest {
    private val zone = ZoneId.of("Europe/London")
    private fun at(y: Int, m: Int, d: Int, h: Int = 12, min: Int = 0) =
        ZonedDateTime.of(LocalDateTime.of(y, m, d, h, min), zone)
    private fun date(y: Int, m: Int, d: Int) = LocalDate.of(y, m, d)

    private fun show(target: LocalDate, now: ZonedDateTime, time: LocalTime? = null, created: LocalDate = now.toLocalDate()) =
        CounterMath.display(target, time, created, now)

    @Test
    fun future() {
        val d = show(date(2026, 12, 25), at(2026, 11, 1))
        assertEquals(CounterDisplay.Phase.FUTURE, d.phase)
        assertEquals(54L, d.days)
        assertEquals("54", d.number)
        assertEquals("DAYS LEFT", d.label)
    }

    @Test
    fun oneDayLeft_isSingular() {
        val d = show(date(2026, 11, 2), at(2026, 11, 1, 0, 1))
        assertEquals("1", d.number)
        assertEquals("DAY LEFT", d.label)
    }

    @Test
    fun today() {
        val d = show(date(2026, 11, 1), at(2026, 11, 1, 23, 59))
        assertEquals(CounterDisplay.Phase.TODAY, d.phase)
        assertEquals("TODAY", d.number)
        assertEquals("", d.label)
        assertEquals(0L, d.days)
    }

    @Test
    fun past() {
        val d = show(date(2026, 10, 1), at(2026, 11, 1))
        assertEquals(CounterDisplay.Phase.PAST, d.phase)
        assertEquals("31", d.number)
        assertEquals("DAYS AGO", d.label)
        assertEquals("DAY AGO", show(date(2026, 10, 31), at(2026, 11, 1)).label)
    }

    @Test
    fun dstWeek_countsCalendarDays() {
        // UK clocks go back on 2026-10-25: that day has 25 hours, still one calendar day.
        val d = show(date(2026, 10, 27), at(2026, 10, 24, 23, 30))
        assertEquals(3L, d.days)
        // Spring forward 2026-03-29 (23 hour day).
        assertEquals(2L, show(date(2026, 3, 30), at(2026, 3, 28, 0, 30)).days)
        // Time inside the spring gap (01:30 does not exist) still gives a positive countdown.
        val gap = show(date(2026, 3, 29), at(2026, 3, 28, 12), LocalTime.of(1, 30))
        // 12:00 GMT -> 02:30 BST (01:30 UTC) = 13h30m
        assertEquals(13L * 3600_000 + 30L * 60_000, gap.millisToTarget)
    }

    @Test
    fun leapDay() {
        assertEquals(366L, show(date(2028, 3, 1), at(2027, 3, 1)).days)
        assertEquals(1L, show(date(2028, 2, 29), at(2028, 2, 28)).days)
        assertEquals(1L, show(date(2028, 3, 1), at(2028, 2, 29)).days)
        assertEquals("TODAY", show(date(2028, 2, 29), at(2028, 2, 29, 8)).number)
    }

    @Test
    fun timeOfDayTargets() {
        // Calendar days, not 24h blocks: 40 minutes away but tomorrow.
        val d = show(date(2026, 11, 2), at(2026, 11, 1, 23, 50), LocalTime.of(0, 30))
        assertEquals("1", d.number)
        assertEquals(40L * 60_000, d.millisToTarget)
        // Same day, time still ahead.
        val t = show(date(2026, 11, 1), at(2026, 11, 1, 9), LocalTime.of(18, 0))
        assertEquals("TODAY", t.number)
        assertEquals(9L * 3600_000, t.millisToTarget)
        // Same day, time passed: still today, no clock.
        val p = show(date(2026, 11, 1), at(2026, 11, 1, 19), LocalTime.of(18, 0))
        assertEquals("TODAY", p.number)
        assertNull(p.millisToTarget)
        // No time: never a clock.
        assertNull(show(date(2026, 12, 1), at(2026, 11, 1)).millisToTarget)
    }

    @Test
    fun progress() {
        val created = date(2026, 1, 1)
        assertEquals(0f, CounterMath.progress(created, date(2026, 1, 11), created), 0.0001f)
        assertEquals(0.5f, CounterMath.progress(created, date(2026, 1, 11), date(2026, 1, 6)), 0.0001f)
        assertEquals(1f, CounterMath.progress(created, date(2026, 1, 11), date(2026, 2, 1)), 0.0001f)
        assertEquals(1f, CounterMath.progress(created, created, created), 0.0001f)
    }

    @Test
    fun nextMidnight_acrossDst() {
        val n = CounterMath.nextMidnight(at(2026, 10, 24, 22))
        assertEquals(ZonedDateTime.of(LocalDateTime.of(2026, 10, 25, 0, 0), zone), n)
    }
}
