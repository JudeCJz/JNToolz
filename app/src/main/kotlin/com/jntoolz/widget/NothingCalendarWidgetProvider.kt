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
import android.view.View
import android.widget.RemoteViews
import com.jntoolz.MainActivity
import com.jntoolz.R
import com.jntoolz.data.CalendarRepository
import com.jntoolz.data.WidgetPreferences

class NothingCalendarWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: IntArray
    ) {
        for (id in appWidgetId) {
            updateAppWidget(context, appWidgetManager, id)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val thisWidget = ComponentName(context, NothingCalendarWidgetProvider::class.java)
        val appWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget)

        if (intent.action == ACTION_OPEN_MONTH) {
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
                e.printStackTrace()
            }
        } else if (intent.action == ACTION_REFRESH ||
            intent.action == Intent.ACTION_TIME_CHANGED ||
            intent.action == Intent.ACTION_TIMEZONE_CHANGED ||
            intent.action == Intent.ACTION_DATE_CHANGED ||
            intent.action == "android.intent.action.PROVIDER_CHANGED"
        ) {
            for (id in appWidgetIds) {
                updateAppWidget(context, appWidgetManager, id)
                appWidgetManager.notifyAppWidgetViewDataChanged(id, R.id.lv_events)
            }
        }
    }

    companion object {
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

            if (!repository.hasCalendarPermission() || !prefs.isCalendarToolEnabled) {
                views.setViewVisibility(R.id.lv_events, View.GONE)
                views.setViewVisibility(R.id.tv_empty, View.VISIBLE)
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

            val widgetBg = prefs.widgetBackgroundColor.toInt()
            views.setInt(R.id.widget_root, "setBackgroundColor", widgetBg)

            // Connect Scrollable RemoteViewsService
            val serviceIntent = Intent(context, NothingWidgetService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
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
