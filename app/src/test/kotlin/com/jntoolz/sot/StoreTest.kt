package com.jntoolz.sot

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class StoreTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val now = System.currentTimeMillis()
    private fun b(t: Long, level: Int, plug: Int = 0) = Store.B(t, level, plug, 2, 300, 4000, 3_000_000)

    @Test
    fun addBattery_skipsUnchangedAndCountsChanges() {
        val st = Store(File(tmp.root, "log.txt"))
        assertEquals(0, st.changes.value)
        assertTrue(st.addBattery(b(now - 2000, 80)))
        assertFalse(st.addBattery(b(now - 1000, 80)))
        assertTrue(st.addBattery(b(now, 80, 1)))
        st.addScreen(now, true)
        st.addScreen(now + 1, true) // same state, ignored
        st.addCurrent(now, Double.NaN) // not reported, ignored
        st.addCurrent(now, -350.4)
        assertEquals(4, st.changes.value)
        val sn = st.snap()
        assertEquals(4, sn.version)
        assertEquals(2, sn.bat.size)
        assertSame(sn, st.snap()) // cached until the next change
        assertEquals(1, sn.screen.size)
        assertArrayEquals(longArrayOf(now, -350), sn.current[0])
    }

    @Test
    fun reload_keepsDataAndDropsTornLine() {
        val f = File(tmp.root, "log.txt")
        // Written by hand: a second Store on the same file would hold it open (Windows can't replace it then).
        f.writeText(
            "B ${now - 2000} 80 0 2 300 4000 3000000\n" +
                "S ${now - 1500} 1\n" +
                "C ${now - 1200} -200\n" +
                "S ${now - 1000} 0\n" +
                "B ${now - 40L * 24 * 3600 * 1000} 90 0 2 300 4000 3000000\n" + // older than KEEP_MS
                "S ${now}9x", // torn last line
        )

        val back = Store(f).snap()
        assertEquals(1, back.bat.size)
        assertEquals(80, back.bat[0].level)
        assertEquals(3_000_000, back.bat[0].cc)
        assertArrayEquals(longArrayOf(now - 1500, now - 1000), back.screen[0])
        assertEquals(1, back.current.size)
        assertFalse(f.readText().contains("9x")) // rewritten without it
    }

    @Test
    fun closeBefore_endsScreenLeftOnBeforeBoot() {
        val st = Store(File(tmp.root, "log.txt"))
        st.addScreen(now - 5000, true)
        st.addBattery(b(now - 3000, 50))
        st.closeBefore(now - 1000)
        assertArrayEquals(longArrayOf(now - 5000, now - 3000), st.snap().screen[0])
    }

    @Test
    fun versions_currentSamplesLeaveBatteryAndScreenAlone() {
        val st = Store(File(tmp.root, "log.txt"))
        st.addBattery(b(now - 3000, 80))
        st.addScreen(now - 2000, true)
        val a = st.snap()
        val da = st.snapData()
        assertTrue(da.current.isEmpty())

        st.addCurrent(now - 1000, -300.0)
        val sb = st.snap()
        assertEquals(a.version + 1, sb.version)
        assertEquals(a.dataVersion, sb.dataVersion)
        assertSame(a.bat, sb.bat) // not copied again
        assertSame(a.screen, sb.screen)
        assertEquals(1, sb.current.size)
        assertSame(da, st.snapData()) // nothing it holds changed

        st.addScreen(now, false)
        val c = st.snap()
        assertEquals(a.batVersion, c.batVersion)
        assertTrue(c.screenVersion != a.screenVersion)
        assertSame(a.bat, c.bat)
        assertSame(sb.current, c.current)
        assertTrue(st.snapData() !== da)

        st.addBattery(b(now, 79))
        assertTrue(st.snap().batVersion != c.batVersion)
    }

    @Test
    fun isNew_matchesAddBattery() {
        val st = Store(File(tmp.root, "log.txt"))
        assertTrue(st.isNew(80, 0, 2))
        st.addBattery(b(now - 1000, 80))
        assertFalse(st.isNew(80, 0, 2))
        assertTrue(st.isNew(79, 0, 2))
        assertTrue(st.isNew(80, 1, 2))
        st.addBattery(b(now, 99, 1))
        assertTrue(st.isNew(99, 1, Store.STATUS_FULL)) // became "full"
    }

    @Test
    fun dailyPrune_dropsOldBatteryAndScreenToo() {
        val f = File(tmp.root, "log.txt")
        val old = now - Store.KEEP_MS + 60_000 // kept at load, past KEEP_MS a day later
        f.writeText(
            "B $old 90 0 2 300 4000 3000000\n" +
                "S ${old + 1} 1\n" +
                "S ${old + 2} 0\n" +
                "C ${now - Store.KEEP_CUR_MS + 60_000} -200\n" +
                "B ${now - 1000} 50 0 2 300 4000 3000000\n",
        )
        val st = Store(f)
        assertEquals(2, st.snap().bat.size)
        val later = now + 2 * 24 * 3600 * 1000L // the old current sample is now a day past its cutoff
        st.addCurrent(later, -100.0)
        val sn = st.snap()
        assertEquals(1, sn.bat.size)
        assertEquals(50, sn.bat[0].level)
        assertTrue(sn.screen.isEmpty())
        assertEquals(1, sn.current.size)
    }
}
