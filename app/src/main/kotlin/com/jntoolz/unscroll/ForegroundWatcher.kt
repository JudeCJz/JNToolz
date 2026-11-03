package com.jntoolz.unscroll

import android.Manifest
import android.app.AppOpsManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.PowerManager
import android.os.Process
import com.jntoolz.R

/**
 * Keeps the accessibility service off except while a watched social app is in front (see [AppGate]), so
 * banking apps do not see an enabled service. While the screen is on it polls the usage events for the
 * foreground app; with the screen off it sleeps and leaves the service off.
 */
class ForegroundWatcher : Service() {
    private lateinit var thread: HandlerThread
    private lateinit var h: Handler // the watcher thread
    private lateinit var store: UnscrollStore
    private lateinit var usm: UsageStatsManager

    // Watcher thread only.
    private var fg: String? = null
    private var lastQuery = 0L
    private var polling = false

    private val poll = object : Runnable {
        override fun run() {
            if (!polling) return
            refresh()
            apply()
            h.postDelayed(this, POLL)
        }
    }

    private val rx = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            if (i.action == Intent.ACTION_SCREEN_OFF) h.post { pause() } else h.post { resume() }
        }
    }

    override fun onCreate() {
        super.onCreate()
        store = UnscrollStore(this)
        usm = getSystemService(UsageStatsManager::class.java)
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Unscroll gate", NotificationManager.IMPORTANCE_LOW).apply { setShowBadge(false) }
        )
        val n = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_unscroll_stat)
            .setContentText("Unscroll watching for social apps")
            .setOngoing(true)
            .setShowWhen(false)
            .build()
        if (Build.VERSION.SDK_INT >= 34) startForeground(ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(ID, n)
        running = true
        thread = HandlerThread("unscroll-gate").also { it.start() }
        h = Handler(thread.looper)
        registerReceiver(rx, IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        })
        // Mode, scope and pause changes take effect at once; stop when the watcher is no longer wanted.
        store.watch { h.post { if (wanted(this)) apply() else stop(this) } }
        h.post { if (getSystemService(PowerManager::class.java).isInteractive) resume() else pause() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!wanted(this)) {
            stop(this)
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        store.unwatch()
        unregisterReceiver(rx)
        polling = false
        h.removeCallbacksAndMessages(null)
        thread.quitSafely()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun resume() {
        if (polling) return
        polling = true
        lastQuery = 0L
        h.post(poll)
    }

    private fun pause() {
        polling = false
        h.removeCallbacks(poll)
        A11yToggle.set(this, false)
    }

    /** Reads the usage events since the last look and keeps the newest app that came to the front. */
    private fun refresh() {
        val now = System.currentTimeMillis()
        // The first look goes back far enough to find an app that has been open for a while.
        val from = if (lastQuery == 0L) now - LOOKBACK else lastQuery
        try {
            val ev = usm.queryEvents(from, now)
            val e = UsageEvents.Event()
            while (ev.hasNextEvent()) {
                ev.getNextEvent(e)
                if (e.eventType == UsageEvents.Event.ACTIVITY_RESUMED || e.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) fg = e.packageName
            }
            lastQuery = now
        } catch (ex: SecurityException) {
            // Usage access revoked; the next start checks it again.
        }
    }

    private fun apply() {
        A11yToggle.set(this, AppGate.shouldEnable(fg, store.mode, store.isPaused()))
    }

    companion object {
        const val CHANNEL = "unscroll_gate"
        const val ID = 1002
        const val POLL = 700L
        const val LOOKBACK = 3 * 60 * 60 * 1000L

        /** True from onCreate to onDestroy. */
        @Volatile
        var running = false
            private set

        /** Whether this phone can run the gate: the settings write permission and usage access are both granted. */
        fun isReady(c: Context): Boolean = A11yToggle.canWrite(c) && usageAllowed(c)

        /** Whether the service is meant to be off outside watched apps: the setup is done. */
        fun appScopeActive(c: Context): Boolean = isReady(c)

        private fun wanted(c: Context): Boolean {
            val s = UnscrollStore(c)
            return s.mode.blocking && isReady(c)
        }

        /** Starts the watcher when it is wanted; otherwise stops it. */
        fun start(c: Context) {
            val app = c.applicationContext
            if (!wanted(app)) {
                stop(app)
                return
            }
            if (running) return
            try {
                app.startForegroundService(Intent(app, ForegroundWatcher::class.java))
            } catch (e: RuntimeException) {
                // Android refused a background start; the app starts it again when opened.
            }
        }

        /** Stops the watcher. The accessibility service is left OFF when the mode is OFF. */
        fun stop(c: Context) {
            val app = c.applicationContext
            try {
                app.stopService(Intent(app, ForegroundWatcher::class.java))
            } catch (e: RuntimeException) {
            }
            val s = UnscrollStore(app)
            if (!s.mode.blocking) A11yToggle.set(app, false)
        }

        @Suppress("DEPRECATION")
        /** Whether usage access is granted in system Settings. */
        fun usageAllowed(c: Context): Boolean {
            val ao = c.getSystemService(AppOpsManager::class.java)
            val mode = if (Build.VERSION.SDK_INT >= 29)
                ao.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), c.packageName)
            else
                ao.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), c.packageName)
            if (mode == AppOpsManager.MODE_DEFAULT)
                return c.checkSelfPermission(Manifest.permission.PACKAGE_USAGE_STATS) == PackageManager.PERMISSION_GRANTED
            return mode == AppOpsManager.MODE_ALLOWED
        }
    }
}
