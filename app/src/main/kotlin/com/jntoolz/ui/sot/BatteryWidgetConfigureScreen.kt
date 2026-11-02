package com.jntoolz.ui.sot

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.graphics.Brush
import com.jntoolz.ui.common.PreviewBox
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.jntoolz.sot.widget.Background
import com.jntoolz.sot.widget.BatteryWidgetConfig
import com.jntoolz.sot.widget.BatteryWidgetModel
import com.jntoolz.sot.widget.BatteryWidgetRenderer
import com.jntoolz.sot.widget.BatteryWidgets
import com.jntoolz.sot.widget.Range
import com.jntoolz.sot.widget.Value
import com.jntoolz.sot.widget.WidgetData
import com.jntoolz.ui.common.ApplyButton
import com.jntoolz.ui.common.ApplyButtonClearance
import com.jntoolz.ui.common.CardStyleEditor
import com.jntoolz.ui.common.ThemeRow
import com.jntoolz.ui.common.pinnedPreviewMaxHeight
import com.jntoolz.ui.common.applyButtonPadding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.min

/**
 * Battery graph widget options with a live preview on top: the exact bitmap the widget draws,
 * at the widget's real size and aspect (scaled down only when wider than the screen).
 * The floating APPLY saves and redraws the widget at once; when the widget is being placed for the first
 * time it hands the choice to [onDone] instead, so the activity finishes with RESULT_OK.
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
    val scope = rememberCoroutineScope()
    var cfg by remember { mutableStateOf<BatteryWidgetConfig?>(null) }
    var saved by remember { mutableStateOf<BatteryWidgetConfig?>(null) }
    // A widget that was never configured has no saved entry yet: it is being placed now.
    val placing = remember(appWidgetId) {
        !BatteryWidgets.hasConfig(context, appWidgetId)
    }
    val data by produceState<WidgetData?>(null) {
        value = withContext(Dispatchers.IO) { BatteryWidgets.load(context.applicationContext) }
    }
    LaunchedEffect(appWidgetId) {
        val loaded = withContext(Dispatchers.IO) { BatteryWidgets.config(context.applicationContext, appWidgetId) }
        cfg = loaded
        saved = loaded
    }
    val c = cfg
    val bgBitmap by produceState<ImageBitmap?>(null, c?.bg, c?.style) {
        value = if (c == null) null else withContext(Dispatchers.Default) {
            BatteryWidgets.backgroundBitmap(context.applicationContext, c, wDp, hDp)?.asImageBitmap()
        }
    }
    val bitmap by produceState<ImageBitmap?>(null, c, data) {
        val d = data
        if (c != null && d != null) {
            value = withContext(Dispatchers.Default) {
                BatteryWidgetRenderer.render(c, BatteryWidgetModel.build(c, d), wDp, hDp, density).asImageBitmap()
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SotColors.Bg)
    ) {
        Column(Modifier.fillMaxSize()) {
            SotTopBar("BATTERY GRAPH", onClose)
            Preview(bitmap, bgBitmap, c?.bg ?: Background.BLACK, wDp, hDp)
            HorizontalDivider(thickness = 1.dp, color = Color(0xFF222222))
            if (c == null) return@Column
            fun set(f: (BatteryWidgetConfig) -> BatteryWidgetConfig) {
                cfg = f(c)
            }
            // The options (and their scrim) never draw up over the pinned preview box.
            Box(Modifier.weight(1f).clipToBounds()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .navigationBarsPadding()
                        .padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = ApplyButtonClearance),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    SotCard(14.dp) {
                        Option("Range") {
                            Segmented(Range.CHOICES.map { it to it.label }, c.range, { v -> set { it.copy(range = v) } }, Modifier.fillMaxWidth())
                        }
                        Option("Big value") {
                            Segmented(Value.BIG.map { it to it.label }, c.big, { v -> set { it.copy(big = v) } }, Modifier.fillMaxWidth())
                        }
                        Option("Small value", last = true) {
                            Segmented(Value.SMALL.map { it to it.label }, c.small, { v -> set { it.copy(small = v) } }, Modifier.fillMaxWidth())
                        }
                    }
                    SotCard(4.dp) {
                        SwitchRow("Label", c.label) { set { it.copy(label = !it.label) } }
                    }
                    // Theming: the same controls as the Calendar and Counter cards.
                    SotCard(4.dp) {
                        val bgs = Background.CHOICES.filter { it != Background.WALLPAPER || BatteryWidgets.wallpaperSupported }
                        ThemeRow("Background") {
                            Segmented(
                                bgs.map { it to it.label }, c.bg, { v -> set { it.copy(bg = v) } },
                                Modifier
                                    .weight(1f)
                                    .padding(vertical = 6.dp)
                            )
                        }
                        if (c.bg == Background.BLACK) {
                            CardStyleEditor(style = c.style, onChange = { s -> set { it.copy(style = s) } })
                        }
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(12.dp)
                        .align(Alignment.TopCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color(0xFF000000), Color.Transparent)
                            )
                        )
                )
            }
        }
        val current = c
        if (current != null) {
            ApplyButton(
                dirty = placing || current != saved,
                onApply = {
                    if (placing) {
                        onDone(current)
                    } else {
                        saved = current
                        val app = context.applicationContext
                        scope.launch(Dispatchers.IO) {
                            BatteryWidgets.save(app, appWidgetId, current)
                            BatteryWidgets.refresh(app, intArrayOf(appWidgetId))
                        }
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .applyButtonPadding()
            )
        }
    }
}

/** The widget as placed: its background (bitmap, else colour) and corners, and the very bitmap it shows. */
@Composable
private fun Preview(bitmap: ImageBitmap?, bgBitmap: ImageBitmap?, bg: Background, wDp: Int, hDp: Int) {
    val context = LocalContext.current
    val maxPreview = pinnedPreviewMaxHeight()
    val radiusDp = BatteryWidgets.cornerPx(context) / LocalDensity.current.density
    val color = remember(bg) { BatteryWidgets.backgroundColor(context, bg)?.let { Color(it) } ?: Color.Transparent }
    PreviewBox(modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp), padding = 14.dp) {
        BoxWithConstraints(
            contentAlignment = Alignment.Center
        ) {
            // Scaled down uniformly to fit the width and the shared pinned-preview height cap; never stretched.
            val k = min(1f, min(maxWidth.value / wDp, (maxPreview.value - 40f) / hDp))
            val w = (wDp * k).dp
            val h = (hDp * k).dp
            Box(
                Modifier
                    .size(w, h)
                    .clip(RoundedCornerShape((radiusDp * k).dp))
                    .then(if (bgBitmap == null) Modifier.background(color) else Modifier)
            ) {
                if (bgBitmap != null) Image(bgBitmap, contentDescription = null, modifier = Modifier.size(w, h))
                if (bitmap != null) Image(bitmap, contentDescription = null, modifier = Modifier.size(w, h))
            }
        }
    }
}

@Composable
private fun ColumnScope.Option(name: String, last: Boolean = false, content: @Composable () -> Unit) {
    SotLabel(name, Modifier.padding(bottom = 8.dp))
    content()
    if (!last) Box(Modifier.height(14.dp))
}
