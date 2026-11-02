package com.jntoolz.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import com.jntoolz.data.CardMath
import com.jntoolz.data.CardSpec
import com.jntoolz.data.CardStyle
import com.jntoolz.ui.main.ColorPickerDialog
import com.jntoolz.widget.CardPainter

private val CARD_PRESETS = listOf(
    0xFF202121L, 0xFF2B1414L, 0xFF0F1E28L, 0xFF17241AL, 0xFF2A1B3DL, 0xFF3A2A12L, 0xFFF4F4F4L
)
private val TOP_PRESETS = listOf(0L, 0xFFFFFFFFL, 0xFF000000L, 0xFFFF2E2EL, 0xFF039BE5L, 0xFFF6BF26L)
private val TOP_SWATCHES = TOP_PRESETS.map { SwatchSpec(color = it) }

/** Paints [spec] as a rounded card background with the exact painter the widgets use. */
fun Modifier.cardBackground(spec: CardSpec, corner: Dp): Modifier = drawBehind {
    drawIntoCanvas { CardPainter.draw(it.nativeCanvas, size.width, size.height, corner.toPx(), density, spec) }
}

/**
 * Card / background look controls shared by every tool's theming section (Calendar cards, Counter widgets,
 * Battery widget background): colour, gradient (top colour, intensity, angle that snaps at multiples of 45)
 * and glass with its level. Opens its own colour picker.
 *
 * @param autoColor Counter only: the auto card colour, offered as the first "A" (auto) swatch.
 * @param showColor false hides the colour row (Calendar cards coloured by the event).
 */
@Composable
fun CardStyleEditor(
    style: CardStyle,
    onChange: (CardStyle) -> Unit,
    modifier: Modifier = Modifier,
    autoColor: Int? = null,
    showColor: Boolean = true,
) {
    var picking by remember { mutableStateOf<String?>(null) }
    val haptic = LocalHapticFeedback.current
    val color = style.color.toLong() and 0xFFFFFFFFL
    val top = style.gradientTop.toLong() and 0xFFFFFFFFL
    val swatches = remember(autoColor) {
        (if (autoColor != null) listOf(SwatchSpec(0L, autoColor.toLong() and 0xFFFFFFFFL or 0xFF000000L, "A")) else emptyList()) +
            CARD_PRESETS.map { SwatchSpec(it) }
    }

    Column(modifier) {
        if (showColor) {
            SwatchRow(
                label = "Color",
                presets = swatches,
                selectedColor = color,
                customSelected = color != 0L && color !in CARD_PRESETS,
                customColor = color,
                onSelect = { onChange(style.copy(color = it.color.toInt())) },
                onCustom = { picking = "card" }
            )
        }

        ThemeSwitchRow("Gradient", style.gradient) { onChange(style.copy(gradient = it)) }
        AnimatedVisibility(
            visible = style.gradient,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column {
                SwatchRow(
                    label = "Top color",
                    presets = TOP_SWATCHES,
                    selectedColor = top,
                    customSelected = top !in TOP_PRESETS,
                    customColor = top,
                    onSelect = { onChange(style.copy(gradientTop = it.color.toInt())) },
                    onCustom = { picking = "top" }
                )
                ThemeSliderRow("Intensity", style.intensity, 0..100, { onChange(style.copy(intensity = it)) })
                ThemeSliderRow("Angle", style.angle, 0..359, { raw ->
                    val snapped = CardMath.snapAngle(raw)
                    if (snapped != style.angle) {
                        if (snapped != raw) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onChange(style.copy(angle = snapped))
                    }
                }, suffix = "°")
            }
        }

        ThemeSwitchRow("Glass", style.glass) { onChange(style.copy(glass = it)) }
        AnimatedVisibility(
            visible = style.glass,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            ThemeSliderRow("Glass", style.glassLevel, 0..100, { onChange(style.copy(glassLevel = it)) })
        }
    }

    picking?.let { which ->
        ColorPickerDialog(
            initial = if (which == "card") (if (style.color == 0) (autoColor ?: 0xFF202121.toInt()) else style.color)
            else (if (style.gradientTop == 0) 0xFFFFFFFF.toInt() else style.gradientTop),
            onDismiss = { picking = null },
            onConfirm = { argb ->
                // Card colour keeps the picker's opacity; the top colour is always opaque.
                onChange(if (which == "card") style.copy(color = argb) else style.copy(gradientTop = argb or 0xFF000000.toInt()))
                picking = null
            }
        )
    }
}
