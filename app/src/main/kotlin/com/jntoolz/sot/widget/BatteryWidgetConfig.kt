package com.jntoolz.sot.widget

/** What the graph covers. */
enum class Range(val label: String) {
    H6("6H"), H24("24H"), SINCE_UNPLUG("UNPLUG")
}

/** A value the widget can show as its big number or its small top-left text. */
enum class Value(val label: String) {
    BATTERY("%"), SOT("SOT"), LEFT("LEFT"), TEMP("TEMP"), NONE("NONE");

    companion object {
        val BIG = listOf(BATTERY, LEFT, SOT)
        val SMALL = listOf(NONE, SOT, LEFT, TEMP)
    }
}

enum class Background(val label: String) { BLACK("BLACK"), CLEAR("CLEAR"), WALLPAPER("WALL") }

/**
 * One widget's choices, saved per appWidgetId as a short "key=value;" string.
 * Older saves had more options (12H/TODAY ranges, HEALTH/CYCLES values, line/shade/dot/green switches, a font):
 * removed values and keys fall back to the defaults.
 */
data class BatteryWidgetConfig(
    val range: Range = Range.H24,
    val big: Value = Value.BATTERY,
    val small: Value = Value.SOT,
    val label: Boolean = true,
    val bg: Background = Background.BLACK,
) {
    fun encode(): String = "r=${range.name};b=${big.name};s=${small.name};l=${if (label) "1" else "0"};k=${bg.name}"

    companion object {
        /** Unknown or missing keys and values keep their defaults, so old saves survive removed options. */
        fun decode(s: String?): BatteryWidgetConfig {
            val d = BatteryWidgetConfig()
            if (s.isNullOrEmpty()) return d
            val m = s.split(';').mapNotNull { p ->
                val i = p.indexOf('=')
                if (i <= 0) null else p.substring(0, i) to p.substring(i + 1)
            }.toMap()
            return BatteryWidgetConfig(
                range = pick(m["r"], Range.entries, d.range),
                big = pick(m["b"], Value.BIG, d.big),
                small = pick(m["s"], Value.SMALL, d.small),
                label = m["l"]?.let { it == "1" } ?: d.label,
                bg = pick(m["k"], Background.entries, d.bg),
            )
        }

        private fun <E : Enum<E>> pick(name: String?, allowed: List<E>, def: E): E =
            allowed.firstOrNull { it.name == name } ?: def
    }
}
