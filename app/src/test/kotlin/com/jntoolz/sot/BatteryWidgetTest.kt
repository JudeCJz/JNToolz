package com.jntoolz.sot

import com.jntoolz.sot.widget.Background
import com.jntoolz.sot.widget.BatteryWidgetConfig
import com.jntoolz.sot.widget.BatteryWidgetModel
import com.jntoolz.sot.widget.BatteryWidgetRenderer
import com.jntoolz.data.CardStyle
import com.jntoolz.sot.widget.Range
import com.jntoolz.sot.widget.RedrawThrottle
import com.jntoolz.sot.widget.Value
import com.jntoolz.sot.widget.WidgetData
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BatteryWidgetTest {
    private val H = Stats.HOUR
    private val now = 1_800_000_000_000L
    private fun b(t: Long, level: Int, plug: Int = 0) = Store.B(t, level, plug, 2, 300, 4000, 3_000_000)

    @Test
    fun config_roundTripsAndKeepsDefaultsForJunk() {
        val c = BatteryWidgetConfig(range = Range.SINCE_UNPLUG, big = Value.LEFT, small = Value.TEMP, label = false, bg = Background.WALLPAPER)
        assertEquals(c, BatteryWidgetConfig.decode(c.encode()))
        assertEquals(BatteryWidgetConfig(), BatteryWidgetConfig.decode(null))
        assertEquals(BatteryWidgetConfig(), BatteryWidgetConfig.decode("garbage;r=NOPE;x"))
        assertEquals(Range.H6, BatteryWidgetConfig.decode("r=H6").range)
    }

    @Test
    fun config_oldSavesMapRemovedOptionsToDefaults() {
        // A save from before the redesign: 12H/TODAY ranges, HEALTH/CYCLES/NONE values, style switches and a font.
        val old = "r=TODAY;b=HEALTH;s=CYCLES;l=0;m=0;h=0;d=0;g=0;k=CLEAR;f=LETTERA"
        assertEquals(BatteryWidgetConfig(range = Range.TODAY, label = false, bg = Background.CLEAR), BatteryWidgetConfig.decode(old))
        assertEquals(Range.H24, BatteryWidgetConfig.decode("r=H12").range)
        // NONE is no longer a big value; BATTERY is no longer a small one (it duplicated the big %).
        assertEquals(Value.BATTERY, BatteryWidgetConfig.decode("b=NONE").big)
        assertEquals(Value.SOT, BatteryWidgetConfig.decode("s=BATTERY").small)
        assertEquals(Value.NONE, BatteryWidgetConfig.decode("s=NONE").small)
        assertEquals(Value.LEFT, BatteryWidgetConfig.decode("b=LEFT;s=SOT").big)
        // The new encoding carries only the kept keys.
        assertEquals("r=H24;b=BATTERY;s=SOT;l=1;k=BLACK", BatteryWidgetConfig().encode())
        // Old values still load, junk falls back
        assertEquals(Background.WALLPAPER, BatteryWidgetConfig.decode("k=WALLPAPER").bg)
        assertEquals(Background.CLEAR, BatteryWidgetConfig.decode("k=CLEAR").bg)
        assertEquals(Background.BLACK, BatteryWidgetConfig.decode("k=FROST").bg)
    }

    @Test
    fun config_legacyGlassBecomesColourBackgroundWithGlassStyle() {
        val c = BatteryWidgetConfig.decode("r=H24;b=BATTERY;s=SOT;l=1;k=GLASS")
        assertEquals(Background.BLACK, c.bg)
        assertEquals(CardStyle(color = 0xFF000000.toInt(), glass = true), c.style)
        // It is saved in the new form and never written back as GLASS.
        assertEquals("r=H24;b=BATTERY;s=SOT;l=1;k=BLACK;sl=1", c.encode())
        assertEquals(c, BatteryWidgetConfig.decode(c.encode()))
        // A saved glass level survives the conversion.
        assertEquals(30, BatteryWidgetConfig.decode("k=GLASS;sv=30").style.glassLevel)
        assertEquals(listOf(Background.BLACK, Background.CLEAR, Background.WALLPAPER), Background.CHOICES)
        assertEquals("COLOR", Background.BLACK.label)
    }

    @Test
    fun config_styleRoundTripsAndMissingKeysAreDefaults() {
        val style = CardStyle(
            color = 0xCC2266AA.toInt(), gradient = true, gradientTop = 0xFFFFEEDD.toInt(),
            intensity = 70, angle = 135, glass = true, glassLevel = 35,
        )
        val c = BatteryWidgetConfig(range = Range.H6, bg = Background.BLACK, style = style)
        assertEquals(c, BatteryWidgetConfig.decode(c.encode()))
        assertEquals(
            "r=H6;b=BATTERY;s=SOT;l=1;k=BLACK;sc=CC2266AA;sg=1;st=FFFFEEDD;si=70;sa=135;sl=1;sv=35",
            c.encode()
        )
        // Default style: black, nothing extra written.
        assertEquals(CardStyle(color = 0xFF000000.toInt()), BatteryWidgetConfig().style)
        assertEquals(BatteryWidgetConfig().style, BatteryWidgetConfig.decode("k=BLACK").style)
        // Partial and junk style keys fall back per key.
        val p = BatteryWidgetConfig.decode("k=BLACK;sg=1;si=999;sa=-90;sc=zz")
        assertEquals(CardStyle(color = 0xFF000000.toInt(), gradient = true, intensity = 100, angle = 270), p.style)
    }

    @Test
    fun config_surfaceSpecAndInk() {
        assertNull(BatteryWidgetConfig(bg = Background.CLEAR).surfaceSpec())
        assertFalse(BatteryWidgetRenderer.darkInk(null))
        // Plain black: white text.
        assertFalse(BatteryWidgetRenderer.darkInk(BatteryWidgetConfig().surfaceSpec()))
        // A light colour background switches to dark ink; glass over it lets the black wallpaper through.
        val light = BatteryWidgetConfig(style = CardStyle(color = 0xFFF0F0F0.toInt()))
        assertTrue(BatteryWidgetRenderer.darkInk(light.surfaceSpec()))
        assertFalse(BatteryWidgetRenderer.darkInk(light.copy(style = light.style.copy(glass = true)).surfaceSpec()))
        // Wallpaper uses the palette colour, not the style colour.
        val wall = BatteryWidgetConfig(bg = Background.WALLPAPER, style = CardStyle(color = 0xFFF0F0F0.toInt()))
        assertEquals(0xFF203040.toInt(), wall.surfaceSpec(0xFF203040.toInt())!!.bottom)
        // Colour 0 resolves to black.
        assertEquals(0xFF000000.toInt(), BatteryWidgetConfig(style = CardStyle()).surfaceSpec()!!.bottom)
    }

    @Test
    fun window_hoursAndSinceUnplug() {
        assertArrayEquals(longArrayOf(now - 6 * H, now), BatteryWidgetModel.window(Range.H6, now, null))
        assertArrayEquals(longArrayOf(now - 24 * H, now), BatteryWidgetModel.window(Range.H24, now, null))
        val bat = listOf(b(now - 10 * H, 50, 1), b(now - 8 * H, 100, 1), b(now - 5 * H, 99), b(now - H, 80))
        val s = Stats.compute(bat, emptyList(), false, now)
        assertArrayEquals(longArrayOf(now - 5 * H, now), BatteryWidgetModel.window(Range.SINCE_UNPLUG, now, s))
        // Just unplugged: the window still spans at least 30 minutes.
        val fresh = Stats.compute(listOf(b(now - 2 * H, 60, 1), b(now - 60_000, 100)), emptyList(), false, now)
        assertArrayEquals(
            longArrayOf(now - BatteryWidgetModel.MIN_SPAN, now),
            BatteryWidgetModel.window(Range.SINCE_UNPLUG, now, fresh)
        )
    }

    @Test
    fun window_todayStartsAtMidnightButKeepsMinSpan() {
        assertEquals(Range.TODAY, BatteryWidgetConfig.decode("r=TODAY").range)
        assertEquals("r=TODAY;b=BATTERY;s=SOT;l=1;k=BLACK", BatteryWidgetConfig(range = Range.TODAY).encode())
        val mid = Stats.midnight(now)
        val w = BatteryWidgetModel.window(Range.TODAY, now, null)
        assertEquals(minOf(mid, now - BatteryWidgetModel.MIN_SPAN), w[0])
        assertEquals(now, w[1])
        // Shortly after midnight the window is widened to the minimum span.
        val early = mid + 5 * 60_000
        assertEquals(early - BatteryWidgetModel.MIN_SPAN, BatteryWidgetModel.window(Range.TODAY, early, null)[0])
        assertEquals("Today", BatteryWidgetModel.label(Range.TODAY))
    }

    @Test
    fun label_text() {
        assertEquals("Battery (24h)", BatteryWidgetModel.label(Range.H24))
        assertEquals("Battery (6h)", BatteryWidgetModel.label(Range.H6))
        assertEquals("Since unplug", BatteryWidgetModel.label(Range.SINCE_UNPLUG))
    }

    @Test
    fun build_texts() {
        val bat = listOf(b(now - 4 * H, 90), b(now - 2 * H, 80))
        val screen = listOf(longArrayOf(now - 3 * H, now - 2 * H))
        val d = WidgetData(now, 78, false, 312, bat, screen)
        val f = BatteryWidgetModel.build(BatteryWidgetConfig(range = Range.H6, small = Value.SOT), d)
        assertEquals("78%", f.big)
        assertEquals("1h 0m on", f.small)
        assertEquals("Battery (6h)", f.label)
        assertFalse(f.smallCharging)
        assertEquals("31°C", BatteryWidgetModel.build(BatteryWidgetConfig(small = Value.TEMP), d).small)
        assertEquals("1h 0m", BatteryWidgetModel.build(BatteryWidgetConfig(big = Value.SOT), d).big)
        // The same value twice shows once.
        assertNull(BatteryWidgetModel.build(BatteryWidgetConfig(big = Value.SOT, small = Value.SOT), d).small)
        val none = BatteryWidgetModel.build(BatteryWidgetConfig(small = Value.NONE, label = false), d)
        assertNull(none.small)
        assertNull(none.label)
        // Unknown temperature: no text rather than a dash.
        assertNull(BatteryWidgetModel.build(BatteryWidgetConfig(small = Value.TEMP), WidgetData(now, 78, false, Int.MIN_VALUE, bat, screen)).small)
        // Charging: no time left; the big LEFT shows the level, the small LEFT says so in the charging colour.
        val ch = WidgetData(now, 78, true, 312, bat, screen)
        assertEquals("78%", BatteryWidgetModel.build(BatteryWidgetConfig(big = Value.LEFT), ch).big)
        val chs = BatteryWidgetModel.build(BatteryWidgetConfig(small = Value.LEFT), ch)
        assertEquals("Charging", chs.small)
        assertTrue(chs.smallCharging)
    }

    @Test
    fun throttle_skipsSameStateWithinAMinute() {
        val t = RedrawThrottle(60_000)
        assertTrue(t.shouldDraw(80, false, true, 0))
        assertFalse(t.shouldDraw(80, false, true, 30_000))
        assertTrue(t.shouldDraw(80, false, false, 31_000)) // screen changed
        assertTrue(t.shouldDraw(79, false, false, 32_000)) // level changed
        assertFalse(t.shouldDraw(79, false, false, 91_999))
        assertTrue(t.shouldDraw(79, false, false, 92_000)) // a minute later
    }

    @Test
    fun series_window_holdsLevelAtStartAndEndsLive() {
        val bat = listOf(b(now - 30 * H, 100, 1), b(now - 20 * H, 70), b(now - 3 * H, 60), b(now - 2 * H, 61, 1))
        val s = GraphMath.window(bat, now - 6 * H, now, 75, true)
        assertArrayEquals(longArrayOf(now - 6 * H, now - 3 * H, now - 2 * H, now), s.t)
        assertArrayEquals(intArrayOf(70, 60, 61, 75), s.l)
        assertEquals(listOf(false, false, true, true), s.c.toList())
        // Nothing before the window: start at the first sample inside it.
        val s2 = GraphMath.window(listOf(b(now - H, 50)), now - 6 * H, now)
        assertArrayEquals(longArrayOf(now - H, now), s2.t)
        assertArrayEquals(intArrayOf(50, 50), s2.l)
        assertEquals(0, GraphMath.window(emptyList(), now - H, now).size)
    }

    @Test
    fun levelAt_interpolatesAndHolds() {
        val t = longArrayOf(0, 100, 200)
        val l = intArrayOf(100, 50, 50)
        assertEquals(100f, GraphMath.levelAt(t, l, -5), 0f)
        assertEquals(75f, GraphMath.levelAt(t, l, 50), 0.001f)
        assertEquals(50f, GraphMath.levelAt(t, l, 500), 0f)
    }

    @Test
    fun smooth_neverOvershoots() {
        val p = floatArrayOf(0f, 100f, 10f, 0f, 20f, 100f, 30f, 100f, 31f, 50f)
        val c = GraphMath.smooth(p)
        assertEquals(16, c.size)
        for (i in 0 until 4) {
            val x0 = p[2 * i]; val y0 = p[2 * i + 1]; val x1 = p[2 * i + 2]; val y1 = p[2 * i + 3]
            for (k in 0..1) {
                val cx = c[4 * i + 2 * k]
                val cy = c[4 * i + 2 * k + 1]
                assertTrue(cx in x0..x1)
                assertTrue(cy in minOf(y0, y1)..maxOf(y0, y1))
            }
        }
    }

    @Test
    fun runs_splitByCharging() {
        val r = GraphMath.runs(booleanArrayOf(false, false, true, true, false, false))
        assertEquals(listOf(listOf(0, 2, 0), listOf(2, 4, 1), listOf(4, 5, 0)), r.map { it.toList() })
        assertTrue(GraphMath.runs(booleanArrayOf(true)).isEmpty())
    }

    @Test
    fun project_mapsTimeAndLevel() {
        val s = GraphMath.Series(longArrayOf(0, 50, 100), intArrayOf(100, 50, 0), booleanArrayOf(false, false, false))
        val p = GraphMath.project(s, 0, 100, 10f, 0f, 110f, 100f)
        assertArrayEquals(floatArrayOf(10f, 0f, 60f, 50f, 110f, 100f), p, 0.001f)
    }
}
