package com.jntoolz.ui.unscroll

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.jntoolz.R
import com.jntoolz.tools.Tool
import com.jntoolz.tools.Tools
import com.jntoolz.ui.hub.OFF_ALPHA
import com.jntoolz.ui.sot.SotColors
import com.jntoolz.unscroll.GuardMode
import com.jntoolz.unscroll.UnscrollStore

/**
 * Hub entry: logo, name and the OFF / SOFT / HARD toggle. SOFT and HARD both block; leaving HARD needs the
 * typed unlock, going HARD is confirmed first. Picking a blocking mode without the service opens the screen,
 * whose SERVICE card turns it on.
 */
@Composable
fun UnscrollHubCard(onOpen: () -> Unit) {
    val context = LocalContext.current
    val store = remember { UnscrollStore(context) }
    var mode by remember { mutableStateOf(store.mode) }
    var askHard by remember { mutableStateOf(false) }
    var unlockTo by remember { mutableStateOf<GuardMode?>(null) }
    val on = mode.blocking

    // Back from the Unscroll screen or system Settings: the mode may have been unlocked meanwhile.
    LifecycleResumeEffect(Unit) {
        mode = store.mode
        onPauseOrDispose { }
    }

    // The mode is saved before the tool switch, so Tools never sees "off while HARD" on the way out of HARD.
    fun apply(m: GuardMode) {
        store.mode = m
        mode = m
        Tools.setEnabled(context, Tool.UNSCROLL, m.blocking)
        if (m.blocking && !UnscrollStore.isServiceOn(context)) {
            Toast.makeText(context, "Turn on the Unscroll service", Toast.LENGTH_SHORT).show()
            onOpen()
        }
    }

    fun pick(m: GuardMode) {
        when (mode.gateTo(m)) {
            GuardMode.Gate.NONE -> if (m != mode) apply(m)
            GuardMode.Gate.CONFIRM -> askHard = true
            GuardMode.Gate.UNLOCK -> unlockTo = m
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(enabled = on) { onOpen() },
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = SotColors.Card),
        border = BorderStroke(1.dp, SotColors.Border)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_unscroll_logo),
                contentDescription = "Unscroll",
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .alpha(if (on) 1f else OFF_ALPHA)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = "Unscroll",
                color = SotColors.Txt,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier
                    .weight(1f)
                    .alpha(if (on) 1f else OFF_ALPHA)
            )
            Spacer(modifier = Modifier.width(12.dp))
            ModeToggle(mode, ::pick)
        }
    }

    if (askHard) HardWarningDialog(
        onConfirm = { askHard = false; apply(GuardMode.HARD) },
        onDismiss = { askHard = false }
    )
    unlockTo?.let { to ->
        UnlockDialog(
            onUnlocked = { unlockTo = null; apply(to) },
            onDismiss = { unlockTo = null }
        )
    }
}

private val TRACK = Color(0xFF1A1A1A)
private val PICKED = Color(0xFFFF2E2E)
private val IDLE_TEXT = Color(0xFF8E8E8E)

/** Compact OFF / SOFT / HARD segmented toggle. */
@Composable
private fun ModeToggle(selected: GuardMode, onPick: (GuardMode) -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = Modifier
            .clip(shape)
            .background(TRACK)
            .padding(2.dp)
    ) {
        for (m in GuardMode.entries) {
            val picked = m == selected
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (picked) PICKED else Color.Transparent)
                    .clickable { onPick(m) }
                    .padding(horizontal = 9.dp, vertical = 7.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = m.name,
                    color = if (picked) Color.White else IDLE_TEXT,
                    fontSize = 11.sp,
                    fontWeight = if (picked) FontWeight.Bold else FontWeight.Normal,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}
