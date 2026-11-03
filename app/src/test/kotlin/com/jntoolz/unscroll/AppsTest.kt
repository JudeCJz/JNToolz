package com.jntoolz.unscroll

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppsTest {
    private fun readFile(vararg candidates: String): String =
        candidates.map { File(it) }.first { it.exists() }.readText()

    @Test
    fun noPackageBelongsToTwoApps() {
        val all = AddictiveApp.entries.flatMap { it.packages }
        assertEquals(all.size, all.toSet().size)
        assertEquals(all.toSet(), AddictiveApp.ALL_PACKAGES)
    }

    @Test
    fun gateWatchesTheCatalogue() {
        assertEquals(AddictiveApp.ALL_PACKAGES, AppGate.WATCHED)
        assertTrue(AppGate.WATCHED.containsAll(ShortsRules.TIMED))
    }

    @Test
    fun serviceXmlAndManifestMatchTheCatalogue() {
        val xml = readFile("src/main/res/xml/unscroll_service.xml", "app/src/main/res/xml/unscroll_service.xml")
        val names = Regex("android:packageNames=\"([^\"]*)\"").find(xml)!!.groupValues[1].split(',').toSet()
        assertEquals(AddictiveApp.ALL_PACKAGES, names)

        val manifest = readFile("src/main/AndroidManifest.xml", "app/src/main/AndroidManifest.xml")
        val queries = Regex("<package android:name=\"([^\"]+)\"").findAll(manifest).map { it.groupValues[1] }.toSet()
        assertTrue(queries.containsAll(AddictiveApp.ALL_PACKAGES))
    }

    @Test
    fun guardsMatchTheBrief() {
        assertEquals(Guard.TABS, AddictiveApp.of("com.google.android.youtube")!!.guard)
        assertEquals(Guard.TABS, AddictiveApp.of("app.revanced.android.youtube")!!.guard)
        assertEquals(Guard.WHOLE, AddictiveApp.of("com.zhiliaoapp.musically.go")!!.guard)
        assertEquals(Guard.WHOLE, AddictiveApp.of("com.ss.android.ugc.trill")!!.guard)
        assertEquals(Guard.FEED, AddictiveApp.of("com.reddit.frontpage")!!.guard)
        assertNull(AddictiveApp.of("com.other"))
    }

    @Test
    fun installedListSortedByGuardThenName() {
        val got = AddictiveApp.installed { true }
        assertEquals(AddictiveApp.entries.size, got.size)
        assertEquals(got.sortedWith(compareBy({ it.guard.ordinal }, { it.label.lowercase() })), got)
        assertEquals(AddictiveApp.FACEBOOK, got.first())
        assertTrue(AddictiveApp.installed { false }.isEmpty())
        assertEquals(listOf(AddictiveApp.REDDIT), AddictiveApp.installed { it == "com.reddit.frontpage" })
    }

    @Test
    fun keysAreUnique() {
        assertEquals(AddictiveApp.entries.size, AddictiveApp.entries.map { it.key }.toSet().size)
        assertEquals("app_youtube", AddictiveApp.YOUTUBE.key)
    }

    private fun old(vararg kv: Pair<String, Boolean>): (String, Boolean) -> Boolean {
        val m = mapOf(*kv)
        return { k, d -> m[k] ?: d }
    }

    @Test
    fun migrationKeepsOldChoices() {
        // Nothing stored: everything on.
        for (a in AddictiveApp.entries) assertTrue(a.legacyOn(old()))
        // YouTube: on if either old flag was on.
        assertFalse(AddictiveApp.YOUTUBE.legacyOn(old("block_shorts" to false, "warn_youtube" to false)))
        assertTrue(AddictiveApp.YOUTUBE.legacyOn(old("block_shorts" to false, "warn_youtube" to true)))
        assertTrue(AddictiveApp.YOUTUBE.legacyOn(old("block_shorts" to true, "warn_youtube" to false)))
        // The single old warning switch is the default of the per-app ones.
        assertFalse(AddictiveApp.YOUTUBE.legacyOn(old("block_shorts" to false, "scroll_warning" to false)))
        // Instagram and Facebook use block_reels or their own warn key.
        assertFalse(AddictiveApp.INSTAGRAM.legacyOn(old("block_reels" to false, "warn_instagram" to false)))
        assertTrue(AddictiveApp.FACEBOOK.legacyOn(old("block_reels" to false, "warn_facebook" to true)))
        // TikTok follows block_tiktok.
        assertFalse(AddictiveApp.TIKTOK.legacyOn(old("block_tiktok" to false)))
        // Reddit and LinkedIn follow their warn keys.
        assertFalse(AddictiveApp.REDDIT.legacyOn(old("warn_reddit" to false)))
        assertTrue(AddictiveApp.LINKEDIN.legacyOn(old("warn_reddit" to false)))
        // Apps that never had a switch are on.
        assertTrue(AddictiveApp.X.legacyOn(old("warn_x" to false, "block_shorts" to false)))
    }
}
