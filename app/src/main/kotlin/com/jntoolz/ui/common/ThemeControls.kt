package com.jntoolz.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jntoolz.data.ThemeMath
import com.jntoolz.ui.main.AccentRed
import com.jntoolz.ui.main.DividerGrey
import com.jntoolz.ui.main.MutedGrey
import com.jntoolz.ui.main.TextWhite
import kotlin.math.roundToInt

// Controls shared by the theming sections of every tool (Calendar, Counter, Battery widget), so the same
// setting looks the same everywhere. Compact: every label sits on its control's row, in one shared column.

/**
 * The longest label of a [ThemeRow] in any tool. The label column is sized to fit it at the current font
 * size, so every row (sliders, colours, segmented choices) lines up the same in Calendar, Counter and Battery.
 */
private const val LongestThemeLabel = "Marker color"
private val ThemeLabelGap = 8.dp

private val ThemeLabelStyle = TextStyle(
    color = TextWhite,
    fontSize = 13.sp,
    fontWeight = FontWeight.Bold,
    fontFamily = FontFamily.Monospace,
)

/** Width of the label column of a [ThemeRow]: [LongestThemeLabel] measured at the current font scale, plus a gap. */
@Composable
internal fun themeLabelWidth(): Dp {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    return remember(measurer, density) {
        with(density) { measurer.measure(LongestThemeLabel, ThemeLabelStyle, maxLines = 1, softWrap = false).size.width.toDp() } +
            ThemeLabelGap
    }
}

/** The one label style of the theming sections. Above a control by default; pass a modifier to inline it. */
@Composable
internal fun ThemeLabel(text: String, modifier: Modifier = Modifier.padding(top = 8.dp, bottom = 6.dp)) {
    Text(
        text = text,
        style = ThemeLabelStyle,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
    )
}

@Composable
internal fun ThemeDivider() {
    HorizontalDivider(color = DividerGrey, thickness = 1.dp, modifier = Modifier.padding(vertical = 4.dp))
}

/** [label] in the shared fixed-width label column on the left, [content] fills the rest of the row. */
@Composable
internal fun ThemeRow(label: String, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ThemeLabel(label, Modifier.width(themeLabelWidth()))
        content()
    }
}

// Compact slider: a small round thumb on a thin track. The laid-out slider is only [SliderHeight] tall (no 48dp
// minimum), so rows stay short and never overlap; Compose still widens the touch target where there is room.
private val SliderThumbSize = DpSize(16.dp, 16.dp)
private val SliderTrackHeight = 4.dp
private val SliderHeight = 20.dp

/**
 * A slider on one row: [label] (name only) in the label column, the slider in the rest. The current value
 * ([value][suffix], e.g. "45°") rides in a small chip just above the thumb, clamped to the slider's ends.
 * Kept at least 8dp away from the row above so the bubble never touches swatches.
 */
@OptIn(ExperimentalMaterial3Api::class) // SliderState in the track slot
@Composable
internal fun ThemeSliderRow(
    label: String,
    value: Int,
    range: IntRange,
    onChange: (Int) -> Unit,
    suffix: String = "",
) {
    val span = (range.last - range.first).coerceAtLeast(1)
    val fraction = ((value - range.first).toFloat() / span).coerceIn(0f, 1f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 2.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        // Bottom-aligned with a small lift, so the label's centre sits level with the track, not the bubble.
        ThemeLabel(label, Modifier.width(themeLabelWidth()).padding(bottom = 1.dp))
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 6.dp)
        ) {
            ValueBubble("$value$suffix", fraction)
            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides SliderHeight) {
                Slider(
                    value = value.toFloat(),
                    onValueChange = { v ->
                        val n = v.roundToInt().coerceIn(range.first, range.last)
                        if (n != value) onChange(n)
                    },
                    valueRange = range.first.toFloat()..range.last.toFloat(),
                    thumb = {
                        Box(
                            Modifier
                                .size(SliderThumbSize)
                                .clip(CircleShape)
                                .background(TextWhite)
                        )
                    },
                    track = { state ->
                        val start = state.valueRange.start
                        val f = ((state.value - start) / (state.valueRange.endInclusive - start).coerceAtLeast(1f))
                            .coerceIn(0f, 1f)
                        Canvas(
                            Modifier
                                .fillMaxWidth()
                                .height(SliderTrackHeight)
                        ) {
                            val r = CornerRadius(size.height / 2f)
                            drawRoundRect(DividerGrey, cornerRadius = r)
                            if (f > 0f) drawRoundRect(AccentRed, size = Size(size.width * f, size.height), cornerRadius = r)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(SliderHeight)
                )
            }
        }
    }
}

/**
 * [text] in a styled pill chip above the slider thumb: bg 0xFF222222, 1dp border 0xFF3A3A3A, 6dp corners,
 * 11sp monospace white, padding 6dp x 2dp. Clamped to the slider edges.
 */
@Composable
private fun ValueBubble(text: String, fraction: Float) {
    val bubbleShape = RoundedCornerShape(6.dp)
    Text(
        text = text,
        color = TextWhite,
        fontSize = 11.sp,
        lineHeight = 11.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = FontFamily.Monospace,
        maxLines = 1,
        softWrap = false,
        modifier = Modifier
            .fillMaxWidth()
            .layout { measurable, constraints ->
                val chip = measurable.measure(constraints.copy(minWidth = 0))
                val width = constraints.maxWidth
                layout(width, chip.height) {
                    val thumb = SliderThumbSize.width.roundToPx()
                    val centre = thumb / 2f + fraction * (width - thumb)
                    val x = (centre - chip.width / 2f).roundToInt().coerceIn(0, (width - chip.width).coerceAtLeast(0))
                    chip.place(x, 0)
                }
            }
            .clip(bubbleShape)
            .background(Color(0xFF222222))
            .border(1.dp, Color(0xFF3A3A3A), bubbleShape)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}

@Composable
internal fun ThemeSwitchRow(label: String, checked: Boolean, tag: String? = null, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ThemeLabel(label, Modifier)
            if (tag != null) {
                Text(
                    text = tag,
                    color = Color(0xFFAAAAAA),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp,
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .border(1.dp, Color(0xFF444444), RoundedCornerShape(6.dp))
                        .background(Color.Transparent)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = TextWhite,
                checkedTrackColor = AccentRed,
                uncheckedThumbColor = MutedGrey,
                uncheckedTrackColor = DividerGrey
            )
        )
    }
}

/**
 * Unified segmented control: one continuous segmented track (bg 0xFF1A1A1A, 12dp corners, 2dp padding);
 * selected segment filled 0xFFFF2E2E with white bold text; others transparent with 0xFF8E8E8E text.
 */
@Composable
fun <T> SegmentedControl(
    items: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    fill: Boolean = true,
    fontFamily: ((T) -> FontFamily)? = null,
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
        items.forEach { (value, label) ->
            val isSelected = value == selected
            Box(
                modifier = (if (fill) Modifier.weight(1f) else Modifier)
                    .clip(itemShape)
                    .background(if (isSelected) AccentRed else Color.Transparent)
                    .clickable { onSelect(value) }
                    .padding(horizontal = if (fill) 4.dp else 12.dp, vertical = 7.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    color = if (isSelected) TextWhite else Color(0xFF8E8E8E),
                    fontSize = if (label.length > 9) 10.sp else 11.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    fontFamily = fontFamily?.invoke(value) ?: FontFamily.Monospace,
                    letterSpacing = 0.5.sp,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}

private val SwatchSize = 30.dp
private val TouchTargetMin = 44.dp

/** One swatch: [color] is the value it sets, [show] what it looks like (differs for "auto"), [label] a tiny caption. */
internal data class SwatchSpec(val color: Long, val show: Long = color, val label: String? = null)

/**
 * A colour row: [label] in the label column, the swatches scroll sideways in the rest, the custom "+" last.
 * Every swatch is 30dp with at least 44dp touch target. Unselected swatches have 1.5dp 0xFF333333 border;
 * selected has a 2dp red ring with a 2dp gap drawn outside the colour.
 */
@Composable
internal fun SwatchRow(
    label: String,
    presets: List<SwatchSpec>,
    selectedColor: Long,
    customSelected: Boolean,
    customColor: Long,
    onSelect: (SwatchSpec) -> Unit,
    onCustom: () -> Unit
) = ThemeRow(label) {
    Row(
        modifier = Modifier
            .weight(1f)
            .padding(vertical = 4.dp)
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        presets.forEach { spec ->
            val selected = selectedColor == spec.color
            val isTransparent = ThemeMath.alpha(spec.show.toInt()) == 0

            Box(
                modifier = Modifier
                    .size(TouchTargetMin)
                    .clickable { onSelect(spec) }
                    .then(
                        if (selected) {
                            Modifier.drawBehind {
                                drawCircle(
                                    color = AccentRed,
                                    radius = 18.dp.toPx(),
                                    style = Stroke(width = 2.dp.toPx())
                                )
                            }
                        } else Modifier
                    ),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(SwatchSize)
                        .clip(CircleShape)
                        .background(Color(spec.show))
                        .then(
                            if (isTransparent) {
                                Modifier.drawBehind {
                                    drawLine(
                                        color = AccentRed,
                                        start = Offset(size.width * 0.2f, size.height * 0.8f),
                                        end = Offset(size.width * 0.8f, size.height * 0.2f),
                                        strokeWidth = 2.dp.toPx(),
                                        cap = StrokeCap.Round
                                    )
                                }
                            } else Modifier
                        )
                        .border(1.5.dp, Color(0xFF333333), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (spec.label != null) {
                        Text(
                            text = spec.label,
                            color = if (ThemeMath.isLight(ThemeMath.effectiveBg(spec.show.toInt()))) Color.Black else TextWhite,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .size(TouchTargetMin)
                .clickable { onCustom() }
                .then(
                    if (customSelected) {
                        Modifier.drawBehind {
                            drawCircle(
                                color = AccentRed,
                                radius = 18.dp.toPx(),
                                style = Stroke(width = 2.dp.toPx())
                            )
                        }
                    } else Modifier
                ),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(SwatchSize)
                    .clip(CircleShape)
                    .background(if (customSelected) Color(customColor) else Color(0xFF1A1A1A))
                    .border(1.5.dp, Color(0xFF333333), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "+",
                    color = if (customSelected && ThemeMath.isLight(ThemeMath.effectiveBg(customColor.toInt()))) Color.Black else TextWhite,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

/** Compact OFF / ON segmented toggle matching Unscroll's hub style. */
@Composable
fun ToolOnOffToggle(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = modifier
            .clip(shape)
            .background(Color(0xFF1A1A1A))
            .padding(2.dp)
    ) {
        listOf(false to "OFF", true to "ON").forEach { (state, label) ->
            val picked = state == enabled
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (picked) AccentRed else Color.Transparent)
                    .clickable { onToggle(state) }
                    .widthIn(min = 36.dp)
                    .padding(vertical = 6.dp, horizontal = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    color = if (picked) Color.White else Color(0xFF8E8E8E),
                    fontSize = 11.sp,
                    fontWeight = if (picked) FontWeight.Bold else FontWeight.Normal,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}
