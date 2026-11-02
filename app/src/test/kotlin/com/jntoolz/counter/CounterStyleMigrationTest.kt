package com.jntoolz.counter

import com.jntoolz.data.CardStyle
import com.jntoolz.ui.theme.NothingFont
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CounterStyleMigrationTest {
    @Test fun presetBecomesGradientStyleKeepingAngle() {
        val s = CounterStore.legacyStyle("RED", 135)
        assertTrue(s.gradient)
        assertEquals(CounterGradient.RED.startColor, s.gradientTop)
        assertEquals(CounterGradient.RED.endColor, s.color)
        assertEquals(100, s.intensity)
        assertEquals(135, s.angle)
        assertTrue(!s.glass)
    }

    @Test fun noPresetOrUnknownNameIsDefault() {
        assertEquals(CardStyle(), CounterStore.legacyStyle(null, 45))
        assertEquals(CardStyle(), CounterStore.legacyStyle("NONE", 45))
        assertEquals(CardStyle(), CounterStore.legacyStyle("SPARKLE", 45))
    }

    @Test fun lightThemesBecomeTheLightCardUnlessAColourWasPicked() {
        for (t in listOf(CounterTheme.WHITE, CounterTheme.YOU_SOFT)) {
            assertEquals(CardStyle(color = 0xFFF4F4F4.toInt()), CounterStore.migrateTheme(t, CardStyle()))
            val picked = CardStyle(color = 0xFF123456.toInt(), gradient = true)
            assertEquals(picked, CounterStore.migrateTheme(t, picked))
        }
        // A gradient on the auto colour keeps its gradient and gets the light card under it.
        val g = CounterStore.migrateTheme(CounterTheme.WHITE, CardStyle(gradient = true, angle = 90))
        assertEquals(CardStyle(color = 0xFFF4F4F4.toInt(), gradient = true, angle = 90), g)
    }

    @Test fun glassThemeBecomesGlassAtLevel50() {
        assertEquals(CardStyle(glass = true, glassLevel = 50), CounterStore.migrateTheme(CounterTheme.GLASS, CardStyle()))
        val already = CardStyle(glass = true, glassLevel = 80)
        assertEquals(already, CounterStore.migrateTheme(CounterTheme.GLASS, already))
    }

    @Test fun darkThemesChangeNothing() {
        val s = CardStyle(color = 0xFF202020.toInt(), gradient = true)
        for (t in listOf(CounterTheme.BLACK, CounterTheme.RED, CounterTheme.PROGRESS, CounterTheme.YOU)) {
            assertEquals(CardStyle(), CounterStore.migrateTheme(t, CardStyle()))
            assertEquals(s, CounterStore.migrateTheme(t, s))
        }
        // Removed theme names map through CounterTheme.of first.
        assertEquals(CardStyle(color = 0xFFF4F4F4.toInt()), CounterStore.migrateTheme(CounterTheme.of("PAPER"), CardStyle()))
        assertEquals(CardStyle(glass = true, glassLevel = 50), CounterStore.migrateTheme(CounterTheme.of("OUTLINE"), CardStyle()))
    }

    @Test fun onlyThemeAndNdot77FontsRemain() {
        assertEquals(listOf(null, NothingFont.NDOT_77), Counter.FONTS)
        assertNull(Counter.allowedFont(null))
        for (f in NothingFont.entries) {
            val expected = if (f.isDot) NothingFont.NDOT_77 else null
            assertEquals(f.name, expected, Counter.allowedFont(f))
        }
        // An old dot-faced theme's legacy Ndot 57 lands on Ndot 77.
        assertEquals(NothingFont.NDOT_77, Counter.allowedFont(CounterTheme.legacyFont("DOT", 1)))
    }

    @Test fun autoCardColourIsTheDarkCard() {
        assertEquals(0xFF161616.toInt(), CounterRenderer.autoCardColor())
        assertEquals(0xFF161616.toInt(), CounterRenderer.cardSpec(CardStyle()).bottom)
    }

    @Test fun everyPersistedPresetStillDecodes() {
        for (g in CounterGradient.entries) assertEquals(g, CounterGradient.of(g.name))
    }
}
