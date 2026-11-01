package com.jntoolz.ui.sot

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jntoolz.sot.Stats

private const val NONE = -1

/** Now: the SOT hero, the graph with its toggle, Screen off | Left, and Health. Tapping a card flips it. */
@Composable
internal fun NowTab(vm: SotViewModel, scroll: ScrollState) {
    val st = vm.stats
    var flipped by remember { mutableIntStateOf(NONE) }
    val onTap = { id: Int -> flipped = if (flipped == id) NONE else id }
    val graphAlpha by animateFloatAsState(if (flipped != NONE) 0.3f else 1f, tween(280), label = "dim")
    val graphHeight = maxOf(200.dp, (LocalConfiguration.current.screenHeightDp * 0.28f).dp)

    SotPage(scroll) {
        FlipCard(0, flipped, onTap, if (vm.today) "Screen-on time today" else "Screen-on time since you unplugged", 20.dp) {
            SotNumber(if (st == null) "–" else if (st.empty) "0m" else Stats.dur(st.sot), 64.sp)
        }

        SotCard(14.dp, Modifier.graphicsLayer { alpha = graphAlpha }) {
            SinceSegment(vm.today, vm::pickToday, Modifier.align(Alignment.End))
            SotGraph(
                st,
                Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
                    .height(graphHeight)
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FlipCard(1, flipped, onTap, "Screen-off time while on battery", 16.dp, Modifier.weight(1f)) {
                TimeFace("Screen off", st?.let { Stats.dur(it.disOff) } ?: "")
            }
            FlipCard(2, flipped, onTap, "Time left at this drain", 16.dp, Modifier.weight(1f)) {
                val remain = st?.leftMixed()
                TimeFace("Left", if (remain == null) "" else if (remain < 0) "–" else Stats.dur(remain))
            }
        }

        FlipCard(3, flipped, onTap, "Battery capacity compared to new", 16.dp) {
            TimeFace("Health", vm.health)
        }
    }
}

/** A half-width card face: just a name and a time. */
@Composable
private fun ColumnScope.TimeFace(name: String, value: String) {
    SotLabel(name)
    SotNumber(value, 28.sp, Modifier.padding(top = 10.dp))
}

/**
 * A Now card that flips over to say what it shows: it turns halfway, swaps its face, turns back.
 * The flipped card steps forward and the rest dim.
 */
@Composable
private fun FlipCard(
    id: Int,
    flipped: Int,
    onTap: (Int) -> Unit,
    back: String,
    vpad: Dp,
    modifier: Modifier = Modifier,
    front: @Composable ColumnScope.() -> Unit
) {
    val show = flipped == id
    val rotation = remember { Animatable(0f) }
    var backFace by remember { mutableStateOf(show) }
    var frontHeight by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    LaunchedEffect(show) {
        if (backFace == show) return@LaunchedEffect
        rotation.animateTo(90f, tween(140, easing = LinearEasing))
        backFace = show
        rotation.snapTo(-90f)
        rotation.animateTo(0f, tween(140))
    }
    val scale by animateFloatAsState(if (show) 1.04f else 1f, tween(140), label = "scale")
    val alpha by animateFloatAsState(if (flipped != NONE && !show) 0.3f else 1f, tween(280), label = "dim")

    SotCard(
        vpad,
        modifier
            .graphicsLayer {
                rotationY = rotation.value
                scaleX = scale
                scaleY = scale
                this.alpha = alpha
                cameraDistance = 12f * density.density
            }
            .tap { onTap(id) }
            .then(
                // The back keeps at least the front's height, so the card does not jump.
                if (backFace) Modifier.heightIn(min = with(density) { frontHeight.toDp() })
                else Modifier.onSizeChanged { frontHeight = it.height }
            )
    ) {
        if (backFace) SotText(back, 13.sp, bold = false, lineHeight = 19.sp) else front()
    }
}
