package com.jntoolz.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context

object WidgetUpdater {
    /**
     * Refreshes every placed widget directly (no broadcast). updateAppWidget performs
     * exactly one notifyAppWidgetViewDataChanged per widget.
     */
    fun updateAllWidgets(context: Context) {
        val appContext = context.applicationContext
        val appWidgetManager = AppWidgetManager.getInstance(appContext)
        val component = ComponentName(appContext, NothingCalendarWidgetProvider::class.java)
        for (id in appWidgetManager.getAppWidgetIds(component)) {
            NothingCalendarWidgetProvider.updateAppWidget(appContext, appWidgetManager, id)
        }
    }
}
