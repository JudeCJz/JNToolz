package com.jntoolz.ui.main

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jntoolz.data.ThemeMath
import com.jntoolz.data.ThemeSettings

internal enum class PickerTarget { MARKER, CARD }

private val MARKER_STYLES = listOf("OFF", "DOT", "RING", "BAR", "CARD")
private val MARKER_PRESETS = listOf(
    0L, 0xFFFF2E2EL, 0xFFFFFFFFL, 0xFFCCCCCCL, 0xFF4DA3FFL,
    0xFF33B679L, 0xFFF6BF26L, 0xFFBA68C8L
)
private val MARKER_SWATCHES = MARKER_PRESETS.map { SwatchSpec(color = it) }
private val CARD_PRESETS = listOf(0xFF202121L, 0xFF2B1414L, 0xFF0F1E28L, 0xFF17241AL)
private val CARD_SWATCHES = CARD_PRESETS.map { SwatchSpec(color = it) } +
    listOf("CHARCOAL", "RUBY", "CYBER", "AMBER").map { SwatchSpec(color = 0xFF000000L, gradient = it) }

@Composable
internal fun ThemeCard(
    theme: ThemeSettings,
    onChange: ((ThemeSettings) -> ThemeSettings) -> Unit,
    onPick: (PickerTarget) -> Unit
) {
    val markerColor = theme.markerColor.toLong() and 0xFFFFFFFFL
    val cardColor = theme.cardColor.toLong() and 0xFFFFFFFFL
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        border = BorderStroke(1.dp, DividerGrey)
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
            ThemeSwitchRow("Google colors", theme.useGoogleColors) { c -> onChange { it.copy(useGoogleColors = c) } }
            ThemeDivider()

            ThemeLabel("Marker")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                MARKER_STYLES.forEach { style ->
                    val selected = theme.markerStyle == style
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (selected) Color(0xFF262626) else Color.Transparent)
                            .border(1.dp, if (selected) AccentRed else Color(0xFF333333), RoundedCornerShape(8.dp))
                            .clickable { onChange { it.copy(markerStyle = style) } }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = style,
                            color = if (selected) TextWhite else MutedGrey,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1
                        )
                    }
                }
            }
            ThemeDivider()

            if (!theme.useGoogleColors && (theme.markerStyle == "DOT" || theme.markerStyle == "RING" || theme.markerStyle == "BAR")) {
                ThemeLabel("Marker color")
                SwatchRow(
                    presets = MARKER_SWATCHES,
                    selectedColor = markerColor,
                    customSelected = markerColor !in MARKER_PRESETS,
                    customColor = markerColor,
                    onSelect = { spec -> onChange { it.copy(markerColor = spec.color.toInt()) } },
                    onCustom = { onPick(PickerTarget.MARKER) }
                )
                ThemeDivider()
            }

            ThemeSwitchRow("Gradient", theme.gradient) { c -> onChange { it.copy(gradient = c) } }
            ThemeDivider()
            ThemeSwitchRow("Fade", theme.fade) { c -> onChange { it.copy(fade = c) } }

            if (!(theme.useGoogleColors && theme.markerStyle == "CARD")) {
                ThemeDivider()
                ThemeLabel("Card")
                SwatchRow(
                    presets = CARD_SWATCHES,
                    selectedColor = cardColor,
                    selectedGradient = theme.gradientStyle,
                    customSelected = theme.gradientStyle == "NONE" && cardColor !in CARD_PRESETS,
                    customColor = cardColor,
                    onSelect = { spec ->
                        if (spec.gradient != null) onChange { it.copy(gradientStyle = spec.gradient) }
                        else onChange { it.copy(gradientStyle = "NONE", cardColor = spec.color.toInt()) }
                    },
                    onCustom = { onPick(PickerTarget.CARD) }
                )
            }
        }
    }
}

// ---------------- Theme card building blocks ----------------

@Composable
private fun ThemeLabel(text: String) {
    Text(
        text = text,
        color = TextWhite,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = FontFamily.Monospace,
        modifier = Modifier.padding(top = 12.dp, bottom = 8.dp)
    )
}

@Composable
private fun ThemeDivider() {
    HorizontalDivider(color = DividerGrey, thickness = 1.dp)
}

@Composable
private fun ThemeSwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = TextWhite,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
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

internal data class SwatchSpec(val color: Long, val gradient: String? = null)

@Composable
private fun SwatchRow(
    presets: List<SwatchSpec>,
    selectedColor: Long,
    selectedGradient: String = "NONE",
    customSelected: Boolean,
    customColor: Long,
    onSelect: (SwatchSpec) -> Unit,
    onCustom: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        presets.forEach { spec ->
            val selected = if (spec.gradient != null) {
                selectedGradient == spec.gradient
            } else {
                selectedGradient == "NONE" && selectedColor == spec.color
            }
            val fillModifier = if (spec.gradient != null) {
                Modifier.background(
                    Brush.verticalGradient(
                        listOf(
                            Color(ThemeMath.gradientStart(spec.gradient)),
                            Color(ThemeMath.gradientEnd(spec.gradient))
                        )
                    )
                )
            } else {
                Modifier.background(Color(spec.color))
            }
            val isTransparent = spec.gradient == null && ThemeMath.alpha(spec.color.toInt()) == 0
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF2A2A2A))
                    .then(fillModifier)
                    .then(
                        if (isTransparent) {
                            Modifier.drawBehind {
                                drawLine(
                                    color = Color(0xFFFF2E2E),
                                    start = Offset(size.width * 0.2f, size.height * 0.8f),
                                    end = Offset(size.width * 0.8f, size.height * 0.2f),
                                    strokeWidth = 2f
                                )
                            }
                        } else Modifier
                    )
                    .border(
                        if (selected) 2.dp else 1.dp,
                        if (selected) AccentRed else Color(0xFF444444),
                        CircleShape
                    )
                    .clickable { onSelect(spec) }
            )
        }

        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(if (customSelected) Color(customColor) else Color(0xFF1A1A1A))
                .border(
                    if (customSelected) 2.dp else 1.dp,
                    if (customSelected) AccentRed else Color(0xFF444444),
                    CircleShape
                )
                .clickable { onCustom() },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "+",
                color = if (customSelected && ThemeMath.isLight(ThemeMath.effectiveBg(customColor.toInt()))) Color.Black else TextWhite,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}
