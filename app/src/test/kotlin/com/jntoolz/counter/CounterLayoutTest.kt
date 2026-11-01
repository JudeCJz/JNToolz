package com.jntoolz.counter

import com.jntoolz.ui.theme.NothingFont
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CounterLayoutTest {

    @Test
    fun squareSizes() {
        assertEquals(CounterLayout.SQUARE, CounterLayouts.forSize(150, 150))
        assertEquals(CounterLayout.SQUARE, CounterLayouts.forSize(200, 180))
        // Exactly 1.25 x width is not yet tall.
        assertEquals(CounterLayout.SQUARE, CounterLayouts.forSize(120, 150))
    }

    @Test
    fun tallSizesFallBackToSquare() {
        assertEquals(CounterLayout.SQUARE, CounterLayouts.forSize(90, 180))
        assertEquals(CounterLayout.SQUARE, CounterLayouts.forSize(150, 230))
        assertEquals(CounterLayout.SQUARE, CounterLayouts.forSize(50, 80))
        assertEquals(CounterLayout.SQUARE, CounterLayouts.forSize(120, 151))
    }

    @Test
    fun wideSizes() {
        assertEquals(CounterLayout.WIDE, CounterLayouts.forSize(300, 70))
        // Short widgets are one row even when not very wide.
        assertEquals(CounterLayout.WIDE, CounterLayouts.forSize(110, 99))
        // Wider than 2.2 x height, even when tall enough.
        assertEquals(CounterLayout.WIDE, CounterLayouts.forSize(400, 150))
        assertEquals(CounterLayout.SQUARE, CounterLayouts.forSize(330, 150))
    }

    @Test
    fun shapeDefaultsPickTheirOwnLayout() {
        assertEquals(CounterLayout.SQUARE, CounterShape.SQUARE.size.let { CounterLayouts.forSize(it.w, it.h) })
        assertEquals(CounterLayout.WIDE, CounterShape.WIDE.size.let { CounterLayouts.forSize(it.w, it.h) })
    }

    @Test
    fun clockSlots() {
        assertEquals(ClockSlot.BOTTOM, CounterLayouts.clockSlot(CounterLayout.SQUARE, 150, 150))
        assertEquals(ClockSlot.END, CounterLayouts.clockSlot(CounterLayout.WIDE, 300, 70))
        // Too small: the clock is hidden.
        assertNull(CounterLayouts.clockSlot(CounterLayout.WIDE, 300, 50))
        assertNull(CounterLayouts.clockSlot(CounterLayout.WIDE, 150, 60))
        assertNull(CounterLayouts.clockSlot(CounterLayout.SQUARE, 110, 110))
    }

    @Test
    fun clockSlotsUseTheLayoutPadding() {
        assertEquals(CounterLayouts.padDp(CounterLayout.SQUARE).toInt(), ClockSlot.BOTTOM.marginHDp)
        assertEquals(CounterLayouts.padDp(CounterLayout.WIDE).toInt(), ClockSlot.END.marginHDp)
    }

    @Test
    fun titleWrapsOnlyWhereThereIsHeight() {
        assertEquals(1, CounterLayouts.titleLines(CounterLayout.WIDE, 150))
        assertEquals(2, CounterLayouts.titleLines(CounterLayout.SQUARE, 150))
        assertEquals(1, CounterLayouts.titleLines(CounterLayout.SQUARE, 120))
    }

    @Test
    fun widgetSizeFallsBackAndClamps() {
        val fb = CounterShape.SQUARE.size
        assertEquals(SizeDp(150, 150), CounterLayouts.widgetSize(0, 0, fb))
        assertEquals(SizeDp(250, 110), CounterLayouts.widgetSize(250, 110, fb))
        assertEquals(SizeDp(40, 40), CounterLayouts.widgetSize(20, 10, fb))
    }

    @Test
    fun fitScaleNeverGrowsOrStretches() {
        assertEquals(1f, CounterLayouts.fitScale(SizeDp(150, 150), 400f), 0f)
        assertEquals(0.5f, CounterLayouts.fitScale(SizeDp(300, 70), 150f), 1e-6f)
        assertEquals(0.4f, CounterLayouts.fitScale(SizeDp(90, 180), 400f, 72f), 1e-6f)
    }

    @Test
    fun oldDataWithoutShapeOrFontParsesToDefaults() {
        // CounterStore.fromJson passes optString(...).takeIf { it.isNotEmpty() }: null for missing keys.
        assertEquals(CounterShape.SQUARE, CounterShape.of(null))
        assertEquals(CounterShape.SQUARE, CounterShape.of("BOGUS"))
        assertEquals(CounterShape.SQUARE, CounterShape.of("TALL"))
        assertEquals(NothingFont.NDOT_57, NothingFont.of(null))
        assertEquals(NothingFont.LETTERA, NothingFont.of("LETTERA"))
    }

    @Test
    fun currentThemeNamesRoundTrip() {
        for (t in CounterTheme.entries) assertEquals(t, CounterTheme.of(t.name))
        assertEquals(CounterTheme.BLACK, CounterTheme.of(null))
        assertEquals(CounterTheme.BLACK, CounterTheme.of(""))
        assertEquals(CounterTheme.BLACK, CounterTheme.of("BOGUS"))
    }

    @Test
    fun removedThemesMapToTheirClosestTheme() {
        val expected = mapOf(
            "DOT" to CounterTheme.BLACK, "MONO" to CounterTheme.BLACK, "LIGHT" to CounterTheme.BLACK,
            "SPLIT" to CounterTheme.BLACK, "STACK" to CounterTheme.BLACK, "CAPSULE" to CounterTheme.BLACK,
            "MATRIX" to CounterTheme.BLACK, "PAPER" to CounterTheme.WHITE, "OUTLINE" to CounterTheme.GLASS,
            "RING" to CounterTheme.PROGRESS, "YOU_DOT" to CounterTheme.YOU,
            "RED" to CounterTheme.RED, "GLASS" to CounterTheme.GLASS, "PROGRESS" to CounterTheme.PROGRESS,
            "YOU" to CounterTheme.YOU, "YOU_SOFT" to CounterTheme.YOU_SOFT,
        )
        for ((old, new) in expected) assertEquals(old, new, CounterTheme.of(old))
    }

    @Test
    fun oldDotThemesKeepDotDigitsOnce() {
        for (old in listOf("DOT", "RED", "PROGRESS", "GLASS", "STACK", "MATRIX", "YOU_DOT")) {
            assertEquals(old, NothingFont.NDOT_57, CounterTheme.legacyFont(old, 1))
            // Saved by this version: "Theme" stays "Theme".
            assertNull(old, CounterTheme.legacyFont(old, 2))
        }
        for (old in listOf("MONO", "PAPER", "LIGHT", "RING", "YOU", "YOU_SOFT", "SPLIT", "CAPSULE", "OUTLINE", null)) {
            assertNull(CounterTheme.legacyFont(old, 1))
        }
    }
}
