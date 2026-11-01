package com.jntoolz.sot

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class UsageTest {
    private fun iv(a: Long, z: Long) = longArrayOf(a, z)

    @Test
    fun resumeAt_startsBeforeAnIntervalThatIsStillOpen() {
        val old = listOf(iv(100, 200), iv(300, 400), iv(500, -1))
        assertEquals(500, Usage.resumeAt(old, 900))
    }

    @Test
    fun resumeAt_keepsSinceWhenEverythingEndedBefore() {
        val old = listOf(iv(100, 200), iv(300, 400))
        assertEquals(900, Usage.resumeAt(old, 900))
        assertEquals(900, Usage.resumeAt(emptyList(), 900))
    }

    @Test
    fun resumeAt_walksBackOverIntervalsTouchingTheStart() {
        // 300-400 ends after since; 200-300 ends exactly where that one starts, so it is re-read too.
        val old = listOf(iv(100, 150), iv(200, 300), iv(300, 400))
        assertEquals(200, Usage.resumeAt(old, 350))
    }

    @Test
    fun merge_keepsClosedIntervalsBeforeTheQueryAndAppendsFresh() {
        val old = listOf(iv(100, 200), iv(300, 400), iv(500, -1))
        val from = Usage.resumeAt(old, 900)
        val fresh = listOf(iv(500, 950), iv(1000, -1)) // the open one closed, a new one opened
        val m = Usage.merge(old, fresh, from)
        assertEquals(4, m.size)
        assertArrayEquals(iv(100, 200), m[0])
        assertArrayEquals(iv(300, 400), m[1])
        assertArrayEquals(iv(500, 950), m[2])
        assertArrayEquals(iv(1000, -1), m[3])
    }

    @Test
    fun merge_sameAsAWholeQueryWhenNothingChanged() {
        val old = listOf(iv(100, 200), iv(500, -1))
        val from = Usage.resumeAt(old, 900)
        val m = Usage.merge(old, listOf(iv(500, -1)), from)
        assertEquals(2, m.size)
        assertArrayEquals(iv(500, -1), m[1])
    }
}
