package com.jntoolz.ui.sot

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jntoolz.sot.DeviceInfo
import com.jntoolz.sot.Drain
import com.jntoolz.sot.Stats
import java.util.Locale
import kotlinx.coroutines.launch

private const val NONE = -1

/**
 * The single dashboard: SOT hero, the graph with its toggle, one stats card, the per-app list, then History.
 * Tapping a stat shows what it means under the card.
 */
@Composable
internal fun Dashboard(vm: SotViewModel, scroll: ScrollState, onUsage: () -> Unit) {
    val st = vm.stats
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val graphHeight = maxOf(200.dp, (LocalConfiguration.current.screenHeightDp * 0.28f).dp)

    SotPage(scroll) {
        Column {
            SotLabel(if (vm.today) "Screen-on time today" else "Screen-on time since you unplugged")
            SotNumber(if (st == null) "–" else if (st.empty) "0m" else Stats.dur(st.sot), 64.sp, Modifier.padding(top = 8.dp))
        }

        SotCard(14.dp) {
            Row(Modifier.align(Alignment.End), verticalAlignment = Alignment.CenterVertically) {
                CopyButton("Copy graph data") { scope.launch { copyGraph(ctx, vm.graphText()) } }
                Spacer(Modifier.width(10.dp))
                SinceSegment(vm.today, vm::pickToday)
            }
            SotGraph(
                st,
                Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
                    .height(graphHeight)
            )
        }

        StatsCard(vm)

        SotSection("BATTERY USAGE BY APP")
        AppsSection(vm, onUsage)

        SotSection("HISTORY")
        HistorySection(vm)
    }
}

/** Puts [text] on the clipboard and says so; does nothing when there is no text. */
internal fun copyGraph(ctx: Context, text: String) {
    if (text.isEmpty()) return
    ctx.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Battery graph", text))
    Toast.makeText(ctx, "Graph data copied", Toast.LENGTH_SHORT).show()
}

/** Screen off | Left (Full in) over 1% lasts (1% in) | Health in one card; tap a cell for its one-line meaning. */
@Composable
private fun StatsCard(vm: SotViewModel) {
    val ctx = LocalContext.current
    val st = vm.stats
    val chg = st?.charging == true
    var picked by remember { mutableIntStateOf(NONE) }
    val pick = { id: Int -> picked = if (picked == id) NONE else id }

    val left = if (chg) "FULL IN" else "LEFT"
    val leftValue = if (chg) {
        if (vm.fullIn < 0) "–" else Stats.dur(vm.fullIn)
    } else {
        val remain = st?.leftMixed()
        if (remain == null) "" else if (remain < 0) "–" else Stats.dur(remain)
    }
    val per = if (chg) "1% IN" else "1% LASTS"
    val explain = when (picked) {
        0 -> "Screen-off time while on battery"
        1 -> if (chg) "Time until full at this charge rate" else "Time left at this drain"
        2 -> if (chg) "Average time to charge 1%, from the last 2 hours" else "Average time to drain 1%, from the last 2 hours"
        3 -> {
            val cap = DeviceInfo.batteryDesignMah(ctx)
            val capStr = String.format(Locale.US, "%,d mAh", cap)
            val age = DeviceInfo.deviceAge()
            "Battery health compared to new ($capStr design · $age)"
        }
        else -> null
    }

    SotCard(0.dp, Modifier.animateContentSize(tween(200))) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            StatCell("SCREEN OFF", st?.let { Stats.dur(it.disOff) } ?: "", Modifier.weight(1f)) { pick(0) }
            VDivider()
            StatCell(left, leftValue, Modifier.weight(1f)) { pick(1) }
        }
        HDivider()
        Row(Modifier.height(IntrinsicSize.Min)) {
            StatCell(per, if (st == null) "" else Drain.short(vm.perPercent), Modifier.weight(1f)) { pick(2) }
            VDivider()
            StatCell("HEALTH", vm.health, Modifier.weight(1f)) { pick(3) }
        }
        if (explain != null) {
            HDivider()
            SotText(explain, 12.sp, SotColors.Dim, bold = false, lineHeight = 18.sp, modifier = Modifier.padding(vertical = 12.dp))
        }
    }
}

@Composable
private fun StatCell(label: String, value: String, modifier: Modifier, onTap: () -> Unit) {
    Column(modifier.tap(onTap).padding(vertical = 16.dp, horizontal = 12.dp)) {
        SotLabel(label)
        SotNumber(value, 28.sp, Modifier.padding(top = 10.dp))
    }
}

@Composable
private fun VDivider() {
    Box(
        Modifier
            .fillMaxHeight()
            .width(1.dp)
            .background(SotColors.Border)
    )
}

@Composable
private fun HDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(SotColors.Border)
    )
}

/** Two overlapping outlined squares: the copy icon, icon only. */
@Composable
internal fun CopyButton(description: String, onClick: () -> Unit) {
    Canvas(
        Modifier
            .size(30.dp)
            .tap(onClick)
            .semantics { contentDescription = description }
    ) {
        val u = size.width / 30f
        val line = Stroke(1.5f * u)
        val r = CornerRadius(3f * u)
        drawRoundRect(SotColors.Dim, Offset(11f * u, 7f * u), Size(12f * u, 14f * u), r, line)
        drawRoundRect(SotColors.Dim, Offset(7f * u, 11f * u), Size(12f * u, 14f * u), r, line)
    }
}
