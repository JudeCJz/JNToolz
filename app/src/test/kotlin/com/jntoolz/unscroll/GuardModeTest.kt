package com.jntoolz.unscroll

import com.jntoolz.unscroll.GuardMode.Gate
import com.jntoolz.unscroll.GuardMode.HARD
import com.jntoolz.unscroll.GuardMode.OFF
import com.jntoolz.unscroll.GuardMode.SOFT
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GuardModeTest {
    @Test
    fun softAndHardBothBlock() {
        assertFalse(OFF.blocking)
        assertTrue(SOFT.blocking)
        assertTrue(HARD.blocking)
        assertTrue(UnscrollConfig(mode = SOFT).blocksPackage(ShortsRules.INSTAGRAM))
        assertTrue(UnscrollConfig(mode = HARD).blocksPackage(ShortsRules.INSTAGRAM))
        assertFalse(UnscrollConfig(mode = OFF).blocksPackage(ShortsRules.INSTAGRAM))
    }

    @Test
    fun leavingHardNeedsUnlock() {
        assertEquals(Gate.UNLOCK, HARD.gateTo(SOFT))
        assertEquals(Gate.UNLOCK, HARD.gateTo(OFF))
        assertFalse(HARD.canSwitch(SOFT))
        assertFalse(HARD.canSwitch(OFF))
        assertTrue(HARD.canSwitch(HARD))
    }

    @Test
    fun goingHardIsConfirmed() {
        assertEquals(Gate.CONFIRM, OFF.gateTo(HARD))
        assertEquals(Gate.CONFIRM, SOFT.gateTo(HARD))
    }

    @Test
    fun everythingElseIsInstant() {
        for ((from, to) in listOf(OFF to SOFT, SOFT to OFF, OFF to OFF, SOFT to SOFT)) {
            assertEquals("$from -> $to", Gate.NONE, from.gateTo(to))
            assertTrue(from.canSwitch(to))
        }
    }

    @Test
    fun restoreMigratesOldOnOffFlag() {
        assertEquals(OFF, GuardMode.restore(null, legacyEnabled = false))
        assertEquals(SOFT, GuardMode.restore(null, legacyEnabled = true))
        assertEquals(HARD, GuardMode.restore("HARD", legacyEnabled = true))
        assertEquals(OFF, GuardMode.restore("OFF", legacyEnabled = true))
        assertEquals(SOFT, GuardMode.restore("SOFT", legacyEnabled = false))
        assertEquals(SOFT, GuardMode.restore("bogus", legacyEnabled = true))
        assertEquals(OFF, GuardMode.restore("bogus", legacyEnabled = false))
    }
}
