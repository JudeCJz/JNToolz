package com.jntoolz.unscroll

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UnscrollConfigTest {
    @Test
    fun timedAppsAreInTheCatalogue() {
        assertTrue(AddictiveApp.ALL_PACKAGES.containsAll(ShortsRules.TIMED))
    }

    @Test
    fun warnOnlyWhenEnabledAndAppOn() {
        val c = UnscrollConfig(mode = GuardMode.SOFT, onApps = setOf(AddictiveApp.REDDIT, AddictiveApp.TIKTOK))
        assertTrue(c.warns(ShortsRules.REDDIT))
        assertTrue(c.warns("com.zhiliaoapp.musically"))
        assertFalse(c.warns(ShortsRules.LINKEDIN))
        assertFalse(c.warns("com.other"))
        assertFalse(c.copy(mode = GuardMode.OFF).warns(ShortsRules.REDDIT))
    }

    @Test
    fun blocksPackageFollowsSwitchesAndGuard() {
        val c = UnscrollConfig(mode = GuardMode.HARD, onApps = AddictiveApp.entries.toSet() - AddictiveApp.YOUTUBE)
        assertFalse(c.blocksPackage(ShortsRules.YOUTUBE))
        assertTrue(c.blocksPackage(ShortsRules.INSTAGRAM))
        assertTrue(c.blocksPackage("com.instagram.lite"))
        assertTrue(c.blocksPackage("com.ss.android.ugc.trill"))
        assertTrue(c.blocksPackage("com.zhiliaoapp.musically.go"))
        assertTrue(c.blocksPackage("in.mohalla.video"))
        assertFalse(c.blocksPackage(ShortsRules.REDDIT)) // FEED apps only warn
        assertFalse(c.blocksPackage("com.other"))
        assertFalse(c.copy(mode = GuardMode.OFF).blocksPackage(ShortsRules.INSTAGRAM))
    }

    @Test
    fun youtubeOffMeansNoBlockAndNoWarn() {
        val c = UnscrollConfig(mode = GuardMode.SOFT, onApps = emptySet())
        assertFalse(c.blocksPackage(ShortsRules.YOUTUBE))
        assertFalse(c.warns(ShortsRules.YOUTUBE))
    }

    @Test
    fun aliasesShareTheMainAppsRules() {
        assertEquals("Shorts", ShortsRules.tabLabel("app.revanced.android.youtube"))
        assertEquals("Reels", ShortsRules.tabLabel("com.instagram.lite"))
        assertEquals("Reels", ShortsRules.tabLabel("com.facebook.lite"))
        assertEquals(
            ShortKind.SHORTS,
            ShortsRules.classify("app.revanced.android.youtube", setOf("app.revanced.android.youtube:id/reel_recycler"), emptyList())
        )
        assertTrue(ShortsRules.clickLooksShort("com.facebook.lite", null, "Reels"))
    }

    @Test
    fun clickDetection() {
        val yt = ShortsRules.YOUTUBE
        assertTrue(ShortsRules.clickLooksShort(yt, "com.google.android.youtube:id/reel_item", null))
        assertTrue(ShortsRules.clickLooksShort(yt, null, "Play Short: funny cat"))
        assertFalse(ShortsRules.clickLooksShort(yt, "com.google.android.youtube:id/thumbnail", "Home"))
        assertTrue(ShortsRules.clickLooksShort(ShortsRules.INSTAGRAM, null, "Reel by someone"))
        assertFalse(ShortsRules.clickLooksShort(ShortsRules.REDDIT, "reel", "reel"))
    }

    @Test
    fun limiterGivesUpThenRecovers() {
        val l = BlockLimiter(max = 3, windowMs = 6_000, coolMs = 10_000)
        assertTrue(l.allow(1_000))
        assertTrue(l.allow(2_000))
        assertTrue(l.allow(3_000))
        assertFalse(l.allow(4_000))
        assertFalse(l.allow(12_000)) // still cooling
        assertTrue(l.allow(14_500))
    }

    @Test
    fun escalationIsBackBackHomeThenStops() {
        assertEquals(Escalation.Step.BACK, Escalation.step(0))
        assertEquals(Escalation.Step.BACK, Escalation.step(1))
        assertEquals(Escalation.Step.HOME, Escalation.step(2))
        assertNull(Escalation.step(3))
    }

    @Test
    fun tabBoxSnap() {
        val w = 1080
        val h = 2400
        val tab = TabBox(432, 2200, 648, 2330)
        assertEquals(tab, TabBox.snap(tab, w, h))
        // clamped to the screen
        assertEquals(TabBox(0, 2200, 200, 2400), TabBox.snap(TabBox(-50, 2200, 200, 2500), w, h))
        // a feed header, a whole bar and an empty box are not tabs
        assertNull(TabBox.snap(TabBox(0, 1000, 200, 1100), w, h))
        assertNull(TabBox.snap(TabBox(0, 2200, 1080, 2330), w, h))
        assertNull(TabBox.snap(TabBox(10, 2200, 10, 2330), w, h))
        assertNotNull(TabBox.snap(TabBox(100, 50, 300, 150), w, h)) // top tab bar
    }
}
