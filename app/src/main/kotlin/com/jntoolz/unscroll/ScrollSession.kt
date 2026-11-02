package com.jntoolz.unscroll

/**
 * Tracks continuous scrolling in one app. Scrolling is continuous while scroll events from the same app
 * arrive less than [gapMs] apart. A different app or a longer pause starts a new session.
 */
class ScrollSession(private val gapMs: Long = 60_000L) {
    private var pkg: String? = null
    private var start = 0L
    private var last = 0L
    private var warned = 0

    /** Whole minutes scrolled in the current session as of the last event. */
    val minutes: Int get() = ((last - start) / 60_000L).toInt()

    /** Feeds one scroll event. Returns true when a warning is due now (at N, 2N, 3N ... minutes). */
    fun onScroll(pkg: String, now: Long, thresholdMs: Long): Boolean {
        if (pkg != this.pkg || now - last > gapMs || now < last) {
            this.pkg = pkg
            start = now
            warned = 0
        }
        last = now
        if (thresholdMs <= 0L) return false
        if (now - start >= thresholdMs * (warned + 1)) {
            warned++
            return true
        }
        return false
    }

    fun reset() {
        pkg = null
        warned = 0
    }
}
