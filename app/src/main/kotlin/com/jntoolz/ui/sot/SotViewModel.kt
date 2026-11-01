package com.jntoolz.ui.sot

import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jntoolz.sot.Apps
import com.jntoolz.sot.Health
import com.jntoolz.sot.SotTool
import com.jntoolz.sot.Stats
import com.jntoolz.sot.Store
import com.jntoolz.sot.Tips
import com.jntoolz.sot.TrackerService
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

/** One day of History; [graph] and [sessions] are filled only for the open day. */
internal class DayUi(
    val from: Long,
    val to: Long,
    val name: String,
    val sot: String,
    val graph: Stats?,
    val rangeFrom: Long,
    val rangeTo: Long,
    val sessions: List<Pair<String, String>>
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

    var page by mutableIntStateOf(0)
        private set
    var today by mutableStateOf(prefs.getBoolean("openToday", false))
        private set
    var stats by mutableStateOf<Stats?>(null)
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

    /** Leaving the tool: next time it opens on Now, counting the way Settings says. */
    fun leave() {
        page = 0
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

    fun show(n: Int) {
        page = n
        if (n == 2) {
            appsAt = 0
            viewModelScope.launch { loadTips() }
            viewModelScope.launch { loadSettings() }
            poke.value++
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
        val need = (if (st.empty) now - 6 * Stats.HOUR else st.start) - Stats.HOUR
        if (exact && !usageBusy && (now - usageAt > 30_000 || need < usageFrom)) loadUsage(need)

        if (sn.batVersion != healthVersion) {
            healthVersion = sn.batVersion
            health = withContext(Dispatchers.IO) { Health.percentText(ctx, sn.bat) }
        }
        val key = Triple(sn.dataVersion, openDay, Stats.midnight(now))
        if (key != historyKey) {
            historyKey = key
            val open = openDay
            days = withContext(Dispatchers.Default) { history(sn, now, open) }
        }
        if (page == 2 && !appsBusy && now - appsAt > 60_000) loadApps(store, st, exact)
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

    /** One row per day, newest first, up to 30 days; the open one also gets its graph and sessions. */
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
        val tf = android.text.format.DateFormat.getTimeFormat(ctx)
        val wd = SimpleDateFormat("EEE", Locale.getDefault())
        return spans.mapIndexed { i, (from, to) ->
            val name = if (i == 0) "Today" else if (i == 1) "Yesterday" else dn.format(Date(from))
            val ds = Stats.day(sn.bat, sn.screen, from, to, now)
            if (from != open) return@mapIndexed DayUi(from, to, name, Stats.dur(ds.sot), null, 0, 0, emptyList())
            // Only the hours with data: a day that started tracking at noon is not a sliver in 24 hours.
            val a = max(from, hour(first, false))
            var z = min(to, hour(now, true))
            if (z - a < 2 * Stats.HOUR) z = min(to, a + 2 * Stats.HOUR)
            // A time inside the day as just the time, outside it with the weekday.
            fun whenText(t: Long): String {
                val dt = Date(t)
                return if (t in from until to) tf.format(dt) else wd.format(dt) + " " + tf.format(dt)
            }
            val sessions = Stats.history(sn.bat, 200).mapNotNull { x ->
                val end = if (x[1] < 0) now else x[1]
                if (end <= from || x[0] >= to) return@mapNotNull null
                val span = whenText(x[0]) + " – " + (if (x[1] < 0) "now" else whenText(end))
                span to Stats.dur(Stats.sotBetween(sn.screen, x[0], end, now))
            }
            DayUi(from, to, name, Stats.dur(ds.sot), ds, a, z, sessions)
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
