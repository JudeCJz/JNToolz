package com.jntoolz.data

import android.content.Context
import android.content.SharedPreferences

class WidgetPreferences(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    init {
        migrateGradientPreset()
    }

    /**
     * One-time: a saved gradient preset becomes a plain gradient card (top = preset start, card colour =
     * preset end, intensity 100, same angle), then the preset is cleared.
     */
    @Synchronized
    private fun migrateGradientPreset() {
        val preset = cardGradientStyle
        val style = ThemeMath.migratePreset(preset, gradientAngle) ?: return
        prefs.edit()
            .putLong(KEY_CUSTOM_CARD_COLOR, style.color.toLong() and 0xFFFFFFFFL)
            .putBoolean(KEY_GRADIENT_ON, true)
            .putLong(KEY_GRADIENT_TOP, style.gradientTop.toLong() and 0xFFFFFFFFL)
            .putInt(KEY_GRADIENT_INTENSITY, style.intensity)
            .putInt(KEY_GRADIENT_ANGLE, style.angle)
            .putString(KEY_CARD_GRADIENT, "NONE")
            .apply()
    }

    var hasCustomizedCalendars: Boolean
        get() = prefs.getBoolean(KEY_HAS_CUSTOM_CALENDARS, false)
        set(value) = prefs.edit().putBoolean(KEY_HAS_CUSTOM_CALENDARS, value).apply()

    var isCalendarToolEnabled: Boolean
        get() = prefs.getBoolean(KEY_CALENDAR_TOOL_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_CALENDAR_TOOL_ENABLED, value).apply()

    /** Whether the tool switch was ever written (an install from before the default turned off has it unset). */
    val hasCalendarToolFlag: Boolean
        get() = prefs.contains(KEY_CALENDAR_TOOL_ENABLED)

    var lastSeenVersion: String
        get() = prefs.getString(KEY_LAST_SEEN_VERSION, "") ?: ""
        set(value) = prefs.edit().putString(KEY_LAST_SEEN_VERSION, value).apply()

    var useGoogleEventColors: Boolean
        get() = prefs.getBoolean(KEY_USE_GOOGLE_COLORS, false)
        set(value) = prefs.edit().putBoolean(KEY_USE_GOOGLE_COLORS, value).apply()

    var markerStyle: String // OFF, DOT, RING, BAR, CARD
        get() {
            val v = prefs.getString(KEY_MARKER_STYLE, null)
            if (v != null) return v
            // Migrate from the old DOT/BOX setting
            return if (prefs.getString(KEY_GOOGLE_COLOR_MODE, "DOT") == "BOX") "CARD" else "DOT"
        }
        set(value) = prefs.edit().putString(KEY_MARKER_STYLE, value).apply()

    var isFadeEnabled: Boolean
        get() = prefs.getBoolean(KEY_FADE_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_FADE_ENABLED, value).apply()

    var widgetBackgroundColor: Long
        // Background is fixed to black; the picker was removed
        get() = 0xFF000000L
        set(value) = prefs.edit().putLong(KEY_WIDGET_BG_COLOR, value).apply()

    var customCardColor: Long
        get() = prefs.getLong(KEY_CUSTOM_CARD_COLOR, 0xFF202121L)
        set(value) = prefs.edit().putLong(KEY_CUSTOM_CARD_COLOR, value).apply()

    /** Legacy gradient preset; only read by [migrateGradientPreset], always "NONE" afterwards. */
    var cardGradientStyle: String
        get() = prefs.getString(KEY_CARD_GRADIENT, "NONE") ?: "NONE"
        set(value) = prefs.edit().putString(KEY_CARD_GRADIENT, value).apply()

    /** Frosted glass widget background (default off); the event-card glass is [isGlassEnabled]. */
    var isBgGlassEnabled: Boolean
        get() = prefs.getBoolean(KEY_BG_GLASS, false)
        set(value) = prefs.edit().putBoolean(KEY_BG_GLASS, value).apply()

    /** How see-through the background glass is, 0..100 (see CardStyle.glassLevel). */
    var bgGlassLevel: Int
        get() = prefs.getInt(KEY_BG_GLASS_LEVEL, CardStyle.DEFAULT_GLASS_LEVEL).coerceIn(0, 100)
        set(value) = prefs.edit().putInt(KEY_BG_GLASS_LEVEL, value.coerceIn(0, 100)).apply()

    var isGlassEnabled: Boolean
        get() = prefs.getBoolean(KEY_GLASS, false)
        set(value) = prefs.edit().putBoolean(KEY_GLASS, value).apply()

    /** How see-through the event-card glass is, 0..100. */
    var glassLevel: Int
        get() = prefs.getInt(KEY_GLASS_LEVEL, CardStyle.DEFAULT_GLASS_LEVEL).coerceIn(0, 100)
        set(value) = prefs.edit().putInt(KEY_GLASS_LEVEL, value.coerceIn(0, 100)).apply()

    var markerColor: Long // 0 = automatic
        get() = prefs.getLong(KEY_MARKER_COLOR, 0L)
        set(value) = prefs.edit().putLong(KEY_MARKER_COLOR, value).apply()

    var isGradientEnabled: Boolean
        get() = prefs.getBoolean(KEY_GRADIENT_ON, false)
        set(value) = prefs.edit().putBoolean(KEY_GRADIENT_ON, value).apply()

    var gradientTopColor: Long // 0 = auto
        get() = prefs.getLong(KEY_GRADIENT_TOP, 0L)
        set(value) = prefs.edit().putLong(KEY_GRADIENT_TOP, value).apply()

    var gradientIntensity: Int // 0..100
        get() = prefs.getInt(KEY_GRADIENT_INTENSITY, 25)
        set(value) = prefs.edit().putInt(KEY_GRADIENT_INTENSITY, value).apply()

    var gradientAngle: Int // 0..359, 0 = top to bottom
        get() = prefs.getInt(KEY_GRADIENT_ANGLE, 0)
        set(value) = prefs.edit().putInt(KEY_GRADIENT_ANGLE, value).apply()

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

    fun saveTheme(theme: ThemeSettings) {
        prefs.edit()
            .putBoolean(KEY_USE_GOOGLE_COLORS, theme.useGoogleColors)
            .putString(KEY_MARKER_STYLE, theme.markerStyle)
            .putBoolean(KEY_FADE_ENABLED, theme.fade)
            .putLong(KEY_CUSTOM_CARD_COLOR, theme.cardColor.toLong() and 0xFFFFFFFFL)
            .putString(KEY_CARD_GRADIENT, theme.gradientStyle)
            .putLong(KEY_MARKER_COLOR, theme.markerColor.toLong() and 0xFFFFFFFFL)
            .putBoolean(KEY_GRADIENT_ON, theme.gradient)
            .putLong(KEY_GRADIENT_TOP, theme.gradientTop.toLong() and 0xFFFFFFFFL)
            .putInt(KEY_GRADIENT_INTENSITY, theme.gradientIntensity)
            .putInt(KEY_GRADIENT_ANGLE, theme.gradientAngle)
            .putBoolean(KEY_GLASS, theme.glass)
            .putInt(KEY_GLASS_LEVEL, theme.glassLevel)
            .putBoolean(KEY_BG_GLASS, theme.bgGlass)
            .putInt(KEY_BG_GLASS_LEVEL, theme.bgGlassLevel)
            .commit()
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
        private const val KEY_MARKER_STYLE = "marker_style"
        private const val KEY_FADE_ENABLED = "fade_enabled"
        private const val KEY_WIDGET_BG_COLOR = "widget_bg_color"
        private const val KEY_CUSTOM_CARD_COLOR = "custom_card_color"
        private const val KEY_CARD_GRADIENT = "card_gradient"
        private const val KEY_MARKER_COLOR = "marker_color"
        private const val KEY_GRADIENT_ON = "gradient_on"
        private const val KEY_GRADIENT_TOP = "gradient_top"
        private const val KEY_GRADIENT_INTENSITY = "gradient_intensity"
        private const val KEY_GRADIENT_ANGLE = "gradient_angle"
        private const val KEY_GLASS = "card_glass"
        private const val KEY_BG_GLASS = "widget_bg_glass"
        private const val KEY_BG_GLASS_LEVEL = "widget_bg_glass_level"
        private const val KEY_GLASS_LEVEL = "card_glass_level"
    }
}
