package com.jntoolz.sot

import org.junit.Assert.assertEquals
import org.junit.Test

class DrainTest {
    private val m = Stats.MIN
    private val now = 10 * Stats.HOUR

    private fun b(t: Long, level: Int, plug: Int = 0) = Store.B(t, level, plug, 0, 0, 0, 0)

    @Test
    fun steadyDrain() {
        // 1% every 5 min, last sample 10 min ago
        val l = (0..10).map { b(now - 70 * m + it * 5 * m, 80 - it) }
        assertEquals(5 * m, Drain.perPercent(l, now))
    }

    @Test
    fun chargingGapExcluded() {
        val l = listOf(
            b(now - 100 * m, 80), b(now - 90 * m, 79), b(now - 80 * m, 78), // 10 min per %
            b(now - 70 * m, 78, 1), b(now - 60 * m, 79, 1), b(now - 50 * m, 80, 1), b(now - 45 * m, 81, 1), // charging
            b(now - 40 * m, 81), b(now - 36 * m, 80), b(now - 32 * m, 79), // 4 min per %
        )
        // Pairs starting at the first sample or a plug change are part-percents and skipped:
        // drain 10 min + 4 min over 2%, charge 10 min + 5 min over 2%
        assertEquals(7 * m, Drain.perPercent(l, now))
        assertEquals(15 * m / 2, Drain.chargePerPercent(l, now))
    }

    @Test
    fun tooLittleData() {
        assertEquals(-1L, Drain.perPercent(emptyList(), now))
        assertEquals(-1L, Drain.perPercent(listOf(b(now - 10 * m, 50)), now))
        assertEquals(-1L, Drain.perPercent(listOf(b(now - 10 * m, 50), b(now - 5 * m, 49)), now))
        assertEquals(-1L, Drain.chargePerPercent(listOf(b(now - 10 * m, 50, 1), b(now - 5 * m, 51, 1)), now))
    }

    @Test
    fun samplesBeforeWindowIgnored() {
        val l = listOf(
            b(now - 300 * m, 90), b(now - 200 * m, 80), // slow, before the 2 h window
            b(now - 100 * m, 70), b(now - 90 * m, 69), b(now - 80 * m, 68), b(now - 70 * m, 67),
        )
        // the 80 -> 70 span starts before the window, so it is not counted
        assertEquals(10 * m, Drain.perPercent(l, now))
        // a shorter window drops the oldest in-window pair as well
        assertEquals(10 * m, Drain.perPercent(l, now, 95 * m))
    }

    @Test
    fun risingWhileUnpluggedIgnored() {
        val l = listOf(b(now - 60 * m, 50), b(now - 55 * m, 49), b(now - 50 * m, 51), b(now - 40 * m, 50), b(now - 30 * m, 49))
        // first pair is a part-percent, 49->51 skipped; then 10 min per 1% twice
        assertEquals(10 * m, Drain.perPercent(l, now))
    }

    @Test
    fun fullInWithTaper() {
        // charging from 50% at 5 min per %, now at 60%
        val l = (0..10).map { b(now - 50 * m + it * 5 * m, 50 + it, 1) }
        // 20 below 80 + 1.5 * 20 above = 50 effective percent
        assertEquals(50 * 5 * m, Drain.fullIn(l, now))
    }

    @Test
    fun fullInUsesRecentRate() {
        val l = listOf(
            b(now - 120 * m, 10, 1), b(now - 100 * m, 12, 1), // 10 min per %, old
            b(now - 20 * m, 13, 1), b(now - 15 * m, 14, 1), b(now - 10 * m, 15, 1), // 5 min per %
        )
        assertEquals((5 * m * (65 + 1.5 * 20)).toLong(), Drain.fullIn(l, now))
    }

    @Test
    fun fullInFallsBackToWholeCharge() {
        val l = listOf(b(now - 120 * m, 69, 1), b(now - 90 * m, 70, 1), b(now - 60 * m, 71, 1), b(now - 20 * m, 72, 1))
        // recent half hour has 0%, whole charge 2% in 70 min = 35 min per %
        assertEquals((35 * m * (8 + 1.5 * 20)).toLong(), Drain.fullIn(l, now))
    }

    @Test
    fun fullInUnknown() {
        assertEquals(-1L, Drain.fullIn(emptyList(), now))
        assertEquals(-1L, Drain.fullIn(listOf(b(now - 10 * m, 50)), now)) // not charging
        assertEquals(-1L, Drain.fullIn(listOf(b(now - 10 * m, 50, 1), b(now - 5 * m, 51, 1)), now)) // 1%
    }

    @Test
    fun shortFormat() {
        assertEquals("–", Drain.short(-1))
        assertEquals("30s", Drain.short(30_000))
        assertEquals("4m 30s", Drain.short(270_000))
        assertEquals("12m", Drain.short(12 * m))
    }
}
