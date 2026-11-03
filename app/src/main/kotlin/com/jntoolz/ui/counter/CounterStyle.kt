package com.jntoolz.ui.counter

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.Context
import android.os.Build
import android.util.LruCache
import com.jntoolz.R
import com.jntoolz.counter.CounterDisplay
import com.jntoolz.counter.Counter
import com.jntoolz.counter.CounterLayouts
import com.jntoolz.counter.CounterRenderer
import com.jntoolz.tools.Tool
import com.jntoolz.tools.Tools
import com.jntoolz.tools.ToolPerms
import com.jntoolz.ui.common.AttentionDot
import com.jntoolz.ui.hub.OFF_ALPHA
import com.jntoolz.counter.SizeDp
import com.jntoolz.ui.common.PreviewBox
import com.jntoolz.ui.common.ToolOnOffToggle
import com.jntoolz.ui.common.PreviewBoxBorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZonedDateTime

internal object CounterColors {
    val Bg = Color(0xFF000000)
    val Card = Color(0xFF141414)
    val Dialog = Color(0xFF161616)
    val Text = Color(0xFFFFFFFF)
    val Muted = Color(0xFF8E8E8E)
    val Red = Color(0xFFFF2E2E)
    val Border = Color(0xFF222222)
}

/** Dark material scheme for the pickers and sheet, whatever the system theme. */
@Composable
internal fun CounterDarkTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = CounterColors.Red,
            onPrimary = CounterColors.Text,
            secondary = CounterColors.Text,
            background = CounterColors.Bg,
            surface = CounterColors.Dialog,
            onSurface = CounterColors.Text,
            onSurfaceVariant = CounterColors.Muted,
            surfaceContainer = CounterColors.Dialog,
            surfaceContainerHigh = CounterColors.Dialog,
            surfaceContainerHighest = Color(0xFF222222),
            outline = Color(0xFF333333),
            outlineVariant = CounterColors.Border,
        ),
        content = content
    )
}

/** Hub entry, same look as the Calendar / Battery cards: logo, name, enable switch. */
@Composable
fun CounterHubCard(onOpen: () -> Unit) {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(Tools.isEnabled(context, Tool.COUNTER)) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(enabled = enabled) { onOpen() },
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = CounterColors.Card),
        border = BorderStroke(1.dp, CounterColors.Border)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_counter_logo),
                contentDescription = "Counter",
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .alpha(if (enabled) 1f else OFF_ALPHA)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Counter",
                    color = CounterColors.Text,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.alpha(if (enabled) 1f else OFF_ALPHA)
                )
                AttentionDot(ToolPerms.needsAttention(context, Tool.COUNTER))
            }
            Spacer(modifier = Modifier.width(12.dp))
            ToolOnOffToggle(
                enabled = enabled,
                onToggle = { checked ->
                    enabled = checked
                    Tools.setEnabled(context, Tool.COUNTER, checked)
                    if (checked && ToolPerms.missing(context, Tool.COUNTER).isNotEmpty()) onOpen()
                }
            )
        }
    }
}

/** A neutral wallpaper-like backdrop so the translucent GLASS theme reads as on the home screen. */
private val Wallpaper = Brush.linearGradient(listOf(Color(0xFF2E2E2E), Color(0xFF1C1C1C), Color(0xFF121212)))

/**
 * One counter as the home-screen widget, on the backdrop. Width is filled; the widget keeps its real
 * size and is only scaled down (uniformly) when it does not fit.
 */
@Composable
internal fun CounterWidgetTile(
    counter: Counter?,
    size: SizeDp,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    maxHeight: Dp = Dp.Infinity,
    pad: Dp = 18.dp,
    onClick: (() -> Unit)? = null,
) {
    PreviewBox(
        modifier = modifier,
        padding = pad,
        borderWidth = if (selected) 2.dp else 1.dp,
        borderColor = if (selected) CounterColors.Red else PreviewBoxBorder,
        onClick = onClick,
    ) {
        BoxWithConstraints(contentAlignment = Alignment.Center) {
            CounterWidgetFace(counter, size, maxWidth = this.maxWidth, maxHeight = maxHeight)
        }
    }
}

/**
 * The widget face exactly as the widget draws it: the bitmap(s) from [CounterRenderer.face] (the same
 * call the widget makes), the launcher's corner radius, the same tints, and the live clock text where the
 * widget's Chronometer sits (same slot, font, size and colour). Scaled uniformly to fit, never stretched.
 */
@Composable
internal fun CounterWidgetFace(
    counter: Counter?,
    size: SizeDp,
    modifier: Modifier = Modifier,
    maxWidth: Dp = Dp.Infinity,
    maxHeight: Dp = Dp.Infinity,
) {
    val context = LocalContext.current
    val density = LocalDensity.current.density
    val k = CounterLayouts.fitScale(size, maxWidth.value, maxHeight.value)
    val today = LocalDate.now()
    val display = remember(counter, today) { counter?.display(ZonedDateTime.now()) }
    val key = FaceCache.Key(counter, display, size, density * k, today, FaceCache.environment(context))
    // Cache hit: shown in this very frame. Miss: rendered on Dispatchers.Default; until then the last
    // face (or an empty tile of the same size) stays, so the layout never jumps.
    val cached = remember(key) { FaceCache.get(key) }
    val rendered by produceState<Shown?>(null, key) {
        if (cached != null) return@produceState
        val face = withContext(Dispatchers.Default) { FaceCache.render(context.applicationContext, key) }
        value = Shown(face)
    }
    val hit = remember(cached) { cached?.let { Shown(it) } }
    val shown = hit ?: rendered
    val corner = shown?.face?.cornerDp ?: CounterRenderer.cornerDp(context)
    Box(
        modifier = modifier
            .size((size.w * k).dp, (size.h * k).dp)
            .clip(RoundedCornerShape((corner * k).dp))
    ) {
        if (shown != null) FaceLayers(counter, shown, k)
    }
}

/** The bitmap layers and the live clock of one rendered face, inside the clipped widget box. */
@Composable
private fun BoxScope.FaceLayers(counter: Counter?, shown: Shown, k: Float) {
    val face = shown.face
    val base = shown.base
    val over = shown.over
    Image(
        bitmap = base,
        contentDescription = counter?.title,
        contentScale = ContentScale.FillBounds,
        colorFilter = face.baseTint?.let { ColorFilter.tint(Color(it)) },
        modifier = Modifier.matchParentSize()
    )
    if (over != null) {
        Image(
            bitmap = over,
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            colorFilter = face.overlayTint?.let { ColorFilter.tint(Color(it)) },
            modifier = Modifier.matchParentSize()
        )
    }
    val slot = face.clock
    if (counter != null && slot != null) {
        LiveClockText(
            counter = counter,
            color = Color(face.clockColor),
            style = TextStyle(
                fontSize = (slot.textSp * k).sp,
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Medium,
                fontFeatureSettings = "tnum",
                // Chronometer is a TextView: keep its font padding so the baseline lands in the same place.
                platformStyle = PlatformTextStyle(includeFontPadding = true),
            ),
            modifier = if (slot.end) {
                Modifier.align(Alignment.CenterEnd).padding(end = (slot.marginHDp * k).dp)
            } else {
                Modifier.align(Alignment.BottomStart)
                    .padding(start = (slot.marginHDp * k).dp, bottom = (slot.marginBottomDp * k).dp)
            }
        )
    }
}

/** A rendered face plus its Compose bitmaps, wrapped once. */
private class Shown(val face: CounterRenderer.Face) {
    val base = face.bitmap.asImageBitmap()
    val over = face.overlay?.asImageBitmap()
}

/**
 * Small LRU of preview faces (by bitmap bytes), so scrolling back, reopening the editor or flipping
 * themes reuses bitmaps instead of drawing them again. Bitmaps are never recycled: evicted ones may
 * still be on screen and are left to the GC.
 */
private object FaceCache {
    /** Everything that changes the drawing. [env] covers font scale and (API 31+) wallpaper colours. */
    data class Key(
        val counter: Counter?,
        val display: CounterDisplay?,
        val size: SizeDp,
        val pxPerDp: Float,
        val day: LocalDate,
        val env: Long,
    )

    private val cache = object : LruCache<Key, CounterRenderer.Face>(16 * 1024 * 1024) {
        override fun sizeOf(key: Key, value: CounterRenderer.Face): Int =
            value.bitmap.allocationByteCount + (value.overlay?.allocationByteCount ?: 0)
    }

    fun get(key: Key): CounterRenderer.Face? = cache.get(key)

    fun render(context: Context, key: Key): CounterRenderer.Face =
        cache.get(key) ?: CounterRenderer.face(context, key.counter, key.display, key.size, pxPerDp = key.pxPerDp)
            .also { cache.put(key, it) }

    fun environment(context: Context): Long {
        val scale = context.resources.configuration.fontScale.toBits().toLong()
        val accent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getColor(android.R.color.system_accent1_200).toLong() and 0xFFFFFFFFL
        } else 0L
        return (scale shl 32) or accent
    }
}

@Composable
private fun LiveClockText(counter: Counter, color: Color, style: TextStyle, modifier: Modifier) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(counter) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000 - now % 1000)
        }
    }
    val ms = remember(now, counter) { counter.clockMillis(counter.display(ZonedDateTime.now())) } ?: return
    Text(text = formatClock(ms), color = color, style = style, maxLines = 1, softWrap = false, modifier = modifier)
}

/** Same shape as a counting-down Chronometer: MM:SS under an hour, H:MM:SS above. */
internal fun formatClock(ms: Long): String = CounterRenderer.clockText(ms)

/** Drawn back arrow (matches the Calendar screen). */
@Composable
internal fun BackArrow(onBack: () -> Unit) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable { onBack() },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(18.dp)) {
            val w = size.width
            val cy = size.height / 2f
            val stroke = 2.dp.toPx()
            val cap = StrokeCap.Round
            drawLine(CounterColors.Text, Offset(w * 0.08f, cy), Offset(w * 0.92f, cy), stroke, cap)
            drawLine(CounterColors.Text, Offset(w * 0.08f, cy), Offset(w * 0.42f, cy - w * 0.34f), stroke, cap)
            drawLine(CounterColors.Text, Offset(w * 0.08f, cy), Offset(w * 0.42f, cy + w * 0.34f), stroke, cap)
        }
    }
}

/** Drawn "+" for the add button. */
@Composable
internal fun PlusMark(color: Color, sizeDp: Int = 16) {
    Canvas(modifier = Modifier.size(sizeDp.dp)) {
        val s = size.width
        val stroke = 2.dp.toPx()
        drawLine(color, Offset(s / 2, s * 0.1f), Offset(s / 2, s * 0.9f), stroke, StrokeCap.Round)
        drawLine(color, Offset(s * 0.1f, s / 2), Offset(s * 0.9f, s / 2), stroke, StrokeCap.Round)
    }
}
