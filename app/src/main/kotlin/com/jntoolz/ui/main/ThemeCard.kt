package com.jntoolz.ui.main

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jntoolz.data.ThemeSettings
import com.jntoolz.ui.common.CardStyleEditor
import com.jntoolz.ui.common.SegmentedControl
import com.jntoolz.ui.common.SwatchSpec
import com.jntoolz.ui.common.SwatchRow
import com.jntoolz.ui.common.ThemeDivider
import com.jntoolz.ui.common.ThemeRow
import com.jntoolz.ui.common.ThemeSliderRow
import com.jntoolz.ui.common.ThemeSwitchRow

internal enum class PickerTarget { MARKER, CARD, GRADIENT }

private val MARKER_STYLES = listOf("OFF", "DOT", "RING", "BAR", "CARD")
private val MARKER_PRESETS = listOf(
    0L, 0xFFFF2E2EL, 0xFFFFFFFFL, 0xFFCCCCCCL, 0xFF4DA3FFL,
    0xFF33B679L, 0xFFF6BF26L, 0xFFBA68C8L
)
private val MARKER_SWATCHES = MARKER_PRESETS.map { SwatchSpec(color = it) }

@Composable
internal fun ThemeCard(
    theme: ThemeSettings,
    onChange: ((ThemeSettings) -> ThemeSettings) -> Unit,
    onPick: (PickerTarget) -> Unit
) {
    val markerColor = theme.markerColor.toLong() and 0xFFFFFFFFL
    // CARD (event colour on the whole card) needs Google colors; without them a saved CARD shows as OFF.
    val styles = if (theme.useGoogleColors) MARKER_STYLES else MARKER_STYLES - "CARD"
    val shownStyle = if (theme.markerStyle in styles) theme.markerStyle else "OFF"
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        border = BorderStroke(1.dp, DividerGrey)
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
            ThemeSwitchRow("Google colors", theme.useGoogleColors, tag = "ALPHA") { c -> onChange { it.copy(useGoogleColors = c) } }

            ThemeRow("Marker") {
                SegmentedControl(
                    items = styles.map { it to it },
                    selected = shownStyle,
                    onSelect = { s -> onChange { it.copy(markerStyle = s) } },
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 4.dp)
                )
            }

            if (!theme.useGoogleColors && (shownStyle == "DOT" || shownStyle == "RING" || shownStyle == "BAR")) {
                SwatchRow(
                    label = "Marker color",
                    presets = MARKER_SWATCHES,
                    selectedColor = markerColor,
                    customSelected = markerColor !in MARKER_PRESETS,
                    customColor = markerColor,
                    onSelect = { spec -> onChange { it.copy(markerColor = spec.color.toInt()) } },
                    onCustom = { onPick(PickerTarget.MARKER) }
                )
            }

            ThemeSwitchRow("Fade", theme.fade) { c -> onChange { it.copy(fade = c) } }
            ThemeSwitchRow("Glass background", theme.bgGlass) { c -> onChange { it.copy(bgGlass = c) } }
            if (theme.bgGlass) {
                ThemeSliderRow("Glass", theme.bgGlassLevel, 0..100, { v -> onChange { it.copy(bgGlassLevel = v) } })
            }

            ThemeDivider()
            CardStyleEditor(
                style = theme.cardStyle,
                onChange = { s -> onChange { it.withCardStyle(s) } },
                showColor = !(theme.useGoogleColors && theme.markerStyle == "CARD")
            )
        }
    }
}
