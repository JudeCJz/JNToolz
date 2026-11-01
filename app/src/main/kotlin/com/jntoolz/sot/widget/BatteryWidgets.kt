package com.jntoolz.sot.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.BatteryManager
import android.os.Build
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import androidx.core.content.edit
import com.jntoolz.R
import com.jntoolz.sot.Health
import com.jntoolz.sot.Store
import com.jntoolz.tools.WidgetKit
import java.util.concurrent.Executors

/**
 * Battery graph widgets: per-widget config, data loading and redraws. No polling and no alarms: the tracker
 * calls [onTrackerEvent] on the battery and screen changes it already wakes for; the rest is APPWIDGET_UPDATE,
 * resizes and config saves.
 */
object BatteryWidgets {
    private const val PREFS = "sot_widgets"
    private const val TAG = "BatteryWidget"
    private const val REQ_OPEN = 7400

    private val throttle = RedrawThrottle()
    private val worker = Executors.newSingleThreadExecutor { r -> Thread(r, "battery-widget").apply { isDaemon = true } }

    private fun prefs(c: Context): SharedPreferences = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun config(c: Context, id: Int): BatteryWidgetConfig = BatteryWidgetConfig.decode(prefs(c).getString("w$id", null))

    fun save(c: Context, id: Int, cfg: BatteryWidgetConfig) = prefs(c).edit { putString("w$id", cfg.encode()) }

    fun remove(c: Context, ids: IntArray) = prefs(c).edit { for (id in ids) remove("w$id") }

    fun ids(c: Context): IntArray = try {
        AppWidgetManager.getInstance(c).getAppWidgetIds(ComponentName(c, BatteryWidgetProvider::class.java))
    } catch (e: RuntimeException) {
        IntArray(0)
    }

    /** From the tracker thread: redraw on the worker unless the same state was drawn under a minute ago. */
    fun onTrackerEvent(c: Context, level: Int, charging: Boolean, screenOn: Boolean) {
        if (!throttle.shouldDraw(level, charging, screenOn, System.currentTimeMillis())) return
        val app = c.applicationContext
        worker.execute {
            val ids = ids(app)
            if (ids.isNotEmpty()) safe { drawAll(app, ids) }
        }
    }

    /** Redraws the given widgets (all when null) on the worker; [done] runs after, on the worker. */
    fun refresh(c: Context, ids: IntArray? = null, done: (() -> Unit)? = null) {
        val app = c.applicationContext
        worker.execute {
            try {
                val list = ids ?: ids(app)
                if (list.isNotEmpty()) safe { drawAll(app, list) }
            } finally {
                done?.invoke()
            }
        }
    }

    private inline fun safe(block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            Log.w(TAG, "update failed: $e")
        }
    }

    private fun drawAll(c: Context, ids: IntArray) {
        val mgr = AppWidgetManager.getInstance(c)
        val cfgs = ids.map { config(c, it) }
        val data = load(c)
        for ((i, id) in ids.withIndex()) draw(c, mgr, id, cfgs[i], data)
    }

    /** Reads the live battery state and the stored history (call off the main thread). */
    fun load(c: Context): WidgetData {
        val sn = Store.get(c).snapData() // widgets never read the current samples
        val i: Intent? = Health.sticky(c)
        var level = -1
        var charging = false
        var temp = Int.MIN_VALUE
        if (i != null) {
            level = Health.level(i)
            charging = i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
            if (i.hasExtra(BatteryManager.EXTRA_TEMPERATURE)) temp = i.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0)
        }
        return WidgetData(
            now = System.currentTimeMillis(),
            level = level,
            charging = charging,
            tempC10 = temp,
            bat = sn.bat,
            screen = sn.screen,
        )
    }

    /** The size a widget is drawn at (portrait: MIN_WIDTH x MAX_HEIGHT), 300 x 150 when the launcher gave none. */
    fun sizeDp(mgr: AppWidgetManager, id: Int): Pair<Int, Int> = WidgetKit.sizeDp(mgr, id, 300, 150)

    private fun draw(c: Context, mgr: AppWidgetManager, id: Int, cfg: BatteryWidgetConfig, data: WidgetData) {
        val (w, h) = sizeDp(mgr, id)
        val face = BatteryWidgetModel.build(cfg, data)
        val views = RemoteViews(c.packageName, R.layout.widget_battery)
        views.setImageViewBitmap(R.id.battery_graph, BatteryWidgetRenderer.render(cfg, face, w, h, c.resources.displayMetrics.density))
        views.setContentDescription(R.id.battery_graph, listOfNotNull(face.label, face.big, face.small).joinToString(", "))
        background(views, cfg.bg)
        views.setOnClickPendingIntent(R.id.battery_root, WidgetKit.openTool(c, "sot", REQ_OPEN))
        mgr.updateAppWidget(id, views)
    }

    private fun background(v: RemoteViews, bg: Background) {
        val id = R.id.battery_bg
        if (bg == Background.CLEAR) {
            v.setViewVisibility(id, View.GONE)
            return
        }
        v.setViewVisibility(id, View.VISIBLE)
        // A colour resource, so the launcher re-resolves it when the wallpaper (and its palette) changes.
        val res = if (Build.VERSION.SDK_INT >= 31 && bg == Background.WALLPAPER) android.R.color.system_accent1_800
        else android.R.color.black
        WidgetKit.tint(v, id, res, BLACK)
    }

    /** The colour the preview paints behind the bitmap, as the widget would show it now. */
    fun backgroundColor(c: Context, bg: Background): Int? = when (bg) {
        Background.CLEAR -> null
        Background.WALLPAPER -> if (Build.VERSION.SDK_INT >= 31) c.getColor(android.R.color.system_accent1_800) else BLACK
        Background.BLACK -> BLACK
    }

    /** Wallpaper colour follows the system palette (Android 12+). */
    val wallpaperSupported: Boolean get() = Build.VERSION.SDK_INT >= 31

    private const val BLACK = 0xFF000000.toInt()
}
