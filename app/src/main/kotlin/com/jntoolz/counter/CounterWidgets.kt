package com.jntoolz.counter

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.view.View
import android.widget.RemoteViews
import android.util.Log
import com.jntoolz.R
import com.jntoolz.tools.WidgetKit
import java.time.ZonedDateTime
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/** Builds and pushes counter widgets, and keeps the single midnight alarm. */
object CounterWidgets {

    const val ACTION_TICK = "com.jntoolz.counter.ACTION_TICK"

    fun ids(context: Context): IntArray =
        AppWidgetManager.getInstance(context)
            .getAppWidgetIds(ComponentName(context, CounterWidgetProvider::class.java))

    /** One background thread for all counter widget work (renders, IPC, alarm), so updates never overlap. */
    private val worker: ExecutorService = Executors.newSingleThreadExecutor { r ->
        Thread(r, "counter-widgets").apply { isDaemon = true }
    }

    /** Runs [block] on the counter worker thread; failures are logged, [done] always runs after. */
    fun runInBackground(done: (() -> Unit)? = null, block: () -> Unit) {
        worker.execute {
            try {
                block()
            } catch (e: Exception) {
                Log.w("CounterWidget", "update failed: $e")
            } finally {
                done?.invoke()
            }
        }
    }

    /** Redraws every counter widget and reschedules (or cancels) the alarm. Parses the counters once. */
    fun updateAll(context: Context) {
        val app = context.applicationContext
        val mgr = AppWidgetManager.getInstance(app)
        val ids = ids(app)
        val store = CounterStore(app)
        val list = store.all()
        for (id in ids) update(app, mgr, id, store, list)
        schedule(app, ids, store, list)
    }

    /** The size a widget is drawn at: portrait MIN_WIDTH x MAX_HEIGHT from the launcher's options. */
    private fun widgetSize(mgr: AppWidgetManager, appWidgetId: Int, fallback: SizeDp): SizeDp {
        val (w, h) = WidgetKit.sizeDp(mgr, appWidgetId, fallback.w, fallback.h)
        return CounterLayouts.widgetSize(w, h, fallback)
    }

    /** Counter id -> real size of the (first) placed widget showing it. Counters not on the home screen are absent. */
    fun placedSizes(context: Context): Map<String, SizeDp> {
        val app = context.applicationContext
        val mgr = AppWidgetManager.getInstance(app)
        val store = CounterStore(app)
        val list = store.all()
        val out = LinkedHashMap<String, SizeDp>()
        for (id in ids(app)) {
            val mapped = store.counterIdForWidget(id)
            val c = list.firstOrNull { it.id == mapped } ?: list.firstOrNull() ?: continue
            if (c.id !in out) out[c.id] = widgetSize(mgr, id, c.shape.size)
        }
        return out
    }

    fun update(
        context: Context,
        mgr: AppWidgetManager,
        appWidgetId: Int,
        store: CounterStore = CounterStore(context),
        list: List<Counter> = store.all(),
    ) {
        val enabled = store.enabled
        val counter = store.counterForWidget(appWidgetId, list)
        val now = ZonedDateTime.now()
        val display = counter?.display(now)
        val size = widgetSize(mgr, appWidgetId, counter?.shape?.size ?: CounterShape.SQUARE.size)
        // Same call as every in-app preview.
        val face = CounterRenderer.face(context, counter, display, size, off = !enabled)
        val clockMs = if (enabled && counter != null && display != null && face.clock != null) counter.clockMillis(display) else null

        val views = RemoteViews(context.packageName, R.layout.widget_counter)
        views.setImageViewBitmap(R.id.counter_image, face.bitmap)
        WidgetKit.tint(views, R.id.counter_image, face.baseTintRes, face.baseTint ?: 0)
        val overlay = face.overlay
        if (overlay != null) {
            views.setViewVisibility(R.id.counter_overlay, View.VISIBLE)
            views.setImageViewBitmap(R.id.counter_overlay, overlay)
            WidgetKit.tint(views, R.id.counter_overlay, face.overlayTintRes, face.overlayTint ?: 0)
        } else {
            views.setViewVisibility(R.id.counter_overlay, View.GONE)
        }
        views.setContentDescription(
            R.id.counter_image,
            when {
                !enabled -> "Counter off"
                counter == null || display == null -> "Add a counter"
                else -> "${counter.title} ${display.number} ${display.label}".trim()
            }
        )

        val shown = if (clockMs != null) face.clock?.let { clockView(it) } else null
        for (id in CLOCK_VIEWS) {
            if (id == shown) continue
            views.setViewVisibility(id, View.GONE)
            views.setChronometer(id, SystemClock.elapsedRealtime(), null, false)
        }
        if (shown != null && clockMs != null) {
            views.setViewVisibility(shown, View.VISIBLE)
            if (face.clockColorRes != 0 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // Resolved by the launcher at apply time, so it follows the wallpaper.
                views.setColorStateList(shown, "setTextColor", face.clockColorRes)
            } else {
                views.setTextColor(shown, face.clockColor)
            }
            // Counting down to base: it ticks by itself, no app wakeups.
            views.setChronometer(shown, SystemClock.elapsedRealtime() + clockMs, null, true)
            views.setChronometerCountDown(shown, true)
        }

        views.setOnClickPendingIntent(R.id.counter_root, WidgetKit.openTool(context, "counter", 7300))
        mgr.updateAppWidget(appWidgetId, views)
    }

    private val CLOCK_VIEWS = intArrayOf(R.id.counter_clock_center, R.id.counter_clock_end, R.id.counter_clock_tall)

    private fun clockView(slot: ClockSlot): Int = when (slot) {
        ClockSlot.BOTTOM -> R.id.counter_clock_center
        ClockSlot.END -> R.id.counter_clock_end
    }

    /** Call after a counter changes in the app: redraws on the worker thread (outlives the screen). */
    fun onCountersChanged(context: Context) {
        val app = context.applicationContext
        runInBackground { updateAll(app) }
    }

    /**
     * One inexact RTC alarm (no exact-alarm permission, no wakeup): next local midnight, or sooner when
     * a live clock reaches zero so it stops showing a negative time. Cancelled when no widgets remain
     * or the tool is switched off.
     */
    fun schedule(
        context: Context,
        ids: IntArray = ids(context),
        store: CounterStore = CounterStore(context),
        list: List<Counter> = store.all(),
    ) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val pi = tickIntent(context)
        if (ids.isEmpty() || !store.enabled) {
            am.cancel(pi)
            return
        }
        val now = ZonedDateTime.now()
        var at = CounterMath.nextMidnight(now).toInstant().toEpochMilli() + 2_000
        for (id in ids) {
            val c = store.counterForWidget(id, list) ?: continue
            val ms = c.clockMillis(c.display(now)) ?: continue
            at = minOf(at, System.currentTimeMillis() + ms + 1_000)
        }
        am.set(AlarmManager.RTC, at, pi)
    }

    fun cancel(context: Context) {
        context.getSystemService(AlarmManager::class.java)?.cancel(tickIntent(context))
    }

    private fun tickIntent(context: Context): PendingIntent {
        val i = Intent(context, CounterWidgetProvider::class.java).setAction(ACTION_TICK)
        return PendingIntent.getBroadcast(context, 7301, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
}
