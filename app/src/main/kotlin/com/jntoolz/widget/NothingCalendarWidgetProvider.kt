package com.jntoolz.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.CalendarContract
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import com.jntoolz.MainActivity
import com.jntoolz.R
import com.jntoolz.data.CalendarRepository
import com.jntoolz.data.ThemeMath
import com.jntoolz.data.ThemeSettings
import com.jntoolz.data.WidgetPreferences

class NothingCalendarWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: IntArray
    ) {
        val pending = goAsync()
        runAsync(pending) {
            for (id in appWidgetId) updateAppWidget(context, appWidgetManager, id)
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, newOptions: android.os.Bundle
    ) {
        updateAppWidget(context, appWidgetManager, appWidgetId)
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_OPEN_MONTH -> {
                val time = intent.getLongExtra(EXTRA_EVENT_TIME, System.currentTimeMillis())
                val calendarUri = ContentUris.appendId(
                    CalendarContract.CONTENT_URI.buildUpon().appendPath("time"),
                    time
                ).build()
                val calIntent = Intent(Intent.ACTION_VIEW).setData(calendarUri).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                try {
                    context.startActivity(calIntent)
                } catch (e: Exception) {
                    Log.w(TAG, "open calendar failed: $e")
                }
            }
            ACTION_REFRESH,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_DATE_CHANGED,
            ACTION_PROVIDER_CHANGED -> {
                val pending = goAsync()
                val app = context.applicationContext
                val light = intent.action == ACTION_PROVIDER_CHANGED
                runAsync(pending) {
                    val mgr = AppWidgetManager.getInstance(app)
                    val ids = mgr.getAppWidgetIds(ComponentName(app, NothingCalendarWidgetProvider::class.java))
                    if (ids.isEmpty()) return@runAsync
                    // Calendar data changed: the layout is already bound, only re-query the list.
                    if (light && WidgetPreferences(app).isCalendarToolEnabled &&
                        CalendarRepository(app).hasCalendarPermission()
                    ) {
                        for (id in ids) mgr.notifyAppWidgetViewDataChanged(id, R.id.lv_events)
                    } else {
                        for (id in ids) updateAppWidget(app, mgr, id)
                    }
                }
            }
            else -> super.onReceive(context, intent)
        }
    }

    private fun runAsync(pending: PendingResult?, block: () -> Unit) {
        if (pending == null) {
            block()
            return
        }
        Thread {
            try {
                block()
            } catch (e: Exception) {
                Log.w(TAG, "widget update failed: $e")
            } finally {
                pending.finish()
            }
        }.start()
    }

    companion object {
        private const val TAG = "NothingWidget"
        private const val ACTION_PROVIDER_CHANGED = "android.intent.action.PROVIDER_CHANGED"
        const val ACTION_REFRESH = "com.jntoolz.ACTION_REFRESH_WIDGET"
        const val ACTION_OPEN_MONTH = "com.jntoolz.ACTION_OPEN_MONTH"
        const val EXTRA_EVENT_TIME = "extra_event_time"

        fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val repository = CalendarRepository(context)
            val prefs = WidgetPreferences(context)
            val views = RemoteViews(context.packageName, R.layout.widget_timeline_nothing)

            // Background: the shared card recipe (black, optionally glass) at the widget size, system corner radius
            val theme = ThemeSettings.from(prefs)
            val widgetBg = theme.bgColor
            val opts = appWidgetManager.getAppWidgetOptions(appWidgetId)
            val wDp = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 250).coerceAtLeast(60)
            val hDp = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 250).coerceAtLeast(60)
            val bg = theme.bgStyle
            views.setImageViewBitmap(R.id.iv_widget_bg, WidgetBitmaps.widget(context, bg.spec(bg.color), wDp, hDp))
            val textColor = ThemeMath.headerColor(widgetBg)

            if (!repository.hasCalendarPermission() || !prefs.isCalendarToolEnabled) {
                views.setViewVisibility(R.id.lv_events, View.GONE)
                views.setViewVisibility(R.id.tv_empty, View.VISIBLE)
                views.setTextColor(R.id.tv_empty, textColor)
                views.setTextViewText(
                    R.id.tv_empty,
                    if (!prefs.isCalendarToolEnabled) "Calendar disabled in JNToolz" else "Tap to grant calendar access"
                )

                val openAppIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                val appPendingIntent = PendingIntent.getActivity(
                    context,
                    102,
                    openAppIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.widget_root, appPendingIntent)
                appWidgetManager.updateAppWidget(appWidgetId, views)
                return
            }

            views.setViewVisibility(R.id.lv_events, View.VISIBLE)
            views.setViewVisibility(R.id.tv_empty, View.GONE)

            // Connect Scrollable RemoteViewsService
            val serviceIntent = Intent(context, NothingWidgetService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                putExtra("themeHash", theme.hashCode())
                data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
            }
            views.setRemoteAdapter(R.id.lv_events, serviceIntent)

            // PendingIntent Template for item clicks (opens Google Calendar at clicked event's month)
            val clickIntent = Intent(context, NothingCalendarWidgetProvider::class.java).apply {
                action = ACTION_OPEN_MONTH
            }
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            val clickPendingIntent = PendingIntent.getBroadcast(context, 501, clickIntent, flags)
            views.setPendingIntentTemplate(R.id.lv_events, clickPendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)
            appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.lv_events)
        }
    }
}
