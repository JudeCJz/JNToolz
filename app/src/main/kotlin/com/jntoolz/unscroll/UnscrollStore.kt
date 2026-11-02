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
        set(v) = prefs.edit().putString("mode", v.name).putBoolean("enabled", v != GuardMode.OFF).apply()

    /** The tool switch ([com.jntoolz.tools.Tools]). Turning on from OFF gives SOFT; turning off is refused while HARD. */
    var enabled: Boolean
        get() = mode.blocking
        set(v) {
            val m = mode
            if (v && m == GuardMode.OFF) mode = GuardMode.SOFT
            else if (!v && m == GuardMode.SOFT) mode = GuardMode.OFF
        }

    val hard: Boolean get() = mode == GuardMode.HARD

    var blockShorts: Boolean
        get() = prefs.getBoolean("block_shorts", true)
        set(v) = prefs.edit().putBoolean("block_shorts", v).apply()

    var blockReels: Boolean
        get() = prefs.getBoolean("block_reels", true)
        set(v) = prefs.edit().putBoolean("block_reels", v).apply()

    var blockTikTok: Boolean
        get() = prefs.getBoolean("block_tiktok", true)
        set(v) = prefs.edit().putBoolean("block_tiktok", v).apply()

    var warnMinutes: Int
        get() = prefs.getInt("warn_minutes", 10).takeIf { it in MINUTES } ?: 10
        set(v) = prefs.edit().putInt("warn_minutes", v).apply()

    /** Whether the scroll warning is on for [app]; defaults to the old single switch so nothing changes on update. */
    fun warns(app: WarnApp): Boolean = prefs.getBoolean(app.key, prefs.getBoolean("scroll_warning", true))

    fun setWarns(app: WarnApp, on: Boolean) = prefs.edit().putBoolean(app.key, on).apply()

    /** Whether blocking [kind] is switched on. */
    fun blocks(kind: ShortKind): Boolean = when (kind) {
        ShortKind.SHORTS -> blockShorts
        ShortKind.REELS -> blockReels
        ShortKind.TIKTOK -> blockTikTok
    }

    fun setBlocks(kind: ShortKind, on: Boolean) = when (kind) {
        ShortKind.SHORTS -> blockShorts = on
        ShortKind.REELS -> blockReels = on
        ShortKind.TIKTOK -> blockTikTok = on
    }

    private fun load() = UnscrollConfig(
        mode = mode,
        blockShorts = blockShorts,
        blockReels = blockReels,
        blockTikTok = blockTikTok,
        warnMinutes = warnMinutes,
        warnApps = WarnApp.entries.filterTo(HashSet()) { warns(it) },
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

    companion object {
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
