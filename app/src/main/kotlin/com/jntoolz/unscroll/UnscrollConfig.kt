package com.jntoolz.unscroll

/** An app with its own scroll-warning switch. [packages] are the Android packages behind it. */
enum class WarnApp(val label: String, val packages: Set<String>) {
    YOUTUBE("YouTube", setOf(ShortsRules.YOUTUBE)),
    INSTAGRAM("Instagram", setOf(ShortsRules.INSTAGRAM)),
    TIKTOK("TikTok", ShortsRules.TIKTOK),
    FACEBOOK("Facebook", setOf(ShortsRules.FACEBOOK)),
    REDDIT("Reddit", setOf(ShortsRules.REDDIT)),
    LINKEDIN("LinkedIn", setOf(ShortsRules.LINKEDIN));

    /** Preference key of this app's switch. */
    val key: String get() = "warn_" + name.lowercase()

    companion object {
        private val BY_PACKAGE: Map<String, WarnApp> =
            entries.flatMap { a -> a.packages.map { it to a } }.toMap()

        fun of(pkg: String): WarnApp? = BY_PACKAGE[pkg]

        /** The apps whose switch is worth showing: at least one of their packages is installed. */
        fun installed(isInstalled: (String) -> Boolean): List<WarnApp> =
            entries.filter { a -> a.packages.any(isInstalled) }
    }
}

/** Which block switches to show: Shorts needs YouTube, Reels Instagram or Facebook, TikTok needs TikTok. */
fun installedKinds(isInstalled: (String) -> Boolean): List<ShortKind> = buildList {
    if (isInstalled(ShortsRules.YOUTUBE)) add(ShortKind.SHORTS)
    if (isInstalled(ShortsRules.INSTAGRAM) || isInstalled(ShortsRules.FACEBOOK)) add(ShortKind.REELS)
    if (ShortsRules.TIKTOK.any(isInstalled)) add(ShortKind.TIKTOK)
}

/** An immutable snapshot of the settings, cached by the service so events never touch SharedPreferences. */
data class UnscrollConfig(
    val mode: GuardMode = GuardMode.OFF,
    val blockShorts: Boolean = true,
    val blockReels: Boolean = true,
    val blockTikTok: Boolean = true,
    val warnMinutes: Int = 10,
    val warnApps: Set<WarnApp> = WarnApp.entries.toSet(),
) {
    /** SOFT and HARD block alike; only OFF does nothing. */
    val enabled: Boolean get() = mode.blocking

    /** Whether blocking [kind] is switched on. */
    fun blocks(kind: ShortKind): Boolean = when (kind) {
        ShortKind.SHORTS -> blockShorts
        ShortKind.REELS -> blockReels
        ShortKind.TIKTOK -> blockTikTok
    }

    /** Whether the short-form part of [pkg] is blocked right now. */
    fun blocksPackage(pkg: String): Boolean = enabled && when (pkg) {
        ShortsRules.YOUTUBE -> blockShorts
        ShortsRules.INSTAGRAM, ShortsRules.FACEBOOK -> blockReels
        in ShortsRules.TIKTOK -> blockTikTok
        else -> false
    }

    /** Whether long scrolling in [pkg] triggers a warning. */
    fun warns(pkg: String): Boolean {
        val a = WarnApp.of(pkg) ?: return false
        return enabled && a in warnApps
    }
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
