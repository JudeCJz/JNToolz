package com.jntoolz.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** The one text rule shared by Calendar, Counter and Battery: ink on a light surface, white on a dark one. */
class ThemeMathTextTest {
    private val ink = 0xFF111111.toInt()
    private val white = 0xFFFFFFFF.toInt()
    private val black = 0xFF000000.toInt()

    @Test fun plainCardsPickByLightness() {
        assertEquals(white, ThemeMath.textOn(CardStyle().spec(0xFF161616.toInt())))
        assertEquals(ink, ThemeMath.textOn(CardStyle().spec(0xFFF4F4F4.toInt())))
        assertEquals(ink, ThemeMath.textOn(CardStyle().spec(0xFFFFEB3B.toInt())))
        assertEquals(white, ThemeMath.textOn(CardStyle().spec(0xFF1565C0.toInt())))
    }

    @Test fun glassIsJudgedOverWhatIsUnderIt() {
        val glass = CardStyle(glass = true, glassLevel = 60).spec(0xFFF4F4F4.toInt())
        // 40% light grey over the default black wallpaper reads dark.
        assertEquals(white, ThemeMath.textOn(glass))
        assertEquals(ink, ThemeMath.textOn(glass, under = 0xFFFFFFFF.toInt()))
        // Fully clear glass is just what is under it.
        val clear = CardStyle(glass = true, glassLevel = 100).spec(0xFFFFFFFF.toInt())
        assertEquals(black, ThemeMath.surfaceOf(clear))
    }

    @Test fun gradientIsJudgedOnItsMiddle() {
        // Black to white: the middle is mid grey, dark enough for white text.
        val g = CardStyle(gradient = true, gradientTop = 0xFFFFFFFF.toInt(), intensity = 100).spec(black)
        assertEquals(ThemeMath.blend(black, 0xFFFFFFFF.toInt(), 0.5f), ThemeMath.surfaceOf(g))
        assertEquals(white, ThemeMath.textOn(g))
        // A light card with a light top stays ink.
        val light = CardStyle(gradient = true, intensity = 50).spec(0xFFE0E0E0.toInt())
        assertEquals(ink, ThemeMath.textOn(light))
    }

    @Test fun surfaceOfOpaquePlainIsItsColour() {
        assertEquals(0xFF336699.toInt(), ThemeMath.surfaceOf(CardStyle().spec(0xFF336699.toInt()), under = 0xFFFFFFFF.toInt()))
    }

    @Test fun calendarCardTextFollowsTheRule() {
        val t = ThemeSettings(false, "DOT", false, black, 0xFFF4F4F4.toInt(), "NONE")
        assertEquals(ink, ThemeMath.resolve(t, 0, 0, false, false).title)
        assertEquals(white, ThemeMath.resolve(t.copy(glass = true), 0, 0, false, false).title)
        assertEquals(white, ThemeMath.resolve(t.copy(cardColor = 0xFF202121.toInt()), 0, 0, false, false).title)
    }
}
