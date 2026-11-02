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
        WidgetBitmaps.clearCache()
        val appContext = context.applicationContext
        val appWidgetManager = AppWidgetManager.getInstance(appContext)
        val component = ComponentName(appContext, NothingCalendarWidgetProvider::class.java)
        val ids = appWidgetManager.getAppWidgetIds(component)
        for (id in ids) {
            NothingCalendarWidgetProvider.updateAppWidget(appContext, appWidgetManager, id)
        }
    }
}
