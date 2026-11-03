package com.jntoolz.tools

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import com.jntoolz.sot.Usage
import com.jntoolz.unscroll.ForegroundWatcher
import com.jntoolz.unscroll.UnscrollStore

/** RUNTIME = a system dialog (via an activity-result launcher); SPECIAL = the user flips it in a Settings screen. */
enum class PermKind { RUNTIME, SPECIAL }

/** One thing a tool needs. [runtime] is the permission name for RUNTIME ones; [settings] is the screen that grants it. */
data class Perm(
    val id: String,
    val label: String,
    val kind: PermKind,
    val runtime: String? = null,
    val granted: (Context) -> Boolean,
    val settings: (Context) -> Intent?
)

/** What each tool needs before it works, and whether that is all given. */
object ToolPerms {
    fun required(c: Context, tool: Tool): List<Perm> = when (tool) {
        Tool.CALENDAR -> listOf(calendar())
        Tool.BATTERY -> listOfNotNull(notifications(), usage(), background())
        Tool.COUNTER -> emptyList()
        Tool.UNSCROLL -> listOfNotNull(notifications(), usage(), service())
    }

    fun missing(c: Context, tool: Tool): List<Perm> = required(c, tool).filterNot { it.granted(c) }

    /** Red marker: tool on and something missing. */
    fun needsAttention(c: Context, tool: Tool): Boolean = Tools.isEnabled(c, tool) && missing(c, tool).isNotEmpty()

    private fun runtimeGranted(c: Context, name: String) = c.checkSelfPermission(name) == PackageManager.PERMISSION_GRANTED

    private fun appDetails(c: Context) = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + c.packageName))

    private fun calendar() = Perm(
        "calendar", "CALENDAR", PermKind.RUNTIME, Manifest.permission.READ_CALENDAR,
        granted = { runtimeGranted(it, Manifest.permission.READ_CALENDAR) },
        settings = ::appDetails
    )

    private fun notifications(): Perm? {
        if (Build.VERSION.SDK_INT < 33) return null
        return Perm(
            "notifications", "NOTIFICATIONS", PermKind.RUNTIME, Manifest.permission.POST_NOTIFICATIONS,
            granted = { runtimeGranted(it, Manifest.permission.POST_NOTIFICATIONS) },
            settings = ::appDetails
        )
    }

    private fun usage() = Perm(
        "usage", "USAGE ACCESS", PermKind.SPECIAL,
        granted = { Usage.allowed(it) },
        settings = {
            if (Build.VERSION.SDK_INT >= 28) Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, Uri.parse("package:" + it.packageName))
            else Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
        }
    )

    private fun background() = Perm(
        "background", "BACKGROUND", PermKind.SPECIAL,
        granted = { it.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(it.packageName) },
        settings = {
            @Suppress("BatteryLife")
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + it.packageName))
        }
    )

    /** The accessibility service is on, or the gate is set up so the app can switch it by itself. */
    private fun service() = Perm(
        "service", "SERVICE", PermKind.SPECIAL,
        granted = { UnscrollStore.isServiceOn(it) || ForegroundWatcher.isReady(it) },
        settings = { Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS) }
    )
}
