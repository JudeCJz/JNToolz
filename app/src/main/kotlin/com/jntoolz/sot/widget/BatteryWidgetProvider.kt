package com.jntoolz.sot.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.os.Bundle

/**
 * "Battery graph" widget. updatePeriodMillis is 0: redrawn by the tracker's own battery/screen events,
 * on resize, after a config change and on APPWIDGET_UPDATE (placement, boot, app update), from stored data.
 */
class BatteryWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        async(context, appWidgetIds)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, newOptions: Bundle,
    ) {
        async(context, intArrayOf(appWidgetId))
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        BatteryWidgets.remove(context, appWidgetIds)
    }

    private fun async(context: Context, ids: IntArray) {
        val pending = goAsync()
        BatteryWidgets.refresh(context, ids) { pending.finish() }
    }
}
