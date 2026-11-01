package com.jntoolz.sot

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import java.util.Arrays

/**
 * Which apps drained the battery. Normal apps cannot read Android's per-app battery stats, so (like AccuBattery)
 * we take the battery current we sampled while the screen was on and charge it to the app in the foreground.
 */
object Apps {
    const val RESUMED = 1 // UsageEvents.Event types
    const val PAUSED = 2
    const val SCREEN_OFF = 16
    const val SHUTDOWN = 26
    const val LOOKBACK = 3 * Stats.HOUR // to see which app was already open at `from`
    const val MAX_GAP = 3 * Stats.MIN   // samples further apart are not joined up
    const val EDGE = Stats.MIN          // a lone sample stands for this long on its open side

    class Use {
        var pkg: String = ""
        var label: CharSequence = ""
        var icon: Drawable? = null
        var fgMs = 0L      // foreground time in [from, to]
        var mAh = 0.0      // measured charge used while in foreground (from current samples), -1 if no samples
        var pct = 0.0      // estimated share of battery % used in [from, to] (percentage points of the battery, e.g. 7.5)
    }

    /** Apps that used the most battery in [from, to], biggest first. Blocking: call off the main thread. Empty if no usage access. */
    fun top(c: Context, from: Long, to: Long, current: List<LongArray>?, usedOnPct: Double, max: Int): List<Use> {
        val out = ArrayList<Use>()
        val until = minOf(to, System.currentTimeMillis())
        if (until <= from || !Usage.allowed(c)) return out

        // Foreground events (only the kinds we need, to keep memory small).
        val qFrom = maxOf(0, from - LOOKBACK)
        var n = 0
        var t = LongArray(256)
        var type = IntArray(256)
        var pkg = arrayOfNulls<String>(256)
        var cls = arrayOfNulls<String>(256)
        try {
            val ev = c.getSystemService(UsageStatsManager::class.java).queryEvents(qFrom, until)
            val e = UsageEvents.Event()
            while (ev.hasNextEvent()) {
                ev.getNextEvent(e)
                val k = e.eventType
                if (k != RESUMED && k != PAUSED && k != SCREEN_OFF && k != SHUTDOWN) continue
                if (n == t.size) {
                    t = t.copyOf(n * 2)
                    type = type.copyOf(n * 2)
                    pkg = pkg.copyOf(n * 2)
                    cls = cls.copyOf(n * 2)
                }
                t[n] = e.timeStamp
                type[n] = k
                pkg[n] = e.packageName
                cls[n] = e.className
                n++
            }
        } catch (e: RuntimeException) {
            return out
        }
        val iv = intervals(t.copyOf(n), type.copyOf(n), pkg.copyOf(n), cls.copyOf(n), qFrom, from, until)

        // Charge per app. Hidden apps (home, system UI, us) still count in the split so the rest are not inflated.
        val m = iv.size
        val names = iv.keys.toTypedArray()
        val fg = LongArray(m)
        val mah = DoubleArray(m)
        val cv = Curve(current)
        for (i in 0 until m) {
            val l = iv.getValue(names[i])
            for (x in l) fg[i] += x[1] - x[0]
            mah[i] = cv.mah(l)
        }
        val share = share(fg, mah)

        val pm = c.packageManager
        val skip = HashSet<String>()
        skip.add(c.packageName)
        skip.add("com.android.systemui")
        skip.add("android") // the "choose an app" resolver
        try {
            val home = pm.resolveActivity(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
                PackageManager.MATCH_DEFAULT_ONLY,
            )
            if (home?.activityInfo != null) skip.add(home.activityInfo.packageName)
        } catch (ignored: RuntimeException) {
        }

        val order = ArrayList<DoubleArray>() // {share, fg, index}
        for (i in 0 until m) {
            if (fg[i] > 0 && names[i] !in skip) order.add(doubleArrayOf(share[i], fg[i].toDouble(), i.toDouble()))
        }
        order.sortWith { a, b -> if (a[0] != b[0]) b[0].compareTo(a[0]) else b[1].compareTo(a[1]) }
        for (o in order) {
            if (max > 0 && out.size >= max) break
            val i = o[2].toInt()
            val u = Use()
            u.pkg = names[i]
            u.fgMs = fg[i]
            u.mAh = mah[i]
            u.pct = if (usedOnPct > 0) usedOnPct * share[i] else 0.0
            try {
                val ai = pm.getApplicationInfo(u.pkg, 0)
                u.label = pm.getApplicationLabel(ai)
                u.icon = pm.getApplicationIcon(ai)
            } catch (e: Exception) {
                u.label = u.pkg // uninstalled, or hidden by package visibility
                u.icon = pm.defaultActivityIcon
            }
            out.add(u)
        }
        return out
    }

    /**
     * Foreground intervals {start, end} per package, clipped to [from, to]. Events must be oldest first; events from
     * qFrom on. One app is in front at a time: a resume of another app ends the last one (covers missing pauses),
     * screen off or shutdown ends it too, and a pause ends it only if it is for the activity that was resumed.
     */
    fun intervals(
        t: LongArray, type: IntArray, pkg: Array<String?>, cls: Array<String?>,
        qFrom: Long, from: Long, to: Long,
    ): Map<String, List<LongArray>> {
        val out = LinkedHashMap<String, MutableList<LongArray>>()
        var cur: String? = null
        var curCls: String? = null
        var at = 0L
        for (i in t.indices) {
            val k = type[i]
            if (k == RESUMED) {
                val p = pkg[i] ?: continue
                if (p == cur) { // another activity of the same app
                    curCls = cls[i]
                    continue
                }
                if (cur != null) add(out, cur, at, t[i], from, to)
                cur = p
                curCls = cls[i]
                at = t[i]
            } else if (k == PAUSED) {
                if (cur == null) {
                    val p = pkg[i]
                    if (i == 0 && p != null) add(out, p, qFrom, t[i], from, to) // already open at qFrom
                } else if (cur == pkg[i] && (curCls == null || cls[i] == null || curCls == cls[i])) {
                    add(out, cur, at, t[i], from, to)
                    cur = null
                }
            } else if (k == SCREEN_OFF || k == SHUTDOWN) {
                if (cur != null) add(out, cur, at, t[i], from, to)
                cur = null
            }
        }
        if (cur != null) add(out, cur, at, to, from, to)
        return out
    }

    private fun add(out: MutableMap<String, MutableList<LongArray>>, p: String, a0: Long, z0: Long, from: Long, to: Long) {
        val a = maxOf(a0, from)
        val z = minOf(z0, to)
        if (z <= a) return
        out.getOrPut(p) { ArrayList() }.add(longArrayOf(a, z))
    }

    /**
     * Each app's share of the screen-on drain: by charge; apps with no samples get the average current of the
     * measured ones; with no samples at all, by foreground time. Shares add up to 1 (or are all 0).
     */
    fun share(fg: LongArray, mah: DoubleArray): DoubleArray {
        var sum = 0.0
        var measured = 0.0
        var measuredFg = 0L
        val est = DoubleArray(fg.size)
        for (i in fg.indices) if (mah[i] >= 0) {
            measured += mah[i]
            measuredFg += fg[i]
        }
        for (i in fg.indices) {
            est[i] = if (measuredFg <= 0) fg[i].toDouble()
            else if (mah[i] >= 0) mah[i]
            else measured / measuredFg * fg[i]
            sum += est[i]
        }
        for (i in fg.indices) est[i] = if (sum > 0) est[i] / sum else 0.0
        return est
    }

    /**
     * Discharge current over time from the {time, mA} samples, in mA (positive = drain). Linear between two
     * discharging samples up to MAX_GAP apart, nearest sample next to a charging one (charging counts as 0),
     * and a lone sample stands for EDGE on its open side. Elsewhere it is unknown.
     */
    class Curve(samples: List<LongArray>?) {
        private val a: LongArray
        private val b: LongArray
        private val va: DoubleArray
        private val vb: DoubleArray
        private var n = 0

        init {
            val s = samples?.toTypedArray() ?: emptyArray()
            Arrays.sort(s) { x, y -> x[0].compareTo(y[0]) } // the wall clock can jump back
            val m = s.size
            a = LongArray(4 * m)
            b = LongArray(4 * m)
            va = DoubleArray(4 * m)
            vb = DoubleArray(4 * m)
            for (i in 0 until m) {
                val ti = s[i][0]
                val v = drain(s[i][1])
                if (i == 0 || ti - s[i - 1][0] > MAX_GAP) piece(ti - EDGE, ti, v, v)
                if (i + 1 < m && s[i + 1][0] - ti <= MAX_GAP) {
                    val tj = s[i + 1][0]
                    val w = drain(s[i + 1][1])
                    if (s[i][1] < 0 && s[i + 1][1] < 0) {
                        piece(ti, tj, v, w)
                    } else {
                        val mid = (ti + tj) / 2
                        piece(ti, mid, v, v)
                        piece(mid, tj, w, w)
                    }
                } else {
                    piece(ti, ti + EDGE, v, v)
                }
            }
        }

        private fun drain(ma: Long): Double = if (ma < 0) -ma.toDouble() else 0.0

        private fun piece(x: Long, y: Long, vx: Double, vy: Double) {
            if (y <= x) return
            a[n] = x
            b[n] = y
            va[n] = vx
            vb[n] = vy
            n++
        }

        /** {mA*ms, covered ms} over [x, y]. */
        fun integrate(x: Long, y: Long): DoubleArray {
            var lo = 0
            var hi = n // first piece that ends after x
            while (lo < hi) {
                val mid = (lo + hi) ushr 1
                if (b[mid] <= x) lo = mid + 1 else hi = mid
            }
            var sum = 0.0
            var cov = 0L
            var k = lo
            while (k < n && a[k] < y) {
                val p = maxOf(a[k], x)
                val q = minOf(b[k], y)
                if (q > p) {
                    val slope = (vb[k] - va[k]) / (b[k] - a[k])
                    sum += (q - p) * (va[k] + slope * (p - a[k]) + va[k] + slope * (q - a[k])) / 2
                    cov += q - p
                }
                k++
            }
            return doubleArrayOf(sum, cov.toDouble())
        }

        /** mAh over the intervals; uncovered parts count at the covered average. -1 if nothing is covered. */
        fun mah(iv: List<LongArray>): Double {
            var sum = 0.0
            var cov = 0L
            var total = 0L
            for (x in iv) {
                val r = integrate(x[0], x[1])
                sum += r[0]
                cov += r[1].toLong()
                total += x[1] - x[0]
            }
            return if (cov > 0) sum / Stats.HOUR * total / cov else -1.0
        }
    }
}
