package com.jntoolz.counter

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jntoolz.ui.theme.NothingFont
import java.io.File
import java.io.FileOutputStream
import com.jntoolz.data.CardStyle
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Not a real test: renders every sample look x widget size x state through [CounterRenderer.face]
 * (the widget's own draw path) onto wallpaper-like backdrops and writes PNGs to
 * <external files>/render, so the faces can be looked at without touching the home screen.
 * Opens no activity. Run with `am instrument -e class com.jntoolz.counter.CounterRenderDump`.
 */
@RunWith(AndroidJUnit4::class)
class CounterRenderDump {

    private val ctx: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val density = ctx.resources.displayMetrics.density
    private val out = File(ctx.getExternalFilesDir(null), "render").apply {
        deleteRecursively(); mkdirs()
    }

    private class Case(val name: String, val size: SizeDp)

    private val sizes = listOf(
        Case("square", SizeDp(150, 150)),
        Case("wide", SizeDp(300, 70)),
        Case("tall", SizeDp(90, 180)),
        Case("big", SizeDp(300, 150)),
        Case("tiny", SizeDp(150, 60)),
    )

    private class State(val name: String, val counter: Counter, val display: CounterDisplay)

    private val today = LocalDate.of(2026, 11, 1)

    /** Sample card looks (counters are themed only by their card style and font). */
    private val looks = listOf(
        "dark" to CardStyle(),
        "light" to CardStyle(color = 0xFFF4F4F4.toInt()),
        "glass" to CardStyle(glass = true, glassLevel = 50),
        "gradient" to CardStyle(gradient = true, intensity = 40, angle = 45),
    )

    private fun states(style: CardStyle, font: NothingFont? = null): List<State> {
        fun c(title: String, date: LocalDate, time: LocalTime? = null, live: Boolean = false) = Counter(
            id = "x", title = title, date = date, time = time, cardStyle = style, liveClock = live, font = font,
        )
        @Suppress("UNUSED_PARAMETER")
        fun d(phase: CounterDisplay.Phase, days: Long, label: String, progress: Float, ms: Long? = null) =
            CounterDisplay(phase, days, if (phase == CounterDisplay.Phase.TODAY) "TODAY" else days.toString(), label, ms)
        return listOf(
            State("future", c("Trip to Japan", today.plusDays(23)), d(CounterDisplay.Phase.FUTURE, 23, "DAYS LEFT", 0.57f)),
            State("oneday", c("Exam", today.plusDays(1)), d(CounterDisplay.Phase.FUTURE, 1, "DAY LEFT", 0.97f)),
            State("today", c("Birthday", today), d(CounterDisplay.Phase.TODAY, 0, "", 1f)),
            State("past", c("Quit smoking", today.minusDays(412)), d(CounterDisplay.Phase.PAST, 412, "DAYS AGO", 1f)),
            State(
                "clock", c("Launch", today.plusDays(3), LocalTime.of(18, 30), live = true),
                d(CounterDisplay.Phase.FUTURE, 3, "DAYS LEFT", 0.9f, ms = 3L * 86_400_000 + 5 * 3_600_000 + 42 * 60_000 + 17_000)
            ),
            State("longtitle", c("Grandma's 90th birthday party in Lisbon", today.plusDays(1204)), d(CounterDisplay.Phase.FUTURE, 1204, "DAYS LEFT", 0.1f)),
        )
    }

    // ---- backdrops ----

    private fun wallpaper(c: Canvas, r: RectF, light: Boolean) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        if (light) {
            p.shader = LinearGradient(r.left, r.top, r.right, r.bottom,
                intArrayOf(0xFFE9E4DA.toInt(), 0xFFCFD8DC.toInt(), 0xFFF3EFE6.toInt()), null, Shader.TileMode.CLAMP)
            c.drawRect(r, p)
            p.shader = RadialGradient(r.left + r.width() * 0.75f, r.top + r.height() * 0.3f, r.width() * 0.5f,
                0x66A7C4D6, 0x00A7C4D6, Shader.TileMode.CLAMP)
            c.drawRect(r, p)
        } else {
            // Mid-dark "photo": dusk sky into a teal/brown landscape.
            p.shader = LinearGradient(r.left, r.top, r.left + r.width() * 0.3f, r.bottom,
                intArrayOf(0xFF2B3A55.toInt(), 0xFF5A4A6A.toInt(), 0xFF8A5A4A.toInt(), 0xFF2E3B33.toInt()),
                floatArrayOf(0f, 0.35f, 0.55f, 1f), Shader.TileMode.CLAMP)
            c.drawRect(r, p)
            p.shader = RadialGradient(r.left + r.width() * 0.2f, r.top + r.height() * 0.45f, r.width() * 0.35f,
                0x88D9905A.toInt(), 0x00D9905A, Shader.TileMode.CLAMP)
            c.drawRect(r, p)
            p.shader = RadialGradient(r.left + r.width() * 0.85f, r.top + r.height() * 0.8f, r.width() * 0.4f,
                0x77203F3A, 0x00203F3A, Shader.TileMode.CLAMP)
            c.drawRect(r, p)
        }
    }

    // ---- one widget, as the launcher shows it ----

    private fun drawWidget(c: Canvas, x: Float, y: Float, k: Float, size: SizeDp, counter: Counter?, display: CounterDisplay?, off: Boolean = false) {
        val face = CounterRenderer.face(ctx, counter, display, size, pxPerDp = density, off = off)
        val dst = RectF(x, y, x + size.w * k, y + size.h * k)
        val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        p.colorFilter = face.baseTint?.let { PorterDuffColorFilter(it, PorterDuff.Mode.SRC_IN) }
        c.drawBitmap(face.bitmap, null, dst, p)
        face.overlay?.let { ov ->
            p.colorFilter = face.overlayTint?.let { PorterDuffColorFilter(it, PorterDuff.Mode.SRC_IN) }
            c.drawBitmap(ov, null, dst, p)
        }
        val slot = face.clock
        val ms = if (counter != null && display != null && slot != null) counter.clockMillis(display) else null
        if (slot != null && ms != null) {
            // Same as the Chronometer TextView: sans-serif-medium, tnum, includeFontPadding, slot gravity / margins.
            val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                textSize = slot.textSp * k * ctx.resources.configuration.fontScale
                fontFeatureSettings = "tnum"
                color = face.clockColor
            }
            val s = CounterRenderer.clockText(ms)
            val fm = tp.fontMetrics
            if (slot.end) {
                tp.textAlign = Paint.Align.RIGHT
                c.drawText(s, dst.right - slot.marginHDp * k, dst.centerY() - (fm.top + fm.bottom) / 2f, tp)
            } else {
                tp.textAlign = Paint.Align.LEFT
                c.drawText(s, dst.left + slot.marginHDp * k, dst.bottom - slot.marginBottomDp * k - fm.bottom, tp)
            }
        }
    }

    private fun save(bmp: Bitmap, name: String) {
        FileOutputStream(File(out, "$name.png")).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bmp.recycle()
    }

    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.MONOSPACE; color = Color.WHITE }

    /** All sizes (rows) x states (columns) of one look. [k] px per dp of the sheet. */
    private fun sheet(name: String, style: CardStyle, light: Boolean, k: Float, font: NothingFont? = null) {
        val st = states(style, font)
        val gap = 16f
        val colW = 300f + gap
        val head = 26f
        val rowHs = sizes.map { it.size.h + gap }
        val w = (gap + st.size * colW + 150f + gap) * k
        val h = (head + rowHs.sum() + gap) * k
        val bmp = Bitmap.createBitmap(w.toInt(), h.toInt(), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        wallpaper(c, RectF(0f, 0f, w, h), light)
        label.textSize = 12f * k
        label.color = if (light) Color.BLACK else Color.WHITE
        c.drawText("$name   ${if (light) "light" else "dark"} wallpaper${font?.let { "   font " + it.label } ?: ""}", gap * k, 17f * k, label)
        var y = head
        for ((ri, sz) in sizes.withIndex()) {
            for ((ci, s) in st.withIndex()) {
                drawWidget(c, (gap + ci * colW) * k, y * k, k, sz.size, s.counter, s.display)
            }
            // Last column: the empty "+" state (top) / off state (others) at this size.
            val x = (gap + st.size * colW) * k
            val small = SizeDp(minOf(sz.size.w, 150), sz.size.h)
            if (ri % 2 == 0) drawWidget(c, x, y * k, k, small, Counter("e", "", today, cardStyle = style), null)
            else drawWidget(c, x, y * k, k, small, null, null, off = true)
            y += rowHs[ri]
        }
        save(bmp, name)
    }

    /** Every size x state of one look as its own PNG at the phone's density. */
    private fun singles(look: String, style: CardStyle, light: Boolean) {
        val k = density
        for (sz in sizes) for (s in states(style)) {
            val m = 24f
            val w = ((sz.size.w + 2 * m) * k).toInt()
            val h = ((sz.size.h + 2 * m) * k).toInt()
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            wallpaper(c, RectF(0f, 0f, w.toFloat(), h.toFloat()), light)
            drawWidget(c, m * k, m * k, k, sz.size, s.counter, s.display)
            save(bmp, "single/${look}_${sz.name}_${s.name}${if (light) "_light" else ""}")
        }
    }

    @Test
    fun dump() {
        File(out, "single").mkdirs()
        for ((name, style) in looks) {
            sheet("sheet_$name", style, light = false, k = 2f)
            sheet("sheet_${name}_light", style, light = true, k = 2f)
            singles(name, style, light = false)
        }
        // The one non-default font, on the first look.
        for (f in Counter.FONTS.filterNotNull()) {
            sheet("font_${f.name.lowercase()}", looks.first().second, light = false, k = 2f, font = f)
        }
    }
}
