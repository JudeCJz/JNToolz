package com.jntoolz.sot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.TimeZone

class GraphExportTest {
    private val utc = TimeZone.getTimeZone("UTC")
    private val m = Stats.MIN
    private val t0 = 20 * Stats.HOUR // 20:00 UTC on day 1 of the epoch
    private val now = t0 + 100 * m

    private fun b(t: Long, level: Int, plug: Int = 0, temp: Int = 300) = Store.B(t, level, plug, 0, temp, 4000, 0)

    private fun data(): List<Store.B> = listOf(
        b(t0, 80), b(t0 + 20 * m, 79, temp = 410), b(t0 + 40 * m, 78, temp = 420), b(t0 + 50 * m, 77),
        b(t0 + 60 * m, 77, 1), b(t0 + 80 * m, 78, 1),
    )

    private fun text(apps: List<GraphExport.AppIv>?, totals: List<GraphExport.AppTotal>?): String {
        val bat = data()
        val screen = listOf(longArrayOf(t0 + 10 * m, t0 + 30 * m), longArrayOf(t0 + 90 * m, -1))
        val cur = listOf(longArrayOf(t0 + 12 * m, -300), longArrayOf(t0 + 15 * m, -500), longArrayOf(t0 + 95 * m, 800))
        val st = Stats.compute(bat, screen, false, now)
        return GraphExport.build(st, "since unplug", bat, screen, cur, apps, totals, 5 * m, true, utc)
    }

    @Test
    fun headerAndLog() {
        val s = text(null, null)
        assertTrue(s, s.contains("From 20:00:00 to 21:40:00"))
        assertTrue(s, s.contains("20:00:00\tBATTERY\t80%\ton battery"))
        assertTrue(s, s.contains("20:10:00\tSCREEN\tON"))
        assertTrue(s, s.contains("20:30:00\tSCREEN\tOFF\ton for 20m, avg 400 mA, peak 500 mA"))
        assertTrue(s, s.contains("21:00:00\tBATTERY\t77%\tcharging"))
        assertTrue(s, s.contains("Time per 1% charged"))
        assertTrue(s, s.contains("Not tracked: Wi-Fi"))
        assertTrue(s, s.contains("usage access is off"))
        // the log is time ordered
        assertTrue(s.indexOf("20:10:00\tSCREEN") < s.indexOf("20:20:00\tBATTERY"))
    }

    @Test
    fun appsAndTopDrains() {
        val s = text(
            listOf(GraphExport.AppIv("Maps", t0 + 11 * m, t0 + 25 * m, 42.0), GraphExport.AppIv("Blip", t0, t0 + 1000, 1.0)),
            listOf(GraphExport.AppTotal("Maps", 14 * m, 42.0, 3.5)),
        )
        assertTrue(s, s.contains("APP\tMaps\t20:11:00 - 20:25:00 (14m), ~42.0 mAh"))
        assertTrue(s, !s.contains("Blip"))
        assertTrue(s, s.contains("Maps: 3.5%"))
    }

    @Test
    fun hotAndChargePeriods() {
        val hot = GraphExport.hotPeriods(data(), now)
        assertEquals(1, hot.size)
        assertEquals(t0 + 20 * m, hot[0][0])
        assertEquals(t0 + 50 * m, hot[0][1])
        assertEquals(420L, hot[0][2])
        val chg = GraphExport.chargePeriods(data(), now)
        assertEquals(1, chg.size)
        assertEquals(t0 + 60 * m, chg[0][0])
        assertEquals(now, chg[0][1])
        assertEquals(77L, chg[0][2])
        assertEquals(78L, chg[0][3])
    }

    @Test
    fun emptyAndMidnight() {
        val st = Stats()
        st.empty = true
        assertTrue(GraphExport.build(st, "today", emptyList(), emptyList(), emptyList(), null, null, -1, false, utc).contains("nothing"))
        val bat = listOf(b(23 * Stats.HOUR, 50), b(25 * Stats.HOUR, 40))
        val s = Stats.compute(bat, emptyList(), false, 26 * Stats.HOUR)
        val txt = GraphExport.build(s, "since unplug", bat, emptyList(), emptyList(), null, null, -1, false, utc)
        assertTrue(txt, txt.contains("01 Jan 23:00:00") && txt.contains("02 Jan 02:00:00"))
    }

    @Test
    fun deviceHeaderIncluded() {
        val header = "Device: Nothing Phone (4a) Pro\nOS: Android 17 · Nothing OS 5.0\nBattery: 5,480 mAh (Health: 100%)\nDevice age: 12 days old (17 battery cycles)"
        val st = Stats()
        st.empty = true
        val txt = GraphExport.build(st, "today", emptyList(), emptyList(), emptyList(), null, null, -1, false, utc, deviceHeader = header)
        assertTrue(txt.startsWith("DEVICE & BATTERY\nDevice: Nothing Phone (4a) Pro"))
        assertTrue(txt.contains("Battery: 5,480 mAh"))
        assertTrue(txt.contains("Device age: 12 days old"))
    }
}
