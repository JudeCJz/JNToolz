package com.jntoolz.sot

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.io.IOException
import java.io.Writer
import java.util.Collections

/**
 * The tool's whole history: one battery sample per level or plug change, one entry per screen on/off,
 * and the battery current every tick while the screen is on (for the per-app estimate).
 * Lives in memory (the service and the screen share the process) and is appended to files/sot_log.txt:
 *   B time level plug status temp(0.1 C) mV chargeCounter(uAh)
 *   S time 1|0
 *   C time mA (+ charging, - discharging)
 */
class Store(private val file: File) {

    class B(
        val t: Long,
        val level: Int,
        val plug: Int,
        val status: Int,
        val temp: Int,
        val mv: Int,
        val cc: Int,
    ) {
        fun full(): Boolean = full(level, plug, status)
    }

    /**
     * A copy that is safe to read on any thread. Screen intervals are {on, off}, off = -1 while still on.
     * [version] counts every change; [batVersion] and [screenVersion] only their own list, so a consumer that
     * does not read [current] can skip work when only a current sample was added (see [dataVersion]).
     */
    class Snap(
        val version: Int,
        val batVersion: Int,
        val screenVersion: Int,
        val bat: List<B>,
        val screen: List<LongArray>,
        val current: List<LongArray>, // {time, mA}, oldest first; empty in a [snapData] copy
    ) {
        /** Changes with the battery or screen history, not with current samples. */
        val dataVersion: Long get() = (batVersion.toLong() shl 32) or (screenVersion.toLong() and 0xFFFFFFFFL)
    }

    private val bat = ArrayList<B>()
    private val scr = ArrayList<LongArray>() // {time, 1|0}
    private val cur = ArrayList<LongArray>() // {time, mA}
    private var version = 0
    private var batV = 0
    private var scrV = 0
    private var curV = 0

    // The copies snap() hands out, each rebuilt only when its own list changed.
    private var batCopy: List<B> = emptyList()
    private var batCopyV = -1
    private var scrCopy: List<LongArray> = emptyList()
    private var scrCopyV = -1
    private var curCopy: List<LongArray> = emptyList()
    private var curCopyV = -1
    private var snap: Snap? = null
    private var dataSnap: Snap? = null
    private var out: Writer? = null // kept open between appends, flushed per line

    private val _changes = MutableStateFlow(0)

    /** The version after every recorded change, so a screen can redraw instead of polling. */
    val changes: StateFlow<Int> = _changes.asStateFlow()

    init {
        load()
    }

    private fun load() {
        if (!file.exists()) return
        val now = System.currentTimeMillis()
        val cutoff = now - KEEP_MS
        val curCutoff = now - KEEP_CUR_MS
        var dropped = 0
        try {
            BufferedReader(FileReader(file)).use { r ->
                while (true) {
                    val line = r.readLine() ?: break
                    val p = line.split(" ")
                    try {
                        val t = p[1].toLong()
                        if (t < cutoff) {
                            dropped++
                            continue
                        }
                        if (p[0] == "B" && p.size >= 8) {
                            bat.add(B(t, p[2].toInt(), p[3].toInt(), p[4].toInt(), p[5].toInt(), p[6].toInt(), p[7].toInt()))
                        } else if (p[0] == "S" && p.size >= 3) {
                            scr.add(longArrayOf(t, p[2].toLong()))
                        } else if (p[0] == "C" && p.size >= 3) {
                            if (t < curCutoff) dropped++
                            else cur.add(longArrayOf(t, p[2].toLong()))
                        }
                    } catch (bad: RuntimeException) {
                        dropped++ // a torn last line after a power cut
                    }
                }
            }
        } catch (ignored: IOException) {
        }
        if (dropped > 0) rewrite()
    }

    private fun rewrite() {
        closeOut()
        val tmp = File(file.path + ".tmp")
        try {
            BufferedWriter(FileWriter(tmp)).use { w ->
                // Order between the kinds does not matter: each list is read back in its own order.
                for (b in bat) w.write(line(b))
                for (s in scr) w.write("S ${s[0]} ${s[1]}\n")
                for (s in cur) w.write("C ${s[0]} ${s[1]}\n")
            }
        } catch (e: IOException) {
            return
        }
        if (!tmp.renameTo(file)) {
            file.delete()
            tmp.renameTo(file)
        }
    }

    private fun append(s: String) {
        try {
            val w = out ?: BufferedWriter(FileWriter(file, true)).also { out = it }
            w.write(s)
            w.flush() // one line reaches the file per record, as before
        } catch (e: IOException) {
            closeOut() // reopen on the next append
        }
    }

    @Synchronized
    private fun close() = closeOut()

    private fun closeOut() {
        try {
            out?.close()
        } catch (ignored: IOException) {
        }
        out = null
    }

    private fun changed() {
        version++
        _changes.value = version
    }

    /** True when a sample with these values would be recorded (level, plug or "full" differ from the last). */
    @Synchronized
    fun isNew(level: Int, plug: Int, status: Int): Boolean {
        if (bat.isEmpty()) return true
        val l = bat[bat.size - 1]
        return l.level != level || l.plug != plug || l.full() != full(level, plug, status)
    }

    /** Records a sample only when the level, the plug or the "full" state changed. */
    @Synchronized
    fun addBattery(b: B): Boolean {
        if (!isNew(b.level, b.plug, b.status)) return false
        bat.add(b)
        append(line(b))
        batV++
        changed()
        return true
    }

    @Synchronized
    fun addScreen(t: Long, on: Boolean) {
        if (scr.isNotEmpty() && (scr[scr.size - 1][1] == 1L) == on) return
        val v = if (on) 1L else 0L
        scr.add(longArrayOf(t, v))
        append("S $t $v\n")
        scrV++
        changed()
    }

    /**
     * Records the battery current in mA (NaN = not reported). About once a day old samples are pruned, and
     * battery and screen entries older than [KEEP_MS] with them (a long-lived process would keep them all).
     */
    @Synchronized
    fun addCurrent(t: Long, ma: Double) {
        if (ma.isNaN()) return
        val v = Math.round(ma)
        cur.add(longArrayOf(t, v))
        append("C $t $v\n")
        curV++
        val cutoff = t - KEEP_CUR_MS
        if (cur[0][0] < cutoff - 24 * 3600 * 1000L) {
            var k = 0
            while (k < cur.size && cur[k][0] < cutoff) k++
            cur.subList(0, k).clear()
            val keep = t - KEEP_MS
            k = 0
            while (k < bat.size && bat[k].t < keep) k++
            if (k > 0) {
                bat.subList(0, k).clear()
                batV++
            }
            k = 0
            while (k < scr.size && scr[k][0] < keep) k++
            if (k > 0) {
                scr.subList(0, k).clear()
                scrV++
            }
            rewrite()
        }
        changed()
    }

    /**
     * After a reboot the last screen entry may still say "on" (the phone was switched off with the screen on
     * and no shutdown broadcast reached us). Close it at the last thing we heard before the boot.
     */
    @Synchronized
    fun closeBefore(bootTime: Long) {
        if (scr.isEmpty()) return
        val l = scr[scr.size - 1]
        if (l[1] != 1L || l[0] >= bootTime) return
        var end = l[0]
        for (i in bat.indices.reversed()) {
            val t = bat[i].t
            if (t < bootTime) {
                end = maxOf(end, t)
                break
            }
        }
        addScreen(end, false)
    }

    /** Everything, current samples included (the per-app estimate). A list is copied only when it changed. */
    @Synchronized
    fun snap(): Snap {
        snap?.let { if (it.version == version) return it }
        if (curCopyV != curV) {
            curCopy = Collections.unmodifiableList(ArrayList(cur))
            curCopyV = curV
        }
        val s = Snap(version, batV, scrV, batList(), screenList(), curCopy)
        snap = s
        return s
    }

    /** The battery and screen history only ([Snap.current] is empty), so a current sample costs nothing. */
    @Synchronized
    fun snapData(): Snap {
        dataSnap?.let { if (it.batVersion == batV && it.screenVersion == scrV) return it }
        val s = Snap(version, batV, scrV, batList(), screenList(), emptyList())
        dataSnap = s
        return s
    }

    private fun batList(): List<B> {
        if (batCopyV != batV) {
            batCopy = Collections.unmodifiableList(ArrayList(bat))
            batCopyV = batV
        }
        return batCopy
    }

    private fun screenList(): List<LongArray> {
        if (scrCopyV != scrV) {
            scrCopy = Collections.unmodifiableList(intervals(scr))
            scrCopyV = scrV
        }
        return scrCopy
    }

    companion object {
        const val KEEP_MS = 30L * 24 * 3600 * 1000
        const val KEEP_CUR_MS = 10L * 24 * 3600 * 1000 // about as long as Android keeps app events
        const val STATUS_FULL = 5 // BatteryManager.BATTERY_STATUS_FULL

        fun full(level: Int, plug: Int, status: Int): Boolean = plug != 0 && (level >= 100 || status == STATUS_FULL)

        private var inst: Store? = null

        /** Drops the in-memory history (the tool was switched off); the next [get] reads the file again. */
        @Synchronized
        fun release() {
            inst?.close()
            inst = null
        }

        /** The loaded store, or null while nothing has read the file yet. */
        @Synchronized
        fun peek(): Store? = inst

        @Synchronized
        fun get(c: Context): Store =
            inst ?: Store(File(c.applicationContext.filesDir, "sot_log.txt")).also { inst = it }

        private fun line(b: B): String =
            "B ${b.t} ${b.level} ${b.plug} ${b.status} ${b.temp} ${b.mv} ${b.cc}\n"

        fun intervals(events: List<LongArray>): List<LongArray> {
            val out = ArrayList<LongArray>()
            var onAt = -1L
            for (e in events) {
                if (e[1] == 1L) {
                    if (onAt < 0) onAt = e[0]
                } else if (onAt >= 0) {
                    out.add(longArrayOf(onAt, e[0]))
                    onAt = -1
                }
            }
            if (onAt >= 0) out.add(longArrayOf(onAt, -1))
            return out
        }
    }
}
