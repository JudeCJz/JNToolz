package com.jntoolz.ui.sot

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

private val TABS = listOf(0 to "NOW", 1 to "HISTORY", 2 to "APPS & SETTINGS")

/** The Battery tool (internal name sot): the app's top bar, a chip row of tabs (Now, History, Apps & Settings), black OLED pages. */
@Composable
fun SotScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val vm: SotViewModel = viewModel()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val scrolls = List(3) { rememberScrollState() }

    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { vm.recheck() }

    LaunchedEffect(Unit) {
        vm.enter()
        if (Build.VERSION.SDK_INT >= 33 && !vm.askedNotif() &&
            context.checkSelfPermission(NOTIF) != PackageManager.PERMISSION_GRANTED
        ) {
            vm.markAskedNotif()
            askPermission.launch(NOTIF)
        }
    }
    // Live updates only while the screen is resumed; permissions and tips are re-checked on each resume.
    LaunchedEffect(lifecycle) { lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) { vm.whileResumed() } }
    DisposableEffect(Unit) { onDispose { vm.leave() } }

    BackHandler { if (vm.page != 0) vm.show(0) else onBack() }

    val openUsage = {
        if (Build.VERSION.SDK_INT >= 28) {
            val i = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, Uri.parse("package:" + context.packageName))
            if (!tryStart(context, i)) tryStart(context, Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SotColors.Bg)
    ) {
        SotTopBar("BATTERY", onBack)
        Segmented(
            TABS,
            vm.page,
            vm::show,
            Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 6.dp)
        )
        Box(Modifier.weight(1f)) { when (vm.page) {
            0 -> NowTab(vm, scrolls[0])
            1 -> HistoryTab(vm, scrolls[1])
            else -> AppsSettingsTab(
                vm,
                scrolls[2],
                onUsage = openUsage,
                onBackground = { openBattery(context) },
                onNotif = {
                    val activity = context.findActivity()
                    if (Build.VERSION.SDK_INT >= 33 && !vm.settings.notif &&
                        activity?.shouldShowRequestPermissionRationale(NOTIF) == true
                    ) {
                        askPermission.launch(NOTIF)
                    } else {
                        tryStart(
                            context,
                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        )
                    }
                },
                onBluetooth = {
                    // Nearby-devices permission, so "Bluetooth on, nothing connected" can be told apart from a connected watch.
                    val activity = context.findActivity()
                    if (vm.settings.bluetooth || (vm.askedBt() && activity?.shouldShowRequestPermissionRationale(BT) != true)) {
                        tryStart(context, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + context.packageName)))
                    } else {
                        vm.markAskedBt()
                        askPermission.launch(BT)
                    }
                },
                onTip = { tryStart(context, it) }
            )
        } }
    }
}

private fun openBattery(context: Context) {
    val pm = context.getSystemService(PowerManager::class.java)
    if (!pm.isIgnoringBatteryOptimizations(context.packageName)) {
        @Suppress("BatteryLife")
        val i = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + context.packageName))
        if (tryStart(context, i)) return
    }
    tryStart(context, Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
}

private fun tryStart(context: Context, i: Intent): Boolean = try {
    context.startActivity(i)
    true
} catch (_: RuntimeException) {
    false
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
