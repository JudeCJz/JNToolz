package com.jntoolz.unscroll

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScrollSessionTest {
    private val min = 60_000L
    private val thr = 10 * min

    /** Scroll events every 30 s in [pkg] from [from] to [to]; returns the times a warning fired. */
    private fun run(s: ScrollSession, pkg: String, from: Long, to: Long): List<Long> {
        val fired = ArrayList<Long>()
        var t = from
        while (t <= to) {
            if (s.onScroll(pkg, t, thr)) fired += t
            t += 30_000L
        }
        return fired
    }

    @Test
    fun warnsAtTenAndTwentyMinutes() {
        val fired = run(ScrollSession(), "a", 0, 25 * min)
        assertEquals(listOf(10 * min, 20 * min), fired)
    }

    @Test
    fun noWarningBeforeThreshold() {
        assertTrue(run(ScrollSession(), "a", 0, 9 * min).isEmpty())
    }

    @Test
    fun longPauseResets() {
        val s = ScrollSession()
        run(s, "a", 0, 8 * min)
        assertFalse(s.onScroll("a", 8 * min + 2 * min, thr)) // 2 min gap: new session
        assertTrue(run(s, "a", 10 * min + 30_000L, 17 * min).isEmpty())
    }

    @Test
    fun switchingAppResets() {
        val s = ScrollSession()
        run(s, "a", 0, 9 * min)
        assertFalse(s.onScroll("b", 9 * min + 30_000L, thr))
        assertTrue(run(s, "b", 10 * min, 18 * min).isEmpty())
    }

    @Test
    fun minutesCountsSession() {
        val s = ScrollSession()
        run(s, "a", 0, 3 * min)
        assertEquals(3, s.minutes)
    }
}
