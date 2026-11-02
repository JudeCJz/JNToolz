package com.jntoolz.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

// The editing screens of every tool (Calendar theming, Counter editor, Battery widget) share one layout:
// the top bar and the live widget preview stay pinned at the top, only the options scroll beneath them,
// and the floating APPLY sits bottom-right on top. The preview is capped so the options stay usable.

/** Share of the screen height the pinned preview may take at most. */
private const val PinnedPreviewShare = 0.30f

/** Tallest the pinned live preview may be: [PinnedPreviewShare] of the screen height. */
@Composable
fun pinnedPreviewMaxHeight(): Dp = (LocalConfiguration.current.screenHeightDp * PinnedPreviewShare).dp

/**
 * Lays out [content] at the full width it is given; if it comes out taller than [maxHeight] it is scaled
 * down uniformly (centred, never stretched) to fit, and takes only the scaled height. Taps still land
 * where they are drawn.
 */
@Composable
fun FitHeight(maxHeight: Dp, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Layout(content = { Box { content() } }, modifier = modifier) { measurables, constraints ->
        val cap = maxHeight.roundToPx().coerceAtMost(constraints.maxHeight)
        // Bounded (a scrolling child crashes on an infinite height), but roomy enough to scale down from.
        val child = measurables.first().measure(constraints.copy(minHeight = 0, maxHeight = cap * 4))
        val scale = if (child.height > cap && child.height > 0) cap.toFloat() / child.height else 1f
        val width = if (constraints.hasBoundedWidth) constraints.maxWidth else child.width
        layout(width, (child.height * scale).roundToInt()) {
            child.placeWithLayer((width - child.width) / 2, 0) {
                scaleX = scale
                scaleY = scale
                transformOrigin = TransformOrigin(0.5f, 0f)
            }
        }
    }
}
