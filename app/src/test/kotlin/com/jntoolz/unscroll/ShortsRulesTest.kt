package com.jntoolz.unscroll

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShortsRulesTest {
    private val yt = ShortsRules.YOUTUBE
    private val ig = ShortsRules.INSTAGRAM

    @Test
    fun youtubePlayerIdIsShorts() {
        val ids = setOf("com.google.android.youtube:id/reel_recycler")
        assertEquals(ShortKind.SHORTS, ShortsRules.classify(yt, ids, emptyList()))
    }

    @Test
    fun youtubeHomeIsNotShorts() {
        assertFalse(ShortsRules.isShortForm(yt, emptySet(), listOf("Home")))
    }

    @Test
    fun selectedShortsTabIsShorts() {
        assertTrue(ShortsRules.isShortForm(yt, emptySet(), listOf("Shorts")))
        assertTrue(ShortsRules.isShortForm(yt, emptySet(), listOf(" SHORTS tab ")))
    }

    @Test
    fun instagramClipsViewerAndReelsTab() {
        assertEquals(ShortKind.REELS, ShortsRules.classify(ig, setOf("com.instagram.android:id/clips_viewer_view_pager"), emptyList()))
        assertEquals(ShortKind.REELS, ShortsRules.classify(ig, emptySet(), listOf("Reels, tab 4 of 5")))
        assertNull(ShortsRules.classify(ig, emptySet(), listOf("Home")))
    }

    @Test
    fun idOfAnotherAppDoesNotMatch() {
        assertNull(ShortsRules.classify(ig, setOf("com.google.android.youtube:id/reel_recycler"), emptyList()))
    }

    @Test
    fun facebookReels() {
        assertEquals(ShortKind.REELS, ShortsRules.classify(ShortsRules.FACEBOOK, emptySet(), listOf("Reels")))
        assertNull(ShortsRules.classify(ShortsRules.FACEBOOK, emptySet(), listOf("Spotlight")))
    }

    @Test
    fun tiktokIsAlwaysShortForm() {
        for (p in ShortsRules.TIKTOK) assertEquals(ShortKind.TIKTOK, ShortsRules.classify(p, emptySet(), emptyList()))
    }

    @Test
    fun unknownAppIsNeverShortForm() {
        assertFalse(ShortsRules.isShortForm("com.other", setOf("x"), listOf("Shorts")))
    }

    @Test
    fun timedAppsIncludeGuardedAndExtras() {
        assertTrue(ShortsRules.REDDIT in ShortsRules.TIMED)
        assertTrue(ShortsRules.GUARDED.all { it in ShortsRules.TIMED })
    }
}
