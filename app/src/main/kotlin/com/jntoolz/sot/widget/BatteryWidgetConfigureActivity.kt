package com.jntoolz.sot.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.jntoolz.ui.sot.BatteryWidgetConfigureScreen

/** Shown when a Battery graph widget is placed or reconfigured: choose what it shows, with a live preview. */
class BatteryWidgetConfigureActivity : ComponentActivity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appWidgetId = intent?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        // Backing out of the first placement removes the widget, as the launcher expects.
        setResult(Activity.RESULT_CANCELED, result())
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        val (wDp, hDp) = BatteryWidgets.sizeDp(AppWidgetManager.getInstance(this), appWidgetId)
        enableEdgeToEdge()
        setContent {
            BatteryWidgetConfigureScreen(
                appWidgetId = appWidgetId,
                wDp = wDp,
                hDp = hDp,
                onDone = { cfg -> done(cfg) },
                onClose = { finish() }
            )
        }
    }

    private fun done(cfg: BatteryWidgetConfig) {
        val id = appWidgetId
        val app = applicationContext
        Thread {
            BatteryWidgets.save(app, id, cfg)
            BatteryWidgets.refresh(app, intArrayOf(id))
        }.start()
        setResult(Activity.RESULT_OK, result())
        finish()
    }

    private fun result() = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
}
