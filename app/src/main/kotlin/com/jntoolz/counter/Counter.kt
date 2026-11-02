package com.jntoolz.counter

import com.jntoolz.data.CardStyle
import com.jntoolz.ui.theme.NothingFont
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime

data class Counter(
    val id: String,
    val title: String,
    val date: LocalDate,
    /** Optional time of day; days are still counted as calendar days. */
    val time: LocalTime? = null,
    /** The whole look, shared with the Calendar and Battery widgets; colour 0 = the dark card (see CounterRenderer.autoCardColor). */
    val cardStyle: CardStyle = CardStyle(),
    /** Ticking countdown under the days (needs [time]). */
    val liveClock: Boolean = false,
    /** Preview default and size hint; the widget itself follows its real size on the home screen. */
    val shape: CounterShape = CounterShape.SQUARE,
    /** Font of the big number (persisted by name): null = "Theme" (system sans) or [NothingFont.NDOT_77], see [FONTS]. */
    val font: NothingFont? = null,
) {
    fun display(now: ZonedDateTime = ZonedDateTime.now()): CounterDisplay =
        CounterMath.display(date, time, now)

    /** Live clock is shown only when asked for, the target has a time and it is still ahead. */
    fun clockMillis(d: CounterDisplay): Long? = if (liveClock && time != null) d.millisToTarget else null

    companion object {
        /** The number fonts a counter may use: null = "Theme" (system sans), and Ndot 77. */
        val FONTS: List<NothingFont?> = listOf(null, NothingFont.NDOT_77)

        /** A saved font mapped onto [FONTS]: any dot font becomes Ndot 77, any other font "Theme". */
        fun allowedFont(f: NothingFont?): NothingFont? = when {
            f == null -> null
            f.isDot -> NothingFont.NDOT_77
            else -> null
        }
    }
}

/**
 * Removed gradient presets. Only read by the decoder: [toStyle] turns a saved one into a [CardStyle].
 * Persisted by [name]: never rename an entry.
 */
enum class CounterGradient(val label: String, val startColor: Int, val endColor: Int) {
    NONE("SOLID", 0, 0),
    CHARCOAL("CHARCOAL", 0xFF2C2C2C.toInt(), 0xFF0D0D0D.toInt()),
    RED("RED", 0xFF5A0E13.toInt(), 0xFF180507.toInt()),
    AMBER("AMBER", 0xFF5C2C07.toInt(), 0xFF180C02.toInt()),
    CYBER("CYBER", 0xFF0C2F48.toInt(), 0xFF05111B.toInt()),
    PURPLE("PURPLE", 0xFF3B144E.toInt(), 0xFF0F0515.toInt()),
    EMERALD("EMERALD", 0xFF0E3822.toInt(), 0xFF04140B.toInt()),
    MONO("MONO", 0xFF424242.toInt(), 0xFF161616.toInt());

    val isGradient: Boolean get() = this != NONE

    /** Equivalent card style (top = preset start, card colour = preset end, full intensity); null for NONE. */
    fun toStyle(angle: Int): CardStyle? =
        if (this == NONE) null else CardStyle.fromPreset(startColor, endColor, angle)

    companion object {
        fun of(name: String?): CounterGradient = entries.firstOrNull { it.name == name } ?: NONE
    }
}

/**
 * Removed counter themes, only read by the decoder: [CounterStore.migrateTheme] turns a saved theme into its
 * [CardStyle] and [legacyFont] keeps the dot digits for the old dot-faced ones. Saves still write BLACK so older
 * app versions can read them. Persisted by [name]: never rename an entry.
 */
enum class CounterTheme(val label: String) {
    BLACK("BLACK"),
    WHITE("WHITE"),
    RED("RED"),
    GLASS("GLASS"),
    PROGRESS("PROGRESS"),
    /** Material You: follow the wallpaper colours live on Android 12+ (fixed neutral below). */
    YOU("YOU"),
    YOU_SOFT("YOU SOFT");

    companion object {
        /** Removed theme name -> current theme. */
        private val LEGACY = mapOf(
            "DOT" to BLACK, "MONO" to BLACK, "LIGHT" to BLACK, "SPLIT" to BLACK, "STACK" to BLACK,
            "CAPSULE" to BLACK, "MATRIX" to BLACK, "PAPER" to WHITE, "OUTLINE" to GLASS, "RING" to PROGRESS,
            "YOU_DOT" to YOU,
        )

        /** Old themes whose number was drawn in dots: they keep that look through the font option. */
        private val LEGACY_DOT = setOf("DOT", "RED", "PROGRESS", "GLASS", "STACK", "MATRIX", "YOU_DOT")

        fun of(name: String?): CounterTheme =
            entries.firstOrNull { it.name == name } ?: LEGACY[name] ?: BLACK

        /**
         * Font for a counter saved by an older version with theme [name] and no font of its own ("Theme").
         * Null: keep "Theme". Dot-faced old themes come back as Ndot 57 so they still look like themselves.
         */
        fun legacyFont(name: String?, savedFontVersion: Int): NothingFont? =
            if (savedFontVersion < 2 && name in LEGACY_DOT) NothingFont.NDOT_57 else null
    }
}
