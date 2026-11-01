package com.jntoolz.sot.widget

import com.jntoolz.sot.GraphMath
import com.jntoolz.sot.Stats
import com.jntoolz.sot.Store
import java.util.Locale

/** Everything a widget face is drawn from, read once per redraw (see BatteryWidgets.load). */
class WidgetData(
    val now: Long,
    val level: Int,          // live level, -1 when unknown
    val charging: Boolean,
    val tempC10: Int,        // 0.1 °C, Int.MIN_VALUE when unknown
    val bat: List<Store.B>,
    val screen: List<LongArray>,
)

/** What one widget shows: the graph series over [t0, t1] and its texts (null = hidden). */
class Face(
    val t0: Long,
    val t1: Long,
    val series: GraphMath.Series,
    val big: String?,
    val small: String?,
    val label: String?,
    val charging: Boolean,
    /** The small text is the charging state (drawn in the charging colour). */
    val smallCharging: Boolean = false,
)

object BatteryWidgetModel {
    const val MIN_SPAN = 30 * Stats.MIN

    /** The graph window [from, now] for a range; "since unplug" spans at least [MIN_SPAN]. */
    fun window(range: Range, now: Long, session: Stats?): LongArray {
        val from = when (range) {
            Range.H6 -> now - 6 * Stats.HOUR
            Range.H24 -> now - 24 * Stats.HOUR
            Range.SINCE_UNPLUG ->
                if (session == null || session.empty || session.fallback && session.gt.isEmpty()) now - 6 * Stats.HOUR
                else session.start
        }
        return longArrayOf(minOf(from, now - MIN_SPAN), now)
    }

    /** The bottom-left text, e.g. "Battery (24h)". */
    fun label(range: Range): String = when (range) {
        Range.H6 -> "Battery (6h)"
        Range.H24 -> "Battery (24h)"
        Range.SINCE_UNPLUG -> "Since unplug"
    }

    fun build(cfg: BatteryWidgetConfig, d: WidgetData): Face {
        val session = Stats.compute(d.bat, d.screen, false, d.now)
        val (t0, t1) = window(cfg.range, d.now, session).let { it[0] to it[1] }
        val level = if (d.level >= 0) d.level else d.bat.lastOrNull()?.level ?: -1
        val series = GraphMath.window(d.bat, t0, t1, level, d.charging)
        val sot = when (cfg.range) {
            Range.SINCE_UNPLUG -> if (session.empty) 0L else session.sot
            else -> Stats.sotBetween(d.screen, t0, t1, d.now)
        }
        val left = if (d.charging) -1L else session.leftMixed()
        val pct = if (level < 0) "–" else "$level%"
        val big = when (cfg.big) {
            Value.SOT -> Stats.dur(sot)
            // Charging has no "time left": the level is the useful number then.
            Value.LEFT -> if (d.charging) pct else if (left < 0) "–" else Stats.dur(left)
            else -> pct
        }
        var smallCharging = false
        val small = if (cfg.small == cfg.big) null else when (cfg.small) {
            Value.SOT -> Stats.dur(sot) + " on"
            Value.LEFT -> if (d.charging) "Charging".also { smallCharging = true }
            else (if (left < 0) "–" else Stats.dur(left)) + " left"
            Value.TEMP -> if (d.tempC10 == Int.MIN_VALUE) null else String.format(Locale.US, "%.0f°C", d.tempC10 / 10.0)
            else -> null
        }
        return Face(
            t0 = t0,
            t1 = t1,
            series = series,
            big = big,
            small = small,
            label = if (cfg.label) label(cfg.range) else null,
            charging = d.charging,
            smallCharging = smallCharging,
        )
    }
}

/**
 * Tracker-driven redraws: skip one when the same level, plug and screen state were drawn less than
 * [minMs] ago. Forced redraws (config, resize, APPWIDGET_UPDATE) bypass it.
 */
class RedrawThrottle(private val minMs: Long = 60_000L) {
    private var lastKey: String? = null
    private var lastAt = Long.MIN_VALUE / 2

    @Synchronized
    fun shouldDraw(level: Int, charging: Boolean, screenOn: Boolean, now: Long): Boolean {
        val key = "$level|$charging|$screenOn"
        if (key == lastKey && now - lastAt in 0 until minMs) return false
        lastKey = key
        lastAt = now
        return true
    }
}
