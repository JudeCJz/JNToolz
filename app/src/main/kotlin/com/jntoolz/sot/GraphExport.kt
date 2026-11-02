package com.jntoolz.sot

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** The graph's data as plain text for the clipboard. Pure logic (the caller gathers app names and usage). */
object GraphExport {
    /** One foreground interval of an app, with its estimated charge (-1 = no current samples). */
    class AppIv(val label: String, val start: Long, val end: Long, val mAh: Double)

    /** One app's total over the range, as on the Apps tab. */
    class AppTotal(val label: String, val fgMs: Long, val mAh: Double, val pct: Double)

    private const val HOT = 400 // 0.1 C: 40 C
    private const val MIN_APP_MS = 10_000L // shorter foreground spans are left out of the log
    private const val MIN_SESSION_MS = 3 * Stats.MIN // shorter screen sessions are not ranked

    /**
     * [st] is the stats the graph shows; the range is st.start to now. [current] are {time, mA} (- = discharging).
     * [apps] and [totals] are null when usage access is off.
     */
    fun build(
        st: Stats,
        range: String,
        bat: List<Store.B>,
        screen: List<LongArray>,
        current: List<LongArray>,
        apps: List<AppIv>?,
        totals: List<AppTotal>?,
        perPercent: Long,
        charging: Boolean,
        zone: TimeZone = TimeZone.getDefault(),
        deviceHeader: String? = null,
    ): String {
        val now = st.now
        val from = st.start
        val sb = StringBuilder()
        if (!deviceHeader.isNullOrBlank()) {
            sb.append("DEVICE & BATTERY\n")
            sb.append(deviceHeader.trim()).append("\n\n")
        }
        if (st.empty || bat.isEmpty()) {
            sb.append("Battery graph data ($range): nothing recorded yet.\n")
            return sb.toString()
        }
        val withDate = day(from, zone) != day(now, zone)
        val tf = SimpleDateFormat(if (withDate) "dd MMM HH:mm:ss" else "HH:mm:ss", Locale.US).also { it.timeZone = zone }
        fun tm(t: Long) = tf.format(Date(t))
        val inRange = bat.filter { it.t in from..now }

        sb.append("Battery graph data ($range)\n")
        sb.append("From ").append(tm(from)).append(" to ").append(tm(now)).append(" (").append(Stats.dur(now - from)).append(")\n")
        sb.append("Battery: ").append(st.startLevel).append("% at start, ").append(st.level).append("% now")
        sb.append(if (st.charging) " (charging)\n" else "\n")
        sb.append("Used: ").append(Stats.pct(st.used())).append(" while on battery, ")
        sb.append("screen on ").append(Stats.dur(st.sot)).append(", screen off ").append(Stats.dur(st.disOff)).append("\n")
        sb.append("Drain: screen on ").append(rate(st.rateOn())).append(", screen off ").append(rate(st.rateOff()))
        sb.append(", average ").append(rate(st.rateAvg())).append("\n")
        sb.append(if (charging) "Time per 1% charged (last 2 h): " else "Time per 1% drained (last 2 h): ")
        sb.append(Drain.short(perPercent)).append("\n")

        // Time-ordered log. Rows: {time, order, text}; order keeps same-time rows steady.
        class Row(val t: Long, val o: Int, val text: String)
        val rows = ArrayList<Row>()
        for (b in inRange) {
            val plug = if (b.plug != 0) "charging" else "on battery"
            rows.add(Row(b.t, 1, "BATTERY\t${b.level}%\t$plug\t${b.temp / 10.0} C\t${b.mv} mV"))
        }
        val sessions = ArrayList<LongArray>() // {a, z, drop, avgMa x10, peakMa}
        for (iv in screen) {
            val a = maxOf(iv[0], from)
            val z = minOf(if (iv[1] < 0) now else iv[1], now)
            if (z <= a) continue
            var sum = 0.0
            var n = 0
            var peak = 0L
            for (c in current) if (c[0] in a..z && c[1] < 0) {
                sum += -c[1]
                n++
                peak = maxOf(peak, -c[1])
            }
            val avg = if (n > 0) sum / n else -1.0
            val drop = if (charged(bat, a, z)) 0 else level(bat, a) - level(bat, z)
            sessions.add(longArrayOf(a, z, drop.toLong(), Math.round(avg * 10), peak))
            rows.add(Row(a, 0, "SCREEN\tON"))
            val cur = if (n > 0) ", avg ${Math.round(avg)} mA, peak $peak mA" else ", no current samples"
            val still = if (iv[1] < 0 || iv[1] > now) " (still on)" else ""
            rows.add(Row(z, 2, "SCREEN\tOFF\ton for ${Stats.dur(z - a)}$cur$still"))
        }
        if (apps != null) for (x in apps) {
            if (x.end - x.start < MIN_APP_MS) continue
            val ma = if (x.mAh >= 0) ", ~${Math.round(x.mAh * 10) / 10.0} mAh" else ""
            rows.add(Row(x.start, 3, "APP\t${x.label}\t${tm(x.start)} - ${tm(x.end)} (${Stats.dur(x.end - x.start)})$ma"))
        }
        rows.sortWith(compareBy({ it.t }, { it.o }))
        sb.append("\nLOG (time, type, details)\n")
        for (r in rows) sb.append(tm(r.t)).append('\t').append(r.text).append('\n')

        // Top drains.
        sb.append("\nTOP DRAINS\n")
        if (totals == null) {
            sb.append("Apps: usage access is off, so apps are not listed.\n")
        } else if (totals.isEmpty()) {
            sb.append("Apps: none recorded.\n")
        } else {
            sb.append("Apps (estimated share of battery):\n")
            for (x in totals.sortedByDescending { it.pct }.take(5)) {
                val ma = if (x.mAh >= 0) ", ~${Math.round(x.mAh)} mAh" else ""
                sb.append("  ${x.label}: ${Stats.pct(x.pct)}, in front ${Stats.dur(x.fgMs)}$ma\n")
            }
        }
        val ranked = sessions.filter { it[1] - it[0] >= MIN_SESSION_MS && it[2] > 0 }
            .sortedByDescending { it[2].toDouble() / (it[1] - it[0]) }.take(5)
        if (ranked.isNotEmpty()) {
            sb.append("Screen-on sessions with the highest drain:\n")
            for (s in ranked) {
                val perH = s[2].toDouble() / ((s[1] - s[0]).toDouble() / Stats.HOUR)
                val cur = if (s[3] > 0) ", avg ${Math.round(s[3] / 10.0)} mA" else ""
                sb.append("  ${tm(s[0])} - ${tm(s[1])}: ${rate(perH)}, ${s[2]}% in ${Stats.dur(s[1] - s[0])}$cur\n")
            }
        }
        val hot = hotPeriods(inRange, now)
        sb.append(if (hot.isEmpty()) "Hot periods (40 C or more): none\n" else "Hot periods (40 C or more):\n")
        for (h in hot) sb.append("  ${tm(h[0])} - ${tm(h[1])}: up to ${h[2] / 10.0} C\n")
        val chg = chargePeriods(inRange, now)
        sb.append(if (chg.isEmpty()) "Charging periods: none\n" else "Charging periods:\n")
        for (c in chg) sb.append("  ${tm(c[0])} - ${tm(c[1])}: ${c[2]}% to ${c[3]}%\n")

        sb.append("\nNot tracked: Wi-Fi, Bluetooth, GPS, mobile data and screen brightness.\n")
        return sb.toString()
    }

    private fun rate(p: Double): String =
        if (p.isNaN()) "–" else String.format(Locale.US, if (p >= 10) "%.0f%%/h" else "%.1f%%/h", p)

    private fun day(t: Long, zone: TimeZone): Int {
        val c = java.util.Calendar.getInstance(zone)
        c.timeInMillis = t
        return c.get(java.util.Calendar.YEAR) * 400 + c.get(java.util.Calendar.DAY_OF_YEAR)
    }

    /** Battery level at t (the last sample at or before it, else the first). */
    private fun level(bat: List<Store.B>, t: Long): Int {
        var l = bat[0].level
        for (b in bat) {
            if (b.t > t) break
            l = b.level
        }
        return l
    }

    /** True if any sample in [a, z] (or the one in force at a) is plugged in. */
    private fun charged(bat: List<Store.B>, a: Long, z: Long): Boolean {
        var plugged = false
        for (b in bat) {
            if (b.t > z) break
            if (b.t <= a) plugged = b.plug != 0 else if (b.plug != 0) return true
        }
        return plugged
    }

    /** {start, end, max temp (0.1 C)} of runs of samples at 40 C or more. */
    internal fun hotPeriods(b: List<Store.B>, now: Long): List<LongArray> {
        val out = ArrayList<LongArray>()
        var cur: LongArray? = null
        for (x in b) {
            if (x.temp >= HOT) {
                if (cur == null) cur = longArrayOf(x.t, x.t, x.temp.toLong())
                else cur[2] = maxOf(cur[2], x.temp.toLong())
            } else if (cur != null) {
                cur[1] = x.t
                out.add(cur)
                cur = null
            }
        }
        if (cur != null) {
            cur[1] = now
            out.add(cur)
        }
        return out
    }

    /** {start, end, startLevel, endLevel} of plugged-in runs. */
    internal fun chargePeriods(b: List<Store.B>, now: Long): List<LongArray> {
        val out = ArrayList<LongArray>()
        var i = 0
        while (i < b.size) {
            if (b[i].plug == 0) {
                i++
                continue
            }
            var j = i
            while (j + 1 < b.size && b[j + 1].plug != 0) j++
            val end = if (j + 1 < b.size) b[j + 1].t else now
            out.add(longArrayOf(b[i].t, end, b[i].level.toLong(), b[j].level.toLong()))
            i = j + 1
        }
        return out
    }
}
