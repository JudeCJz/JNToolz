package com.jntoolz.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jntoolz.ui.main.AccentRed
import com.jntoolz.ui.main.TextWhite
import kotlinx.coroutines.delay

/** Bottom padding a scrolling theming screen needs so its last control is never under the [ApplyButton]. */
internal val ApplyButtonClearance = 96.dp

/** Where the [ApplyButton] floats: bottom-right of a BoxScope, above the nav bar. Apply after `align`. */
internal fun Modifier.applyButtonPadding(): Modifier = navigationBarsPadding().padding(end = 20.dp, bottom = 16.dp)

private val DisabledBg = Color(0xFF1C1C1E)
private val DisabledText = Color(0xFF555558)
private val Shape = RoundedCornerShape(50)

/**
 * The floating APPLY pill shared by the theming sections (Calendar, Counter editor, Battery widget).
 * Red while [dirty] (unsaved changes), dimmed but still tappable otherwise. After a tap it briefly reads APPLIED.
 */
@Composable
internal fun ApplyButton(dirty: Boolean, onApply: () -> Unit, modifier: Modifier = Modifier) {
    var applied by remember { mutableStateOf(false) }
    LaunchedEffect(applied) {
        if (applied) {
            delay(1400)
            applied = false
        }
    }
    // A new edit ends the confirmation at once.
    LaunchedEffect(dirty) { if (dirty) applied = false }
    val active = dirty || applied
    Box(
        modifier = modifier
            .shadow(8.dp, Shape)
            .clip(Shape)
            .background(if (active) AccentRed else DisabledBg)
            .clickable {
                onApply()
                applied = true
            }
            .widthIn(min = 116.dp)
            .padding(horizontal = 24.dp, vertical = 13.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (applied) "APPLIED" else "APPLY",
            color = if (active) TextWhite else DisabledText,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 1.5.sp,
            maxLines = 1
        )
    }
}
