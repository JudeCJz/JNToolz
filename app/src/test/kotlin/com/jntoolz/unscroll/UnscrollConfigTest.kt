package com.jntoolz.unscroll

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UnscrollConfigTest {
    @Test
    fun timedAppsCoverRequestedSet() {
        val want = setOf(
            "com.reddit.frontpage", "com.linkedin.android", "com.google.android.youtube", "com.instagram.android",
            "com.zhiliaoapp.musically", "com.ss.android.ugc.trill", "com.facebook.katana",
        )
        assertEquals(want, ShortsRules.TIMED)
        assertEquals(want, WarnApp.entries.flatMap { it.packages }.toSet())
    }

    @Test
    fun warnOnlyWhenEnabledAndAppOn() {
        val c = UnscrollConfig(mode = GuardMode.SOFT, warnApps = setOf(WarnApp.REDDIT))
        assertTrue(c.warns(ShortsRules.REDDIT))
        assertFalse(c.warns(ShortsRules.LINKEDIN))
        assertFalse(c.warns("com.other"))
        assertFalse(c.copy(mode = GuardMode.OFF).warns(ShortsRules.REDDIT))
    }

    @Test
    fun blocksPackageFollowsSwitches() {
        val c = UnscrollConfig(mode = GuardMode.HARD, blockShorts = false)
        assertFalse(c.blocksPackage(ShortsRules.YOUTUBE))
        assertTrue(c.blocksPackage(ShortsRules.INSTAGRAM))
        assertTrue(c.blocksPackage("com.ss.android.ugc.trill"))
        assertFalse(c.blocksPackage(ShortsRules.REDDIT))
        assertFalse(c.copy(mode = GuardMode.OFF).blocksPackage(ShortsRules.INSTAGRAM))
    }

    @Test
    fun switchesOnlyForInstalledApps() {
        val none = installedKinds { false }
        assertTrue(none.isEmpty())
        assertEquals(listOf(ShortKind.SHORTS), installedKinds { it == ShortsRules.YOUTUBE })
        assertEquals(listOf(ShortKind.REELS), installedKinds { it == ShortsRules.FACEBOOK })
        assertEquals(listOf(ShortKind.TIKTOK), installedKinds { it == "com.ss.android.ugc.trill" })
        assertEquals(setOf(WarnApp.REDDIT, WarnApp.LINKEDIN), WarnApp.installed { it == "com.reddit.frontpage" || it == "com.linkedin.android" }.toSet())
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
