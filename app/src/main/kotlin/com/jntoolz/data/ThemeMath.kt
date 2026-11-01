package com.jntoolz.data

import kotlin.math.roundToInt

/** Snapshot of every theming preference; used by both the widget and the in-app preview. */
data class ThemeSettings(
    val useGoogleColors: Boolean,
    val markerStyle: String,      // OFF, DOT, RING, BAR, CARD
    val dim: Boolean,
    val fade: Boolean,
    val bgColor: Int,             // ARGB
    val cardColor: Int,           // ARGB
    val gradientStyle: String,    // NONE, CHARCOAL, RUBY, CYBER, AMBER
    val markerColor: Int = 0,     // 0 = automatic
    val gradient: Boolean = false // light-to-normal vertical gradient on card fills
) {
    companion object {
        fun from(p: WidgetPreferences) = ThemeSettings(
            useGoogleColors = p.useGoogleEventColors,
            markerStyle = p.markerStyle,
            dim = false, // Dim option removed
            fade = p.isFadeEnabled,
            bgColor = p.widgetBackgroundColor.toInt(),
            cardColor = p.customCardColor.toInt(),
            gradientStyle = p.cardGradientStyle,
            markerColor = p.markerColor.toInt(),
            gradient = p.isGradientEnabled
        )
    }
}

/** Fully resolved look of one event card. Colors are ARGB ints. */
data class CardLook(
    val gradient: String?,     // non-null: gradient preset
    val fill: Int,             // opaque RGB used for solid fill
    val fillAlpha: Int,        // 0..255 applied to solid fill or gradient
    val title: Int,
    val sub: Int,
    val markerVisible: Boolean,
    val markerColor: Int,
    val lightGradient: Boolean = false, // fill is drawn as fillTop -> fill
    val fillTop: Int = 0
)

object ThemeMath {
    const val OPAQUE = 0xFF000000.toInt()
    const val RED = 0xFFFF2E2E.toInt()

    fun red(c: Int) = (c shr 16) and 0xFF
    fun green(c: Int) = (c shr 8) and 0xFF
    fun blue(c: Int) = c and 0xFF
    fun alpha(c: Int) = (c ushr 24) and 0xFF
    fun rgb(r: Int, g: Int, b: Int) =
        OPAQUE or (r.coerceIn(0, 255) shl 16) or (g.coerceIn(0, 255) shl 8) or b.coerceIn(0, 255)
    fun opaque(c: Int) = c or OPAQUE

    fun blend(a: Int, b: Int, f: Float): Int = rgb(
        (red(a) + f * (red(b) - red(a))).roundToInt(),
        (green(a) + f * (green(b) - green(a))).roundToInt(),
        (blue(a) + f * (blue(b) - blue(a))).roundToInt()
    )

    fun scale(c: Int, f: Float): Int =
        rgb((red(c) * f).roundToInt(), (green(c) * f).roundToInt(), (blue(c) * f).roundToInt())

    /** Relative luminance 0..1 of the RGB part. */
    fun luminance(c: Int): Float {
        fun lin(v: Int): Float {
            val s = v / 255f
            return if (s <= 0.03928f) s / 12.92f else Math.pow(((s + 0.055f) / 1.055f).toDouble(), 2.4).toFloat()
        }
        return 0.2126f * lin(red(c)) + 0.7152f * lin(green(c)) + 0.0722f * lin(blue(c))
    }

    fun isLight(opaqueColor: Int) = luminance(opaqueColor) > 0.40f

    /** Widget background as seen when composited over black (e.g. a dark wallpaper). */
    fun effectiveBg(bg: Int): Int = blend(OPAQUE, opaque(bg), alpha(bg) / 255f)

    fun headerColor(bg: Int): Int = if (isLight(effectiveBg(bg))) 0xFF666666.toInt() else 0xFF8E8E8E.toInt()
    fun dividerColor(bg: Int): Int = if (isLight(effectiveBg(bg))) 0xFFD4D4D4.toInt() else 0xFF252525.toInt()

    fun gradientStart(style: String): Int = when (style) {
        "RUBY" -> 0xFF361717.toInt()
        "CYBER" -> 0xFF142B33.toInt()
        "AMBER" -> 0xFF302414.toInt()
        else -> 0xFF2A2B2D.toInt()
    }

    fun gradientEnd(style: String): Int = when (style) {
        "RUBY" -> 0xFF180A0A.toInt()
        "CYBER" -> 0xFF0A161A.toInt()
        "AMBER" -> 0xFF161008.toInt()
        else -> 0xFF141517.toInt()
    }

    fun resolve(t: ThemeSettings, eventColor: Int, index: Int, isToday: Boolean, isFirstToday: Boolean): CardLook {
        val idx = index.coerceIn(0, 4)
        val step = if (t.fade) idx / 4f else 0f
        val frac = step * 0.35f      // card fill fade, capped at 35%
        val textFrac = step * 0.30f  // text fade, capped at 30%
        val bgRgb = effectiveBg(t.bgColor)
        val cardMode = t.useGoogleColors && t.markerStyle == "CARD"

        var gradient: String? = null
        var fillAlpha = 255
        val fill: Int
        val surface: Int
        if (cardMode) {
            var c = opaque(eventColor)
            if (t.dim) c = scale(c, 0.30f)
            fill = blend(c, bgRgb, frac)
            surface = fill
        } else if (t.gradientStyle != "NONE" && !t.gradient) {
            // Gradient switch off: preset shows as its flat base colour
            fill = blend(gradientStart(t.gradientStyle), bgRgb, frac)
            surface = fill
        } else if (t.gradientStyle != "NONE") {
            gradient = t.gradientStyle
            val a = if (t.fade) (1f - idx * 0.0875f).coerceIn(0.65f, 1f) else 1f
            fillAlpha = (a * 255).roundToInt()
            fill = gradientStart(t.gradientStyle)
            surface = blend(bgRgb, fill, a)
        } else {
            val a = alpha(t.cardColor)
            fillAlpha = a
            fill = blend(opaque(t.cardColor), bgRgb, frac)
            surface = blend(bgRgb, fill, a / 255f)
        }

        val light = isLight(surface)
        val titleBase = if (light) 0xFF111111.toInt() else 0xFFFFFFFF.toInt()
        // Derived from the card surface so it stays readable on bright event colors
        val subBase = blend(titleBase, surface, 0.30f)
        val title = if (t.fade) blend(titleBase, surface, textFrac) else titleBase
        val sub = if (t.fade) blend(subBase, surface, textFrac) else subBase

        val markerVisible = t.markerStyle == "DOT" || t.markerStyle == "RING" || t.markerStyle == "BAR"
        val markerColor = when {
            !t.useGoogleColors && t.markerColor != 0 -> opaque(t.markerColor)
            t.useGoogleColors -> opaque(eventColor)
            isFirstToday -> RED
            isToday -> if (light) 0xFF777777.toInt() else 0xFFCCCCCC.toInt()
            else -> if (light) 0xFF111111.toInt() else 0xFFFFFFFF.toInt()
        }
        val lg = t.gradient && gradient == null
        return CardLook(gradient, fill, fillAlpha, title, sub, markerVisible, markerColor, lg, if (lg) blend(fill, 0xFFFFFFFF.toInt(), 0.25f) else fill)
    }
}
