package com.jntoolz.ui.sot

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jntoolz.ui.theme.NothingFont
import com.jntoolz.ui.theme.NothingFonts

/** The app's palette (same as Calendar and Counter): black, #141414 cards, grey text, red accent; green = charging. */
internal object SotColors {
    val Bg = Color(0xFF000000)
    val Card = Color(0xFF141414)
    val Border = Color(0xFF222222)
    val Chip = Color(0xFF262626)
    val ChipBorder = Color(0xFF333333)
    val Txt = Color(0xFFFFFFFF)
    val Dim = Color(0xFF8E8E8E)
    val Faint = Color(0xFF5C5C5C)
    val Grid = Color(0xFF2A2A2A)
    val Red = Color(0xFFFF2E2E)
    val Green = Color(0xFF3DDC84)
}

/** Ndot for the big numbers, Lettera Mono for small labels (system fallbacks when the phone has neither). */
internal object SotFonts {
    val dot: FontFamily get() = NothingFonts.family(NothingFont.NDOT_57)
    val label: FontFamily get() = NothingFonts.family(NothingFont.LETTERA)
}

/** A number in Ndot (tabular). */
@Composable
internal fun SotNumber(text: String, size: TextUnit, modifier: Modifier = Modifier, color: Color = SotColors.Txt) {
    Text(
        text = text,
        modifier = modifier,
        color = color,
        fontSize = size,
        fontFamily = SotFonts.dot,
        maxLines = 1,
        style = TextStyle(fontFeatureSettings = "tnum")
    )
}

/** Row and body text, the app's monospace (bold for row names). */
@Composable
internal fun SotText(
    text: String,
    size: TextUnit = 14.sp,
    color: Color = SotColors.Txt,
    modifier: Modifier = Modifier,
    bold: Boolean = true,
    singleLine: Boolean = false,
    lineHeight: TextUnit = TextUnit.Unspecified
) {
    Text(
        text = text,
        modifier = modifier,
        color = color,
        fontSize = size,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        fontFamily = FontFamily.Monospace,
        lineHeight = lineHeight,
        maxLines = if (singleLine) 1 else Int.MAX_VALUE,
        overflow = if (singleLine) TextOverflow.Ellipsis else TextOverflow.Clip
    )
}

/** A small grey caps label (Lettera Mono, spaced), like the Counter's "DAYS LEFT". */
@Composable
internal fun SotLabel(text: String, modifier: Modifier = Modifier, color: Color = SotColors.Dim) {
    Text(
        text = text.uppercase(),
        modifier = modifier,
        color = color,
        fontSize = 11.sp,
        fontFamily = SotFonts.label,
        letterSpacing = 1.5.sp,
        maxLines = 1
    )
}

/** A click with no ripple (cards that flip, rows with a switch). */
internal fun Modifier.tap(onClick: () -> Unit): Modifier = clickable(interactionSource = null, indication = null, onClick = onClick)

/** The app's card: #141414, 18dp corners, 1dp #222 border. */
@Composable
internal fun SotCard(vpad: Dp, modifier: Modifier = Modifier, border: Color = SotColors.Border, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(SotColors.Card)
            .border(BorderStroke(1.dp, border), shape)
            .padding(horizontal = 16.dp, vertical = vpad),
        content = content
    )
}

/** One scrolling tab page with the app's 20dp side padding. */
@Composable
internal fun SotPage(scroll: ScrollState, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(scroll)
            .navigationBarsPadding()
            .padding(start = 20.dp, top = 8.dp, end = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        content = content
    )
}

/** A section header: small muted monospace caps, as on the Calendar screen. */
@Composable
internal fun SotSection(text: String) {
    Text(
        text = text,
        color = SotColors.Dim,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = FontFamily.Monospace,
        letterSpacing = 1.5.sp,
        modifier = Modifier.padding(top = 8.dp)
    )
}

/** The app's top bar: back arrow and a monospace bold caps title; with [onSettings], a gear at the right. */
@Composable
internal fun SotTopBar(title: String, onBack: () -> Unit, onSettings: (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(SotColors.Bg)
            .statusBarsPadding()
            .height(56.dp)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BackArrow(onBack)
        Spacer(Modifier.width(8.dp))
        Text(
            title,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 2.sp,
            color = SotColors.Txt,
            modifier = Modifier.weight(1f)
        )
        if (onSettings != null) GearButton(onSettings)
    }
}

/** Drawn settings gear: a ring with eight teeth. */
@Composable
private fun GearButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable { onClick() }
            .semantics { contentDescription = "Settings" },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(24.dp)) {
            val c = Offset(size.width / 2f, size.height / 2f)
            val u = size.width / 24f
            drawCircle(SotColors.Txt, 5.5f * u, c, style = Stroke(2f * u))
            for (i in 0 until 8) {
                val a = Math.toRadians(i * 45.0)
                val d = Offset(Math.cos(a).toFloat(), Math.sin(a).toFloat())
                drawLine(SotColors.Txt, c + d * (8f * u), c + d * (11f * u), 2.6f * u, StrokeCap.Round)
            }
        }
    }
}

/** Drawn back arrow (same as Calendar and Counter). */
@Composable
private fun BackArrow(onBack: () -> Unit) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable { onBack() }
            .semantics { contentDescription = "Back" },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(18.dp)) {
            val w = size.width
            val cy = size.height / 2f
            val stroke = 2.dp.toPx()
            val cap = StrokeCap.Round
            drawLine(SotColors.Txt, Offset(w * 0.08f, cy), Offset(w * 0.92f, cy), stroke, cap)
            drawLine(SotColors.Txt, Offset(w * 0.08f, cy), Offset(w * 0.42f, cy - w * 0.34f), stroke, cap)
            drawLine(SotColors.Txt, Offset(w * 0.08f, cy), Offset(w * 0.42f, cy + w * 0.34f), stroke, cap)
        }
    }
}

/**
 * A row of monospace caps chips, like the Calendar theme card: the picked one on #262626 with a red border.
 * [fill] spreads them over the row; otherwise each is as wide as its text.
 */
@Composable
internal fun <T> Segmented(
    options: List<Pair<T, String>>,
    selected: T,
    onPick: (T) -> Unit,
    modifier: Modifier = Modifier,
    fill: Boolean = true,
    height: Dp = 36.dp
) {
    val trackShape = RoundedCornerShape(12.dp)
    val itemShape = RoundedCornerShape(10.dp)
    Row(
        modifier = modifier
            .clip(trackShape)
            .background(Color(0xFF1A1A1A))
            .padding(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        for ((value, name) in options) {
            val on = value == selected
            Box(
                modifier = (if (fill) Modifier.weight(1f) else Modifier)
                    .height(height)
                    .clip(itemShape)
                    .background(if (on) SotColors.Red else Color.Transparent)
                    .clickable { onPick(value) }
                    .semantics {
                        role = Role.Tab
                        this.selected = on
                    }
                    .padding(horizontal = if (fill) 4.dp else 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = name,
                    color = if (on) Color.White else Color(0xFF8E8E8E),
                    fontSize = if (name.length > 10) 9.5.sp else 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = if (name.length > 10) 0.4.sp else 1.sp,
                    maxLines = 1
                )
            }
        }
    }
}

/** The "Since unplug | Today" toggle. */
@Composable
internal fun SinceSegment(today: Boolean, onPick: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Segmented(listOf(false to "SINCE UNPLUG", true to "TODAY"), today, onPick, modifier, fill = false, height = 30.dp)
}

/** A text row, with a chevron when it does something. */
@Composable
internal fun LinkRow(text: String, onClick: (() -> Unit)?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .then(if (onClick != null) Modifier.tap(onClick) else Modifier),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SotText(text, 13.sp, modifier = Modifier.weight(1f))
        if (onClick != null) SotText("›", 20.sp, SotColors.Dim, bold = false)
    }
}

/** A labelled switch row; the whole row is the tap target. Switch colours as on the hub. */
@Composable
internal fun SwitchRow(label: String, on: Boolean, onClick: () -> Unit) = SwitchRow(label, on, null, onClick)

/** [SwitchRow] with a [tag]: a tiny dim word before the switch. */
@Composable
internal fun SwitchRow(label: String, on: Boolean, tag: String?, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .tap(onClick)
            .semantics { contentDescription = label },
        verticalAlignment = Alignment.CenterVertically
    ) {
        SotText(label, 13.sp, modifier = Modifier.weight(1f))
        if (tag != null) {
            SotLabel(tag)
            Spacer(Modifier.width(10.dp))
        }
        Switch(
            checked = on,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedThumbColor = SotColors.Txt,
                checkedTrackColor = SotColors.Red,
                uncheckedThumbColor = SotColors.Dim,
                uncheckedTrackColor = SotColors.Border
            )
        )
    }
}
