package com.jntoolz.sot

import android.content.Context
import android.os.Build
import android.provider.Settings
import kotlin.math.roundToInt

/**
 * Accurate device, hardware, and battery health telemetry read dynamically from system properties and kernel state.
 */
object DeviceInfo {

    /** The real commercial name of the phone (e.g. "Nothing Phone (4a) Pro"). */
    fun deviceName(context: Context): String {
        val brandDevice = getProp("ro.product.brand_device_name")
        if (brandDevice.isNotBlank()) return brandDevice

        val globalName = try {
            Settings.Global.getString(context.contentResolver, "device_name")
        } catch (_: Throwable) { null }
        if (!globalName.isNullOrBlank()) return globalName.trim()

        val market = getProp("ro.product.marketname")
        if (market.isNotBlank()) return market

        val model = Build.MODEL
        val manu = Build.MANUFACTURER
        return if (model.startsWith(manu, ignoreCase = true)) model else "$manu $model"
    }

    /** OS and firmware release (e.g. "Android 17 · Nothing OS 5.0"). */
    fun osVersion(): String {
        val nothingVer = getProp("ro.nothing.version.id").ifBlank { getProp("ro.build.nothing.version") }
        val androidVer = "Android ${Build.VERSION.RELEASE}"
        val display = Build.DISPLAY
        return when {
            nothingVer.isNotBlank() -> "$androidVer · Nothing OS $nothingVer ($display)"
            display.isNotBlank() -> "$androidVer · $display"
            else -> androidVer
        }
    }

    /** Battery design capacity in mAh (e.g. "5,480 mAh"). */
    fun batteryDesignMah(context: Context): Int {
        val d = Health.designMah(context)
        return if (d > 500) d.roundToInt() else 5000
    }

    /** Battery health percentage (checks Nothing OS vendor telemetry first, then kernel/usage). */
    fun batteryHealth(context: Context, bat: List<Store.B>): String {
        val ntHealth = getProp("vendor.nt.battery_info.health")
        if (ntHealth.isNotBlank() && ntHealth.toIntOrNull() != null) {
            return "${ntHealth.trim()}%"
        }
        return Health.percentText(context, bat)
    }

    /** Battery charge cycles reported by kernel PMIC or system state. */
    fun batteryCycles(): Int? {
        val prop = getProp("persist.sys.BatteryCycleCount")
        val c = prop.toIntOrNull()
        if (c != null && c >= 0) return c
        val sys = Health.kernelCycles()
        return if (sys >= 0) sys.toInt() else null
    }

    /** Approximate physical age of the phone derived from firmware build timestamp and cycle count. */
    fun deviceAge(): String {
        val buildUtc = getProp("ro.build.date.utc").toLongOrNull()
        val buildTime = if (buildUtc != null && buildUtc > 0) buildUtc * 1000L else Build.TIME
        val now = System.currentTimeMillis()
        val diffMs = now - buildTime
        val cycles = batteryCycles()
        val cycleStr = if (cycles != null) " ($cycles battery cycles)" else ""

        if (diffMs < 0) return "Brand new$cycleStr"

        val days = (diffMs / (24 * 3600 * 1000L)).toInt()
        val months = (days / 30.4375).toInt()
        val years = days / 365

        val timeStr = when {
            days == 0 -> "Less than a day old"
            days == 1 -> "1 day old"
            days < 30 -> "$days days old"
            months < 12 -> "$months month${if (months > 1) "s" else ""} old"
            else -> {
                val remMonths = months % 12
                if (remMonths > 0) "$years yr $remMonths mo old"
                else "$years yr old"
            }
        }
        return "$timeStr$cycleStr"
    }

    fun summary(context: Context, bat: List<Store.B>): String {
        val name = deviceName(context)
        val os = osVersion()
        val cap = batteryDesignMah(context)
        val capStr = String.format(java.util.Locale.US, "%,d mAh", cap)
        val health = batteryHealth(context, bat)
        val age = deviceAge()
        return "Device: $name\nOS: $os\nBattery: $capStr (Health: $health)\nDevice age: $age"
    }

    private fun getProp(key: String): String =
        try {
            val c = Class.forName("android.os.SystemProperties")
            val get = c.getMethod("get", String::class.java, String::class.java)
            (get.invoke(null, key, "") as? String)?.trim().orEmpty()
        } catch (_: Throwable) {
            ""
        }
}
