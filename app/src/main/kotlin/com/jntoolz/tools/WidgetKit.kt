package com.jntoolz.tools

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Build
import android.widget.RemoteViews
import com.jntoolz.MainActivity

/** Small helpers shared by the home-screen widgets. */
object WidgetKit {

    /** Portrait size in dp from AppWidgetManager options: MIN_WIDTH x MAX_HEIGHT, with fallbacks. */
    fun sizeDp(mgr: AppWidgetManager, id: Int, fallbackW: Int, fallbackH: Int): Pair<Int, Int> {
        val o = mgr.getAppWidgetOptions(id)
        val w = o?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0) ?: 0
        val h = o?.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0) ?: 0
        return Pair(if (w > 0) w else fallbackW, if (h > 0) h else fallbackH)
    }

    /** PendingIntent that opens MainActivity on a tool (extra MainActivity.EXTRA_OPEN_TOOL), NEW_TASK|SINGLE_TOP|CLEAR_TOP, FLAG_IMMUTABLE|FLAG_UPDATE_CURRENT. */
    fun openTool(ctx: Context, toolId: String, requestCode: Int): PendingIntent {
        val open = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_OPEN_TOOL, toolId)
        }
        return PendingIntent.getActivity(ctx, requestCode, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    /**
     * Asks the launcher to place one [provider] widget (the launcher shows its own "Add to home screen" sheet and,
     * for a provider with a configure activity, opens it). Returns false when the launcher cannot pin, so the
     * caller can tell the user to add it by hand from the widget list.
     */
    fun requestPin(ctx: Context, provider: Class<*>): Boolean {
        val mgr = AppWidgetManager.getInstance(ctx)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || !mgr.isRequestPinAppWidgetSupported) return false
        return mgr.requestPinAppWidget(ComponentName(ctx, provider), null, null)
    }

    /**
     * Tint an ImageView in RemoteViews: on API 31+ with a colour resource via
     * setColorStateList(id, "setImageTintList", res) so it follows the wallpaper; below 31 with the
     * fixed fallback colour via setInt(id, "setColorFilter", color). res = 0 clears the tint on 31+;
     * below 31 only [fallback] is used, and fallback = 0 clears it.
     */
    fun tint(views: RemoteViews, viewId: Int, colorRes: Int, fallback: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (colorRes != 0) views.setColorStateList(viewId, "setImageTintList", colorRes)
            else views.setColorStateList(viewId, "setImageTintList", null as ColorStateList?)
        } else {
            views.setInt(viewId, "setColorFilter", fallback)
        }
    }
}
