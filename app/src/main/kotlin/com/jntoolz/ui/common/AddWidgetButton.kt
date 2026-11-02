package com.jntoolz.ui.common

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jntoolz.tools.WidgetKit

private val PillShape = RoundedCornerShape(50)

/**
 * Secondary outlined pill button: transparent bg, 1dp 0xFF333333 border, white text, 44dp tall.
 * Asks the launcher to pin [provider]; when it cannot, says how to add it by hand.
 */
@Composable
fun AddWidgetButton(provider: Class<*>, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(PillShape)
            .border(1.dp, Color(0xFF333333), PillShape)
            .background(Color.Transparent)
            .clickable {
                if (!WidgetKit.requestPin(context, provider)) {
                    Toast.makeText(context, "Long-press the home screen > Widgets > JNToolz", Toast.LENGTH_LONG).show()
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "+ ADD WIDGET",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            letterSpacing = 1.sp,
        )
    }
}
