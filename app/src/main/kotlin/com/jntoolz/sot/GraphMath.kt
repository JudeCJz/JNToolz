package com.jntoolz.sot

import kotlin.math.max
import kotlin.math.min

/**
 * Battery-graph math for the home-screen widget (android.graphics); the in-app graph uses [levelAt].
 * A series is parallel arrays: time, level and "charging" for the segment that starts at each point.
 */
object GraphMath {

    class Series(val t: LongArray, val l: IntArray, val c: BooleanArray) {
        val size: Int get() = t.size
    }

    /**
     * Samples inside [t0, t1], starting with the level held at t0 (the last sample before it) and ending at t1
     * with [endLevel] (the live level) when given. Charges are included and flagged.
     */
    fun window(b: List<Store.B>, t0: Long, t1: Long, endLevel: Int = -1, endCharging: Boolean = false): Series {
        val t = ArrayList<Long>()
        val l = ArrayList<Int>()
        val c = ArrayList<Boolean>()
        var i0 = -1
        for (i in b.indices) if (b[i].t <= t0) i0 = i else break
        if (i0 >= 0) {
            t += t0
            l += b[i0].level
            c += b[i0].plug != 0
        }
        for (i in i0 + 1 until b.size) {
            val x = b[i]
            if (x.t > t1) break
            t += x.t
            l += x.level
            c += x.plug != 0
        }
        val lastLevel = if (endLevel >= 0) endLevel else l.lastOrNull()
        if (lastLevel != null && (t.isEmpty() || t.last() < t1)) {
            t += t1
            l += lastLevel
            c += if (endLevel >= 0) endCharging else c.lastOrNull() ?: false
        }
        return Series(t.toLongArray(), l.toIntArray(), c.toBooleanArray())
    }

    /** The level at time t: linear between points, held before the first and after the last. */
    fun levelAt(t: LongArray, l: IntArray, at: Long): Float {
        val n = t.size
        if (n == 0) return 0f
        if (at <= t[0]) return l[0].toFloat()
        for (i in 0 until n - 1) {
            if (at <= t[i + 1]) {
                val span = t[i + 1] - t[i]
                return if (span > 0) l[i] + (l[i + 1] - l[i]) * (at - t[i]).toFloat() / span else l[i + 1].toFloat()
            }
        }
        return l[n - 1].toFloat()
    }

    /**
     * Points in pixels: x across [left, right] for [t0, t1], y from bottom (0%) to top (100%).
     * Returns {x0, y0, x1, y1, ...}.
     */
    fun project(s: Series, t0: Long, t1: Long, left: Float, top: Float, right: Float, bottom: Float): FloatArray {
        val out = FloatArray(s.size * 2)
        val span = max(1L, t1 - t0).toFloat()
        for (i in 0 until s.size) {
            val f = ((s.t[i] - t0) / span).coerceIn(0f, 1f)
            out[2 * i] = left + f * (right - left)
            out[2 * i + 1] = bottom - s.l[i].coerceIn(0, 100) / 100f * (bottom - top)
        }
        return out
    }

    /**
     * Bezier control points for a smooth curve through the points (Catmull-Rom), clamped so a segment never
     * leaves the band between its two ends: the curve does not overshoot past 0 or 100% or invent bumps.
     * Returns {c1x, c1y, c2x, c2y} per segment.
     */
    fun smooth(p: FloatArray): FloatArray {
        val n = p.size / 2
        if (n < 2) return FloatArray(0)
        val out = FloatArray((n - 1) * 4)
        fun x(i: Int) = p[2 * i.coerceIn(0, n - 1)]
        fun y(i: Int) = p[2 * i.coerceIn(0, n - 1) + 1]
        for (i in 0 until n - 1) {
            val lo = min(y(i), y(i + 1))
            val hi = max(y(i), y(i + 1))
            var c1x = x(i) + (x(i + 1) - x(i - 1)) / 6f
            var c2x = x(i + 1) - (x(i + 2) - x(i)) / 6f
            // Keep control points inside the segment's x span, so the curve never runs backwards in time.
            // min/max: a clock change can put a sample before the one it follows.
            val xl = min(x(i), x(i + 1))
            val xr = max(x(i), x(i + 1))
            c1x = c1x.coerceIn(xl, xr)
            c2x = c2x.coerceIn(xl, xr)
            out[4 * i] = c1x
            out[4 * i + 1] = (y(i) + (y(i + 1) - y(i - 1)) / 6f).coerceIn(lo, hi)
            out[4 * i + 2] = c2x
            out[4 * i + 3] = (y(i + 1) - (y(i + 2) - y(i)) / 6f).coerceIn(lo, hi)
        }
        return out
    }

    /** Runs of segments with the same charging flag: {firstPoint, lastPoint, charging(1|0)} each. */
    fun runs(c: BooleanArray): List<IntArray> {
        val out = ArrayList<IntArray>()
        val n = c.size
        if (n < 2) return out
        var a = 0
        for (i in 1 until n - 1) {
            if (c[i] != c[a]) {
                out += intArrayOf(a, i, if (c[a]) 1 else 0)
                a = i
            }
        }
        out += intArrayOf(a, n - 1, if (c[a]) 1 else 0)
        return out
    }
}
