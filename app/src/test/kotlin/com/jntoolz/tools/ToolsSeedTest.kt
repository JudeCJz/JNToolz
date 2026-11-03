package com.jntoolz.tools

import org.junit.Assert.assertEquals
import org.junit.Test

class ToolsSeedTest {
    @Test
    fun freshInstallWritesNothing() {
        assertEquals(Tools.Seed(false, false), Tools.seedDecision(false, false, false))
    }

    @Test
    fun updateWithUnsetSwitchesKeepsOldOnDefault() {
        assertEquals(Tools.Seed(true, true), Tools.seedDecision(true, false, false))
    }

    @Test
    fun updateKeepsSwitchesThatWereWritten() {
        assertEquals(Tools.Seed(false, true), Tools.seedDecision(true, true, false))
        assertEquals(Tools.Seed(true, false), Tools.seedDecision(true, false, true))
        assertEquals(Tools.Seed(false, false), Tools.seedDecision(true, true, true))
    }
}
