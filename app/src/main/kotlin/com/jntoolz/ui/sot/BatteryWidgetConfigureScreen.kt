package com.jntoolz.ui.sot

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jntoolz.sot.widget.Background
import com.jntoolz.sot.widget.BatteryWidgetConfig
import com.jntoolz.sot.widget.BatteryWidgetModel
import com.jntoolz.sot.widget.BatteryWidgetRenderer
import com.jntoolz.sot.widget.BatteryWidgets
import com.jntoolz.sot.widget.Range
import com.jntoolz.sot.widget.Value
import com.jntoolz.sot.widget.WidgetData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.min

/**
 * Battery graph widget options with a live preview on top: the exact bitmap the widget draws,
 * at the widget's real size and aspect (scaled down only when wider than the screen).
 */
@Composable
fun BatteryWidgetConfigureScreen(
    appWidgetId: Int,
    wDp: Int,
    hDp: Int,
    onDone: (BatteryWidgetConfig) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current.density
    var cfg by remember { mutableStateOf<BatteryWidgetConfig?>(null) }
    val data by produceState<WidgetData?>(null) {
        value = withContext(Dispatchers.IO) { BatteryWidgets.load(context.applicationContext) }
    }
    LaunchedEffect(appWidgetId) {
        cfg = withContext(Dispatchers.IO) { BatteryWidgets.config(context.applicationContext, appWidgetId) }
    }
    val c = cfg
    val bitmap by produceState<ImageBitmap?>(null, c, data) {
        val d = data
        if (c != null && d != null) {
            value = withContext(Dispatchers.Default) {
                BatteryWidgetRenderer.render(c, BatteryWidgetModel.build(c, d), wDp, hDp, density).asImageBitmap()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SotColors.Bg)
    ) {
        SotTopBar("BATTERY GRAPH", onClose)
        Preview(bitmap, c?.bg ?: Background.BLACK, wDp, hDp)
        if (c == null) return@Column
        fun set(f: (BatteryWidgetConfig) -> BatteryWidgetConfig) {
            cfg = f(c)
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            SotCard(14.dp) {
                Option("Range") {
                    Segmented(Range.entries.map { it to it.label }, c.range, { v -> set { it.copy(range = v) } }, Modifier.fillMaxWidth())
                }
                Option("Big value") {
                    Segmented(Value.BIG.map { it to it.label }, c.big, { v -> set { it.copy(big = v) } }, Modifier.fillMaxWidth())
                }
                Option("Small value") {
                    Segmented(Value.SMALL.map { it to it.label }, c.small, { v -> set { it.copy(small = v) } }, Modifier.fillMaxWidth())
                }
                val bgs = Background.entries.filter { it != Background.WALLPAPER || BatteryWidgets.wallpaperSupported }
                Option("Background", last = true) {
                    Segmented(bgs.map { it to it.label }, c.bg, { v -> set { it.copy(bg = v) } }, Modifier.fillMaxWidth())
                }
            }
            SotCard(4.dp) {
                SwitchRow("Label", c.label) { set { it.copy(label = !it.label) } }
            }
        }
        Box(
            modifier = Modifier
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 12.dp, top = 4.dp)
                .fillMaxWidth()
                .height(52.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(SotColors.Red)
                .clickable { onDone(c) },
            contentAlignment = Alignment.Center
        ) {
            Text("DONE", color = SotColors.Txt, fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, letterSpacing = 2.sp)
        }
    }
}

/** The widget as placed: its background colour and corners, and the very bitmap it shows. */
@Composable
private fun Preview(bitmap: ImageBitmap?, bg: Background, wDp: Int, hDp: Int) {
    val context = LocalContext.current
    val color = remember(bg) { BatteryWidgets.backgroundColor(context, bg)?.let { Color(it) } ?: Color.Transparent }
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        val k = min(1f, maxWidth.value / wDp)
        val w = (wDp * k).dp
        val h = (hDp * k).dp
        Box(
            Modifier
                .size(w, h)
                .clip(RoundedCornerShape((24 * k).dp))
                .background(color)
        ) {
            if (bitmap != null) Image(bitmap, contentDescription = null, modifier = Modifier.size(w, h))
        }
    }
}

@Composable
private fun ColumnScope.Option(name: String, last: Boolean = false, content: @Composable () -> Unit) {
    SotLabel(name, Modifier.padding(bottom = 8.dp))
    content()
    if (!last) Box(Modifier.height(14.dp))
}
