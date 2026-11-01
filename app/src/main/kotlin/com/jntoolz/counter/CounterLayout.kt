package com.jntoolz.counter

/**
 * Pure size / layout decisions for the counter widget (no Android types, unit tested).
 */

/** Widget size in dp, portrait: the launcher's MIN_WIDTH x MAX_HEIGHT, like the renderer uses. */
data class SizeDp(val w: Int, val h: Int)

/** The counter's preferred widget shape: the preview default and the size hint (cells). */
enum class CounterShape(val label: String, val cells: String, val size: SizeDp) {
    SQUARE("SQUARE", "2×2", SizeDp(150, 150)),
    WIDE("WIDE", "4×1", SizeDp(300, 70));

    companion object {
        /** Old data or TALL: SQUARE. */
        fun of(name: String?): CounterShape = when (name) {
            "WIDE" -> WIDE
            else -> SQUARE
        }
    }
}

/** How the face is laid out, picked from the widget's actual aspect on the home screen. */
enum class CounterLayout { SQUARE, WIDE }

/**
 * Where the Chronometer view sits over the bitmap. One view per slot in widget_counter.xml with
 * these exact values; the renderer leaves room for it and the in-app preview places its text the same way.
 */
enum class ClockSlot(
    /** True: center_vertical|end (one-row layouts). False: bottom|start under the label. */
    val end: Boolean,
    /** Start margin (bottom|start) or end margin (end), dp: the layout's padding. */
    val marginHDp: Int,
    /** Bottom margin of the view, dp (bottom|start only). */
    val marginBottomDp: Int,
    val textSp: Int,
) {
    BOTTOM(end = false, marginHDp = 16, marginBottomDp = 12, textSp = 14),
    END(end = true, marginHDp = 16, marginBottomDp = 0, textSp = 15),
}

object CounterLayouts {

    /** Wide when width > 2.2 x height or short row (h < 100dp and w >= h), else square (tall/vertical layouts eliminated). */
    fun forSize(wDp: Int, hDp: Int): CounterLayout = when {
        wDp > hDp * 2.2f || (hDp < 100 && wDp >= hDp) -> CounterLayout.WIDE
        else -> CounterLayout.SQUARE
    }

    /** The clock's slot, or null when the widget is too small to fit it next to / under the content. */
    fun clockSlot(layout: CounterLayout, wDp: Int, hDp: Int): ClockSlot? = when (layout) {
        CounterLayout.SQUARE -> if (hDp >= 120 && wDp >= 100) ClockSlot.BOTTOM else null
        CounterLayout.WIDE -> if (hDp >= 56 && wDp >= 220) ClockSlot.END else null
    }

    /**
     * The one spacing scale: edge padding in dp. Every theme uses 16dp.
     */
    fun padDp(layout: CounterLayout): Float = 16f

    /** Lines the title may wrap to: one in a single-row widget, two where there is height for it. */
    fun titleLines(layout: CounterLayout, hDp: Int): Int = when (layout) {
        CounterLayout.WIDE -> 1
        CounterLayout.SQUARE -> if (hDp >= 140) 2 else 1
    }

    /**
     * Size the widget is drawn at from launcher options; a missing or zero value (some launchers before
     * the first layout) falls back to [fallback]. Very small values are lifted to a drawable minimum.
     */
    fun widgetSize(minWidth: Int, maxHeight: Int, fallback: SizeDp): SizeDp = SizeDp(
        (if (minWidth > 0) minWidth else fallback.w).coerceAtLeast(40),
        (if (maxHeight > 0) maxHeight else fallback.h).coerceAtLeast(40),
    )

    /** Uniform scale (never above 1) so a [size] preview fits in [maxW] x [maxH] dp. */
    fun fitScale(size: SizeDp, maxW: Float, maxH: Float = Float.MAX_VALUE): Float =
        minOf(1f, maxW / size.w, maxH / size.h).coerceAtLeast(0.01f)
}
