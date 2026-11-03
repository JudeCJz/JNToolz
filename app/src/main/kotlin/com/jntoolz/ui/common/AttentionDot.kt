package com.jntoolz.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.jntoolz.ui.sot.SotColors

/** The red marker next to a tool's name on the hub: the tool is on and a permission is missing. */
@Composable
fun AttentionDot(visible: Boolean, modifier: Modifier = Modifier) {
    if (!visible) return
    Spacer(
        modifier
            .padding(start = 8.dp)
            .size(8.dp)
            .clip(CircleShape)
            .background(SotColors.Red)
    )
}
