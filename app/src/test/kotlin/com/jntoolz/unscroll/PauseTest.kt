package com.jntoolz.unscroll

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PauseTest {
    private val until = 10_000L

    @Test
    fun softPausedUntilExpiry() {
        val c = UnscrollConfig(mode = GuardMode.SOFT, pausedUntil = until)
        assertTrue(c.pausedAt(until - 1))
        assertFalse(c.activeAt(until - 1))
        assertFalse(c.blocksPackage(ShortsRules.YOUTUBE, until - 1))
        assertFalse(c.warns(ShortsRules.REDDIT, until - 1))
        assertFalse(c.pausedAt(until))
        assertTrue(c.activeAt(until))
        assertTrue(c.blocksPackage(ShortsRules.YOUTUBE, until))
    }

    @Test
    fun hardAndOffNeverPause() {
        for (m in listOf(GuardMode.HARD, GuardMode.OFF)) {
            assertFalse(UnscrollConfig(mode = m, pausedUntil = until).pausedAt(0))
        }
        assertTrue(UnscrollConfig(mode = GuardMode.HARD, pausedUntil = until).activeAt(0))
        assertFalse(UnscrollConfig(mode = GuardMode.OFF, pausedUntil = until).activeAt(0))
    }

    @Test
    fun noPauseByDefault() {
        assertFalse(UnscrollConfig(mode = GuardMode.SOFT).pausedAt(0))
    }
}
