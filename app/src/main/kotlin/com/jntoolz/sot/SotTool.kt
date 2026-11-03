package com.jntoolz.sot

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/** On/off switch and settings for the Battery tool (internal name: sot). */
object SotTool {
    private const val ENABLED = "enabled"

    /** The tool's own prefs file. Other keys: "today", "openToday", "askedBt". Widget configs live in "sot_widgets". */
    fun prefs(c: Context): SharedPreferences = c.getSharedPreferences("sot", Context.MODE_PRIVATE)

    fun isEnabled(c: Context): Boolean = prefs(c).getBoolean(ENABLED, false)

    /** Only saves the flag; switch the tool with [com.jntoolz.tools.Tools.setEnabled], which also stops/starts its work. */
    fun saveEnabled(c: Context, on: Boolean) {
        prefs(c).edit { putBoolean(ENABLED, on) }
    }
}
