package com.jntoolz.ui.sot

import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** The per-app battery list: data, so it sits on the dashboard. */
@Composable
internal fun AppsSection(vm: SotViewModel, onUsage: () -> Unit) {
    SotCard(8.dp) {
        when (val apps = vm.apps) {
            null -> Unit
            AppsUi.NoAccess -> LinkRow("Allow usage access", onUsage)
            AppsUi.NotStarted -> EmptyRow("Starts at the next unplug")
            is AppsUi.Rows ->
                if (apps.rows.isEmpty()) EmptyRow("Nothing yet")
                else apps.rows.forEach { AppRowView(it) }
        }
    }
}

/** The settings page (gear on the dashboard): extra-battery tips, permissions and the start-on choice. */
@Composable
internal fun SettingsPage(
    vm: SotViewModel,
    scroll: ScrollState,
    onUsage: () -> Unit,
    onBackground: () -> Unit,
    onNotif: () -> Unit,
    onBluetooth: () -> Unit,
    onTip: (android.content.Intent) -> Unit,
) {
    val s = vm.settings
    SotPage(scroll) {
        // Tips: settings that use extra battery
        val tips = vm.tips
        if (tips.isNotEmpty()) {
            SotSection("USES EXTRA BATTERY")
            SotCard(12.dp) {
                for (t in tips) {
                    val action = t.action
                    LinkRow(t.title, if (action == null) null else { { onTip(action) } })
                }
            }
        }

        SotSection("PERMISSIONS & SETTINGS")
        SotCard(4.dp) {
            SwitchRow("Usage access", s.usage, onUsage)
            SwitchRow("Run in background", s.background, onBackground)
            SwitchRow("Notification", s.notif, onNotif)
            if (Build.VERSION.SDK_INT >= 31) SwitchRow("Bluetooth check", s.bluetooth, onBluetooth)
        }

        SotCard(4.dp) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SotText("Start on", 13.sp, modifier = Modifier.weight(1f))
                SinceSegment(s.startToday, vm::setStartToday)
            }
        }
    }
}

@Composable
private fun AppRowView(row: AppRow) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(32.dp)) {
            if (row.icon != null) Image(row.icon, contentDescription = null, modifier = Modifier.size(32.dp))
        }
        SotText(
            row.name,
            13.sp,
            singleLine = true,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp)
        )
        Box(Modifier.width(72.dp), contentAlignment = Alignment.CenterEnd) {
            SotText(row.pct, 13.sp)
        }
    }
}

@Composable
private fun EmptyRow(text: String) {
    Box(Modifier.fillMaxWidth().heightIn(min = 48.dp), contentAlignment = Alignment.CenterStart) {
        SotText(text, 13.sp, SotColors.Dim, bold = false)
    }
}
