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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
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
import com.jntoolz.sot.widget.BatteryWidgetProvider
import com.jntoolz.ui.common.AddWidgetButton

/** The Battery tool (internal name sot): one dashboard page (gear in the top bar opens Settings), black OLED. */
@Composable
fun SotScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val vm: SotViewModel = viewModel()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val dashScroll = rememberScrollState()
    val settingsScroll = rememberScrollState()

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

    BackHandler { if (vm.settingsOpen) vm.showSettings(false) else onBack() }

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
        if (vm.settingsOpen) {
            SotTopBar("SETTINGS", { vm.showSettings(false) })
            SettingsPage(
                vm,
                settingsScroll,

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
        } else {
            SotTopBar("BATTERY", onBack, onSettings = { vm.showSettings(true) })
            AddWidgetButton(
                provider = BatteryWidgetProvider::class.java,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp)
            )
            Dashboard(vm, dashScroll, onUsage = openUsage)
        }
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
