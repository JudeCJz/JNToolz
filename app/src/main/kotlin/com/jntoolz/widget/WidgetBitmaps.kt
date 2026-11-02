package com.jntoolz.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import com.jntoolz.R
import com.jntoolz.data.CardSpec

/** Rounded-rect bitmaps for RemoteViews (work on every launcher, unlike colour filters). */
object WidgetBitmaps {
    /** Access-ordered LRU, capped by bitmap bytes so many event colours cannot pile up memory. */
    private const val MAX_CACHE_BYTES = 6 * 1024 * 1024
    private data class Key(val spec: CardSpec, val w: Int, val h: Int, val radius: Float)
    private val cardCache = LinkedHashMap<Key, Bitmap>(16, 0.75f, true)
    private var cacheBytes = 0

    /** The launcher's widget corner radius in px: the system radius on Android 12+, else 24dp. */
    fun cornerPx(context: Context): Float = context.resources.getDimension(R.dimen.widget_corner_radius)

    /** A flat widget background of [argb] at the widget size with the system corner radius. */
    fun background(context: Context, argb: Int, wDp: Int, hDp: Int): Bitmap =
        widget(context, CardSpec(argb, argb, 0, false), wDp, hDp)

    /**
     * A whole-widget background drawn by [CardPainter] (the same code the in-app previews run) at the widget's
     * real size and corner [radiusPx]. Not cached: one per redraw, and large enough to push the cards out.
     */
    fun widget(context: Context, spec: CardSpec, wDp: Int, hDp: Int, radiusPx: Float = cornerPx(context)): Bitmap {
        val d = context.resources.displayMetrics.density
        val w = (wDp * d).toInt().coerceIn(64, 1200)
        val h = (hDp * d).toInt().coerceIn(64, 1200)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        CardPainter.draw(Canvas(bmp), w.toFloat(), h.toFloat(), radiusPx.coerceAtMost(minOf(w, h) / 2f), d, spec)
        return bmp
    }

    /** Card bitmap built at the real pixel size so the 14dp corners are true circles; keyed by the whole [spec]. */
    @Synchronized
    fun card(context: Context, spec: CardSpec, wDp: Int, hDp: Int): Bitmap {
        val d = context.resources.displayMetrics.density
        val w = (wDp * d).toInt().coerceIn(64, 1600)
        val h = (hDp * d).toInt().coerceIn(32, 600)
        return cached(Key(spec, w, h, 14f * d), d)
    }

    private fun cached(key: Key, d: Float): Bitmap {
        val (spec, w, h) = key
        cardCache[key]?.let { return it }
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        CardPainter.draw(Canvas(bmp), w.toFloat(), h.toFloat(), key.radius, d, spec)
        cardCache[key] = bmp
        cacheBytes += bmp.allocationByteCount
        val it = cardCache.entries.iterator()
        while (cacheBytes > MAX_CACHE_BYTES && cardCache.size > 1 && it.hasNext()) {
            val e = it.next()
            if (e.value === bmp) break
            cacheBytes -= e.value.allocationByteCount
            it.remove()
        }
        return bmp
    }

    @Synchronized
    fun clearCache() {
        cardCache.clear()
        cacheBytes = 0
    }
}
