package com.jntoolz.sot.widget

import com.jntoolz.data.CardMath
import com.jntoolz.data.CardSpec
import com.jntoolz.data.CardStyle

/** What the graph covers. */
enum class Range(val label: String) {
    H6("6H"), H24("24H"), SINCE_UNPLUG("UNPLUG"), TODAY("TODAY");

    companion object {
        /** The choices in the order the configure screen lists them (entries are saved by name, never renamed). */
        val CHOICES = listOf(TODAY, H24, SINCE_UNPLUG, H6)
    }
}

/** A value the widget can show as its big number or its small top-left text. */
enum class Value(val label: String) {
    BATTERY("%"), SOT("SOT"), LEFT("LEFT"), TEMP("TEMP"), NONE("NONE");

    companion object {
        val BIG = listOf(BATTERY, LEFT, SOT)
        val SMALL = listOf(NONE, SOT, LEFT, TEMP)
    }
}

/**
 * Persisted by name: never rename. BLACK is the custom colour background (the [BatteryWidgetConfig.style]).
 * GLASS is legacy (now `BLACK` + a glass style) and no longer offered; [BatteryWidgetConfig.decode] converts it.
 */
enum class Background(val label: String) {
    BLACK("COLOR"), CLEAR("CLEAR"), WALLPAPER("WALL"), GLASS("GLASS");

    companion object {
        /** The choices the configure screen offers, in order. */
        val CHOICES = listOf(BLACK, CLEAR, WALLPAPER)
    }
}

/**
 * One widget's choices, saved per appWidgetId as a short "key=value;" string.
 * Older saves had more options (12H range, HEALTH/CYCLES values, line/shade/dot/green switches, a font):
 * removed values and keys fall back to the defaults. The background [style] (shared card recipe) is saved as
 * s* keys, each only when it differs from the default, so plain configs keep their old short string.
 */
data class BatteryWidgetConfig(
    val range: Range = Range.H24,
    val big: Value = Value.BATTERY,
    val small: Value = Value.SOT,
    val label: Boolean = true,
    val bg: Background = Background.BLACK,
    /** The [Background.BLACK] background's look; colour 0 = black. */
    val style: CardStyle = DEFAULT_STYLE,
) {
    fun encode(): String {
        val b = StringBuilder("r=${range.name};b=${big.name};s=${small.name};l=${if (label) "1" else "0"};k=${bg.name}")
        val d = DEFAULT_STYLE
        fun put(k: String, v: String) = b.append(';').append(k).append('=').append(v)
        if (style.color != d.color) put("sc", hex(style.color))
        if (style.gradient != d.gradient) put("sg", bit(style.gradient))
        if (style.gradientTop != d.gradientTop) put("st", hex(style.gradientTop))
        if (style.intensity != d.intensity) put("si", style.intensity.toString())
        if (style.angle != d.angle) put("sa", style.angle.toString())
        if (style.glass != d.glass) put("sl", bit(style.glass))
        if (style.glassLevel != d.glassLevel) put("sv", style.glassLevel.toString())
        return b.toString()
    }

    /** The resolved background colour of [style] (its 0 = auto is black). */
    val styleColor: Int get() = if (style.color == 0) BLACK_ARGB else style.color

    /**
     * Drawing recipe of the background, null for [Background.CLEAR]. The wallpaper background draws the
     * style's gradient / glass on [wallpaper] (the system palette colour) instead of the style colour.
     */
    fun surfaceSpec(wallpaper: Int = BLACK_ARGB): CardSpec? = when (bg) {
        Background.CLEAR -> null
        Background.WALLPAPER -> style.spec(wallpaper)
        else -> style.spec(styleColor)
    }

    companion object {
        private const val BLACK_ARGB = 0xFF000000.toInt()
        val DEFAULT_STYLE = CardStyle(color = BLACK_ARGB)

        private fun hex(c: Int) = (c.toLong() and 0xFFFFFFFFL).toString(16).uppercase()
        private fun bit(b: Boolean) = if (b) "1" else "0"

        /** Unknown or missing keys and values keep their defaults, so old saves survive removed options. */
        fun decode(s: String?): BatteryWidgetConfig {
            val d = BatteryWidgetConfig()
            if (s.isNullOrEmpty()) return d
            val m = s.split(';').mapNotNull { p ->
                val i = p.indexOf('=')
                if (i <= 0) null else p.substring(0, i) to p.substring(i + 1)
            }.toMap()
            val ds = DEFAULT_STYLE
            var style = CardStyle(
                color = m["sc"]?.toLongOrNull(16)?.toInt() ?: ds.color,
                gradient = m["sg"]?.let { it == "1" } ?: ds.gradient,
                gradientTop = m["st"]?.toLongOrNull(16)?.toInt() ?: ds.gradientTop,
                intensity = m["si"]?.toIntOrNull()?.coerceIn(0, 100) ?: ds.intensity,
                angle = m["sa"]?.toIntOrNull()?.let { CardMath.normalize(it) } ?: ds.angle,
                glass = m["sl"]?.let { it == "1" } ?: ds.glass,
                glassLevel = m["sv"]?.toIntOrNull()?.coerceIn(0, 100) ?: ds.glassLevel,
            )
            var bg = pick(m["k"], Background.entries, d.bg)
            // Legacy glass background: the colour background with a glass style.
            if (bg == Background.GLASS) {
                bg = Background.BLACK
                style = style.copy(glass = true)
            }
            return BatteryWidgetConfig(
                range = pick(m["r"], Range.entries, d.range),
                big = pick(m["b"], Value.BIG, d.big),
                small = pick(m["s"], Value.SMALL, d.small),
                label = m["l"]?.let { it == "1" } ?: d.label,
                bg = bg,
                style = style,
            )
        }

        private fun <E : Enum<E>> pick(name: String?, allowed: List<E>, def: E): E =
            allowed.firstOrNull { it.name == name } ?: def
    }
}
