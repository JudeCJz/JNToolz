package com.jntoolz.sot

import java.util.Calendar
import java.util.Locale

/**
 * One session worked out from the samples: from the last unplug (or the unplug after the last 100%)
 * to now, or to the moment the charger went back in while charging.
 */
class Stats {
    var empty = false      // no discharge recorded yet
    var fallback = false   // no unplug/full charge seen yet: counting from when tracking started
    var charging = false   // plugged in now; the numbers describe the session before it
    var fullMode = false
    var now = 0L
    var start = 0L
    var end = 0L
    var level = 0
    var startLevel = 0
    var endLevel = 0
    var sot = 0L
    var off = 0L
    var longestOn = 0L
    var disOn = 0L         // screen on while discharging (charges left out)
    var disOff = 0L        // screen off while discharging
    var ons = 0
    var usedOn = 0.0
    var usedOff = 0.0
    var gained = 0.0
    var chgStart = 0L
    var chgStartLevel = 0

    // Graph: battery points and screen-on intervals clipped to [start, end].
    var gt = LongArray(0)
    var gl = IntArray(0)
    var gc = BooleanArray(0)
    var on = LongArray(0)

    fun used(): Double = usedOn + usedOff
    fun length(): Long = end - start
    fun rateOn(): Double = if (disOn >= 2 * MIN) usedOn / (disOn.toDouble() / HOUR) else Double.NaN
    fun rateOff(): Double = if (disOff >= 10 * MIN) usedOff / (disOff.toDouble() / HOUR) else Double.NaN
    fun rateAvg(): Double =
        if (length() >= 10 * MIN && used() > 0) used() / (length().toDouble() / HOUR) else Double.NaN
    fun leftMixed(): Long = hoursLeft(rateAvg())
    fun leftOn(): Long = hoursLeft(rateOn())
    fun projectedSot(): Long = if (used() >= 2) (sot * 100 / used()).toLong() else -1

    private fun hoursLeft(rate: Double): Long {
        if (charging || rate.isNaN() || rate <= 0) return -1
        return (level / rate * HOUR).toLong()
    }

    companion object {
        const val MIN = 60_000L
        const val HOUR = 3_600_000L

        fun compute(b: List<Store.B>, screen: List<LongArray>, full: Boolean, now: Long): Stats =
            compute(b, screen, full, -1, now)

        /** "Today": from midnight (or the first sample after it) to now, charges included. */
        fun today(b: List<Store.B>, screen: List<LongArray>, now: Long): Stats =
            compute(b, screen, false, midnight(now), now)

        /** Local midnight at the start of t's day. */
        fun midnight(t: Long): Long = Calendar.getInstance().run {
            timeInMillis = t
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            timeInMillis
        }

        /** One whole day [from, to): the samples up to its end, charges included, as if "now" were its end. */
        fun day(b: List<Store.B>, screen: List<LongArray>, from: Long, to: Long, now: Long): Stats {
            var k = 0
            while (k < b.size && b[k].t < to) k++
            return compute(b.subList(0, k), screen, false, from, minOf(to, now))
        }

        private fun compute(b: List<Store.B>, screen: List<LongArray>, full: Boolean, since: Long, now: Long): Stats {
            val s = Stats()
            s.now = now
            s.fullMode = full
            s.start = now
            s.end = now
            val n = b.size
            if (n == 0) {
                s.empty = true
                return s
            }
            val last = b[n - 1]
            s.level = last.level

            var endIdx = n // samples [si, endIdx) belong to the session
            if (last.plug != 0) {
                var k = n - 1
                while (k > 0 && b[k - 1].plug != 0) k--
                s.charging = true
                s.chgStart = b[k].t
                s.chgStartLevel = b[k].level
                endIdx = k
                s.end = b[k].t
            }

            var si = -1
            if (since > 0) {
                si = 0
                for (i in endIdx - 1 downTo 0) if (b[i].t <= since) {
                    si = i
                    break
                }
                if (si >= endIdx) {
                    s.empty = true
                    return s
                }
            } else if (full) {
                for (i in endIdx - 1 downTo 0) {
                    if (b[i].full()) {
                        for (j in i + 1 until endIdx) if (b[j].plug == 0) {
                            si = j
                            break
                        }
                        break
                    }
                }
            } else {
                for (i in endIdx - 1 downTo 0) {
                    if (b[i].plug == 0 && (i == 0 || b[i - 1].plug != 0)) {
                        si = i
                        break
                    }
                }
            }
            if (since <= 0 && (si < 0 || (si == 0 && !full))) {
                var f = 0
                while (f < endIdx && b[f].plug != 0) f++
                if (si < 0) si = f
                s.fallback = si == 0 || si == f
            }
            if (si >= endIdx) {
                s.empty = true
                return s
            }

            s.start = maxOf(b[si].t, since)
            s.startLevel = b[si].level
            s.endLevel = if (s.charging) b[endIdx].level else last.level
            if (s.end < s.start) s.end = s.start

            val pts = endIdx - si + 1
            s.gt = LongArray(pts)
            s.gl = IntArray(pts)
            s.gc = BooleanArray(pts)
            for (i in si until endIdx) {
                val x = b[i]
                s.gt[i - si] = maxOf(x.t, s.start)
                s.gl[i - si] = x.level
                s.gc[i - si] = x.plug != 0
            }
            s.gt[pts - 1] = s.end
            s.gl[pts - 1] = s.endLevel
            if (s.charging) {
                // Draw the charge in progress after the session, up to now.
                val extra = n - endIdx
                s.gt = s.gt.copyOf(pts + extra)
                s.gl = s.gl.copyOf(pts + extra)
                s.gc = s.gc.copyOf(pts + extra)
                for (i in pts - 1 until pts + extra) s.gc[i] = true
                for (i in endIdx + 1 until n) {
                    s.gt[pts - 1 + i - endIdx] = b[i].t
                    s.gl[pts - 1 + i - endIdx] = b[i].level
                }
                s.gt[pts + extra - 1] = maxOf(now, s.gt[pts + extra - 2])
                s.gl[pts + extra - 1] = last.level
            }

            // Clip screen-on intervals to the session.
            val clip = ArrayList<LongArray>()
            for (iv in screen) {
                val a = maxOf(iv[0], s.start)
                val z = minOf(if (iv[1] < 0) now else iv[1], s.end)
                if (z > a) {
                    clip.add(longArrayOf(a, z))
                    s.sot += z - a
                    s.longestOn = maxOf(s.longestOn, z - a)
                    s.ons++
                }
            }
            s.on = LongArray(clip.size * 2)
            for (i in clip.indices) {
                s.on[2 * i] = clip[i][0]
                s.on[2 * i + 1] = clip[i][1]
            }
            s.off = maxOf(0, s.length() - s.sot)
            var dis = 0L
            for (i in 0 until s.gt.size - 1) {
                if (s.gc[i]) continue
                dis += s.gt[i + 1] - s.gt[i]
                s.disOn += onTime(s.on, s.gt[i], s.gt[i + 1])
            }
            s.disOff = maxOf(0, dis - s.disOn)

            // Each 1% drop is split between screen on and off by how much of its span the screen was on.
            val stop = if (s.charging) endIdx else endIdx - 1
            for (i in si until stop) {
                val a = b[i]
                val z = b[i + 1]
                val d = a.level - z.level
                if (a.plug == 0 && d > 0) {
                    val from = maxOf(a.t, s.start)
                    val span = z.t - from
                    val f = if (span > 0) onTime(s.on, from, z.t).toDouble() / span else 0.0
                    s.usedOn += d * f
                    s.usedOff += d * (1 - f)
                } else if (a.plug != 0 && d < 0) {
                    s.gained -= d
                }
            }
            return s
        }

        fun onTime(on: LongArray, from: Long, to: Long): Long {
            var t = 0L
            var i = 0
            while (i < on.size) {
                val a = maxOf(on[i], from)
                val z = minOf(on[i + 1], to)
                if (z > a) t += z - a
                i += 2
            }
            return t
        }

        /** Discharge sessions, newest first: {start, end, startLevel, endLevel}; end = -1 for the one still running. */
        fun history(b: List<Store.B>, max: Int): List<LongArray> {
            val out = ArrayList<LongArray>()
            var i = 1
            while (i < b.size) {
                if (b[i].plug != 0 || b[i - 1].plug == 0) {
                    i++
                    continue
                }
                var j = i + 1
                while (j < b.size && b[j].plug == 0) j++
                if (j >= b.size) {
                    out.add(0, longArrayOf(b[i].t, -1, b[i].level.toLong(), b[b.size - 1].level.toLong()))
                    break
                }
                out.add(0, longArrayOf(b[i].t, b[j].t, b[i].level.toLong(), b[j].level.toLong()))
                i = j + 1
            }
            while (out.size > max) out.removeAt(out.size - 1)
            return out
        }

        fun sotBetween(screen: List<LongArray>, from: Long, to: Long, now: Long): Long {
            var t = 0L
            for (iv in screen) {
                val a = maxOf(iv[0], from)
                val z = minOf(if (iv[1] < 0) now else iv[1], to)
                if (z > a) t += z - a
            }
            return t
        }

        fun dur(ms: Long): String {
            if (ms < 0) return "–"
            var m = ms / MIN
            val h = m / 60
            m %= 60
            if (h >= 24) return "${h / 24}d ${h % 24}h"
            return if (h > 0) "${h}h ${m}m" else "${m}m"
        }

        fun pct(p: Double): String =
            String.format(Locale.US, if (p >= 10 || p == 0.0) "%.0f%%" else "%.1f%%", p)
    }
}
