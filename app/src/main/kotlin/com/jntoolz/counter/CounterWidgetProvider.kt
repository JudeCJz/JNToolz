package com.jntoolz.counter

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle

/** "Counter" home-screen widget. No updatePeriodMillis: refreshed by the midnight alarm and time broadcasts. */
class CounterWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        async { CounterWidgets.updateAll(context) }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, newOptions: Bundle,
    ) {
        async { CounterWidgets.update(context.applicationContext, appWidgetManager, appWidgetId) }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        CounterStore(context).removeWidgets(appWidgetIds)
    }

    override fun onDisabled(context: Context) {
        CounterWidgets.cancel(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            CounterWidgets.ACTION_TICK,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_BOOT_COMPLETED -> async { CounterWidgets.updateAll(context) }
            else -> super.onReceive(context, intent)
        }
    }

    /** Runs on the shared counter worker thread, keeping the broadcast alive until it is done. */
    private fun async(block: () -> Unit) {
        val pending = goAsync()
        CounterWidgets.runInBackground(done = { pending?.finish() }, block = block)
    }
}
