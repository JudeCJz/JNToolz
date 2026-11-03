package com.jntoolz.unscroll

/** An immutable snapshot of the settings, cached by the service so events never touch SharedPreferences. */
data class UnscrollConfig(
    val mode: GuardMode = GuardMode.OFF,
    val warnMinutes: Int = 10,
    val onApps: Set<AddictiveApp> = AddictiveApp.entries.toSet(),
    val pausedUntil: Long = 0L,
) {
    /** SOFT and HARD block alike; only OFF does nothing. */
    val enabled: Boolean get() = mode.blocking

    /** Whether a SOFT pause is running at wall-clock [now] (ms). HARD and OFF never pause. */
    fun pausedAt(now: Long) = mode == GuardMode.SOFT && now < pausedUntil

    /** Whether the guard acts at [now]: on and not paused. Ends a pause on time with no setting change. */
    fun activeAt(now: Long = System.currentTimeMillis()): Boolean = enabled && !pausedAt(now)

    /** Whether the switch of [pkg]'s app is on. */
    private fun appOn(pkg: String): AddictiveApp? = AddictiveApp.of(pkg)?.takeIf { it in onApps }

    /** Whether the short-form part (tabs) or the whole of [pkg] is blocked right now. */
    fun blocksPackage(pkg: String, now: Long = System.currentTimeMillis()): Boolean {
        val a = appOn(pkg) ?: return false
        return activeAt(now) && (a.guard == Guard.TABS || a.guard == Guard.WHOLE)
    }

    /** Whether long scrolling in [pkg] triggers a warning. */
    fun warns(pkg: String, now: Long = System.currentTimeMillis()): Boolean =
        appOn(pkg) != null && activeAt(now)
}

/** Gives up when blocks fire too often, so the user is never trapped in a back / home loop. */
class BlockLimiter(
    private val max: Int = 3,
    private val windowMs: Long = 6_000L,
    private val coolMs: Long = 10_000L,
) {
    private var windowStart = 0L
    private var count = 0
    private var coolUntil = 0L

    /** True when a block may fire at [now]. The call after [max] blocks inside the window starts a cooldown. */
    fun allow(now: Long): Boolean {
        if (now < coolUntil) return false
        if (now - windowStart > windowMs || now < windowStart) {
            windowStart = now
            count = 0
        }
        if (count >= max) {
            coolUntil = now + coolMs
            count = 0
            return false
        }
        count++
        return true
    }
}

/** The escalation when a short-form screen survives: BACK, BACK, then HOME, then give up. */
object Escalation {
    enum class Step { BACK, HOME }

    /** The step for [attempt] (0 is the first); null once everything was tried. */
    fun step(attempt: Int): Step? = when {
        attempt < 0 -> null
        attempt < 2 -> Step.BACK
        attempt == 2 -> Step.HOME
        else -> null
    }
}

/** A screen rectangle for the tap-blocking overlay; [snap] only accepts one that looks like a nav-bar tab. */
data class TabBox(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top

    /** This box cut to a screen of [w] x [h]. */
    fun clampedTo(w: Int, h: Int) = TabBox(maxOf(0, left), maxOf(0, top), minOf(w, right), minOf(h, bottom))

    /** In the top or bottom fifth of the screen, where tab bars live (a "Shorts" shelf header is not). */
    fun inNavBand(h: Int): Boolean = top >= h * 4 / 5 || bottom <= h / 5

    /** Small enough to be one tab, not a whole bar or screen. */
    fun isTabSized(w: Int, h: Int): Boolean = width > 0 && height > 0 && width <= w / 2 && height <= h / 5

    companion object {
        /** [raw] clamped to the screen, or null when it is not a plausible tab there. */
        fun snap(raw: TabBox, w: Int, h: Int): TabBox? {
            val b = raw.clampedTo(w, h)
            return if (b.isTabSized(w, h) && b.inNavBand(h)) b else null
        }
    }
}
