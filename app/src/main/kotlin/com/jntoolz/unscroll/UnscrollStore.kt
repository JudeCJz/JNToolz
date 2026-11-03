package com.jntoolz.unscroll

import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.provider.Settings
import java.time.LocalDate

/** Unscroll settings in the "unscroll" prefs file. The hub toggle is [mode] (default OFF); [enabled] = not OFF. */
class UnscrollStore(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    @Volatile private var cached: UnscrollConfig? = null
    private var onChange: ((UnscrollConfig) -> Unit)? = null

    // Held strongly: SharedPreferences keeps its listeners weakly.
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "blocks" || key == "blocks_day") return@OnSharedPreferenceChangeListener
        val c = load()
        cached = c
        onChange?.invoke(c)
    }

    /** Saves before this existed only had "enabled": on reads as SOFT. "enabled" is still written for old readers. */
    var mode: GuardMode
        get() = GuardMode.restore(prefs.getString("mode", null), prefs.getBoolean("enabled", false))
        set(v) {
            val e = prefs.edit().putString("mode", v.name).putBoolean("enabled", v != GuardMode.OFF)
            if (v != GuardMode.SOFT) e.putLong("paused_until", 0L) // a pause only exists in SOFT
            e.apply()
        }

    /** The tool switch ([com.jntoolz.tools.Tools]). Turning on from OFF gives SOFT; turning off is refused while HARD. */
    var enabled: Boolean
        get() = mode.blocking
        set(v) {
            val m = mode
            if (v && m == GuardMode.OFF) mode = GuardMode.SOFT
            else if (!v && m == GuardMode.SOFT) mode = GuardMode.OFF
        }

    val hard: Boolean get() = mode == GuardMode.HARD

    var warnMinutes: Int
        get() = prefs.getInt("warn_minutes", 10).takeIf { it in MINUTES } ?: 10
        set(v) = prefs.edit().putInt("warn_minutes", v).apply()

    /**
     * Whether the switch of [app] is on. Default on; a missing key is derived from the old block / warn keys
     * (see [AddictiveApp.legacyOn]) so updates keep their choices.
     */
    fun on(app: AddictiveApp): Boolean =
        if (prefs.contains(app.key)) prefs.getBoolean(app.key, true) else app.legacyOn(prefs::getBoolean)

    fun setOn(app: AddictiveApp, on: Boolean) = prefs.edit().putBoolean(app.key, on).apply()

    private fun load() = UnscrollConfig(
        mode = mode,
        warnMinutes = warnMinutes,
        onApps = AddictiveApp.entries.filterTo(HashSet()) { on(it) },
        pausedUntil = pausedUntil,
    )

    /** The current settings. Cheap once [watch] was called: served from memory. */
    val config: UnscrollConfig get() = cached ?: load()

    /** Starts keeping [config] in memory, refreshed when a setting changes. [changed] runs on the main thread. */
    fun watch(changed: (UnscrollConfig) -> Unit) {
        onChange = changed
        cached = load()
        prefs.unregisterOnSharedPreferenceChangeListener(listener)
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    fun unwatch() {
        prefs.unregisterOnSharedPreferenceChangeListener(listener)
        onChange = null
    }

    /** Blocks counted today (resets on a new day). */
    val blocksToday: Int
        get() = if (prefs.getString("blocks_day", null) == LocalDate.now().toString()) prefs.getInt("blocks", 0) else 0

    @Synchronized
    fun countBlock() {
        val today = LocalDate.now().toString()
        val n = if (prefs.getString("blocks_day", null) == today) prefs.getInt("blocks", 0) else 0
        prefs.edit().putString("blocks_day", today).putInt("blocks", n + 1).apply()
    }

    /** Wall-clock ms until which a SOFT pause keeps blocking off; 0 when not paused. Ends on its own. */
    val pausedUntil: Long get() = prefs.getLong("paused_until", 0L)

    fun isPaused(now: Long = System.currentTimeMillis()): Boolean = mode == GuardMode.SOFT && now < pausedUntil

    /** Pauses started today (resets on a new day). */
    val pausesToday: Int
        get() = if (prefs.getString("pauses_day", null) == LocalDate.now().toString()) prefs.getInt("pauses", 0) else 0

    /** Starts a pause of [minutes] (one of [PAUSE_MINUTES]); refused outside SOFT, while paused, or after [PAUSES_PER_DAY]. */
    @Synchronized
    fun pause(minutes: Int, now: Long = System.currentTimeMillis()): Boolean {
        if (minutes !in PAUSE_MINUTES || mode != GuardMode.SOFT || isPaused(now) || pausesToday >= PAUSES_PER_DAY) return false
        prefs.edit()
            .putString("pauses_day", LocalDate.now().toString())
            .putInt("pauses", pausesToday + 1)
            .putLong("paused_until", now + minutes * 60_000L)
            .apply()
        return true
    }

    /** Ends a pause early. The pause still counts toward today's limit. */
    fun resume() = prefs.edit().putLong("paused_until", 0L).apply()

    /** The accessibility service is only switched on while a watched app is open (see AppGate); always true. */
    val appScoped: Boolean get() = true

    companion object {
        val PAUSE_MINUTES = listOf(5, 10, 15)
        const val PAUSES_PER_DAY = 3
        const val FILE = "unscroll"
        val MINUTES = listOf(5, 10, 15, 20, 30)
        const val SERVICE_CLASS = "com.jntoolz.unscroll.UnscrollService"

        /** Whether [pkg] is installed (needs the manifest <queries> entries on Android 11+). */
        fun isInstalled(c: Context, pkg: String): Boolean = try {
            c.packageManager.getPackageInfo(pkg, 0)
            true
        } catch (e: Exception) {
            false
        }

        /** Whether the user has switched the accessibility service on in system Settings. */
        fun isServiceOn(c: Context): Boolean {
            val flat = ComponentName(c.packageName, SERVICE_CLASS).flattenToString()
            val list = try {
                Settings.Secure.getString(c.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            } catch (e: Exception) {
                null
            } ?: return false
            return list.split(':').any { it.equals(flat, true) }
        }
    }
}
