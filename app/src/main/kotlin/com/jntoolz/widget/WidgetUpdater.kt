package com.jntoolz.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.jntoolz.R

object WidgetUpdater {
    fun updateAllWidgets(context: Context) {
        val appWidgetManager = AppWidgetManager.getInstance(context)

        // Update Calendar Widgets
        val calendarWidgetComponent = ComponentName(context, NothingCalendarWidgetProvider::class.java)
        val calendarWidgetIds = appWidgetManager.getAppWidgetIds(calendarWidgetComponent)
        if (calendarWidgetIds.isNotEmpty()) {
            val intent = Intent(context, NothingCalendarWidgetProvider::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, calendarWidgetIds)
            }
            context.sendBroadcast(intent)
            for (id in calendarWidgetIds) {
                appWidgetManager.notifyAppWidgetViewDataChanged(id, R.id.lv_events)
            }
        }
    }
}
