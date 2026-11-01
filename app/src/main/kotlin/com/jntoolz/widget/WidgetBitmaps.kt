package com.jntoolz.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader

/** Rounded-rect bitmaps for RemoteViews (work on every launcher, unlike colour filters). */
object WidgetBitmaps {
    private val cardCache = HashMap<String, Bitmap>()

    fun background(context: Context, argb: Int, wDp: Int, hDp: Int): Bitmap {
        val d = context.resources.displayMetrics.density
        val w = (wDp * d).toInt().coerceIn(64, 900)
        val h = (hDp * d).toInt().coerceIn(64, 900)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = argb }
        val r = 24f * d
        Canvas(bmp).drawRoundRect(RectF(0f, 0f, w.toFloat(), h.toFloat()), r, r, p)
        return bmp
    }

    /** Card bitmap built at the real pixel size so the 14dp corners are true circles. */
    @Synchronized
    fun card(context: Context, top: Int, bottom: Int, alpha: Int, wDp: Int, hDp: Int): Bitmap {
        val d = context.resources.displayMetrics.density
        val w = (wDp * d).toInt().coerceIn(64, 1600)
        val h = (hDp * d).toInt().coerceIn(32, 600)
        val key = "$top|$bottom|$alpha|$w|$h"
        cardCache[key]?.let { return it }
        if (cardCache.size > 40) cardCache.clear()
        val a = alpha / 255f
        fun withA(c: Int) = (c and 0xFFFFFF) or ((a * 255).toInt() shl 24)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, 0f, 0f, h.toFloat(), withA(top), withA(bottom), Shader.TileMode.CLAMP)
        }
        val r = 14f * d
        Canvas(bmp).drawRoundRect(RectF(0f, 0f, w.toFloat(), h.toFloat()), r, r, p)
        cardCache[key] = bmp
        return bmp
    }
}
