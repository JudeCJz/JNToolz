package com.jntoolz.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderColors
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jntoolz.data.ThemeMath
import kotlin.math.roundToInt

internal val GOOGLE_PALETTE = listOf(
    0xFFD50000, 0xFFE67C73, 0xFFF4511E, 0xFFF6BF26, 0xFF33B679, 0xFF0B8043,
    0xFF039BE5, 0xFF3F51B5, 0xFF7986CB, 0xFF8E24AA, 0xFF616161, 0xFF795548
).map { it.toInt() }

@Composable
internal fun ColorPickerDialog(
    initial: Int,
    presets: List<Int> = emptyList(),
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
    onReset: (() -> Unit)? = null
) {
    val init = remember {
        FloatArray(3).also { android.graphics.Color.colorToHSV(initial, it) }
    }
    var hue by remember { mutableStateOf(init[0]) }
    var sat by remember { mutableStateOf(init[1]) }
    var bri by remember { mutableStateOf(init[2]) }
    var opacity by remember { mutableStateOf(ThemeMath.alpha(initial) / 255f) }
    var hex by remember { mutableStateOf(String.format("%06X", initial and 0xFFFFFF)) }

    fun rgbNow(): Int = android.graphics.Color.HSVToColor(floatArrayOf(hue, sat, bri)) and 0xFFFFFF
    fun argbNow(): Int = rgbNow() or ((opacity * 255).roundToInt() shl 24)
    fun syncHex() { hex = String.format("%06X", rgbNow()) }

    val sliderColors = SliderDefaults.colors(
        thumbColor = TextWhite,
        activeTrackColor = AccentRed,
        inactiveTrackColor = Color(0xFF333333)
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardBg,
        title = {
            Text(
                text = "COLOR",
                color = TextWhite,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.5.sp
            )
        },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF777777))
                            .border(1.dp, Color(0xFF444444), RoundedCornerShape(10.dp))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(argbNow()))
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    OutlinedTextField(
                        value = hex,
                        onValueChange = { raw ->
                            val cleaned = raw.uppercase().filter { it in "0123456789ABCDEF" }.take(6)
                            hex = cleaned
                            if (cleaned.length == 6) {
                                val c = cleaned.toInt(16) or ThemeMath.OPAQUE
                                val hsv = FloatArray(3)
                                android.graphics.Color.colorToHSV(c, hsv)
                                hue = hsv[0]; sat = hsv[1]; bri = hsv[2]
                            }
                        },
                        singleLine = true,
                        prefix = { Text("#", color = MutedGrey, fontFamily = FontFamily.Monospace) },
                        textStyle = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 14.sp,
                            color = TextWhite
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AccentRed,
                            unfocusedBorderColor = Color(0xFF444444),
                            cursorColor = AccentRed
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }
                if (presets.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        presets.forEach { pc ->
                            Box(
                                modifier = Modifier
                                    .size(26.dp)
                                    .clip(CircleShape)
                                    .background(Color(pc))
                                    .border(1.dp, Color(0xFF444444), CircleShape)
                                    .clickable {
                                        val hsv = FloatArray(3)
                                        android.graphics.Color.colorToHSV(pc, hsv)
                                        hue = hsv[0]; sat = hsv[1]; bri = hsv[2]
                                        opacity = 1f
                                        syncHex()
                                    }
                            )
                        }
                    }
                }
                PickerSlider("H", hue, 0f..360f, sliderColors) { hue = it; syncHex() }
                PickerSlider("S", sat, 0f..1f, sliderColors) { sat = it; syncHex() }
                PickerSlider("B", bri, 0f..1f, sliderColors) { bri = it; syncHex() }
                PickerSlider("A", opacity, 0f..1f, sliderColors) { opacity = it }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(argbNow()) }) {
                Text("OK", color = AccentRed, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            Row {
                if (onReset != null) {
                    TextButton(onClick = onReset) {
                        Text("RESET", color = AccentRed, fontFamily = FontFamily.Monospace)
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("CANCEL", color = MutedGrey, fontFamily = FontFamily.Monospace)
                }
            }
        }
    )
}

@Composable
private fun PickerSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    colors: SliderColors,
    onChange: (Float) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            color = MutedGrey,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.width(16.dp)
        )
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            colors = colors,
            modifier = Modifier.weight(1f)
        )
    }
}
