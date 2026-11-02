package com.jntoolz.sot

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.graphics.drawable.Icon
import android.content.pm.ServiceInfo
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import com.jntoolz.R
import com.jntoolz.sot.widget.BatteryWidgets
import com.jntoolz.tools.WidgetKit

/**
 * Sleeps until Android tells it something changed: battery level or plug, screen on/off, shutdown.
 * No polling and no wake locks; only while the screen is on it ticks every 30 s to sample the battery
 * current (for the per-app estimate) and refresh its notification. All of it (the receiver, the tick and
 * the store's file IO) runs on its own thread, so the app's UI never waits on it.
 */
class TrackerService : Service() {
    private var thread: HandlerThread? = null
    private lateinit var h: Handler // the tracker thread
    private lateinit var bm: BatteryManager
    private lateinit var nm: NotificationManager
    private lateinit var prefs: SharedPreferences
    private val open: PendingIntent by lazy { WidgetKit.openTool(this, "sot", ID) }
    private var inForeground = false

    // Tracker thread only.
    private var shown: String? = null
    private var builtData = Long.MIN_VALUE // Snap.dataVersion the notification text was worked out from
    private var builtToday = false
    private var validUntil = 0L // when the visible text can next change on its own (the minute ticks over)
    private var plug = -1 // from the last battery broadcast, -1 = not known yet
    private var listening = false
    private var level = -1
    private var screenOn = false

    private val tick = object : Runnable {
        override fun run() {
            sample()
            update()
            h.postDelayed(this, TICK)
        }
    }

    private val rx = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            when (i.action) {
                Intent.ACTION_BATTERY_CHANGED -> battery(i)
                Intent.ACTION_SCREEN_ON -> screen(true)
                Intent.ACTION_SCREEN_OFF, Intent.ACTION_SHUTDOWN -> screen(false)
            }
        }
    }

    // Only the "today" setting changes the notification text. Kept as a field: prefs hold listeners weakly.
    private val prefsChanged = SharedPreferences.OnSharedPreferenceChangeListener { _, k ->
        if (k == "today") h.post { update() }
    }

    override fun onCreate() {
        super.onCreate()
        bm = getSystemService(BatteryManager::class.java)
        nm = getSystemService(NotificationManager::class.java)
        prefs = SotTool.prefs(this)
        val ch = NotificationChannel(CHANNEL, "Live tracking", NotificationManager.IMPORTANCE_MIN)
        ch.setShowBadge(false)
        nm.createNotificationChannel(ch)
        if (!foreground()) return
        if (!SotTool.isEnabled(this)) { // e.g. a sticky restart after the tool was turned off
            stopSelf()
            return
        }
        running = true
        val t = HandlerThread("sot-tracker").also { it.start() }
        thread = t
        h = Handler(t.looper)
        h.post { begin() }
    }

    /** On the tracker thread: loads the history, then listens. */
    private fun begin() {
        val st = Store.get(this)
        val pm = getSystemService(PowerManager::class.java)
        st.closeBefore(System.currentTimeMillis() - SystemClock.elapsedRealtime())
        screenOn = pm.isInteractive
        st.addScreen(System.currentTimeMillis(), screenOn)

        val f = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        f.addAction(Intent.ACTION_SCREEN_ON)
        f.addAction(Intent.ACTION_SCREEN_OFF)
        f.addAction(Intent.ACTION_SHUTDOWN)
        val sticky = registerReceiver(rx, f, null, h)
        listening = true
        if (sticky != null) battery(sticky)
        prefs.registerOnSharedPreferenceChangeListener(prefsChanged)
        if (screenOn) h.post(tick)
        else update()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!inForeground) foreground()
        return START_STICKY
    }

    /**
     * Goes foreground with a placeholder (the level) so the main thread never loads the history;
     * the tracker thread replaces it with the real text right after.
     */
    private fun foreground(): Boolean =
        try {
            val lv = Health.sticky(this)?.let { Health.level(it) } ?: -1
            val n = notification(if (lv >= 0) "$lv%" else "Battery", null)
            if (Build.VERSION.SDK_INT >= 34) startForeground(ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            else startForeground(ID, n)
            inForeground = true
            true
        } catch (e: RuntimeException) {
            stopSelf()
            false
        }

    override fun onDestroy() {
        running = false
        thread?.let { t ->
            h.post {
                h.removeCallbacks(tick)
                if (listening) {
                    try {
                        unregisterReceiver(rx)
                    } catch (ignored: RuntimeException) {
                    }
                    listening = false
                }
                prefs.unregisterOnSharedPreferenceChangeListener(prefsChanged)
            }
            t.quitSafely() // runs the clean-up above (and anything already due), drops the next tick
        }
        thread = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun battery(i: Intent) {
        val level = Health.level(i)
        if (level < 0) return
        plug = i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
        this.level = level
        val status = i.getIntExtra(BatteryManager.EXTRA_STATUS, 0)
        val st = Store.get(this)
        // The broadcast also fires on voltage and temperature changes: skip those before the binder call.
        if (!st.isNew(level, plug, status)) return
        val b = Store.B(
            System.currentTimeMillis(), level,
            plug,
            status,
            i.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0),
            i.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0),
            bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER),
        )
        if (st.addBattery(b)) {
            update()
            widgets()
        }
    }

    /** One battery current sample. The tick runs right away when the screen turns on, so this does too. */
    private fun sample() {
        val charging = plug > 0 || (plug < 0 && bm.isCharging)
        Store.get(this).addCurrent(System.currentTimeMillis(), Health.currentMa(bm, charging))
    }

    private fun screen(on: Boolean) {
        screenOn = on
        Store.get(this).addScreen(System.currentTimeMillis(), on)
        h.removeCallbacks(tick)
        if (on) h.post(tick)
        else update()
        widgets()
    }

    /**
     * Battery graph widgets ride on the wakeups above; throttled, and a no-op when none are placed.
     * Not while the screen is off: nobody sees them, and the screen-on redraw catches up.
     */
    private fun widgets() {
        if (screenOn && level >= 0) BatteryWidgets.onTrackerEvent(this, level, plug > 0, screenOn)
    }

    /** Re-posts the notification when its text changed; works the text out only when it can have changed. */
    private fun update() {
        val today = prefs.getBoolean("today", false)
        val sn = Store.get(this).snapData()
        val now = System.currentTimeMillis()
        if (sn.dataVersion == builtData && today == builtToday && now < validUntil) return
        val s = if (today) Stats.today(sn.bat, sn.screen, now) else Stats.compute(sn.bat, sn.screen, false, now)
        // Only the screen time of a session still running (screen on now) moves with the clock.
        val ticking = !s.empty && !s.charging && sn.screen.lastOrNull()?.let { it[1] < 0 } == true
        var until = if (ticking) now + (Stats.MIN - s.sot % Stats.MIN) else Long.MAX_VALUE
        if (today) until = minOf(until, Stats.midnight(Stats.midnight(now) + 36 * Stats.HOUR))
        builtData = sn.dataVersion
        builtToday = today
        validUntil = until

        val title: String
        val text: String?
        if (s.charging || s.empty) {
            title = (if (s.charging) "Charging " else "") + s.level + "%"
            text = if (s.empty) null else "Last: " + Stats.dur(s.sot) + " screen on"
        } else {
            title = Stats.dur(s.sot) + " screen on"
            text = s.level.toString() + "% · " + Stats.pct(s.used()) + " used"
        }
        val key = "$title|$text"
        if (key == shown) return
        shown = key
        nm.notify(ID, notification(title, text))
    }

    private fun notification(title: String, text: String?): Notification =
        Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_jntoolz)
            .setLargeIcon(Icon.createWithResource(this, R.drawable.ic_sot_stat))
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(Notification.CATEGORY_STATUS)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .build()

    companion object {
        const val CHANNEL = "sot_track"
        const val ID = 1001
        const val TICK = 30_000L

        /** True from onCreate to onDestroy, so opening the tool does not restart a running tracker. */
        @Volatile
        var running = false
            private set

        /** Starts the tracker when the tool is on; does nothing when it is off or already running. */
        fun start(c: Context) {
            if (running || !SotTool.isEnabled(c)) return
            try {
                c.startForegroundService(Intent(c, TrackerService::class.java))
            } catch (ignored: RuntimeException) {
                // Android refused a background start; the app starts it again when opened.
            }
        }

        fun stop(c: Context) {
            try {
                c.stopService(Intent(c, TrackerService::class.java))
            } catch (ignored: RuntimeException) {
            }
        }
    }
}
