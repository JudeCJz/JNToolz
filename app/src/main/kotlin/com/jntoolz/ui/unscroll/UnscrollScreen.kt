package com.jntoolz.ui.unscroll

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.jntoolz.tools.Tool
import com.jntoolz.ui.common.PermissionGate
import com.jntoolz.ui.sot.SotCard
import com.jntoolz.ui.sot.SotColors
import com.jntoolz.ui.sot.SotLabel
import com.jntoolz.ui.sot.SotPage
import com.jntoolz.ui.sot.SotSection
import com.jntoolz.ui.sot.SotTopBar
import com.jntoolz.ui.sot.SwitchRow
import com.jntoolz.unscroll.A11yToggle
import com.jntoolz.unscroll.ForegroundWatcher
import com.jntoolz.unscroll.AddictiveApp
import com.jntoolz.unscroll.GuardMode
import com.jntoolz.unscroll.UnscrollStore
import kotlinx.coroutines.delay

private const val GRANT_COMMAND = "adb shell pm grant com.jntoolz android.permission.WRITE_SECURE_SETTINGS"

/** The Unscroll tool: blocks today, pause, status, missing setup, one switch per installed app. */
@Composable
fun UnscrollScreen(onBack: () -> Unit) {
    BackHandler { onBack() }
    PermissionGate(Tool.UNSCROLL)
    val context = LocalContext.current
    val store = remember { UnscrollStore(context) }
    var serviceOn by remember { mutableStateOf(UnscrollStore.isServiceOn(context)) }
    var notifOk by remember { mutableStateOf(notificationsAllowed(context)) }
    var blocks by remember { mutableIntStateOf(store.blocksToday) }
    var installed by remember { mutableStateOf(installedSet(context)) }
    val appOn = remember { mutableStateMapOf<AddictiveApp, Boolean>() }
    // Read-only here: the mode is changed with the hub toggle.
    var mode by remember { mutableStateOf(store.mode) }
    val hard = mode == GuardMode.HARD
    var pausedUntil by remember { mutableLongStateOf(store.pausedUntil) }
    var pausesLeft by remember { mutableIntStateOf(UnscrollStore.PAUSES_PER_DAY - store.pausesToday) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val paused = mode == GuardMode.SOFT && now < pausedUntil
    var appScope by remember { mutableStateOf(ForegroundWatcher.appScopeActive(context)) }
    var usageOk by remember { mutableStateOf(ForegroundWatcher.usageAllowed(context)) }
    var writeOk by remember { mutableStateOf(A11yToggle.canWrite(context)) }
    var pickPause by remember { mutableStateOf(false) }
    var sheet by remember { mutableStateOf(false) }
    // Ticks the countdown; ends by itself when the time is up, which flips the card back to the button.
    LaunchedEffect(pausedUntil) {
        while (true) {
            now = System.currentTimeMillis()
            if (now >= pausedUntil) break
            delay(1000)
        }
    }
    fun locked() = Toast.makeText(context, "Locked in Hard mode", Toast.LENGTH_SHORT).show()

    // Back from system Settings (or another app): re-read service, permissions and installed apps.
    LifecycleResumeEffect(Unit) {
        serviceOn = UnscrollStore.isServiceOn(context)
        mode = store.mode
        pausedUntil = store.pausedUntil
        pausesLeft = UnscrollStore.PAUSES_PER_DAY - store.pausesToday
        now = System.currentTimeMillis()
        appScope = ForegroundWatcher.appScopeActive(context)
        usageOk = ForegroundWatcher.usageAllowed(context)
        writeOk = A11yToggle.canWrite(context)
        notifOk = notificationsAllowed(context)
        blocks = store.blocksToday
        installed = installedSet(context)
        for (a in AddictiveApp.entries) appOn[a] = store.on(a)
        onPauseOrDispose { }
    }
    val askNotif = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        notifOk = notificationsAllowed(context)
    }
    val apps = AddictiveApp.installed { it in installed }

    val auto = !serviceOn && appScope
    val missing = mutableListOf<SetupItem>()
    if (!serviceOn && !auto) {
        missing += SetupItem("SERVICE", "OPEN") { context.startSafely(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        if (Build.VERSION.SDK_INT >= 33) {
            missing += SetupItem("RESTRICTED SETTINGS", "OPEN") {
                context.startSafely(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                )
            }
        }
    }
    if (!usageOk) missing += SetupItem("USAGE ACCESS", "OPEN") { context.startSafely(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
    if (!writeOk) missing += SetupItem("SECURE SETTINGS", "COPY ADB") { copyText(context, GRANT_COMMAND) }
    if (!notifOk && apps.any { appOn[it] ?: store.on(it) }) {
        missing += SetupItem("NOTIFICATIONS", "ALLOW") {
            if (Build.VERSION.SDK_INT >= 33) askNotif.launch(Manifest.permission.POST_NOTIFICATIONS)
            else context.startSafely(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            )
        }
    }

    Scaffold(
        containerColor = SotColors.Bg,
        topBar = { SotTopBar("UNSCROLL", onBack) }
    ) { inner ->
        Column(Modifier.padding(inner)) {
            SotPage(rememberScrollState()) {
                UnscrollTopCards(
                    blocks = blocks,
                    showPause = mode == GuardMode.SOFT,
                    paused = paused,
                    secondsLeft = ((pausedUntil - now + 999) / 1000).coerceAtLeast(0),
                    pausesLeft = pausesLeft,
                    onButton = {
                        if (paused) {
                            store.resume()
                            pausedUntil = 0L
                            now = System.currentTimeMillis()
                        } else if (pausesLeft > 0) pickPause = true
                    }
                )

                if (missing.isNotEmpty()) SetupRow(missing.size) { sheet = true }

                SotSection("APPS")
                SotCard(vpad = 8.dp) {
                    if (apps.isEmpty()) {
                        SotLabel("NO APPS FOUND", Modifier.padding(vertical = 12.dp))
                    } else {
                        for (a in apps) {
                            val on = appOn[a] ?: store.on(a)
                            SwitchRow(a.label, on) {
                                if (hard && on) locked() else { appOn[a] = !on; store.setOn(a, !on) }
                            }
                        }
                    }
                }
            }
        }
    }

    if (pickPause) {
        PauseDialog(
            pausesLeft = pausesLeft,
            onPick = { m ->
                pickPause = false
                if (store.pause(m)) {
                    pausedUntil = store.pausedUntil
                    pausesLeft = UnscrollStore.PAUSES_PER_DAY - store.pausesToday
                    now = System.currentTimeMillis()
                }
            },
            onDismiss = { pickPause = false }
        )
    }

    if (sheet) {
        if (missing.isEmpty()) sheet = false else SetupSheet(missing) { sheet = false }
    }
}

private fun installedSet(c: Context): Set<String> =
    AddictiveApp.ALL_PACKAGES.filterTo(HashSet()) { UnscrollStore.isInstalled(c, it) }

private fun notificationsAllowed(c: Context): Boolean =
    Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(c, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

private fun copyText(c: Context, text: String) {
    c.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("command", text))
    Toast.makeText(c, "Copied", Toast.LENGTH_SHORT).show()
}

private fun Context.startSafely(intent: Intent) {
    try {
        startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) {
        // No activity for it on this phone.
    }
}
