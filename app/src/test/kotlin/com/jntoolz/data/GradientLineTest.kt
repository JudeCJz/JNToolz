package com.jntoolz.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** The one gradient line every card, widget background and preview is drawn with (via CardPainter). */
class GradientLineTest {
    private val eps = 0.01f

    private fun assertLine(l: GradientLine, x0: Float, y0: Float, x1: Float, y1: Float) {
        assertEquals(x0, l.x0, eps); assertEquals(y0, l.y0, eps)
        assertEquals(x1, l.x1, eps); assertEquals(y1, l.y1, eps)
    }

    @Test fun fortyFiveOnSquareIsCornerToCorner() = assertLine(CardMath.gradientLine(45, 100f, 100f), 0f, 0f, 100f, 100f)

    @Test fun twoSeventyIsRightToLeft() = assertLine(CardMath.gradientLine(270, 200f, 100f), 200f, 50f, 0f, 50f)

    @Test fun diagonalOnSquareTouchesCorners() = assertLine(CardMath.gradientLine(135, 100f, 100f), 0f, 100f, 100f, 0f)

    @Test fun normalizesOutOfRange() {
        assertEquals(10, CardMath.normalize(370))
        assertEquals(350, CardMath.normalize(-10))
        assertLine(CardMath.gradientLine(360, 80f, 40f), 40f, 0f, 40f, 40f)
    }

    @Test fun lineCoversRectangleForAnyAngle() {
        val w = 300f; val h = 120f
        for (a in 0..359) {
            val l = CardMath.gradientLine(a, w, h)
            val dx = l.x1 - l.x0; val dy = l.y1 - l.y0
            val len2 = dx * dx + dy * dy
            for ((cx, cy) in listOf(0f to 0f, w to 0f, 0f to h, w to h)) {
                val t = ((cx - l.x0) * dx + (cy - l.y0) * dy) / len2
                assertEquals("angle $a corner $cx,$cy in range", true, t >= -0.001f && t <= 1.001f)
            }
        }
    }

    @Test fun zeroIsTopToBottom() {
        val g = CardMath.gradientLine(0, 200f, 100f)
        assertEquals(100f, g.x0, eps); assertEquals(0f, g.y0, eps)
        assertEquals(100f, g.x1, eps); assertEquals(100f, g.y1, eps)
    }

    @Test fun ninetyIsLeftToRight() {
        val g = CardMath.gradientLine(90, 200f, 100f)
        assertEquals(0f, g.x0, eps); assertEquals(50f, g.y0, eps)
        assertEquals(200f, g.x1, eps); assertEquals(50f, g.y1, eps)
    }

    @Test fun oneEightyIsBottomToTop() {
        val g = CardMath.gradientLine(180, 200f, 100f)
        assertEquals(100f, g.y0, eps); assertEquals(0f, g.y1, eps)
    }

    @Test fun diagonalCornersGetExactEndColours() {
        val g = CardMath.gradientLine(45, 200f, 100f)
        val dx = g.x1 - g.x0
        val dy = g.y1 - g.y0
        val len2 = dx * dx + dy * dy
        fun t(x: Float, y: Float) = ((x - g.x0) * dx + (y - g.y0) * dy) / len2
        assertEquals(0f, t(0f, 0f), eps)
        assertEquals(1f, t(200f, 100f), eps)
    }

    @Test fun anglesWrap() {
        val a = CardMath.gradientLine(360, 200f, 100f)
        val b = CardMath.gradientLine(0, 200f, 100f)
        assertEquals(b.y1, a.y1, eps)
        val c = CardMath.gradientLine(-90, 200f, 100f)
        val d = CardMath.gradientLine(270, 200f, 100f)
        assertEquals(d.x0, c.x0, eps)
    }
}
