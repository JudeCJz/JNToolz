package com.jntoolz.sot.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.graphics.Typeface
import androidx.core.graphics.createBitmap
import com.jntoolz.sot.GraphMath
import com.jntoolz.ui.theme.NothingFont
import com.jntoolz.ui.theme.NothingFonts
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Draws a Battery graph widget face as one transparent bitmap (the background is a separate tinted view,
 * so the wallpaper colour can follow the system live). The configure preview shows this same bitmap.
 *
 * Two layouts: one row (under 100 dp tall) has the graph on the left and the big value with one quiet line
 * under it on the right; taller widgets have the quiet value top-left, the big value top-right, the graph
 * across the middle and the label bottom-left. One text style (Lettera 11) for everything but the big value.
 */
object BatteryWidgetRenderer {
    private const val MAX_PIXELS = 900_000 // ~3.6 MB, well under the RemoteViews bitmap limit
    private const val MAX_SIDE = 1600

    const val WHITE = 0xFFFFFFFF.toInt()
    const val GREEN = 0xFF3DDC84.toInt()
    private const val SOFT = 0xB8FFFFFF.toInt()   // the small value
    private const val QUIET = 0x80FFFFFF.toInt()  // the label
    private const val DIM = 0x59FFFFFF            // "no data yet" dots
    private const val SHADOW = 0xB3000000.toInt() // behind everything on a clear background

    /** Is this size the one-row layout (graph left, big value right)? */
    fun isRow(hDp: Int): Boolean = hDp < 100

    fun render(cfg: BatteryWidgetConfig, face: Face, wDp: Int, hDp: Int, density: Float): Bitmap {
        val w0 = max(40, wDp) * density
        val h0 = max(40, hDp) * density
        // Cap the bitmap: scale the whole drawing down rather than cropping it.
        var k = 1f
        if (w0 * h0 > MAX_PIXELS) k = sqrt(MAX_PIXELS / (w0 * h0))
        k = min(k, MAX_SIDE / max(w0, h0))
        val d = density * k
        val w = (w0 * k).roundToInt().coerceAtLeast(1)
        val h = (h0 * k).roundToInt().coerceAtLeast(1)
        val bmp = createBitmap(w, h)
        Pen(d, cfg.bg == Background.CLEAR).run {
            if (isRow(hDp)) row(Canvas(bmp), face, w.toFloat(), h.toFloat()) else tall(Canvas(bmp), face, w.toFloat(), h.toFloat())
        }
        return bmp
    }

    /** Paints and layouts for one bitmap at d px per dp; [clear] adds a soft shadow so it reads on any wallpaper. */
    private class Pen(val d: Float, val clear: Boolean) {
        // On a clear background translucent white vanishes over light wallpapers: opaque greys there.
        val soft = if (clear) 0xFFEDEDED.toInt() else SOFT
        val quiet = if (clear) 0xFFD6D6D6.toInt() else QUIET
        val big = text(NothingFonts.typeface(NothingFont.NDOT_57), WHITE, 40f)
        val small = text(NothingFonts.typeface(NothingFont.LETTERA), soft, 11f)
        val label = text(NothingFonts.typeface(NothingFont.LETTERA), quiet, 11f)

        fun text(face: Typeface, color: Int, sizeDp: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = face
            this.color = color
            textSize = sizeDp * d
            if (clear) setShadowLayer(1.6f * d, 0f, 0.6f * d, SHADOW)
        }

        /** Cap height, near enough: digits and capitals sit on the baseline and reach about 0.72 em. */
        fun cap(p: Paint): Float = p.textSize * 0.72f

        /**
         * Taller widgets: quiet value top-left and big value top-right when both fit at full size, else (narrow
         * squares) the big value top-left with the quiet value under it. Graph across the middle, label bottom-left.
         */
        fun tall(cv: Canvas, f: Face, w: Float, h: Float) {
            val pad = 16 * d
            val inner = w - 2 * pad
            big.textSize = (h / d * 0.3f).coerceIn(30f, 56f) * d
            val bigText = f.big
            val bw0 = bigText?.let { big.measureText(it) } ?: 0f
            if (bw0 > inner) big.textSize *= inner / bw0
            val bw = bigText?.let { big.measureText(it) } ?: 0f
            small.color = if (f.smallCharging) GREEN else soft
            val sw = f.small?.let { small.measureText(it) } ?: 0f
            val side = f.small == null || bigText == null || sw + 14 * d + bw <= inner
            val capBig = if (bigText != null) cap(big) else 0f
            var top = pad + capBig
            if (side) {
                bigText?.let { cv.drawText(it, w - pad - bw, top, big) }
                f.small?.let { cv.drawText(it, pad, pad + cap(small), small) }
                if (bigText == null && f.small != null) top = pad + cap(small)
            } else {
                cv.drawText(bigText!!, pad, top, big)
                top += 9 * d + cap(small)
                cv.drawText(fit(f.small!!, small, inner), pad, top, small)
            }
            var gb = h - pad
            f.label?.let {
                cv.drawText(fit(it, label, inner), pad, h - pad, label)
                gb -= cap(label) + 12 * d
            }
            graph(cv, f, pad, top + 14 * d, w - pad, gb, 2f)
        }

        /** One row: graph left, big value and one quiet line (small value, else label) right. */
        fun row(cv: Canvas, f: Face, w: Float, h: Float) {
            val pad = 14 * d
            val gap = 5 * d
            val inner = w - 2 * pad
            val bigText = f.big
            val p2 = small
            var secText = f.small ?: f.label
            p2.color = if (f.small != null) (if (f.smallCharging) GREEN else soft) else quiet
            fun sizeBig(withSecond: Boolean): Float {
                val secH = if (withSecond) cap(p2) + gap else 0f
                big.textSize = ((h - 2 * pad - secH) / 0.72f / d).coerceIn(18f, 40f) * d
                var bw = bigText?.let { big.measureText(it) } ?: 0f
                if (bw > inner * 0.55f) {
                    big.textSize *= inner * 0.55f / bw
                    bw = big.measureText(bigText!!)
                }
                return bw
            }
            var bw = sizeBig(secText != null)
            // The quiet line may be as wide as the big value or half the widget; never ellipsized, dropped instead.
            secText?.let { s ->
                val maxW = max(bw, inner * 0.5f)
                if (p2.measureText(s) > maxW) p2.textSize = 10 * d
                if (p2.measureText(s) > maxW) {
                    secText = null
                    bw = sizeBig(false)
                }
            }
            val secW = secText?.let { p2.measureText(it) } ?: 0f
            val secH = if (secText != null) cap(p2) + gap else 0f
            val blockW = max(bw, secW)
            val blockH = (if (bigText != null) cap(big) else -gap) + secH
            val y0 = (h - blockH) / 2
            val right = w - pad
            bigText?.let { cv.drawText(it, right - bw, y0 + cap(big), big) }
            secText?.let { cv.drawText(it, right - secW, y0 + blockH, p2) }
            graph(cv, f, pad, pad, right - blockW - 16 * d, h - pad, 1.8f)
        }

        /**
         * The level line over [l, r] x [t, b] (0 % at b, 100 % at t): white, green while charging, a soft shade
         * under it and a dot at now. Time before the first sample is a dim dotted line at the first known level.
         */
        fun graph(cv: Canvas, f: Face, l: Float, t0: Float, r0: Float, b: Float, strokeDp: Float) {
            val s = f.series
            val dotR = (if (strokeDp >= 2f) 3.6f else 3.2f) * d
            val t = t0 + dotR
            val r = r0 - dotR
            if (s.size == 0 || b - t < 6 * d || r - l < 16 * d) return
            val p = GraphMath.project(s, f.t0, f.t1, l, t, r, b)
            val n = s.size
            if (p[0] > l + 3 * d) {
                val dots = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = 1.6f * d
                    strokeCap = Paint.Cap.ROUND
                    color = DIM
                    pathEffect = DashPathEffect(floatArrayOf(0.01f, 4.5f * d), 0f)
                    if (clear) setShadowLayer(1.2f * d, 0f, 0.5f * d, SHADOW)
                }
                val y = p[1]
                cv.drawLine(l + 0.8f * d, y, p[0] - 4 * d, y, dots)
            }
            if (n >= 2) {
                val ctrl = GraphMath.smooth(p)
                val whole = Path()
                whole.moveTo(p[0], p[1])
                for (i in 0 until n - 1) segment(whole, p, ctrl, i)
                // Shade: one area for the whole line, strongest just under its highest point.
                var top = b
                for (i in 0 until n) top = min(top, p[2 * i + 1])
                val area = Path(whole)
                area.lineTo(p[2 * (n - 1)], b)
                area.lineTo(p[0], b)
                area.close()
                val fill = Paint(Paint.ANTI_ALIAS_FLAG)
                fill.shader = LinearGradient(0f, top, 0f, b, if (clear) 0x2EFFFFFF else 0x47FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP)
                cv.drawPath(area, fill)

                val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = strokeDp * d
                    strokeCap = Paint.Cap.ROUND
                    strokeJoin = Paint.Join.ROUND
                    if (clear) setShadowLayer(1.4f * d, 0f, 0.6f * d, SHADOW)
                }
                for (run in GraphMath.runs(s.c)) {
                    val path = Path()
                    path.moveTo(p[2 * run[0]], p[2 * run[0] + 1])
                    for (i in run[0] until run[1]) segment(path, p, ctrl, i)
                    line.color = if (run[2] == 1) GREEN else WHITE
                    cv.drawPath(path, line)
                }
            }
            val x = p[2 * (n - 1)]
            val y = p[2 * (n - 1) + 1]
            val dot = Paint(Paint.ANTI_ALIAS_FLAG)
            val color = if (f.charging) GREEN else WHITE
            dot.color = (color and 0x00FFFFFF) or 0x2E000000
            cv.drawCircle(x, y, dotR * 2.1f, dot)
            dot.color = color
            if (clear) dot.setShadowLayer(1.4f * d, 0f, 0.6f * d, SHADOW)
            cv.drawCircle(x, y, dotR, dot)
        }

        /** Segment i to i+1 as a curve with the given controls. */
        private fun segment(path: Path, p: FloatArray, ctrl: FloatArray, i: Int) {
            path.cubicTo(ctrl[4 * i], ctrl[4 * i + 1], ctrl[4 * i + 2], ctrl[4 * i + 3], p[2 * i + 2], p[2 * i + 3])
        }

        /** Ellipsizes text to fit a width. */
        fun fit(s: String, p: Paint, maxW: Float): String {
            if (maxW <= 0) return ""
            if (p.measureText(s) <= maxW) return s
            var e = s.length
            while (e > 0 && p.measureText(s, 0, e) + p.measureText("…") > maxW) e--
            return if (e == 0) "" else s.substring(0, e).trimEnd() + "…"
        }
    }
}
