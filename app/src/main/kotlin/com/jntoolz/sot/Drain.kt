package com.jntoolz.sot

/** Drain and charge speed from the level-change samples. Pure logic, no Android. */
object Drain {
    const val WINDOW = 2 * Stats.HOUR
    private const val RECENT = 30 * Stats.MIN // recent part of the current charge used for "Full in"
    private const val TAPER_FROM = 80          // above this, charging is slower
    private const val TAPER = 1.5              // each remaining percent above TAPER_FROM counts this much
    private const val MIN_PCT = 2              // percent needed before a number is shown

    /** Average ms to drain 1% over [window] before [now], discharge only; -1 if under 2% dropped. */
    fun perPercent(b: List<Store.B>, now: Long, window: Long = WINDOW): Long =
        rate(b, now - window, now, false)

    /** Average ms to charge 1% over [window] before [now], charging only; -1 if under 2% gained. */
    fun chargePerPercent(b: List<Store.B>, now: Long, window: Long = WINDOW): Long =
        rate(b, now - window, now, true)

    /**
     * Time until 100% while charging, -1 if unknown. The rate is the last ~30 min of the current charge,
     * or the whole current charge if that gained under 2%. Taper: the remaining percent above 80 count 1.5x
     * (the rate is not corrected for taper already inside the measured span).
     */
    fun fullIn(b: List<Store.B>, now: Long): Long {
        val n = b.size
        if (n == 0 || b[n - 1].plug == 0) return -1
        var k = n - 1
        while (k > 0 && b[k - 1].plug != 0) k--
        var r = rate(b, maxOf(b[k].t, now - RECENT), now, true)
        if (r < 0) r = rate(b, b[k].t, now, true)
        if (r < 0) return -1
        val level = b[n - 1].level
        val below = maxOf(0, TAPER_FROM - level)
        val above = 100 - maxOf(level, TAPER_FROM)
        return (r * (below + TAPER * maxOf(0, above))).toLong()
    }

    /**
     * Sums, over consecutive sample pairs that both start at/after [from] (the level samples mark the moment
     * the level changed; a pair starting at a plug change is skipped) and end by [now], the time and percent of those moving the right way in the right
     * state. Pairs the wrong way (level rising while unplugged), or across a plug change, are skipped whole.
     */
    private fun rate(b: List<Store.B>, from: Long, now: Long, charging: Boolean): Long {
        var ms = 0L
        var pct = 0
        for (i in 0 until b.size - 1) {
            val a = b[i]
            val z = b[i + 1]
            if (a.t < from || z.t > now || z.t <= a.t) continue
            if ((a.plug != 0) != charging || (z.plug != 0) != charging) continue
            // a must itself be a level change: the span from a plug change (or the first sample) to the next
            // change is only part of a percent, and counting it as a whole one would skew the average.
            if (i == 0 || (b[i - 1].plug != 0) != charging) continue
            val d = if (charging) z.level - a.level else a.level - z.level
            if (d <= 0) continue
            ms += z.t - a.t
            pct += d
        }
        return if (pct >= MIN_PCT) ms / pct else -1
    }

    /** "4m 30s" under 10 minutes, else like [Stats.dur]; "–" for unknown. */
    fun short(ms: Long): String {
        if (ms < 0) return "–"
        if (ms >= 10 * Stats.MIN) return Stats.dur(ms)
        val s = (ms + 500) / 1000
        return if (s < 60) "${s}s" else "${s / 60}m ${s % 60}s"
    }
}
