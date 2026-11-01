package com.jntoolz.sot

import com.jntoolz.sot.Stats.Companion.HOUR
import com.jntoolz.sot.Stats.Companion.MIN
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class StatsTest {
    private fun b(t: Long, level: Int, plug: Int = 0) = Store.B(t, level, plug, 0, 0, 0, 0)
    private fun iv(a: Long, z: Long) = longArrayOf(a, z)
    private val eps = 1e-9

    // Charge to 100 by 1h, unplug at 2h, 10% per hour, screen on 30 min in each hour.
    private val dis = listOf(
        b(0, 50, 1), b(1 * HOUR, 100, 1), b(2 * HOUR, 100), b(3 * HOUR, 90), b(4 * HOUR, 80),
    )
    private val scr = listOf(iv(2 * HOUR, 2 * HOUR + 30 * MIN), iv(3 * HOUR, 3 * HOUR + 30 * MIN))

    @Test
    fun compute_dischargeSessionFromUnplug() {
        val s = Stats.compute(dis, scr, false, 4 * HOUR)
        assertFalse(s.empty)
        assertFalse(s.charging)
        assertFalse(s.fallback)
        assertEquals(2 * HOUR, s.start)
        assertEquals(4 * HOUR, s.end)
        assertEquals(100, s.startLevel)
        assertEquals(80, s.endLevel)
        assertEquals(80, s.level)
        assertEquals(HOUR, s.sot)
        assertEquals(HOUR, s.off)
        assertEquals(30 * MIN, s.longestOn)
        assertEquals(2, s.ons)
        assertEquals(HOUR, s.disOn)
        assertEquals(HOUR, s.disOff)
        assertEquals(10.0, s.usedOn, eps)
        assertEquals(10.0, s.usedOff, eps)
        assertEquals(20.0, s.used(), eps)
        assertEquals(10.0, s.rateOn(), eps)
        assertEquals(10.0, s.rateOff(), eps)
        assertEquals(10.0, s.rateAvg(), eps)
        assertEquals(8 * HOUR, s.leftMixed())
        assertEquals(8 * HOUR, s.leftOn())
        assertEquals(5 * HOUR, s.projectedSot())
        assertArrayEquals(longArrayOf(2 * HOUR, 3 * HOUR, 4 * HOUR, 4 * HOUR), s.gt)
        assertArrayEquals(intArrayOf(100, 90, 80, 80), s.gl)
        assertArrayEquals(booleanArrayOf(false, false, false, false), s.gc)
        assertArrayEquals(
            longArrayOf(2 * HOUR, 2 * HOUR + 30 * MIN, 3 * HOUR, 3 * HOUR + 30 * MIN), s.on,
        )
    }

    @Test
    fun compute_fullModeStartsAtUnplugAfterFull() {
        val s = Stats.compute(dis, scr, true, 4 * HOUR)
        assertTrue(s.fullMode)
        assertFalse(s.fallback)
        assertEquals(2 * HOUR, s.start)
        assertEquals(20.0, s.used(), eps)
    }

    @Test
    fun compute_chargingDescribesSessionBefore() {
        val b = dis + listOf(b(5 * HOUR, 70, 1), b(6 * HOUR, 85, 1))
        val now = 6 * HOUR + 30 * MIN
        val s = Stats.compute(b, scr, false, now)
        assertTrue(s.charging)
        assertEquals(85, s.level)
        assertEquals(5 * HOUR, s.chgStart)
        assertEquals(70, s.chgStartLevel)
        assertEquals(2 * HOUR, s.start)
        assertEquals(5 * HOUR, s.end)
        assertEquals(70, s.endLevel)
        assertEquals(10.0, s.usedOn, eps)
        assertEquals(20.0, s.usedOff, eps)
        assertEquals(-1, s.leftMixed())
        assertArrayEquals(longArrayOf(2 * HOUR, 3 * HOUR, 4 * HOUR, 5 * HOUR, 6 * HOUR, now), s.gt)
        assertArrayEquals(intArrayOf(100, 90, 80, 70, 85, 85), s.gl)
        assertArrayEquals(booleanArrayOf(false, false, false, true, true, true), s.gc)
    }

    @Test
    fun compute_fallbackWithoutUnplug() {
        val b = listOf(b(0, 90), b(HOUR, 80))
        val s = Stats.compute(b, emptyList(), false, HOUR)
        assertTrue(s.fallback)
        assertFalse(s.empty)
        assertEquals(0, s.start)
        assertEquals(90, s.startLevel)
        assertEquals(10.0, s.usedOff, eps)

        val f = Stats.compute(b, emptyList(), true, HOUR) // no full charge seen either
        assertTrue(f.fallback)
        assertEquals(0, f.start)
    }

    @Test
    fun compute_emptyCases() {
        assertTrue(Stats.compute(emptyList(), emptyList(), false, 1000).empty)
        val plugged = Stats.compute(listOf(b(0, 50, 1), b(HOUR, 60, 1)), emptyList(), false, 2 * HOUR)
        assertTrue(plugged.empty)
        assertTrue(plugged.charging)
        assertEquals(60, plugged.level)
    }

    private fun midnight(): Long {
        val c = Calendar.getInstance()
        c.set(2026, Calendar.JUNE, 10, 0, 0, 0)
        c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }

    @Test
    fun today_startsAtMidnight() {
        val mid = midnight()
        val b = listOf(b(mid - 2 * HOUR, 100), b(mid + HOUR, 95), b(mid + 3 * HOUR, 90))
        val screen = listOf(iv(mid - HOUR, mid + 30 * MIN))
        val s = Stats.today(b, screen, mid + 3 * HOUR)
        assertFalse(s.empty)
        assertFalse(s.fallback)
        assertEquals(mid, s.start)
        assertEquals(100, s.startLevel)
        assertEquals(30 * MIN, s.sot)
        assertEquals(2.5, s.usedOn, eps)
        assertEquals(7.5, s.usedOff, eps)
    }

    @Test
    fun day_usesOnlySamplesBeforeItsEnd() {
        val mid = midnight()
        val b = listOf(b(mid - 2 * HOUR, 100), b(mid + HOUR, 95))
        val screen = listOf(iv(mid - HOUR, mid + 30 * MIN))
        val s = Stats.day(b, screen, mid - 24 * HOUR, mid, mid + 2 * HOUR)
        assertEquals(mid - 2 * HOUR, s.start)
        assertEquals(mid, s.end)
        assertEquals(HOUR, s.sot)
        assertEquals(0.0, s.used(), eps)
    }

    @Test
    fun history_newestFirst() {
        val b = listOf(
            b(0, 50, 1), b(HOUR, 100, 1), b(2 * HOUR, 100), b(3 * HOUR, 80),
            b(4 * HOUR, 70, 1), b(5 * HOUR, 90, 1), b(6 * HOUR, 90), b(7 * HOUR, 85),
        )
        val h = Stats.history(b, 10)
        assertEquals(2, h.size)
        assertArrayEquals(longArrayOf(6 * HOUR, -1, 90, 85), h[0])
        assertArrayEquals(longArrayOf(2 * HOUR, 4 * HOUR, 100, 70), h[1])
        assertEquals(1, Stats.history(b, 1).size)
        assertTrue(Stats.history(emptyList(), 5).isEmpty())
    }

    @Test
    fun sotBetween_andOnTime() {
        val screen = listOf(iv(0, 10), iv(20, -1))
        assertEquals(15, Stats.sotBetween(screen, 5, 100, 30))
        assertEquals(10, Stats.onTime(longArrayOf(0, 10, 20, 30), 5, 25))
        assertEquals(0, Stats.onTime(longArrayOf(0, 10), 10, 20))
    }

    @Test
    fun storeIntervals() {
        val ev = listOf(longArrayOf(1, 1), longArrayOf(2, 1), longArrayOf(3, 0), longArrayOf(4, 0), longArrayOf(5, 1))
        val out = Store.intervals(ev)
        assertEquals(2, out.size)
        assertArrayEquals(longArrayOf(1, 3), out[0])
        assertArrayEquals(longArrayOf(5, -1), out[1])
        assertTrue(Store.intervals(emptyList()).isEmpty())
    }

    @Test
    fun formatting() {
        assertEquals("–", Stats.dur(-1))
        assertEquals("0m", Stats.dur(0))
        assertEquals("0m", Stats.dur(59_999))
        assertEquals("1h 30m", Stats.dur(90 * MIN))
        assertEquals("1d 1h", Stats.dur(25 * HOUR))
        assertEquals("7.5%", Stats.pct(7.5))
        assertEquals("12%", Stats.pct(12.4))
        assertEquals("0%", Stats.pct(0.0))
    }
}
