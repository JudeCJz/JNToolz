package com.jntoolz.sot

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.core.graphics.createBitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jntoolz.R
import com.jntoolz.data.CardStyle
import com.jntoolz.sot.widget.Background
import com.jntoolz.sot.widget.BatteryWidgetConfig
import com.jntoolz.sot.widget.BatteryWidgetModel
import com.jntoolz.sot.widget.BatteryWidgetRenderer
import com.jntoolz.sot.widget.BatteryWidgets
import com.jntoolz.sot.widget.Range
import com.jntoolz.sot.widget.Value
import com.jntoolz.sot.widget.WidgetData
import java.io.File
import java.io.FileOutputStream
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Not a real test: renders the Battery graph widget through the widget's own path
 * (BatteryWidgetModel.build -> BatteryWidgetRenderer.render on BatteryWidgets.backgroundColor) for every size,
 * option and a few synthetic histories, composited on wallpaper-like backdrops, and writes PNGs plus contact
 * sheets to <external files>/render-battery. Reads no real history, opens no activity.
 * Run with `am instrument -w -e class com.jntoolz.sot.BatteryRenderDump com.jntoolz.test/androidx.test.runner.AndroidJUnitRunner`.
 */
@RunWith(AndroidJUnit4::class)
class BatteryRenderDump {

    private val ctx: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val density = ctx.resources.displayMetrics.density
    private val radiusDp = ctx.resources.getDimension(R.dimen.widget_corner_radius) / density
    private val out = File(ctx.getExternalFilesDir(null), "render-battery").apply {
        deleteRecursively(); mkdirs()
    }

    private class Size(val name: String, val w: Int, val h: Int)

    private val sizes = listOf(
        Size("2x1", 150, 60), Size("3x1", 220, 70), Size("4x1", 300, 70), Size("2x2", 150, 150), Size("4x2", 300, 150),
    )

    // ---- synthetic histories ----

    private val now = System.currentTimeMillis() / 60_000 * 60_000
    private val H = Stats.HOUR
    private val M = Stats.MIN

    private fun b(t: Long, level: Int, plug: Int = 0) = Store.B(t, level, plug, if (plug != 0) 2 else 3, 300, 4000, 3_000_000)

    private class History(val name: String, val data: WidgetData)

    /** Discharge from [from] (at level a) to [to] (level z), one sample per level step, with an uneven rate. */
    private fun MutableList<Store.B>.drain(from: Long, to: Long, a: Int, z: Int, wobble: Double = 0.35) {
        val steps = a - z
        for (i in 0..steps) {
            val f = i.toDouble() / steps
            val warp = f + wobble * Math.sin(f * Math.PI * 3) / 6
            add(b(from + ((to - from) * warp.coerceIn(0.0, 1.0)).toLong(), a - i))
        }
    }

    private fun MutableList<Store.B>.charge(from: Long, to: Long, a: Int, z: Int) {
        val steps = z - a
        for (i in 0..steps) {
            // Fast to 80 %, slow after: the usual charge curve.
            val f = i.toDouble() / steps
            val warp = if (f < 0.7) f * 0.55 / 0.7 else 0.55 + (f - 0.7) / 0.3 * 0.45
            add(b(from + ((to - from) * warp).toLong(), a + i, 1))
        }
    }

    private fun screens(vararg on: Pair<Double, Double>) = on.map { (a, z) -> longArrayOf(now - (a * H).toLong(), now - (z * H).toLong()) }

    private fun histories(): List<History> {
        val day = ArrayList<Store.B>().apply {
            drain(now - 30 * H, now - 13 * H, 96, 24)
            charge(now - 13 * H + 5 * M, now - 11 * H, 24, 100)
            drain(now - 11 * H + 3 * M, now, 100, 78, 0.6)
        }
        val low = ArrayList<Store.B>().apply {
            charge(now - 26 * H, now - 23 * H, 40, 100)
            drain(now - 23 * H + 2 * M, now, 100, 6, 0.5)
        }
        val fresh = ArrayList<Store.B>().apply {
            charge(now - 70 * M, now - 12 * M, 71, 100)
            add(b(now - 11 * M, 100))
            add(b(now - 2 * M, 99))
        }
        val charging = ArrayList<Store.B>().apply {
            drain(now - 20 * H, now - 50 * M, 88, 19)
            charge(now - 48 * M, now, 19, 46)
        }
        val scr = screens(9.0 to 8.2, 6.5 to 5.0, 3.0 to 2.6, 1.2 to 0.4)
        return listOf(
            History("day", WidgetData(now, 78, false, 312, day, scr)),
            History("low", WidgetData(now, 6, false, 355, low, scr)),
            History("fresh", WidgetData(now, 99, false, 290, fresh, screens(0.1 to 0.0))),
            History("charging", WidgetData(now, 46, true, 360, charging, scr)),
            History("nodata", WidgetData(now, 64, false, Int.MIN_VALUE, emptyList(), emptyList())),
        )
    }

    // ---- drawing ----

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
            p.shader = LinearGradient(r.left, r.top, r.left + r.width() * 0.3f, r.bottom,
                intArrayOf(0xFF2B3A55.toInt(), 0xFF5A4A6A.toInt(), 0xFF8A5A4A.toInt(), 0xFF2E3B33.toInt()),
                floatArrayOf(0f, 0.35f, 0.55f, 1f), Shader.TileMode.CLAMP)
            c.drawRect(r, p)
            p.shader = RadialGradient(r.left + r.width() * 0.2f, r.top + r.height() * 0.45f, r.width() * 0.35f,
                0x88D9905A.toInt(), 0x00D9905A, Shader.TileMode.CLAMP)
            c.drawRect(r, p)
        }
    }

    /** One widget as the launcher shows it: rounded tinted background, then the face bitmap scaled into it. */
    private fun widget(c: Canvas, x: Float, y: Float, k: Float, s: Size, cfg: BatteryWidgetConfig, d: WidgetData) {
        val dst = RectF(x, y, x + s.w * k, y + s.h * k)
        val bg = BatteryWidgets.backgroundBitmap(ctx, cfg, s.w, s.h)
        if (bg != null) c.drawBitmap(bg, null, dst, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        else BatteryWidgets.backgroundColor(ctx, cfg.bg)?.let { col ->
            c.drawRoundRect(dst, radiusDp * k, radiusDp * k, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = col })
        }
        val dark = BatteryWidgetRenderer.darkInk(BatteryWidgets.surfaceSpec(ctx, cfg))
        val bmp = BatteryWidgetRenderer.render(cfg, BatteryWidgetModel.build(cfg, d), s.w, s.h, density, dark)
        val clip = Path().apply { addRoundRect(dst, radiusDp * k, radiusDp * k, Path.Direction.CW) }
        c.save()
        c.clipPath(clip)
        c.drawBitmap(bmp, null, dst, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        c.restore()
    }

    private class Cell(val label: String, val size: Size, val cfg: BatteryWidgetConfig, val data: WidgetData, val light: Boolean)

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFDDDDDD.toInt(); typeface = Typeface.MONOSPACE }

    /** Rows of cells; each cell is a wallpaper patch with the widget in it and a caption above. k = sheet px per dp. */
    private fun sheet(name: String, rows: List<List<Cell>>, k: Float = 2f) {
        val m = 14 * k
        val cap = 14 * k
        val colW = FloatArray(rows.maxOf { it.size })
        for (r in rows) for ((i, cell) in r.withIndex()) colW[i] = maxOf(colW[i], cell.size.w * k + 2 * m)
        val rowH = rows.map { r -> r.maxOf { it.size.h } * k + 2 * m + cap }
        val w = colW.sum().toInt()
        val h = rowH.sum().toInt()
        val bmp = createBitmap(w, h)
        val c = Canvas(bmp)
        c.drawColor(0xFF161616.toInt())
        labelPaint.textSize = 9 * k
        var y = 0f
        for ((ri, r) in rows.withIndex()) {
            var x = 0f
            for ((i, cell) in r.withIndex()) {
                val patch = RectF(x + 2 * k, y + cap, x + colW[i] - 2 * k, y + rowH[ri] - 2 * k)
                wallpaper(c, patch, cell.light)
                c.drawText(cell.label, x + 4 * k, y + cap - 4 * k, labelPaint)
                widget(c, patch.left + m - 2 * k, patch.top + m - 2 * k, k, cell.size, cell.cfg, cell.data)
                x += colW[i]
            }
            y += rowH[ri]
        }
        save(bmp, name)
    }

    private fun save(bmp: Bitmap, name: String) {
        FileOutputStream(File(out, "$name.png")).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** Native-density single widget on a patch of wallpaper, for close looks. */
    private fun single(name: String, s: Size, cfg: BatteryWidgetConfig, d: WidgetData, light: Boolean) {
        val m = 16 * density
        val bmp = createBitmap((s.w * density + 2 * m).toInt(), (s.h * density + 2 * m).toInt())
        val c = Canvas(bmp)
        wallpaper(c, RectF(0f, 0f, bmp.width.toFloat(), bmp.height.toFloat()), light)
        widget(c, m, m, density, s, cfg, d)
        save(bmp, name)
    }

    @Test
    fun dump() {
        val hs = histories()
        val day = hs.first { it.name == "day" }.data
        val def = BatteryWidgetConfig()

        // 1. Every size x history, default options, dark and light wallpaper.
        for (light in listOf(false, true)) {
            sheet("sizes_" + if (light) "light" else "dark",
                sizes.map { s -> hs.map { Cell("${s.name} ${it.name}", s, def, it.data, light) } })
        }
        // 2. Backgrounds.
        val bgs = Background.CHOICES.map { it.name to def.copy(bg = it) } + listOf(
            "GLASS" to def.copy(style = def.style.copy(glass = true)),
            "LIGHT" to def.copy(style = CardStyle(color = 0xFFF4F4F4.toInt())),
            "GRADIENT" to def.copy(style = CardStyle(color = 0xFF102040.toInt(), gradient = true, intensity = 40, angle = 45)),
        )
        sheet("backgrounds", bgs.map { (name, cfg) ->
            listOf(false, true).flatMap { light ->
                listOf(sizes[1], sizes[4]).map { s -> Cell("$name ${s.name} ${if (light) "light" else "dark"}", s, cfg, day, light) }
            }
        })
        // 3. Every option that changes the look, at 3x1 and 4x2 and 2x2.
        val opts = optionVariants()
        val ss = listOf(sizes[0], sizes[1], sizes[3], sizes[4])
        for ((group, variants) in opts) {
            sheet("opt_$group", variants.map { (label, cfg, hist) ->
                ss.map { s -> Cell("$label ${s.name}", s, cfg, hs.first { it.name == hist }.data, false) }
            })
        }
        // 4. Close-up singles at phone density.
        for (s in sizes) for (h in hs) single("one_${s.name}_${h.name}", s, def, h.data, false)
        single("one_4x2_day_light_clear", sizes[4], def.copy(bg = Background.CLEAR), day, true)
        single("one_4x2_day_wall", sizes[4], def.copy(bg = Background.WALLPAPER), day, false)
    }

    /** group -> (caption, config, history name). Keep in step with BatteryWidgetConfig's options. */
    private fun optionVariants(): List<Pair<String, List<Triple<String, BatteryWidgetConfig, String>>>> {
        val d = BatteryWidgetConfig()
        return listOf(
            "big" to Value.BIG.map { Triple("big=${it.name}", d.copy(big = it), "day") },
            "small" to Value.SMALL.map { Triple("small=${it.name}", d.copy(small = it), "day") },
            "range" to Range.entries.flatMap { r ->
                listOf(Triple("range=${r.name}", d.copy(range = r), "day"), Triple("range=${r.name} fresh", d.copy(range = r), "fresh"))
            },
            "style" to listOf(
                Triple("charging", d, "charging"),
                Triple("charging small=LEFT", d.copy(small = Value.LEFT), "charging"),
                Triple("charging big=LEFT", d.copy(big = Value.LEFT, small = Value.TEMP), "charging"),
                Triple("no label", d.copy(label = false), "day"),
                Triple("no label no small", d.copy(label = false, small = Value.NONE), "day"),
                Triple("low", d, "low"),
                Triple("fresh", d, "fresh"),
                Triple("nodata", d, "nodata"),
            ),
        )
    }
}
