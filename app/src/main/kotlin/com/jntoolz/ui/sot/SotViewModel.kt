package com.jntoolz.ui.sot

import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jntoolz.sot.Apps
import com.jntoolz.sot.DeviceInfo
import com.jntoolz.sot.Drain
import com.jntoolz.sot.GraphExport
import com.jntoolz.sot.Health
import com.jntoolz.sot.SotTool
import com.jntoolz.sot.Stats
import com.jntoolz.sot.Store
import com.jntoolz.sot.Tips
import com.jntoolz.sot.TrackerService
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import com.jntoolz.sot.Usage
import com.jntoolz.ui.theme.NothingFonts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

internal const val NOTIF = "android.permission.POST_NOTIFICATIONS"
internal const val BT = "android.permission.BLUETOOTH_CONNECT"

/** One day of History; [graph] is filled only for the open day. */
internal class DayUi(
    val from: Long,
    val to: Long,
    val name: String,
    val sot: String,
    val graph: Stats?,
    val rangeFrom: Long,
    val rangeTo: Long
)

internal class AppRow(val icon: ImageBitmap?, val name: String, val pct: String)

internal sealed interface AppsUi {
    data object NoAccess : AppsUi
    data object NotStarted : AppsUi
    class Rows(val rows: List<AppRow>) : AppsUi
}

internal data class SettingsUi(
    val usage: Boolean = false,
    val background: Boolean = false,
    val notif: Boolean = false,
    val bluetooth: Boolean = false,
    val startToday: Boolean = false
)

/**
 * State of the SOT tool. Work runs only while the screen is resumed ([whileResumed]): stats are recomputed
 * when the store changes and on a 15 s clock tick, all disk and system reads off the main thread.
 */
internal class SotViewModel(app: Application) : AndroidViewModel(app) {
    private val ctx: Context get() = getApplication()
    private val prefs = SotTool.prefs(app)

    var settingsOpen by mutableStateOf(false)
        private set
    var today by mutableStateOf(prefs.getBoolean("openToday", false))
        private set
    var stats by mutableStateOf<Stats?>(null)
        private set
    var perPercent by mutableLongStateOf(-1L) // ms per 1% drained (charged while charging), last 2 h; -1 unknown
        private set
    var fullIn by mutableLongStateOf(-1L) // ms until 100% while charging; -1 unknown
        private set
    var health by mutableStateOf("–")
        private set
    var apps by mutableStateOf<AppsUi?>(null)
        private set
    var tips by mutableStateOf<List<Tips.Tip>>(emptyList())
        private set
    var days by mutableStateOf<List<DayUi>>(emptyList())
        private set
    var openDay by mutableLongStateOf(0L)
        private set
    var settings by mutableStateOf(SettingsUi())
        private set

    private val poke = MutableStateFlow(0)
    private var usage: List<LongArray>? = null
    private var usageFrom = Long.MAX_VALUE
    private var usageAt = 0L
    private var usageTo = 0L // the end of the last successful query; the next one resumes from there
    private var appsAt = 0L
    private var usageBusy = false
    private var appsBusy = false
    private var healthVersion = -1
    private var historyKey: Triple<Long, Long, Long>? = null

    /** The tool only opens while it is on (hub switch); make sure the tracker runs, and load the fonts. */
    suspend fun enter() = withContext(Dispatchers.IO) {
        TrackerService.start(ctx)
        NothingFonts.warm()
    }

    /** Leaving the tool: next time it opens on the dashboard, counting the way Settings says. */
    fun leave() {
        settingsOpen = false
        today = prefs.getBoolean("openToday", false)
    }

    /** Runs until cancelled; call while the lifecycle is RESUMED. */
    suspend fun whileResumed() {
        usageAt = 0
        appsAt = 0
        historyKey = null
        recheck()
        val store = withContext(Dispatchers.IO) { Store.get(ctx) }
        val clock = flow { while (true) { emit(Unit); delay(15_000) } }
        combine(store.changes, clock, poke) { _, _, _ -> }.conflate().collect { refresh(store) }
    }

    /** Opens or closes the settings page; opening re-reads the tips and permissions. */
    fun showSettings(on: Boolean) {
        settingsOpen = on
        if (on) {
            viewModelScope.launch { loadTips() }
            viewModelScope.launch { loadSettings() }
        }
    }

    fun pickToday(on: Boolean) {
        today = on
        appsAt = 0
        poke.value++
    }

    fun toggleDay(from: Long) {
        openDay = if (openDay == from) 0 else from
        poke.value++
    }

    fun setStartToday(on: Boolean) {
        settings = settings.copy(startToday = on)
        viewModelScope.launch(Dispatchers.IO) { prefs.edit().putBoolean("openToday", on).apply() }
    }

    fun askedNotif(): Boolean = prefs.getBoolean("askedNotif", false)
    fun markAskedNotif() = prefs.edit().putBoolean("askedNotif", true).apply()
    fun askedBt(): Boolean = prefs.getBoolean("askedBt", false)
    fun markAskedBt() = prefs.edit().putBoolean("askedBt", true).apply()

    /** The graph's data (the range now shown) as text for the clipboard. Blocking work runs off the main thread. */
    suspend fun graphText(): String {
        val st = stats ?: return ""
        return exportText(st, if (today) "today" else "since unplug", perPercent, st.charging)
    }

    /** One History day's graph data as text for the clipboard. */
    suspend fun dayText(day: DayUi): String {
        val st = day.graph ?: return ""
        return exportText(st, day.name.lowercase(), -1L, false)
    }

    private suspend fun exportText(st: Stats, range: String, pp: Long, charging: Boolean): String =
        withContext(Dispatchers.IO) {
            val to = st.now
            val sn = Store.get(ctx).snap()
            val exact = Usage.allowed(ctx)
            // The usage cache only reaches back to usageFrom; an older day falls back to the tracker's screen log.
            val screen = usage.takeIf { exact && st.start >= usageFrom } ?: sn.screen
            var ivs: List<GraphExport.AppIv>? = null
            var totals: List<GraphExport.AppTotal>? = null
            if (exact && !st.empty) {
                ivs = appIntervals(st.start, to, sn.current)
                totals = Apps.top(ctx, st.start, to, sn.current, st.usedOn, 0)
                    .map { GraphExport.AppTotal(it.label.toString(), it.fgMs, it.mAh, it.pct) }
            }
            val devInfo = DeviceInfo.summary(ctx, sn.bat)
            GraphExport.build(st, range, sn.bat, screen, sn.current, ivs, totals, pp, charging, deviceHeader = devInfo)
        }

    /** Foreground intervals of every app in [from, to] with their estimated charge. Blocking. */
    private fun appIntervals(from: Long, to: Long, current: List<LongArray>): List<GraphExport.AppIv> {
        val qFrom = maxOf(0L, from - Apps.LOOKBACK)
        val t = ArrayList<Long>()
        val type = ArrayList<Int>()
        val pkg = ArrayList<String?>()
        val cls = ArrayList<String?>()
        try {
            val ev = ctx.getSystemService(UsageStatsManager::class.java).queryEvents(qFrom, to)
            val e = UsageEvents.Event()
            while (ev.hasNextEvent()) {
                ev.getNextEvent(e)
                val k = e.eventType
                if (k != Apps.RESUMED && k != Apps.PAUSED && k != Apps.SCREEN_OFF && k != Apps.SHUTDOWN) continue
                t += e.timeStamp
                type += k
                pkg += e.packageName
                cls += e.className
            }
        } catch (e: RuntimeException) {
            return emptyList()
        }
        val map = Apps.intervals(t.toLongArray(), type.toIntArray(), pkg.toTypedArray(), cls.toTypedArray(), qFrom, from, to)
        val cv = Apps.Curve(current)
        val pm = ctx.packageManager
        val skip = setOf(ctx.packageName, "com.android.systemui", "android")
        val out = ArrayList<GraphExport.AppIv>()
        for ((p, list) in map) {
            if (p in skip) continue
            val label = try { pm.getApplicationLabel(pm.getApplicationInfo(p, 0)).toString() } catch (e: Exception) { p }
            for (iv in list) out += GraphExport.AppIv(label, iv[0], iv[1], cv.mah(listOf(iv)))
        }
        return out
    }

    /** Permissions or settings may have changed (on resume, after a permission dialog). */
    fun recheck() {
        viewModelScope.launch {
            loadSettings()
            loadTips()
            withContext(Dispatchers.IO) { TrackerService.start(ctx) }
        }
    }

    private fun granted(perm: String) = ctx.checkSelfPermission(perm) == PackageManager.PERMISSION_GRANTED

    private suspend fun loadSettings() {
        settings = withContext(Dispatchers.IO) {
            SettingsUi(
                usage = Usage.allowed(ctx),
                background = ctx.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(ctx.packageName),
                notif = Build.VERSION.SDK_INT < 33 || granted(NOTIF),
                bluetooth = granted(BT),
                startToday = prefs.getBoolean("openToday", false)
            )
        }
    }

    private suspend fun loadTips() {
        tips = withContext(Dispatchers.IO) { Tips.check(ctx) }
    }

    private suspend fun refresh(store: Store) {
        val now = System.currentTimeMillis()
        // Battery and screen history only: a current sample (every 30 s) re-copies nothing here.
        val (sn, exact) = withContext(Dispatchers.IO) { store.snapData() to Usage.allowed(ctx) }
        val screen = usage.takeIf { exact } ?: sn.screen
        val td = today
        val st = withContext(Dispatchers.Default) {
            if (td) Stats.today(sn.bat, screen, now) else Stats.compute(sn.bat, screen, false, now)
        }
        stats = st
        if (st.charging) {
            perPercent = Drain.chargePerPercent(sn.bat, now)
            fullIn = Drain.fullIn(sn.bat, now)
        } else {
            perPercent = Drain.perPercent(sn.bat, now)
            fullIn = -1
        }
        val need = (if (st.empty) now - 6 * Stats.HOUR else st.start) - Stats.HOUR
        if (exact && !usageBusy && (now - usageAt > 30_000 || need < usageFrom)) loadUsage(need)

        if (sn.batVersion != healthVersion) {
            healthVersion = sn.batVersion
            health = withContext(Dispatchers.IO) { DeviceInfo.batteryHealth(ctx, sn.bat) }
        }
        val key = Triple(sn.dataVersion, openDay, Stats.midnight(now))
        if (key != historyKey) {
            historyKey = key
            val open = openDay
            days = withContext(Dispatchers.Default) { history(sn, now, open) }
        }
        if (!appsBusy && now - appsAt > 60_000) loadApps(store, st, exact)
    }

    /**
     * Screen intervals from Usage access. The first query (or one reaching further back) reads everything from
     * [need]; after that only from where the last one ended (less a small overlap), merged into the cache.
     */
    private fun loadUsage(need: Long) {
        usageBusy = true
        val old = usage
        val whole = old == null || need < usageFrom
        val from = if (whole) need else Usage.resumeAt(old!!, usageTo - Usage.OVERLAP)
        viewModelScope.launch {
            val to = System.currentTimeMillis()
            val r = withContext(Dispatchers.IO) { Usage.screen(ctx, from, to) }
            if (r != null) {
                usage = if (whole) r else Usage.merge(old!!, r, from)
                if (whole) usageFrom = need
                usageTo = to
            }
            usageAt = System.currentTimeMillis()
            usageBusy = false
            if (r != null) poke.value++
        }
    }

    private fun loadApps(store: Store, st: Stats, exact: Boolean) {
        appsAt = System.currentTimeMillis()
        if (!exact) { apps = AppsUi.NoAccess; return }
        if (st.empty) { apps = AppsUi.NotStarted; return }
        appsBusy = true
        val px = (32 * ctx.resources.displayMetrics.density).roundToInt()
        viewModelScope.launch {
            val rows = withContext(Dispatchers.IO) {
                val current = store.snap().current // the only reader of the current samples
                val out = ArrayList<AppRow>()
                for (u in Apps.top(ctx, st.start, st.end, current, st.usedOn, 8)) {
                    if (u.pct < 0.1) continue
                    out += AppRow(u.icon?.toBitmap(px, px)?.asImageBitmap(), u.label.toString(), Stats.pct(u.pct))
                }
                if (st.usedOff >= 0.5) out += AppRow(null, "Screen off", Stats.pct(st.usedOff))
                out
            }
            appsBusy = false
            apps = AppsUi.Rows(rows)
        }
    }

    /** One row per day, newest first, up to 30 days; the open one also gets its graph. */
    private fun history(sn: Store.Snap, now: Long, open: Long): List<DayUi> {
        val c = Calendar.getInstance()
        c.timeInMillis = Stats.midnight(now)
        val first = if (sn.bat.isEmpty()) now else sn.bat[0].t
        val spans = ArrayList<LongArray>()
        for (i in 0 until 30) {
            val a = c.timeInMillis
            c.add(Calendar.DAY_OF_MONTH, 1)
            val z = c.timeInMillis
            c.add(Calendar.DAY_OF_MONTH, -2)
            if (z <= first) break
            spans += longArrayOf(a, z)
        }
        val dn = SimpleDateFormat("EEE d MMM", Locale.getDefault())
        return spans.mapIndexed { i, (from, to) ->
            val name = if (i == 0) "Today" else if (i == 1) "Yesterday" else dn.format(Date(from))
            val ds = Stats.day(sn.bat, sn.screen, from, to, now)
            if (from != open) return@mapIndexed DayUi(from, to, name, Stats.dur(ds.sot), null, 0, 0)
            // Only the hours with data: a day that started tracking at noon is not a sliver in 24 hours.
            val a = max(from, hour(first, false))
            var z = min(to, hour(now, true))
            if (z - a < 2 * Stats.HOUR) z = min(to, a + 2 * Stats.HOUR)
            DayUi(from, to, name, Stats.dur(ds.sot), ds, a, z)
        }
    }

    private companion object {
        /** t rounded down (or up) to a whole hour. */
        fun hour(t: Long, up: Boolean): Long {
            val c = Calendar.getInstance()
            c.timeInMillis = t
            val exact = c.get(Calendar.MINUTE) == 0 && c.get(Calendar.SECOND) == 0 && c.get(Calendar.MILLISECOND) == 0
            c.set(Calendar.MINUTE, 0)
            c.set(Calendar.SECOND, 0)
            c.set(Calendar.MILLISECOND, 0)
            if (up && !exact) c.add(Calendar.HOUR_OF_DAY, 1)
            return c.timeInMillis
        }
    }
}
