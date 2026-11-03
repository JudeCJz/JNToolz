package com.jntoolz.ui.unscroll

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.jntoolz.ui.sot.SotCard
import com.jntoolz.ui.sot.SotColors
import com.jntoolz.ui.sot.SotLabel
import com.jntoolz.ui.sot.SotNumber
import com.jntoolz.ui.sot.SotText
import com.jntoolz.ui.sot.tap
import com.jntoolz.unscroll.UnscrollStore

/**
 * The two top cards: BLOCKS TODAY, and (soft mode only) a pause card with a round button. The button opens
 * [PauseDialog] (the screen owns that); while paused the red countdown replaces the "N LEFT" text.
 * Both cards are 150dp tall and the height never changes.
 */
@Composable
internal fun UnscrollTopCards(
    blocks: Int,
    showPause: Boolean,
    paused: Boolean,
    secondsLeft: Long,
    pausesLeft: Int,
    onButton: () -> Unit
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        SotCard(vpad = 16.dp, modifier = Modifier.weight(1f).height(150.dp)) {
            SotLabel("BLOCKS TODAY")
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                SotNumber("$blocks", 56.sp)
            }
        }
        if (showPause) {
            val canPause = pausesLeft > 0
            SotCard(vpad = 16.dp, modifier = Modifier.weight(1f).height(150.dp)) {
                Row(Modifier.fillMaxWidth().height(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    SotLabel("PAUSE", Modifier.weight(1f))
                    if (paused) SotLabel("%d:%02d".format(secondsLeft / 60, secondsLeft % 60), color = SotColors.Red)
                    else SotLabel("$pausesLeft LEFT")
                }
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    PauseButton(paused, dim = !paused && !canPause, onClick = onButton)
                }
            }
        }
    }
}

/** The pause popup: 5 / 10 / 15 minute pills. Picking one calls [onPick]; tapping outside or back calls [onDismiss]. */
@Composable
internal fun PauseDialog(pausesLeft: Int, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        SotCard(vpad = 20.dp) {
            SotLabel("PAUSE FOR")
            Row(
                Modifier.fillMaxWidth().padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for (m in UnscrollStore.PAUSE_MINUTES) PausePill(m) { onPick(m) }
            }
            Box(Modifier.fillMaxWidth().padding(top = 10.dp), contentAlignment = Alignment.Center) { SotLabel("MIN") }
            Box(Modifier.fillMaxWidth().padding(top = 16.dp), contentAlignment = Alignment.Center) { SotLabel("$pausesLeft LEFT") }
        }
    }
}

/** One duration pill, at least 56dp tall. */
@Composable
private fun RowScope.PausePill(minutes: Int, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        Modifier
            .weight(1f)
            .heightIn(min = 56.dp)
            .clip(shape)
            .background(SotColors.Chip)
            .border(1.dp, SotColors.ChipBorder, shape)
            .tap(onClick),
        contentAlignment = Alignment.Center
    ) {
        SotText("$minutes", 16.sp, bold = true)
    }
}

/** The 64dp round button: two bars (pause) or a triangle (play, red), drawn on a canvas. */
@Composable
private fun PauseButton(paused: Boolean, dim: Boolean, onClick: () -> Unit) {
    val tint = if (paused) SotColors.Red else SotColors.Txt
    Box(
        modifier = Modifier
            .size(64.dp)
            .alpha(if (dim) 0.4f else 1f)
            .clip(CircleShape)
            .background(SotColors.Chip)
            .border(1.dp, if (paused) SotColors.Red else SotColors.ChipBorder, CircleShape)
            .tap(onClick)
            .semantics { contentDescription = if (paused) "Resume" else "Pause" },
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.size(24.dp)) {
            val w = size.width
            val h = size.height
            if (paused) {
                val p = Path().apply {
                    moveTo(w * 0.15f, 0f)
                    lineTo(w, h / 2f)
                    lineTo(w * 0.15f, h)
                    close()
                }
                drawPath(p, tint)
            } else {
                val bar = w * 0.3f
                val r = CornerRadius(w * 0.06f)
                drawRoundRect(tint, Offset(w * 0.08f, 0f), Size(bar, h), r)
                drawRoundRect(tint, Offset(w * 0.62f, 0f), Size(bar, h), r)
            }
        }
    }
}

@Composable
internal fun HDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(SotColors.Border)
    )
}

/** One missing setup step: a short [label], what tapping does ([action]), and the tap itself. */
internal class SetupItem(val label: String, val action: String, val onClick: () -> Unit)

/** The SETUP row: label, red count, chevron. */
@Composable
internal fun SetupRow(count: Int, onClick: () -> Unit) {
    SotCard(vpad = 0.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .tap(onClick),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SotLabel("SETUP", modifier = Modifier.weight(1f))
            SotNumber("$count", 28.sp, color = SotColors.Red)
            Spacer(Modifier.width(12.dp))
            SotText("›", 20.sp, SotColors.Dim, bold = false)
        }
    }
}

/** The sheet that lists each missing step as a row with an action. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SetupSheet(items: List<SetupItem>, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = SotColors.Card) {
        Column(
            Modifier
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 16.dp)
        ) {
            items.forEachIndexed { i, it ->
                if (i > 0) HDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .tap(it.onClick),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SotText(it.label, 13.sp, modifier = Modifier.weight(1f))
                    SotLabel(it.action)
                }
            }
        }
    }
}
