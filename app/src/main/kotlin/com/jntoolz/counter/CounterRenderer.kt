package com.jntoolz.counter

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import androidx.annotation.RequiresApi
import com.jntoolz.ui.theme.NothingFont
import com.jntoolz.ui.theme.NothingFonts
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Draws a counter widget face as one bitmap so custom type (Ndot 57 or drawn dot digits) looks the same
 * on every launcher. [face] is the only entry point: the widget and every in-app preview call it, so they
 * cannot drift apart.
 *
 * Design system (every theme, every size): one layout engine, themes only pick colours.
 * - Spacing: [CounterLayouts.padDp] at the edges; fixed gaps between rows.
 * - Type: the number in the counter's font (default: system sans), title in sentence case (medium),
 *   label in small tracked caps (medium). Everything aligns to the left padding.
 * - Square / tall: title top-left, number bottom-left, label under it, live clock under that.
 * - Wide (one row): number left, title over label beside it, live clock at the right edge.
 */
object CounterRenderer {

    /** Keeps each widget bitmap well under the RemoteViews bitmap memory limit (~1.4 MB at ARGB_8888). */
    private const val MAX_PIXELS = 360_000

    /**
     * One rendered face plus where (and in which colour) the live clock sits on top of it.
     *
     * Most themes are one full-colour [bitmap]. Material You themes are two white alpha masks,
     * [bitmap] (base: the card) and [overlay] (number / title / label), that the widget tints with
     * system colour resources so the launcher recolours them live when the wallpaper changes.
     */
    class Face(
        val bitmap: Bitmap,
        val overlay: Bitmap?,
        val size: SizeDp,
        val layout: CounterLayout,
        /** Null: no live clock (off, no time, past, or no room in this layout). */
        val clock: ClockSlot?,
        /** Tint for [bitmap] / [overlay] resolved now (null: draw as is), and the clock colour. */
        val baseTint: Int?,
        val overlayTint: Int?,
        val clockColor: Int,
        /** API 31+ colour resources for the same three (0: use the resolved colour). */
        val baseTintRes: Int,
        val overlayTintRes: Int,
        val clockColorRes: Int,
        /** Corner radius of the card in dp (the launcher's widget radius). */
        val cornerDp: Float,
    )

    /**
     * @param counter null draws the empty "+" state.
     * @param pxPerDp pixel density to draw at; previews pass a smaller one for thumbnails. Everything is
     *   laid out in dp, so a lower density gives the same face, just with fewer pixels.
     */
    fun face(
        context: Context,
        counter: Counter?,
        display: CounterDisplay?,
        size: SizeDp,
        pxPerDp: Float = context.resources.displayMetrics.density,
        /** Tool switched off: a dim card with the dot logo, no numbers. */
        off: Boolean = false,
    ): Face {
        val wDp = size.w.coerceAtLeast(40)
        val hDp = size.h.coerceAtLeast(40)
        val layout = CounterLayouts.forSize(wDp, hDp)
        val theme = counter?.theme ?: CounterTheme.BLACK
        val clockMs = if (counter != null && display != null) counter.clockMillis(display) else null
        val clock = if (clockMs != null && !off) CounterLayouts.clockSlot(layout, wDp, hDp) else null
        val corner = cornerDp(context)
        val fontScale = context.resources.configuration.fontScale.coerceIn(1f, 1.3f)
        val gradient = counter?.gradient ?: CounterGradient.NONE

        fun paint(pal: Palette, maxPixels: Int): Bitmap {
            var w = wDp * pxPerDp
            var h = hDp * pxPerDp
            var u = pxPerDp
            if (w * h > maxPixels) {
                val k = sqrt(maxPixels / (w * h))
                w *= k; h *= k; u *= k
            }
            val bmp = Bitmap.createBitmap(w.roundToInt().coerceAtLeast(1), h.roundToInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
            val s = Scene(Canvas(bmp), bmp.width.toFloat(), bmp.height.toFloat(), u, hDp, pal, layout,
                min(corner * u, min(bmp.width, bmp.height) / 2f), counter?.font, fontScale, gradient)
            s.card()
            if (counter == null || display == null) s.empty()
            else s.draw(counter.title.trim(), display, clock, clockMs)
            return bmp
        }

        if (off) {
            return Face(offBitmap(wDp, hDp, pxPerDp, corner, theme), null, SizeDp(wDp, hDp), layout, null,
                null, null, LABEL_DARK, 0, 0, 0, corner)
        }
        val tint = if (gradient.isGradient) null else tint(context, theme)
        if (tint == null) {
            val pal = if (gradient.isGradient && (theme == CounterTheme.YOU || theme == CounterTheme.YOU_SOFT)) {
                Palette(CARD_DARK, 0, WHITE, WHITE, LABEL_DARK, 0, 0, WHITE)
            } else {
                palette(theme)
            }
            return Face(paint(pal, MAX_PIXELS), null, SizeDp(wDp, hDp), layout, clock,
                null, null, pal.clock, 0, 0, 0, corner)
        }
        // Two layers share the RemoteViews bitmap budget.
        val base = paint(YOU_BASE, MAX_PIXELS * 3 / 4)
        val over = paint(YOU_OVER, MAX_PIXELS * 3 / 4)
        return Face(base, over, SizeDp(wDp, hDp), layout, clock,
            tint.base, tint.over, tint.clock, tint.baseRes, tint.overRes, tint.clockRes, corner)
    }

    /** The "off" face: the dark card with a dim 4x4 dot logo in the middle. */
    private fun offBitmap(wDp: Int, hDp: Int, pxPerDp: Float, cornerDp: Float, theme: CounterTheme): Bitmap {
        val (card, dots) = when (theme) {
            CounterTheme.WHITE, CounterTheme.YOU_SOFT -> CARD_LIGHT to 0xFFCFCFCF.toInt()
            CounterTheme.GLASS -> GLASS_CARD to 0x40FFFFFF
            else -> CARD_DARK to 0xFF3A3A3A.toInt()
        }
        var u = pxPerDp
        if (wDp * hDp * u * u > MAX_PIXELS) u *= sqrt(MAX_PIXELS / (wDp * hDp * u * u))
        val bmp = Bitmap.createBitmap((wDp * u).roundToInt().coerceAtLeast(1), (hDp * u).roundToInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val w = bmp.width.toFloat(); val h = bmp.height.toFloat()
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = card }
        val r = min(cornerDp * u, min(w, h) / 2f)
        c.drawRoundRect(RectF(0f, 0f, w, h), r, r, p)
        val side = min(min(w, h) * 0.3f, 40f * u)
        val step = side / 4f
        p.color = dots
        for (i in 0 until 4) for (j in 0 until 4) {
            c.drawCircle(w / 2 - side / 2 + step * (i + 0.5f), h / 2 - side / 2 + step * (j + 0.5f), step * 0.3f, p)
        }
        return bmp
    }

    // ---- Material You ----

    private class Tint(val baseRes: Int, val overRes: Int, val clockRes: Int, val base: Int?, val over: Int, val clock: Int)

    private fun tint(context: Context, theme: CounterTheme): Tint? = when (theme) {
        CounterTheme.YOU, CounterTheme.YOU_SOFT ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) systemTint(context, theme)
            else if (theme == CounterTheme.YOU) Tint(0, 0, 0, 0xFF2B2B2B.toInt(), 0xFFEDEDED.toInt(), 0xFFEDEDED.toInt())
            else Tint(0, 0, 0, 0xFFE6E6E6.toInt(), 0xFF1A1A1A.toInt(), 0xFF1A1A1A.toInt())
        else -> null
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun systemTint(context: Context, theme: CounterTheme): Tint {
        val (b, o) = if (theme == CounterTheme.YOU) {
            android.R.color.system_accent1_800 to android.R.color.system_accent1_100
        } else {
            android.R.color.system_accent1_100 to android.R.color.system_accent1_900
        }
        return Tint(b, o, o, context.getColor(b), context.getColor(o), context.getColor(o))
    }

    /** The launcher's widget corner radius in dp: the system value on Android 12+, else 16dp. */
    fun cornerDp(context: Context): Float {
        cachedCorner?.let { return it }
        val dp = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                context.resources.getDimension(android.R.dimen.system_app_widget_background_radius) /
                    context.resources.displayMetrics.density
            } catch (e: Exception) {
                16f
            }
        } else 16f
        return dp.coerceIn(0f, 40f).also { cachedCorner = it }
    }

    @Volatile
    private var cachedCorner: Float? = null

    // ---- themes: colours only ----

    private class Palette(
        /** Card fill; null = nothing drawn (You overlay mask). */
        val card: Int?,
        /** 1dp inner hairline, 0 = none. */
        val stroke: Int,
        val number: Int,
        val title: Int,
        val label: Int,
        /** Progress bar: unfilled track / filled part; 0 = no bar. */
        val track: Int,
        val fill: Int,
        val clock: Int,
    )

    private fun palette(theme: CounterTheme): Palette = when (theme) {
        CounterTheme.BLACK -> Palette(CARD_DARK, 0, WHITE, WHITE, LABEL_DARK, 0, 0, WHITE)
        CounterTheme.WHITE -> Palette(CARD_LIGHT, 0, INK, INK, LABEL_LIGHT, 0, 0, INK)
        CounterTheme.RED -> Palette(CARD_DARK, 0, RED, WHITE, LABEL_DARK, 0, 0, WHITE)
        CounterTheme.GLASS -> Palette(GLASS_CARD, 0x24FFFFFF, WHITE, WHITE, 0xC8FFFFFF.toInt(), 0, 0, WHITE)
        CounterTheme.PROGRESS -> Palette(CARD_DARK, 0, WHITE, WHITE, LABEL_DARK, 0xFF333333.toInt(), RED, WHITE)
        CounterTheme.YOU, CounterTheme.YOU_SOFT -> YOU_OVER
    }

    /** Material You masks: white where the layer paints, the tint gives the colour. */
    private val YOU_BASE = Palette(WHITE, 0, 0, 0, 0, 0, 0, 0)
    private val YOU_OVER = Palette(null, 0, WHITE, WHITE, 0xB3FFFFFF.toInt(), 0, 0, WHITE)

    /** Per-render state. Paints are created here (renders can run on two threads at once). */
    private class Scene(
        val c: Canvas,
        val W: Float,
        val H: Float,
        val u: Float,
        val hDp: Int,
        val pal: Palette,
        val layout: CounterLayout,
        val corner: Float,
        /** The counter's number font; null = "Theme": the system sans. */
        val numFont: NothingFont?,
        val fs: Float,
        val gradient: CounterGradient = CounterGradient.NONE,
    ) {
        val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        val bounds = Rect()
        val pad = CounterLayouts.padDp(layout) * u
        /** Small widgets step the text down one size. */
        val compact = (layout == CounterLayout.WIDE && H / u < 64f)
        val titleSize = (if (compact) 12f else 13f) * u * fs
        val labelSize = (if (compact) 10f else 11f) * u * fs

        fun card() {
            val r = corner
            if (gradient.isGradient) {
                val shader = android.graphics.LinearGradient(
                    0f, 0f, W, H,
                    gradient.startColor,
                    gradient.endColor,
                    android.graphics.Shader.TileMode.CLAMP
                )
                fill.shader = shader
                c.drawRoundRect(RectF(0f, 0f, W, H), r, r, fill)
                fill.shader = null
            } else {
                pal.card?.let {
                    fill.color = it
                    c.drawRoundRect(RectF(0f, 0f, W, H), r, r, fill)
                }
            }
            if (pal.stroke != 0) {
                val sw = maxOf(1f, 1f * u)
                val st = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE; strokeWidth = sw; color = pal.stroke
                }
                c.drawRoundRect(RectF(sw / 2, sw / 2, W - sw / 2, H - sw / 2), r - sw / 2, r - sw / 2, st)
            }
        }

        // ---- text ----

        fun titlePaint() = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Fonts.medium; textSize = titleSize; color = pal.title; textAlign = Paint.Align.LEFT
        }

        fun labelPaint() = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Fonts.medium; textSize = labelSize; color = pal.label; letterSpacing = 0.08f
            textAlign = Paint.Align.LEFT
        }

        /** Height of capital letters / digits of [p] (from the glyphs, not the line box). */
        fun capH(p: Paint): Float {
            p.getTextBounds("H", 0, 1, bounds)
            return -bounds.top.toFloat()
        }

        /** One line ellipsized to [maxW] with its baseline at [baseline]; returns the drawn width. */
        fun line(s: String, p: TextPaint, x: Float, baseline: Float, maxW: Float): Float {
            if (s.isEmpty() || maxW <= 4f * u) return 0f
            val t = TextUtils.ellipsize(s, p, maxW, TextUtils.TruncateAt.END).toString()
            c.drawText(t, x, baseline, p)
            return p.measureText(t)
        }

        /**
         * Title wrapped to at most [maxLines], cap top of the first line at [capTop].
         * Returns the baseline of its last line (capTop when there is no title).
         */
        fun title(s: String, x: Float, capTop: Float, maxW: Float, maxLines: Int): Float {
            if (s.isEmpty() || maxW <= 4f * u) return capTop
            val p = titlePaint()
            val sl = StaticLayout.Builder.obtain(s, 0, s.length, p, maxW.toInt().coerceAtLeast(1))
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setIncludePad(false)
                .setLineSpacing(0f, 1.05f)
                .setMaxLines(maxLines)
                .setEllipsize(TextUtils.TruncateAt.END)
                .build()
            val first = sl.getLineBaseline(0).toFloat()
            val dy = capTop + capH(p) - first
            c.save()
            c.translate(x, dy)
            sl.draw(c)
            c.restore()
            return dy + sl.getLineBaseline(sl.lineCount - 1)
        }

        // ---- number ----

        /**
         * The number with its baseline at [baseline], ink starting at [x], as large as fits in [maxW] wide and
         * [maxCap] tall (digit cap height). Returns the ink rect.
         */
        fun number(s: String, x: Float, baseline: Float, maxW: Float, maxCap: Float, draw: Boolean = true): RectF {
            if (maxW <= 1f || maxCap <= 1f) return RectF(x, baseline, x, baseline)
            val nf = numFont
            val dot = nf?.isDot == true
            if (nf != null && dot && !NothingFonts.available(nf)) return dots(s, x, baseline, maxW, maxCap, draw)
            val tf = if (nf != null) NothingFonts.typeface(nf) else Fonts.number
            val p = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = tf; textSize = 100f; color = pal.number; textAlign = Paint.Align.LEFT
                letterSpacing = if (dot) 0.04f else -0.01f
            }
            // Cap height from a reference glyph so 1, 23 and 1204 share one baseline and top.
            val ref = if (s.all { it.isDigit() }) "0" else "H"
            p.getTextBounds(ref, 0, 1, bounds)
            val cap100 = (-bounds.top).coerceAtLeast(1).toFloat()
            // Width fit as if there were at least two digits, so "1" is not drawn twice the size of "23".
            val fitRef = if (s.length < 2 && ref == "0") "00" else s
            p.getTextBounds(fitRef, 0, fitRef.length, bounds)
            val ink100 = bounds.width().coerceAtLeast(1).toFloat()
            // Words (TODAY) sit a step below the digits so they do not shout edge to edge.
            val capLimit = if (ref == "H") maxCap * 0.78f else maxCap
            val size = min(100f * capLimit / cap100, 100f * maxW / ink100)
            p.textSize = size
            p.getTextBounds(s, 0, s.length, bounds)
            val dx = x - bounds.left
            if (draw) c.drawText(s, dx, baseline, p)
            return RectF(x, baseline - cap100 * size / 100f, x + bounds.width(), baseline)
        }

        /** Fallback dot-matrix digits (5x7) when the picked dot font is not on the phone. */
        private fun dots(s: String, x: Float, baseline: Float, maxW: Float, maxCap: Float, draw: Boolean): RectF {
            val glyphs = s.map { DOT_GLYPHS[it] ?: DOT_GLYPHS[' ']!! }
            val cols = glyphs.size * 6 - 1
            val cell = min(maxW / maxOf(cols, 11), (if (s.all { it.isDigit() }) maxCap else maxCap * 0.78f) / 7f)
            val top = baseline - cell * 7
            if (!draw) return RectF(x, top, x + cols * cell, baseline)
            fill.color = pal.number
            val r = cell * 0.40f
            glyphs.forEachIndexed { gi, g ->
                for (row in 0 until 7) for (col in 0 until 5) {
                    if (g[row][col] == '1') {
                        c.drawCircle(x + (gi * 6 + col + 0.5f) * cell, top + (row + 0.5f) * cell, r, fill)
                    }
                }
            }
            return RectF(x, top, x + cols * cell, baseline)
        }

        // ---- parts ----

        /** Rounded progress bar centred on [cy]. */
        fun bar(progress: Float, left: Float, right: Float, cy: Float) {
            if (right - left < 8f * u) return
            val h = 4f * u
            val r = h / 2
            fill.color = pal.track
            c.drawRoundRect(RectF(left, cy - r, right, cy + r), r, r, fill)
            val p = progress.coerceIn(0f, 1f)
            if (p > 0f) {
                fill.color = pal.fill
                c.drawRoundRect(RectF(left, cy - r, left + maxOf(h, (right - left) * p), cy + r), r, r, fill)
            }
        }

        private val hasBar get() = pal.track != 0

        /** The bar only means something while counting down (or on the day itself). */
        private fun showBar(d: CounterDisplay) = hasBar && d.phase != CounterDisplay.Phase.PAST

        /** Clock paint matching the Chronometer of [slot] (sans medium, tabular figures). */
        fun clockPaint(slot: ClockSlot) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Fonts.medium; textSize = slot.textSp * u * fs; fontFeatureSettings = "tnum"
        }

        /** The empty state: a thin "+" in the label colour. */
        fun empty() {
            val s = min(min(W, H) * 0.22f, 28f * u)
            val st = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE; strokeWidth = 2f * u; strokeCap = Paint.Cap.ROUND; color = pal.label
            }
            c.drawLine(W / 2 - s / 2, H / 2, W / 2 + s / 2, H / 2, st)
            c.drawLine(W / 2, H / 2 - s / 2, W / 2, H / 2 + s / 2, st)
        }

        // ---- layouts ----

        fun draw(title: String, d: CounterDisplay, clock: ClockSlot?, clockMs: Long?) {
            if (layout == CounterLayout.WIDE) row(title, d, clock, clockMs) else column(title, d, clock)
        }

        /** Square and tall: title top-left; bottom-up: clock, label, (bar), number. */
        private fun column(title: String, d: CounterDisplay, clock: ClockSlot?) {
            val cw = W - 2 * pad
            val titleBase = title(title, pad, pad, cw, CounterLayouts.titleLines(layout, hDp))
            var y = H - pad
            if (clock != null) {
                // Where the Chronometer's digits start (TextView with font padding, bottom margin).
                val cp = clockPaint(clock)
                val clockBase = H - clock.marginBottomDp * u - cp.fontMetrics.bottom
                y = clockBase - capH(cp) - 8f * u
            }
            // Measure the block (number, bar, label) bottom-up first, then draw it.
            val lp = labelPaint()
            val labelH = if (d.label.isNotEmpty()) capH(lp) + 8f * u else 0f
            val barH = if (showBar(d)) 4f * u + 10f * u else 0f
            val numBase = y - labelH - barH
            val top = if (title.isEmpty()) pad else titleBase + 14f * u
            // One dominant number, but never more than ~0.42 of the card's height.
            val cap = min(numBase - top, 0.42f * H)
            number(d.number, pad, numBase, cw, cap, draw = false)
            if (d.label.isNotEmpty()) line(d.label, lp, pad, y, cw)
            if (showBar(d)) bar(d.progress, pad, W - pad, y - labelH - 2f * u)
            number(d.number, pad, numBase, cw, cap)
        }

        /** One row: number left; title over label beside it; clock (a separate view) at the right edge. */
        private fun row(title: String, d: CounterDisplay, clock: ClockSlot?, clockMs: Long?) {
            val padH = pad
            var right = W - padH
            if (clock != null && clockMs != null) {
                val cp = clockPaint(clock)
                right -= cp.measureText(clockText(clockMs)) + 12f * u
            }
            val cap = if (compact) min(H * 0.46f, 30f * u) else min(H - 2 * 15f * u, 40f * u)
            val baseline = H / 2 + cap / 2
            val gap = (if (compact) 10f else 14f) * u
            // The label is never cut: the number gives way first (the title may ellipsize).
            val labelW = if (d.label.isNotEmpty()) labelPaint().measureText(d.label) + 1f * u else 0f
            val numMaxW = min((right - padH) * 0.55f, right - padH - gap - labelW).coerceAtLeast(cap * 0.6f)
            val ink = number(d.number, padH, baseline, numMaxW, cap)
            val tx = ink.right + gap
            val tw = right - tx
            val tp = titlePaint()
            val lp = labelPaint()
            val hasTitle = title.isNotEmpty()
            val hasLabel = d.label.isNotEmpty()
            when {
                hasTitle && hasLabel -> {
                    // Title cap top on the number's top, label baseline on the number's baseline.
                    // Uses the row's full cap height, not the drawn number's: a width-bound number is shorter,
                    // and the two lines must still keep their spacing.
                    val capTop = baseline - cap
                    line(title, tp, tx, capTop + capH(tp), tw)
                    line(d.label, lp, tx, baseline, tw)
                    if (showBar(d) && !compact) bar(d.progress, tx, right, (capTop + capH(tp) + baseline - capH(lp)) / 2f)
                }
                hasTitle -> line(title, tp, tx, H / 2 + capH(tp) / 2, tw)
                hasLabel -> line(d.label, lp, tx, H / 2 + capH(lp) / 2, tw)
            }
        }
    }

    /** Same text a counting-down Chronometer shows: MM:SS under an hour, H:MM:SS above. */
    fun clockText(ms: Long): String {
        val s = (ms + 999) / 1000
        val h = s / 3600
        val m = (s / 60) % 60
        val sec = s % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%02d:%02d".format(m, sec)
    }

    // ---- typefaces (created once) ----

    private object Fonts {
        val medium: Typeface by lazy { Typeface.create("sans-serif-medium", Typeface.NORMAL) }
        val number: Typeface by lazy {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) Typeface.create(Typeface.SANS_SERIF, 500, false)
            else Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }
    }

    private const val WHITE = 0xFFFFFFFF.toInt()
    private const val INK = 0xFF111111.toInt()
    private const val RED = 0xFFFF2E2E.toInt()
    private const val CARD_DARK = 0xFF161616.toInt()
    private const val CARD_LIGHT = 0xFFF4F4F4.toInt()
    private const val LABEL_DARK = 0xFF9A9A9A.toInt()
    private const val LABEL_LIGHT = 0xFF6A6A6A.toInt()
    private const val GLASS_CARD = 0x80000000.toInt()

    private val DOT_GLYPHS: Map<Char, List<String>> = mapOf(
        '0' to listOf("01110", "10001", "10011", "10101", "11001", "10001", "01110"),
        '1' to listOf("00100", "01100", "00100", "00100", "00100", "00100", "01110"),
        '2' to listOf("01110", "10001", "00001", "00010", "00100", "01000", "11111"),
        '3' to listOf("11111", "00010", "00100", "00010", "00001", "10001", "01110"),
        '4' to listOf("00010", "00110", "01010", "10010", "11111", "00010", "00010"),
        '5' to listOf("11111", "10000", "11110", "00001", "00001", "10001", "01110"),
        '6' to listOf("00110", "01000", "10000", "11110", "10001", "10001", "01110"),
        '7' to listOf("11111", "00001", "00010", "00100", "01000", "01000", "01000"),
        '8' to listOf("01110", "10001", "10001", "01110", "10001", "10001", "01110"),
        '9' to listOf("01110", "10001", "10001", "01111", "00001", "00010", "01100"),
        'T' to listOf("11111", "00100", "00100", "00100", "00100", "00100", "00100"),
        'O' to listOf("01110", "10001", "10001", "10001", "10001", "10001", "01110"),
        'D' to listOf("11110", "10001", "10001", "10001", "10001", "10001", "11110"),
        'A' to listOf("01110", "10001", "10001", "11111", "10001", "10001", "10001"),
        'Y' to listOf("10001", "10001", "01010", "00100", "00100", "00100", "00100"),
        ' ' to listOf("00000", "00000", "00000", "00000", "00000", "00000", "00000"),
    )
}
