package com.jntoolz.data

import android.content.Context
import android.content.SharedPreferences

class WidgetPreferences(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var hasCustomizedCalendars: Boolean
        get() = prefs.getBoolean(KEY_HAS_CUSTOM_CALENDARS, false)
        set(value) = prefs.edit().putBoolean(KEY_HAS_CUSTOM_CALENDARS, value).apply()

    var isCalendarToolEnabled: Boolean
        get() = prefs.getBoolean(KEY_CALENDAR_TOOL_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_CALENDAR_TOOL_ENABLED, value).apply()

    var lastSeenVersion: String
        get() = prefs.getString(KEY_LAST_SEEN_VERSION, "") ?: ""
        set(value) = prefs.edit().putString(KEY_LAST_SEEN_VERSION, value).apply()

    var useGoogleEventColors: Boolean
        get() = prefs.getBoolean(KEY_USE_GOOGLE_COLORS, false)
        set(value) = prefs.edit().putBoolean(KEY_USE_GOOGLE_COLORS, value).apply()

    var googleColorDisplayMode: String // "DOT" or "BOX"
        get() = prefs.getString(KEY_GOOGLE_COLOR_MODE, "DOT") ?: "DOT"
        set(value) = prefs.edit().putString(KEY_GOOGLE_COLOR_MODE, value).apply()

    var isFadeEnabled: Boolean
        get() = prefs.getBoolean(KEY_FADE_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_FADE_ENABLED, value).apply()

    var widgetBackgroundColor: Long
        get() = prefs.getLong(KEY_WIDGET_BG_COLOR, 0xFF000000L)
        set(value) = prefs.edit().putLong(KEY_WIDGET_BG_COLOR, value).apply()

    var customCardColor: Long
        get() = prefs.getLong(KEY_CUSTOM_CARD_COLOR, 0xFF202121L)
        set(value) = prefs.edit().putLong(KEY_CUSTOM_CARD_COLOR, value).apply()

    var cardGradientStyle: String // "NONE", "CHARCOAL", "RUBY", "CYBER"
        get() = prefs.getString(KEY_CARD_GRADIENT, "NONE") ?: "NONE"
        set(value) = prefs.edit().putString(KEY_CARD_GRADIENT, value).apply()

    fun getEnabledCalendarIds(): Set<String> {
        return prefs.getStringSet(KEY_ENABLED_CALENDARS, emptySet()) ?: emptySet()
    }

    fun setCalendarEnabled(calendarId: Long, enabled: Boolean, allAvailableCalendars: List<CalendarInfo>) {
        val current = getEnabledCalendarIds().toMutableSet()
        if (!hasCustomizedCalendars) {
            // First time customization: initialize with the calendars currently visible in Google
            allAvailableCalendars.forEach { cal ->
                if (cal.isVisibleInGoogle) current.add(cal.id.toString())
            }
            hasCustomizedCalendars = true
        }

        if (enabled) {
            current.add(calendarId.toString())
        } else {
            current.remove(calendarId.toString())
        }
        prefs.edit().putStringSet(KEY_ENABLED_CALENDARS, current).apply()
    }

    fun isCalendarEnabled(calendar: CalendarInfo): Boolean {
        if (!hasCustomizedCalendars) {
            // "just make sure to just show wat is enabled to be showing in my google calender das it"
            return calendar.isVisibleInGoogle
        }
        return getEnabledCalendarIds().contains(calendar.id.toString())
    }

    companion object {
        private const val PREFS_NAME = "nothing_calendar_prefs"
        private const val KEY_ENABLED_CALENDARS = "enabled_calendar_ids"
        private const val KEY_HAS_CUSTOM_CALENDARS = "has_custom_calendars"
        private const val KEY_CALENDAR_TOOL_ENABLED = "calendar_tool_enabled"
        private const val KEY_LAST_SEEN_VERSION = "last_seen_version"
        private const val KEY_USE_GOOGLE_COLORS = "use_google_colors"
        private const val KEY_GOOGLE_COLOR_MODE = "google_color_mode"
        private const val KEY_FADE_ENABLED = "fade_enabled"
        private const val KEY_WIDGET_BG_COLOR = "widget_bg_color"
        private const val KEY_CUSTOM_CARD_COLOR = "custom_card_color"
        private const val KEY_CARD_GRADIENT = "card_gradient"
    }
}
