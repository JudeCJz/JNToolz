package com.jntoolz.ui.unscroll

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.jntoolz.ui.sot.Segmented
import com.jntoolz.ui.sot.SotCard
import com.jntoolz.ui.sot.SotColors
import com.jntoolz.ui.sot.SotLabel
import com.jntoolz.ui.sot.SotNumber
import com.jntoolz.ui.sot.SotPage
import com.jntoolz.ui.sot.SotText
import com.jntoolz.ui.sot.SotTopBar
import com.jntoolz.ui.sot.SwitchRow
import com.jntoolz.ui.sot.tap
import android.widget.Toast
import com.jntoolz.unscroll.GuardMode
import com.jntoolz.unscroll.UnscrollStore
import com.jntoolz.unscroll.WarnApp
import com.jntoolz.unscroll.installedKinds

/** The Unscroll tool: guard mode (read-only), service state, what to block, which apps to warn about, blocks today. */
@Composable
fun UnscrollScreen(onBack: () -> Unit) {
    BackHandler { onBack() }
    val context = LocalContext.current
    val store = remember { UnscrollStore(context) }
    var serviceOn by remember { mutableStateOf(UnscrollStore.isServiceOn(context)) }
    var notifOk by remember { mutableStateOf(notificationsAllowed(context)) }
    var minutes by remember { mutableIntStateOf(store.warnMinutes) }
    var blocks by remember { mutableIntStateOf(store.blocksToday) }
    var installed by remember { mutableStateOf(installedSet(context)) }
    val blockOn = remember { mutableStateMapOf<com.jntoolz.unscroll.ShortKind, Boolean>() }
    val warnOn = remember { mutableStateMapOf<WarnApp, Boolean>() }
    // Read-only here: the mode is changed with the hub toggle.
    var mode by remember { mutableStateOf(store.mode) }
    val hard = mode == GuardMode.HARD
    fun locked() = Toast.makeText(context, "Locked in Hard mode", Toast.LENGTH_SHORT).show()

    // Back from system Settings (or another app): re-read service, permission and installed apps.
    LifecycleResumeEffect(Unit) {
        serviceOn = UnscrollStore.isServiceOn(context)
        mode = store.mode
        notifOk = notificationsAllowed(context)
        blocks = store.blocksToday
        installed = installedSet(context)
        for (k in com.jntoolz.unscroll.ShortKind.entries) blockOn[k] = store.blocks(k)
        for (a in WarnApp.entries) warnOn[a] = store.warns(a)
        onPauseOrDispose { }
    }
    val askNotif = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        notifOk = notificationsAllowed(context)
    }
    val kinds = installedKinds { it in installed }
    val apps = WarnApp.installed { it in installed }

    Scaffold(
        containerColor = SotColors.Bg,
        topBar = { SotTopBar("UNSCROLL", onBack) }
    ) { inner ->
        Column(Modifier.padding(inner)) {
            SotPage(rememberScrollState()) {
                SotCard(vpad = 14.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SotLabel("MODE", modifier = Modifier.weight(1f))
                        SotText(mode.name, 14.sp, if (hard) SotColors.Red else SotColors.Txt)
                    }
                    SotText(
                        if (hard) "Change on the hub. Leaving Hard needs the unlock." else "Change on the hub.",
                        11.sp,
                        SotColors.Dim,
                        bold = false,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                SotCard(vpad = 14.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SotLabel("SERVICE", modifier = Modifier.weight(1f))
                        SotText(
                            if (serviceOn) "ON" else "OFF",
                            14.sp,
                            if (serviceOn) SotColors.Green else SotColors.Red
                        )
                    }
                    if (!serviceOn) {
                        Spacer(Modifier.height(12.dp))
                        ActionButton("TURN ON") {
                            context.startSafely(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        }
                        if (Build.VERSION.SDK_INT >= 33) {
                            SotText(
                                "Blocked? Allow restricted settings",
                                11.sp,
                                SotColors.Dim,
                                bold = false,
                                modifier = Modifier
                                    .padding(top = 12.dp)
                                    .tap {
                                        context.startSafely(
                                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                                        )
                                    }
                            )
                        }
                    }
                }

                if (serviceOn) {
                    SotCard(vpad = 14.dp) {
                        SotLabel("BLOCKED TODAY")
                        SotNumber("$blocks", 40.sp, modifier = Modifier.padding(top = 6.dp))
                    }
                }

                if (kinds.isNotEmpty()) {
                    SotCard(vpad = 8.dp) {
                        SotLabel("BLOCK", modifier = Modifier.padding(top = 6.dp))
                        for (k in kinds) {
                            val on = blockOn[k] ?: store.blocks(k)
                            SwitchRow(k.label, on) {
                                if (hard && on) locked() else { blockOn[k] = !on; store.setBlocks(k, !on) }
                            }
                        }
                    }
                }

                if (apps.isNotEmpty()) {
                    SotCard(vpad = 8.dp) {
                        SotLabel("WARN", modifier = Modifier.padding(top = 6.dp))
                        for (a in apps) {
                            val on = warnOn[a] ?: store.warns(a)
                            SwitchRow(a.label, on) {
                                if (hard && on) locked() else { warnOn[a] = !on; store.setWarns(a, !on) }
                            }
                        }
                        val anyOn = apps.any { warnOn[it] ?: store.warns(it) }
                        if (anyOn) {
                            SotLabel("MINUTES", modifier = Modifier.padding(top = 4.dp, bottom = 8.dp))
                            Segmented(
                                options = UnscrollStore.MINUTES.map { it to "$it" },
                                selected = minutes,
                                // Hard only lets the limit get stricter.
                                onPick = { if (hard && it > minutes) locked() else { minutes = it; store.warnMinutes = it } },
                                modifier = Modifier.padding(bottom = 12.dp)
                            )
                            if (!notifOk) {
                                ActionButton("ALLOW NOTIFICATIONS") {
                                    if (Build.VERSION.SDK_INT >= 33) askNotif.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    else context.startSafely(
                                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                    )
                                }
                                Spacer(Modifier.height(12.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun installedSet(c: Context): Set<String> =
    com.jntoolz.unscroll.ShortsRules.TIMED.filterTo(HashSet()) { UnscrollStore.isInstalled(c, it) }

private fun notificationsAllowed(c: Context): Boolean =
    Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(c, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

private fun Context.startSafely(intent: Intent) {
    try {
        startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) {
        // No activity for it on this phone.
    }
}

/** A full-width outlined caps button in the app's chip style. */
@Composable
private fun ActionButton(text: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(shape)
            .background(SotColors.Chip)
            .border(1.dp, SotColors.ChipBorder, shape)
            .tap(onClick),
        contentAlignment = Alignment.Center
    ) {
        SotText(text, 11.sp)
    }
}
