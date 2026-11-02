package com.jntoolz.ui.sot

import android.text.format.DateFormat
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.CacheDrawScope
import androidx.compose.ui.draw.DrawResult
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import com.jntoolz.sot.GraphMath
import com.jntoolz.sot.Stats
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private val ChargeShade = listOf(Color(0x4D3DDC84), Color(0x003DDC84))
private val WhiteShade = listOf(Color(0x47FFFFFF), Color(0x00FFFFFF))

/** A measured label and where its top-left goes. */
private class Placed(val r: TextLayoutResult, val at: Offset)

/**
 * Battery level over the session: a white line with a fading shade under it (green while charging),
 * and a screen-on strip below. Drag sideways to inspect. With a range (r0 > 0) it shows exactly [r0, r1].
 * Everything but the drag readout is built once per data and size (drawWithCache); a drag frame only
 * draws the cursor and its readout. [axis] = false leaves out the scale and time labels (History).
 */
@Composable
internal fun SotGraph(s: Stats?, modifier: Modifier = Modifier, r0: Long = 0, r1: Long = 0, axis: Boolean = true) {
    val context = LocalContext.current
    val tf = remember { DateFormat.getTimeFormat(context) }
    val dayFmt = remember { SimpleDateFormat("EEE ", Locale.getDefault()) }
    val measurer = rememberTextMeasurer()
    val font = SotFonts.label
    var sx by remember { mutableFloatStateOf(-1f) }

    val build: CacheDrawScope.() -> DrawResult = remember(s, r0, r1, axis, font, measurer, tf, dayFmt) {
        {
            val d = density
            val w = size.width
            val top = 24 * d
            val pb = size.height - (if (axis) 24 else 8) * d
            val gx = (if (axis) 30 else 6) * d // left gutter for the 0/50/100 scale
            val pw = w - gx - 5 * d // room for the end dot
            val sh = 4 * d
            val st = pb + 6 * d
            fun y(level: Float) = top + (100 - level) / 100f * (pb - top)

            fun layout(t: String, color: Color, px: Float = 11 * d) =
                measurer.measure(t, TextStyle(color = color, fontSize = px.toSp(), fontFamily = font, fontFeatureSettings = "tnum"))

            /** Text with its baseline at y; align 0 = left, 0.5 = centre, 1 = right. */
            fun label(t: String, x: Float, y: Float, align: Float, color: Color): Placed {
                val r = layout(t, color)
                return Placed(r, Offset(x - r.size.width * align, y - r.firstBaseline))
            }

            // Battery scale: faint gridlines at 0, 50 and 100 with their numbers in the gutter.
            val labels = ArrayList<Placed>()
            if (axis) for (lv in 0..100 step 50) labels += label(lv.toString(), gx - 8 * d, y(lv.toFloat()) + 4 * d, 1f, SotColors.Dim)

            if (s == null || s.empty || s.gt.isEmpty()) {
                if (r0 == 0L) labels += label("Starts at the next unplug", w / 2, y(50f), 0.5f, SotColors.Dim)
                onDrawBehind {
                    for (lv in 0..100 step 50) drawRect(SotColors.Grid, Offset(gx, y(lv.toFloat()) - d / 2), Size(w - gx, d))
                    drawRoundRect(SotColors.Grid, Offset(gx, st), Size(w - gx, sh), CornerRadius(sh / 2))
                    for (p in labels) drawText(p.r, topLeft = p.at)
                }
            } else {
                val n = s.gt.size
                val t0 = if (r0 > 0) r0 else s.start
                val t1 = if (r0 > 0) r1 else max(s.gt[n - 1], s.start + Stats.MIN)
                fun x(t: Long) = gx + (t - t0).toFloat() / (t1 - t0) * pw
                fun time(t: Long): String {
                    val dt = Date(t)
                    return (if (r0 == 0L && t1 - t0 > 20 * Stats.HOUR) dayFmt.format(dt) else "") + tf.format(dt)
                }

                // Areas under the line, split into discharging and charging.
                val dis = Path()
                val charge = Path()
                for (i in 0 until n - 1) {
                    val a = if (s.gc[i]) charge else dis
                    val x0 = x(s.gt[i])
                    val x1 = x(s.gt[i + 1])
                    a.moveTo(x0, pb + d)
                    a.lineTo(x0, y(s.gl[i].toFloat()))
                    a.lineTo(x1, y(s.gl[i + 1].toFloat()))
                    a.lineTo(x1, pb + d)
                    a.close()
                }
                val disBrush = Brush.verticalGradient(WhiteShade, startY = top, endY = pb)
                val chargeBrush = Brush.verticalGradient(ChargeShade, startY = top, endY = pb)

                val ex = x(s.gt[n - 1])
                val ey = y(s.gl[n - 1].toFloat())
                val endLabel = label("${s.gl[n - 1]}%", min(ex + 4 * d, w), max(ey - 12 * d, 12 * d), 1f, SotColors.Txt)

                val ly = size.height - 2 * d
                if (!axis) {
                    // History graphs are the picture only: no time labels.
                } else if (r0 > 0) {
                    // Hour ticks spaced to fit: every hour for short spans, up to every 6 hours for a whole day.
                    val span = (r1 - r0) / Stats.HOUR
                    val step = (if (span <= 4) 1 else if (span <= 9) 2 else if (span <= 15) 3 else 6) * Stats.HOUR
                    labels += label(tf.format(Date(r0)), gx, ly, 0f, SotColors.Dim)
                    var t = r0 + step
                    while (t < r1 - Stats.HOUR / 2) {
                        labels += label(tf.format(Date(t)), x(t), ly, 0.5f, SotColors.Dim)
                        t += step
                    }
                } else {
                    labels += label(time(t0), gx, ly, 0f, SotColors.Dim)
                    labels += label("now", w, ly, 1f, SotColors.Dim)
                }

                onDrawBehind {
                    for (lv in 0..100 step 50) drawRect(SotColors.Grid, Offset(gx, y(lv.toFloat()) - d / 2), Size(w - gx, d))
                    drawRoundRect(SotColors.Grid, Offset(gx, st), Size(w - gx, sh), CornerRadius(sh / 2))
                    drawPath(dis, disBrush)
                    drawPath(charge, chargeBrush)
                    for (i in 0 until n - 1) {
                        drawLine(
                            if (s.gc[i]) SotColors.Green else SotColors.Txt,
                            Offset(x(s.gt[i]), y(s.gl[i].toFloat())),
                            Offset(x(s.gt[i + 1]), y(s.gl[i + 1].toFloat())),
                            strokeWidth = 2.4f * d,
                            cap = StrokeCap.Round
                        )
                    }
                    for (i in 0 until s.on.size - 1 step 2) {
                        val a = x(s.on[i])
                        val z = min(max(x(s.on[i + 1]), a + d), w)
                        drawRect(SotColors.Txt, Offset(a, st), Size(z - a, sh))
                    }
                    drawCircle(SotColors.Card, 5.5f * d, Offset(ex, ey))
                    drawCircle(if (s.charging) SotColors.Green else SotColors.Txt, 4 * d, Offset(ex, ey))
                    for (p in labels) drawText(p.r, topLeft = p.at)

                    // Per frame while dragging: only this part reads the drag position.
                    val cx = sx
                    if (cx < 0) {
                        drawText(endLabel.r, topLeft = endLabel.at)
                    } else {
                        val px = max(gx, min(gx + pw, cx))
                        val t = t0 + ((px - gx) / pw * (t1 - t0)).toLong()
                        val lv = GraphMath.levelAt(s.gt, s.gl, t)
                        drawRect(SotColors.Faint, Offset(px - d / 2, top), Size(d, st + sh - top))
                        drawCircle(SotColors.Txt, 4.5f * d, Offset(px, y(lv)))
                        var on = false
                        for (i in 0 until s.on.size - 1 step 2) if (t >= s.on[i] && t <= s.on[i + 1]) { on = true; break }
                        // The readout: kept on screen, on a black backing so it reads over the line.
                        val r = layout("${time(t)}  ·  ${lv.roundToInt()}%  ·  screen ${if (on) "on" else "off"}", SotColors.Txt, 12 * d)
                        val tw = r.size.width.toFloat()
                        val tx = max(0f, min(w - tw, px - tw / 2))
                        drawRect(SotColors.Card, Offset(tx - 6 * d, 0f), Size(tw + 12 * d, 18 * d))
                        drawText(r, topLeft = Offset(tx, 13 * d - r.firstBaseline))
                    }
                }
            }
        }
    }

    Spacer(
        modifier
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = { sx = -1f },
                    onDragCancel = { sx = -1f }
                ) { change, _ ->
                    change.consume()
                    sx = change.position.x
                }
            }
            .drawWithCache(build)
    )
}
