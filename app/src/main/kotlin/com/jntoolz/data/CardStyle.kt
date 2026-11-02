package com.jntoolz.data

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** Start and end point of a gradient line, in the pixels of the rectangle it fills. */
data class GradientLine(val x0: Float, val y0: Float, val x1: Float, val y1: Float)

/**
 * One card look, shared by the Calendar cards and background, the Counter widgets and the Battery widget.
 *
 * Colours are ARGB ints. [color] 0 = auto: the host's own card colour (e.g. the Counter dark card).
 * With [gradient] the card flows from [gradientTop] (0 = auto white tint) to the card colour, [intensity]
 * 0..100 is how far the top colour is mixed in, and [angle] is the direction, see [CardMath.gradientLine].
 * [glass] makes the fill translucent and adds a soft sheen and a hairline border; [glassLevel] 0..100 is how
 * see-through it is (fill alpha = colour alpha * (1 - glassLevel / 100)).
 * Every card and widget background (Calendar, Counter, Battery) is drawn from one of these by CardPainter.
 */
data class CardStyle(
    val color: Int = 0,
    val gradient: Boolean = false,
    val gradientTop: Int = 0,
    val intensity: Int = 25,
    val angle: Int = 0,
    val glass: Boolean = false,
    val glassLevel: Int = DEFAULT_GLASS_LEVEL,
) {
    /** True when nothing is customised, so a host may draw its plain card. */
    val isDefault: Boolean get() = color == 0 && !gradient && !glass

    /**
     * Drawing recipe on top of [base], the resolved ARGB card colour (its alpha is the fill opacity).
     * Glass scales the opacity down by [glassLevel].
     */
    fun spec(base: Int): CardSpec {
        val alpha = if (glass) (ThemeMath.alpha(base) * glassFill(glassLevel)).roundToInt() else ThemeMath.alpha(base)
        val bottom = ThemeMath.opaque(base)
        val top = if (gradient) {
            val t = if (gradientTop == 0) 0xFFFFFFFF.toInt() else ThemeMath.opaque(gradientTop)
            ThemeMath.blend(bottom, t, intensity.coerceIn(0, 100) / 100f)
        } else bottom
        fun withA(c: Int) = (alpha shl 24) or (c and 0xFFFFFF)
        return CardSpec(withA(top), withA(bottom), CardMath.normalize(angle), glass)
    }

    companion object {
        /** Default see-through amount of glass: 60 = a 40% fill. */
        const val DEFAULT_GLASS_LEVEL = 60

        /** Fill opacity of glass at [level] 0..100, as a fraction of the colour's own alpha. */
        fun glassFill(level: Int): Float = 1f - level.coerceIn(0, 100) / 100f

        /** Calendar gradient preset (removed from the UI) -> the equivalent style, keeping [angle]. */
        fun fromPreset(start: Int, end: Int, angle: Int) = CardStyle(
            color = end, gradient = true, gradientTop = start, intensity = 100, angle = CardMath.normalize(angle)
        )
    }
}

/** Resolved drawing recipe: ARGB [top] flowing to [bottom] along [angle], optionally glass. */
data class CardSpec(val top: Int, val bottom: Int, val angle: Int, val glass: Boolean) {
    /** A flat colour: can be drawn without a bitmap. */
    val isPlain: Boolean get() = !glass && top == bottom
}

/** Angle and gradient-line math for [CardStyle]; pure, so the widget bitmap and previews agree. */
object CardMath {
    /** Snap points are multiples of this many degrees. */
    const val SNAP_STEP = 45
    /** Within this many degrees of a snap point the angle sticks to it. */
    const val SNAP_RANGE = 6

    fun normalize(angle: Int): Int = ((angle % 360) + 360) % 360

    /** Nearest multiple of 45 if [raw] is within +-6 degrees of one, else [raw] normalised to 0..359. */
    fun snapAngle(raw: Int): Int {
        val n = normalize(raw)
        val nearest = ((n + SNAP_STEP / 2) / SNAP_STEP) * SNAP_STEP
        return if (abs(n - nearest) <= SNAP_RANGE) nearest % 360 else n
    }

    /**
     * Gradient line covering a w x h rectangle at [angleDeg], like CSS linear-gradient.
     * Angle is the direction from the start colour to the end colour, clockwise from "down":
     * 0 = start at top flowing down, 90 = start at left flowing right,
     * 180 = start at bottom flowing up, 270 = start at right flowing left.
     * The line passes through the centre and is long enough that the corners get exactly
     * the start / end colours.
     */
    fun gradientLine(angleDeg: Int, w: Float, h: Float): GradientLine {
        val rad = Math.toRadians(normalize(angleDeg).toDouble())
        val dx = sin(rad).toFloat()
        val dy = cos(rad).toFloat()
        val half = (abs(w * dx) + abs(h * dy)) / 2f
        val cx = w / 2f
        val cy = h / 2f
        return GradientLine(cx - dx * half, cy - dy * half, cx + dx * half, cy + dy * half)
    }
}
