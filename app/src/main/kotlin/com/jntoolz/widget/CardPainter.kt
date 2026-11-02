package com.jntoolz.widget

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import com.jntoolz.data.CardMath
import com.jntoolz.data.CardSpec

/**
 * The one place a card background is drawn: Calendar widget bitmaps, Counter widget bitmaps and both
 * in-app previews call [draw], so preview and widget cannot differ.
 *
 * Glass (no real blur, RemoteViews cannot): a translucent fill (the spec already carries the lower
 * alpha), a white sheen of [SHEEN_ALPHA] fading to nothing across the top-left half, and a 1dp
 * hairline border of [BORDER_ALPHA] white. The sheen goes over the gradient.
 */
object CardPainter {
    /** Sheen white at its brightest corner, 0..255 (about 18%). */
    const val SHEEN_ALPHA = 46
    /** Border white, 0..255 (about 22%). */
    const val BORDER_ALPHA = 56

    /** Draws the card into (0,0,w,h) with corner [radius] px; [px] is pixels per dp for the border. */
    fun draw(c: Canvas, w: Float, h: Float, radius: Float, px: Float, s: CardSpec) {
        val rect = RectF(0f, 0f, w, h)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        if (s.top == s.bottom) {
            p.color = s.bottom
        } else {
            val g = CardMath.gradientLine(s.angle, w, h)
            p.shader = LinearGradient(g.x0, g.y0, g.x1, g.y1, s.top, s.bottom, Shader.TileMode.CLAMP)
        }
        c.drawRoundRect(rect, radius, radius, p)
        if (!s.glass) return

        p.shader = LinearGradient(
            0f, 0f, w, h,
            intArrayOf((SHEEN_ALPHA shl 24) or 0xFFFFFF, 0x00FFFFFF, 0x00FFFFFF),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )
        c.drawRoundRect(rect, radius, radius, p)

        val sw = maxOf(1f, px)
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = sw; color = (BORDER_ALPHA shl 24) or 0xFFFFFF
        }
        val r = (radius - sw / 2).coerceAtLeast(0f)
        c.drawRoundRect(RectF(sw / 2, sw / 2, w - sw / 2, h - sw / 2), r, r, line)
    }
}
