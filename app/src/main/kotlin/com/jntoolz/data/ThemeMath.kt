package com.jntoolz.data

import kotlin.math.roundToInt

/** Snapshot of every theming preference; used by both the widget and the in-app preview. */
data class ThemeSettings(
    val useGoogleColors: Boolean,
    val markerStyle: String,      // OFF, DOT, RING, BAR, CARD
    val fade: Boolean,
    val bgColor: Int,             // ARGB
    val cardColor: Int,           // ARGB
    val gradientStyle: String,    // legacy preset name, always NONE after WidgetPreferences migrates
    val markerColor: Int = 0,     // 0 = automatic
    val gradient: Boolean = false, // top-colour-to-normal gradient on card fills
    val gradientTop: Int = 0,      // 0 = auto (white tint)
    val gradientIntensity: Int = 25, // 0..100
    val gradientAngle: Int = 0,    // 0..359, see CardMath.gradientLine
    val glass: Boolean = false,
    val bgGlass: Boolean = false,  // widget background (not the cards) is frosted glass
    val bgGlassLevel: Int = CardStyle.DEFAULT_GLASS_LEVEL, // 0..100, see CardStyle.glassLevel
    val glassLevel: Int = CardStyle.DEFAULT_GLASS_LEVEL,   // card glass, 0..100
) {
    /** The shared card look; [cardColor] is always explicit here (never 0 = auto). */
    val cardStyle: CardStyle
        get() = CardStyle(cardColor, gradient, gradientTop, gradientIntensity, gradientAngle, glass, glassLevel)

    /** The widget background look: black, optionally glass. Drawn by CardPainter like the cards. */
    val bgStyle: CardStyle
        get() = CardStyle(color = 0xFF000000.toInt(), glass = bgGlass, glassLevel = bgGlassLevel)

    fun withCardStyle(s: CardStyle) = copy(
        cardColor = s.color, gradient = s.gradient, gradientTop = s.gradientTop,
        gradientIntensity = s.intensity, gradientAngle = s.angle, glass = s.glass, glassLevel = s.glassLevel
    )

    companion object {
        fun from(p: WidgetPreferences) = ThemeSettings(
            useGoogleColors = p.useGoogleEventColors,
            markerStyle = p.markerStyle,
            fade = p.isFadeEnabled,
            bgColor = p.widgetBackgroundColor.toInt(),
            cardColor = p.customCardColor.toInt(),
            gradientStyle = p.cardGradientStyle,
            markerColor = p.markerColor.toInt(),
            gradient = p.isGradientEnabled,
            gradientTop = p.gradientTopColor.toInt(),
            gradientIntensity = p.gradientIntensity,
            gradientAngle = p.gradientAngle,
            glass = p.isGlassEnabled,
            bgGlass = p.isBgGlassEnabled,
            bgGlassLevel = p.bgGlassLevel,
            glassLevel = p.glassLevel,
        )
    }
}

/** Fully resolved look of one event card. Colors are ARGB ints. */
data class CardLook(
    val spec: CardSpec,        // how the card background is drawn, see CardPainter
    val title: Int,
    val sub: Int,
    val markerVisible: Boolean,
    val markerColor: Int
)

object ThemeMath {
    const val OPAQUE = 0xFF000000.toInt()
    const val RED = 0xFFFF2E2E.toInt()
    /** Text on light surfaces; [WHITE] on dark ones (see [textOn]). */
    const val INK = 0xFF111111.toInt()
    const val WHITE = 0xFFFFFFFF.toInt()

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

    /**
     * The card [spec] as seen composited over [under] (opaque RGB, default black wallpaper): the gradient's
     * middle colour at the spec's fill alpha. Glass sheen is ignored (it is faint white on top).
     */
    fun surfaceOf(spec: CardSpec, under: Int = OPAQUE): Int {
        val mid = blend(opaque(spec.top), opaque(spec.bottom), 0.5f)
        val a = (alpha(spec.top) + alpha(spec.bottom)) / 2f / 255f
        return blend(opaque(under), mid, a)
    }

    /** The one text rule of every tool: [INK] on a light [surfaceOf] surface, white on a dark one. */
    fun textOn(spec: CardSpec, under: Int = OPAQUE): Int = if (isLight(surfaceOf(spec, under))) INK else WHITE

    fun headerColor(bg: Int): Int = if (isLight(effectiveBg(bg))) 0xFF666666.toInt() else 0xFF8E8E8E.toInt()
    fun dividerColor(bg: Int): Int = if (isLight(effectiveBg(bg))) 0xFFD4D4D4.toInt() else 0xFF252525.toInt()

    private fun gradientStart(style: String): Int = when (style) {
        "RUBY" -> 0xFF361717.toInt()
        "CYBER" -> 0xFF142B33.toInt()
        "AMBER" -> 0xFF302414.toInt()
        else -> 0xFF2A2B2D.toInt()
    }

    private fun gradientEnd(style: String): Int = when (style) {
        "RUBY" -> 0xFF180A0A.toInt()
        "CYBER" -> 0xFF0A161A.toInt()
        "AMBER" -> 0xFF161008.toInt()
        else -> 0xFF141517.toInt()
    }

    /** Style equivalent to a removed calendar gradient preset (null for "NONE"/unknown-empty). */
    fun migratePreset(style: String, angle: Int): CardStyle? =
        if (style == "NONE" || style.isEmpty()) null
        else CardStyle.fromPreset(gradientStart(style), gradientEnd(style), angle)

    fun resolve(t: ThemeSettings, eventColor: Int, index: Int, isToday: Boolean, isFirstToday: Boolean): CardLook {
        val idx = index.coerceIn(0, 4)
        val step = if (t.fade) idx / 4f else 0f
        val frac = step * 0.65f      // card fill fade, capped at 65%
        val textFrac = step * 0.50f  // text fade, capped at 50%
        val bgRgb = effectiveBg(t.bgColor)
        val cardMode = t.useGoogleColors && t.markerStyle == "CARD"

        val fill: Int
        val fillAlpha: Int
        if (cardMode) {
            fill = blend(opaque(eventColor), bgRgb, frac)
            fillAlpha = 255
        } else {
            fill = blend(opaque(t.cardColor), bgRgb, frac)
            fillAlpha = alpha(t.cardColor)
        }
        val spec = t.cardStyle.spec((fillAlpha shl 24) or (fill and 0xFFFFFF))
        val surface = surfaceOf(spec, bgRgb)

        val titleBase = textOn(spec, bgRgb)
        val light = titleBase == INK
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
            else -> titleBase
        }
        return CardLook(spec, title, sub, markerVisible, markerColor)
    }
}
