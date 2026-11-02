package com.jntoolz.ui.common

import android.annotation.SuppressLint
import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private val NeutralGradient = Brush.linearGradient(
    listOf(Color(0xFF2E2E2E), Color(0xFF1C1C1C), Color(0xFF121212))
)

@Stable
data class WallpaperBackdropState(
    val bitmap: ImageBitmap? = null,
    val fallbackBrush: Brush = NeutralGradient,
)

/**
 * Loads the user's real home-screen wallpaper off the main thread, scaled down to max ~1080px wide.
 * Updates dynamically on resume and listens for wallpaper changes via [WallpaperManager.addOnColorsChangedListener].
 */
@Composable
fun rememberWallpaperBackdrop(): WallpaperBackdropState {
    val context = LocalContext.current.applicationContext
    var changeCounter by remember { mutableIntStateOf(0) }

    // Reload when the screen resumes (in case wallpaper changed while away).
    LifecycleResumeEffect(Unit) {
        changeCounter++
        onPauseOrDispose { }
    }

    // Listen to wallpaper color changes (API 27+).
    DisposableEffect(context) {
        var listener: WallpaperManager.OnColorsChangedListener? = null
        val wm = runCatching { WallpaperManager.getInstance(context) }.getOrNull()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1 && wm != null) {
            val l = WallpaperManager.OnColorsChangedListener { _, which ->
                if (which == 0 || (which and WallpaperManager.FLAG_SYSTEM) != 0) {
                    changeCounter++
                }
            }
            listener = l
            val handler = Handler(Looper.getMainLooper())
            runCatching { wm.addOnColorsChangedListener(l, handler) }
        }
        onDispose {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1 && wm != null && listener != null) {
                runCatching { wm.removeOnColorsChangedListener(listener) }
            }
        }
    }

    val state by produceState(WallpaperCache.current ?: WallpaperBackdropState(), changeCounter) {
        val loaded = withContext(Dispatchers.IO) {
            loadWallpaperState(context)
        }
        WallpaperCache.current = loaded
        value = loaded
    }

    return state
}

private object WallpaperCache {
    var current: WallpaperBackdropState? = null
}

/** The image needs MANAGE_EXTERNAL_STORAGE on Android 13+; without it the call throws and the wallpaper colours are used. */
@SuppressLint("MissingPermission")
private fun loadWallpaperState(context: Context): WallpaperBackdropState {
    val wm = runCatching { WallpaperManager.getInstance(context) }.getOrNull()

    // 1. Try real wallpaper drawable
    val drawable = runCatching {
        wm?.drawable ?: wm?.peekDrawable()
    }.getOrNull()

    var imageBitmap: ImageBitmap? = null
    if (drawable != null) {
        val bmp = drawableToBitmap(drawable, maxDim = 1080)
        if (bmp != null) {
            imageBitmap = bmp.asImageBitmap()
        }
    }

    // 2. Fallback gradient: build from WallpaperManager.getWallpaperColors(FLAG_SYSTEM) primary/secondary
    val fallbackBrush = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1 && wm != null) {
        val colors = runCatching { wm.getWallpaperColors(WallpaperManager.FLAG_SYSTEM) }.getOrNull()
        if (colors != null) {
            val primary = Color(colors.primaryColor.toArgb())
            val secondary = colors.secondaryColor?.toArgb()?.let { Color(it) }
            val tertiary = colors.tertiaryColor?.toArgb()?.let { Color(it) }

            val gradColors = if (secondary != null) {
                listOf(primary, secondary, tertiary ?: primary.copy(alpha = 0.5f))
            } else {
                listOf(primary, primary.copy(alpha = 0.6f), Color(0xFF121212))
            }
            Brush.linearGradient(gradColors)
        } else {
            NeutralGradient
        }
    } else {
        NeutralGradient
    }

    return WallpaperBackdropState(bitmap = imageBitmap, fallbackBrush = fallbackBrush)
}

private fun drawableToBitmap(drawable: Drawable, maxDim: Int = 1080): Bitmap? {
    return runCatching {
        if (drawable is BitmapDrawable && drawable.bitmap != null) {
            val orig = drawable.bitmap
            if (orig.width <= maxDim) orig
            else {
                val scale = maxDim.toFloat() / orig.width
                val targetH = (orig.height * scale).roundToInt().coerceAtLeast(1)
                Bitmap.createScaledBitmap(orig, maxDim, targetH, true)
            }
        } else {
            val origW = drawable.intrinsicWidth.takeIf { it > 0 } ?: maxDim
            val origH = drawable.intrinsicHeight.takeIf { it > 0 } ?: 1920
            val targetW = minOf(origW, maxDim)
            val targetH = (origH * (targetW.toFloat() / origW)).roundToInt().coerceAtLeast(1)
            val bmp = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bmp)
            drawable.setBounds(0, 0, targetW, targetH)
            drawable.draw(canvas)
            bmp
        }
    }.getOrNull()
}

/**
 * Draws the wallpaper backdrop center-cropped inside the current drawing bounds,
 * falling back to the primary/secondary wallpaper colors gradient or neutral gradient.
 */
fun Modifier.drawWallpaperBackdrop(
    bitmap: ImageBitmap?,
    fallbackBrush: Brush
): Modifier = drawBehind {
    if (bitmap != null) {
        val canvasW = size.width
        val canvasH = size.height
        val bmpW = bitmap.width.toFloat()
        val bmpH = bitmap.height.toFloat()
        if (bmpW > 0f && bmpH > 0f) {
            val scale = maxOf(canvasW / bmpW, canvasH / bmpH)
            val scaledW = bmpW * scale
            val scaledH = bmpH * scale
            val left = (canvasW - scaledW) / 2f
            val top = (canvasH - scaledH) / 2f
            drawImage(
                image = bitmap,
                srcOffset = IntOffset.Zero,
                srcSize = IntSize(bitmap.width, bitmap.height),
                dstOffset = IntOffset(left.roundToInt(), top.roundToInt()),
                dstSize = IntSize(scaledW.roundToInt(), scaledH.roundToInt())
            )
        } else {
            drawRect(fallbackBrush)
        }
    } else {
        drawRect(fallbackBrush)
    }
}

/**
 * Convenience modifier to clip to [shape] and draw the dynamic wallpaper backdrop.
 */
@Composable
fun Modifier.wallpaperBackdrop(shape: Shape? = null): Modifier {
    val state = rememberWallpaperBackdrop()
    return (if (shape != null) this.clip(shape) else this)
        .drawWallpaperBackdrop(state.bitmap, state.fallbackBrush)
}

/** Corner radius of every widget preview box in the app (Calendar, Counter, Battery). */
val PreviewBoxShape = RoundedCornerShape(18.dp)

/** The preview box's 1dp outline. */
val PreviewBoxBorder = Color(0xFF222222)

/**
 * The one widget preview box: full width, the user's wallpaper (or gradient fallback) behind the widget,
 * everything clipped to [PreviewBoxShape] first so neither backdrop nor content pokes out of the corners,
 * and the outline drawn with that same shape on top. [content] (the widget, with its own launcher corners)
 * is centred with even [padding] all round. [modifier] is outside the box (e.g. outer margins).
 */
@Composable
fun PreviewBox(
    modifier: Modifier = Modifier,
    padding: Dp = 14.dp,
    borderWidth: Dp = 1.dp,
    borderColor: Color = PreviewBoxBorder,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val state = rememberWallpaperBackdrop()
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(PreviewBoxShape)
            .drawWallpaperBackdrop(state.bitmap, state.fallbackBrush)
            .border(borderWidth, borderColor, PreviewBoxShape)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(padding),
        contentAlignment = Alignment.Center,
        content = content
    )
}
