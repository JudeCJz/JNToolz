package com.jntoolz.unscroll

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppGateTest {
    @Test
    fun watchedCoversShortsRulesAndExtras() {
        assertTrue(AppGate.WATCHED.containsAll(ShortsRules.TIMED))
        assertTrue("com.snapchat.android" in AppGate.WATCHED)
        assertTrue("com.zhiliaoapp.musically.go" in AppGate.WATCHED)
        assertTrue("org.joinmastodon.android" in AppGate.WATCHED)
    }

    @Test
    fun enablesOnlyForWatchedAppWhileBlocking() {
        assertTrue(AppGate.shouldEnable(ShortsRules.YOUTUBE, GuardMode.SOFT, false))
        assertTrue(AppGate.shouldEnable(ShortsRules.INSTAGRAM, GuardMode.HARD, false))
        assertFalse(AppGate.shouldEnable(ShortsRules.YOUTUBE, GuardMode.OFF, false))
        assertFalse(AppGate.shouldEnable(ShortsRules.YOUTUBE, GuardMode.SOFT, true))
        assertFalse(AppGate.shouldEnable("in.org.npci.upiapp", GuardMode.HARD, false))
        assertFalse(AppGate.shouldEnable(null, GuardMode.HARD, false))
    }
}
