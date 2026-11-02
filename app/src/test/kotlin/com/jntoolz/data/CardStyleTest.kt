package com.jntoolz.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CardStyleTest {
    @Test fun snapsWithinSixDegreesOfMultiplesOf45() {
        assertEquals(0, CardMath.snapAngle(4))
        assertEquals(0, CardMath.snapAngle(356))
        assertEquals(0, CardMath.snapAngle(360))
        assertEquals(45, CardMath.snapAngle(40))
        assertEquals(45, CardMath.snapAngle(51))
        assertEquals(90, CardMath.snapAngle(85))
        assertEquals(315, CardMath.snapAngle(320))
        assertEquals(270, CardMath.snapAngle(-85))
    }

    @Test fun doesNotSnapOutsideRange() {
        assertEquals(7, CardMath.snapAngle(7))
        assertEquals(38, CardMath.snapAngle(38))
        assertEquals(52, CardMath.snapAngle(52))
        assertEquals(353, CardMath.snapAngle(353))
    }

    @Test fun defaultStyleIsPlainAndUnchanged() {
        val s = CardStyle().spec(0xFF202121.toInt())
        assertTrue(s.isPlain)
        assertEquals(0xFF202121.toInt(), s.bottom)
        assertTrue(CardStyle().isDefault)
    }

    @Test fun gradientMixesTopByIntensity() {
        val s = CardStyle(gradient = true, gradientTop = 0xFFFFFFFF.toInt(), intensity = 100).spec(0xFF000000.toInt())
        assertEquals(0xFFFFFFFF.toInt(), s.top)
        assertEquals(0xFF000000.toInt(), s.bottom)
        assertFalse(s.isPlain)
    }

    @Test fun glassLowersFillAlphaAndKeepsColour() {
        val s = CardStyle(glass = true).spec(0xFF102030.toInt())
        // Default level 60: a 40% fill, the old card glass.
        assertEquals(CardStyle.DEFAULT_GLASS_LEVEL, CardStyle().glassLevel)
        assertEquals(102, ThemeMath.alpha(s.bottom))
        assertEquals(0x102030, s.bottom and 0xFFFFFF)
        assertTrue(s.glass)
        assertFalse(s.isPlain)
    }

    @Test fun calendarPresetMigratesToGradient() {
        val s = ThemeMath.migratePreset("RUBY", 90)!!
        assertTrue(s.gradient)
        assertEquals(0xFF361717.toInt(), s.gradientTop)
        assertEquals(0xFF180A0A.toInt(), s.color)
        assertEquals(100, s.intensity)
        assertEquals(90, s.angle)
        assertNull(ThemeMath.migratePreset("NONE", 90))
        // The result is the old preset look: start colour flowing to end colour.
        val spec = s.spec(s.color)
        assertEquals(0xFF361717.toInt(), spec.top)
        assertEquals(0xFF180A0A.toInt(), spec.bottom)
    }

    @Test fun glassLevelScalesFillAlpha() {
        fun a(level: Int, base: Int = 0xFF000000.toInt()) = ThemeMath.alpha(CardStyle(glass = true, glassLevel = level).spec(base).bottom)
        assertEquals(255, a(0))
        assertEquals(128, a(50))
        assertEquals(102, a(60))
        assertEquals(0, a(100))
        // Relative to the colour's own alpha, and out-of-range levels are clamped.
        assertEquals(64, a(50, 0x80000000.toInt()))
        assertEquals(0, a(150))
        assertEquals(255, a(-20))
        // Level only matters with glass on; a gradient keeps the same alpha at both ends.
        assertEquals(255, ThemeMath.alpha(CardStyle(glassLevel = 90).spec(0xFF000000.toInt()).bottom))
        val g = CardStyle(gradient = true, glass = true, glassLevel = 50).spec(0xFF000000.toInt())
        assertEquals(ThemeMath.alpha(g.top), ThemeMath.alpha(g.bottom))
    }

    @Test fun glassStaysNonDefault() {
        assertFalse(CardStyle(glass = true).isDefault)
        assertTrue(CardStyle(glassLevel = 10).isDefault)
    }

    @Test fun calendarBackgroundStyleIsBlackWithOptionalGlass() {
        val t = ThemeSettings(false, "DOT", true, 0xFF000000.toInt(), 0xFF202121.toInt(), "NONE", bgGlass = true, bgGlassLevel = 30)
        assertEquals(CardStyle(color = 0xFF000000.toInt(), glass = true, glassLevel = 30), t.bgStyle)
        val spec = t.bgStyle.spec(t.bgStyle.color)
        assertTrue(spec.glass)
        assertEquals(179, ThemeMath.alpha(spec.bottom))
        assertTrue(t.copy(bgGlass = false).bgStyle.spec(0xFF000000.toInt()).isPlain)
    }

    @Test fun cardGlassLevelRoundTripsThroughThemeSettings() {
        val t = ThemeSettings(false, "DOT", true, 0xFF000000.toInt(), 0xFF202121.toInt(), "NONE")
        val s = CardStyle(color = 0xFF334455.toInt(), glass = true, glassLevel = 25)
        assertEquals(s, t.withCardStyle(s).cardStyle)
        assertEquals(25, t.withCardStyle(s).glassLevel)
    }
}
