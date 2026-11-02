package com.jntoolz.counter

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.jntoolz.tools.WidgetKit
import com.jntoolz.ui.counter.CounterConfigureScreen

/** Shown when a Counter widget is placed (or reconfigured): pick which counter it shows. */
class CounterConfigureActivity : ComponentActivity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appWidgetId = intent?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        // Backing out of the first placement removes the widget, as the launcher expects.
        setResult(Activity.RESULT_CANCELED, resultIntent())
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        val store = CounterStore(this)
        // Every counter is previewed at this widget's real size when the launcher already sized it.
        val (w, h) = WidgetKit.sizeDp(AppWidgetManager.getInstance(this), appWidgetId, 0, 0)
        val size = if (w > 0 && h > 0) CounterLayouts.widgetSize(w, h, CounterShape.SQUARE.size) else null
        enableEdgeToEdge()
        setContent {
            CounterConfigureScreen(
                currentId = store.counterIdForWidget(appWidgetId),
                onPick = { counter -> pick(store, counter) },
                onClose = { finish() },
                widgetSize = size,
                editing = store.get(store.counterIdForWidget(appWidgetId)),
            )
        }
    }

    private var picking = false

    /** Saves the choice, then renders and schedules on the counter worker; result + finish back on the main thread. */
    private fun pick(store: CounterStore, counter: Counter) {
        if (picking) return
        picking = true
        store.setWidgetCounter(appWidgetId, counter.id)
        val app = applicationContext
        val id = appWidgetId
        CounterWidgets.runInBackground(done = {
            runOnUiThread {
                setResult(Activity.RESULT_OK, resultIntent())
                finish()
            }
        }) {
            val list = store.all()
            CounterWidgets.update(app, AppWidgetManager.getInstance(app), id, store, list)
            CounterWidgets.schedule(app, CounterWidgets.ids(app), store, list)
        }
    }

    private fun resultIntent() = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
}
