package com.jntoolz.sot

import android.Manifest
import android.annotation.SuppressLint
import android.app.UiModeManager
import android.app.WallpaperManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.hardware.display.DisplayManager
import android.location.LocationManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.nfc.NfcAdapter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.view.Display
import java.io.BufferedReader
import java.io.FileReader
import java.net.Inet4Address
import java.net.NetworkInterface

/** Things the user could turn off right now to save battery. Read-only checks, no network. */
object Tips {
    class Tip(val title: String, val action: Intent?)

    private const val AP_ENABLING = 12
    private const val AP_ENABLED = 13
    private val AP_NAME = Regex("(ap|swlan|softap|wlan[1-9]|wifi_ap)\\d*.*")

    /** What to turn off now, most battery first. Fast (no network); safe on the main thread; never throws. */
    fun check(context: Context): List<Tip> {
        val out = ArrayList<Tip>()
        val c = context.applicationContext ?: context
        var bat: Intent? = null
        try {
            bat = c.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        } catch (ignored: Throwable) {
        }
        var wifi: Boolean? = null
        var bt: Boolean? = null
        try { wifi = wifiOn(c) } catch (ignored: Throwable) {}
        try { bt = btOn(c) } catch (ignored: Throwable) {}

        try { hot(c, bat, out) } catch (ignored: Throwable) {}
        try { hotspot(c, out) } catch (ignored: Throwable) {}
        try { brightness(c, out) } catch (ignored: Throwable) {}
        try { saver(c, bat, out) } catch (ignored: Throwable) {}
        try { refresh(c, out) } catch (ignored: Throwable) {}
        try { aod(c, out) } catch (ignored: Throwable) {}
        try { timeout(c, out) } catch (ignored: Throwable) {}
        try { location(c, out) } catch (ignored: Throwable) {}
        try { if (wifi == true) wifiIdle(c, out) } catch (ignored: Throwable) {}
        try { if (bt == true) btIdle(c, out) } catch (ignored: Throwable) {}
        try { live(c, out) } catch (ignored: Throwable) {}
        try { dark(c, out) } catch (ignored: Throwable) {}
        try { if (wifi == false) wifiScan(c, out) } catch (ignored: Throwable) {}
        try { if (bt == false) bleScan(c, out) } catch (ignored: Throwable) {}
        try { nfc(c, out) } catch (ignored: Throwable) {}
        return out
    }

    // ---- checks ----

    private fun hot(c: Context, bat: Intent?, out: MutableList<Tip>) {
        if (bat == null) return
        val t = bat.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
        if (t == Int.MIN_VALUE || t <= 400) return
        out.add(Tip("Battery hot, ${Math.round(t / 10f)} °C", pick(c, Intent(Intent.ACTION_POWER_USAGE_SUMMARY), null)))
    }

    private fun hotspot(c: Context, out: MutableList<Tip>) {
        val iface = apIface()
        val on = apOn(c) ?: (iface != null)
        if (!on) return
        val n = if (iface == null) -1 else arpClients(iface)
        if (n > 0) return // someone is using it
        val fix = pick(
            c, Intent("android.settings.TETHER_SETTINGS"),
            Intent("android.settings.WIFI_TETHER_SETTINGS"),
            cmp("com.android.settings", "com.android.settings.Settings\$TetherSettingsActivity"),
            cmp("com.android.settings", "com.android.settings.TetherSettings"),
            Intent(Settings.ACTION_WIRELESS_SETTINGS),
        )
        out.add(Tip(if (n == 0) "Hotspot on, nobody connected" else "Hotspot on", fix))
    }

    private fun brightness(c: Context, out: MutableList<Tip>) {
        if (sysInt(c, Settings.System.SCREEN_BRIGHTNESS_MODE, 1) != Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL) return
        val v = sysInt(c, Settings.System.SCREEN_BRIGHTNESS, -1)
        var max = 255
        try {
            val pm = c.getSystemService(PowerManager::class.java)
            val m = PowerManager::class.java.getMethod("getMaximumScreenBrightnessSetting").invoke(pm) as Int
            if (m > 0) max = m
        } catch (ignored: Throwable) {
        }
        // The stored value is linear; half of max is about 85% on the (perceptual) slider.
        if (v >= 0 && v >= max * 0.5) out.add(Tip("Brightness high", display(c)))
    }

    private fun saver(c: Context, bat: Intent?, out: MutableList<Tip>) {
        if (bat == null || bat.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0) return
        val l = bat.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val s = bat.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        if (l < 0 || s <= 0 || l * 100f / s > 20) return
        if (c.getSystemService(PowerManager::class.java).isPowerSaveMode) return
        out.add(Tip("Battery saver off", pick(c, Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS), null)))
    }

    private fun refresh(c: Context, out: MutableList<Tip>) {
        val d = c.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY) ?: return
        val cur = d.mode
        var low = Float.MAX_VALUE
        for (m in d.supportedModes)
            if (m.physicalWidth == cur.physicalWidth && m.physicalHeight == cur.physicalHeight && m.refreshRate >= 50)
                low = minOf(low, m.refreshRate)
        if (low == Float.MAX_VALUE) return
        var peak = -1f
        try {
            peak = Settings.System.getFloat(c.contentResolver, "peak_refresh_rate")
        } catch (ignored: Throwable) {
        }
        @Suppress("DEPRECATION")
        if (peak <= 0 || peak > 1000) peak = d.refreshRate // missing, or "infinity" = highest
        if (peak > 1000) return
        if (peak > low + 1) out.add(Tip("Refresh rate ${Math.round(peak)} Hz", display(c)))
    }

    private fun aod(c: Context, out: MutableList<Tip>) {
        var v = -1
        try {
            v = Settings.Secure.getInt(c.contentResolver, "doze_always_on")
        } catch (ignored: Throwable) {
        }
        if (v == 1) out.add(Tip("Always-on display on", pick(c, Intent("android.settings.LOCK_SCREEN_SETTINGS"), display(c))))
    }

    private fun timeout(c: Context, out: MutableList<Tip>) {
        val ms = sysInt(c, Settings.System.SCREEN_OFF_TIMEOUT, -1)
        if (ms <= 60_000) return
        val t = if (ms >= 24 * 3_600_000) "never"
        else if (ms >= 3_600_000) "${Math.round(ms / 3_600_000f)} h"
        else "${Math.round(ms / 60_000f)} min"
        out.add(Tip("Screen timeout $t", pick(c, Intent("android.settings.SCREEN_TIMEOUT_SETTINGS"), display(c))))
    }

    @Suppress("DEPRECATION")
    private fun location(c: Context, out: MutableList<Tip>) {
        val on = if (Build.VERSION.SDK_INT >= 28) c.getSystemService(LocationManager::class.java).isLocationEnabled
        else Settings.Secure.getInt(c.contentResolver, Settings.Secure.LOCATION_MODE, 0) != 0
        if (on) out.add(Tip("Location on", pick(c, Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS), null)))
    }

    @Suppress("DEPRECATION")
    private fun wifiIdle(c: Context, out: MutableList<Tip>) {
        val cm = c.getSystemService(ConnectivityManager::class.java)
        for (n in cm.allNetworks) {
            val nc = cm.getNetworkCapabilities(n)
            if (nc != null && nc.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return
        }
        out.add(
            Tip(
                "Wi-Fi on, not connected",
                if (Build.VERSION.SDK_INT >= 29) pick(c, Intent(Settings.Panel.ACTION_WIFI), Intent(Settings.ACTION_WIFI_SETTINGS))
                else pick(c, Intent(Settings.ACTION_WIFI_SETTINGS), null),
            )
        )
    }

    private fun btIdle(c: Context, out: MutableList<Tip>) {
        if (btConnected(c)) return
        out.add(Tip("Bluetooth on, nothing connected", pick(c, Intent(Settings.ACTION_BLUETOOTH_SETTINGS), null)))
    }

    private fun live(c: Context, out: MutableList<Tip>) {
        if (WallpaperManager.getInstance(c).wallpaperInfo != null)
            out.add(Tip("Live wallpaper", pick(c, Intent(Intent.ACTION_SET_WALLPAPER), null)))
    }

    private fun dark(c: Context, out: MutableList<Tip>) {
        val night = c.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        if (night == Configuration.UI_MODE_NIGHT_YES) return
        if (night == Configuration.UI_MODE_NIGHT_UNDEFINED) {
            val um = c.getSystemService(UiModeManager::class.java)
            if (um == null || um.nightMode != UiModeManager.MODE_NIGHT_NO) return
        }
        out.add(Tip("Dark mode off", pick(c, Intent("android.settings.DARK_THEME_SETTINGS"), display(c))))
    }

    @Suppress("DEPRECATION")
    private fun wifiScan(c: Context, out: MutableList<Tip>) {
        var on: Boolean? = null
        try {
            on = c.getSystemService(WifiManager::class.java).isScanAlwaysAvailable
        } catch (ignored: Throwable) {
        }
        if (on ?: (globalInt(c, "wifi_scan_always_enabled", 0) == 1)) out.add(Tip("Wi-Fi scanning on", scanning(c)))
    }

    private fun bleScan(c: Context, out: MutableList<Tip>) {
        var v = globalInt(c, "ble_scan_always_enabled", -1)
        if (v < 0) {
            try {
                val a = c.getSystemService(BluetoothManager::class.java).adapter
                v = if (BluetoothAdapter::class.java.getMethod("isBleScanAlwaysAvailable").invoke(a) as Boolean) 1 else 0
            } catch (ignored: Throwable) {
            }
        }
        if (v == 1) out.add(Tip("Bluetooth scanning on", scanning(c)))
    }

    private fun nfc(c: Context, out: MutableList<Tip>) {
        if (!c.packageManager.hasSystemFeature(PackageManager.FEATURE_NFC)) return
        val a = NfcAdapter.getDefaultAdapter(c)
        if (a == null || !a.isEnabled) return
        out.add(
            Tip(
                "NFC on",
                if (Build.VERSION.SDK_INT >= 29) pick(c, Intent(Settings.Panel.ACTION_NFC), Intent(Settings.ACTION_NFC_SETTINGS))
                else pick(c, Intent(Settings.ACTION_NFC_SETTINGS), null),
            )
        )
    }

    // ---- radios ----

    private fun wifiOn(c: Context): Boolean? {
        try {
            return c.getSystemService(WifiManager::class.java).isWifiEnabled // needs ACCESS_WIFI_STATE
        } catch (ignored: Throwable) {
        }
        val v = globalInt(c, Settings.Global.WIFI_ON, -1) // 1 on, 2 on in airplane mode
        return if (v < 0) null else v == 1 || v == 2
    }

    private fun btOn(c: Context): Boolean? {
        if (!c.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH)) return null
        try {
            val a = c.getSystemService(BluetoothManager::class.java).adapter
            if (a != null) return a.isEnabled
        } catch (ignored: Throwable) {
        }
        val v = globalInt(c, Settings.Global.BLUETOOTH_ON, -1)
        return if (v < 0) null else v == 1
    }

    /** True unless we can tell nothing is connected. Without BLUETOOTH_CONNECT (Android 12+) we can't, so true. */
    @SuppressLint("MissingPermission")
    private fun btConnected(c: Context): Boolean {
        try {
            val am = c.getSystemService(AudioManager::class.java)
            for (d in am.getDevices(AudioManager.GET_DEVICES_OUTPUTS or AudioManager.GET_DEVICES_INPUTS)) {
                val t = d.type
                if (t == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || t == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                    t == AudioDeviceInfo.TYPE_HEARING_AID || t == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                    t == AudioDeviceInfo.TYPE_BLE_SPEAKER || t == AudioDeviceInfo.TYPE_BLE_BROADCAST
                ) return true
            }
        } catch (ignored: Throwable) {
        }
        val perm = if (Build.VERSION.SDK_INT >= 31) Manifest.permission.BLUETOOTH_CONNECT else Manifest.permission.BLUETOOTH
        if (c.checkSelfPermission(perm) != PackageManager.PERMISSION_GRANTED) return true
        try {
            val bm = c.getSystemService(BluetoothManager::class.java)
            val a = bm.adapter ?: return true
            try {
                val s = BluetoothAdapter::class.java.getMethod("getConnectionState").invoke(a)
                if (s is Int && s == BluetoothAdapter.STATE_CONNECTED) return true
            } catch (ignored: Throwable) {
            }
            // A2DP, headset, HID host, PAN, HID device, hearing aid, LE audio
            for (p in intArrayOf(2, 1, 4, 5, 19, 21, 22))
                if (a.getProfileConnectionState(p) != BluetoothAdapter.STATE_DISCONNECTED) return true
            if (bm.getConnectedDevices(BluetoothProfile.GATT).isNotEmpty()) return true
            val bonded: Set<BluetoothDevice>? = a.bondedDevices
            if (bonded != null) for (d in bonded) {
                try {
                    if (BluetoothDevice::class.java.getMethod("isConnected").invoke(d) as Boolean) return true
                } catch (ignored: Throwable) {
                }
            }
            return false
        } catch (e: Throwable) {
            return true
        }
    }

    private fun apOn(c: Context): Boolean? {
        val w = c.getSystemService(WifiManager::class.java)
        try {
            return WifiManager::class.java.getMethod("isWifiApEnabled").invoke(w) as Boolean
        } catch (ignored: Throwable) {
        }
        try {
            val s = WifiManager::class.java.getMethod("getWifiApState").invoke(w) as Int
            return s == AP_ENABLED || s == AP_ENABLING
        } catch (ignored: Throwable) {
        }
        try {
            val i = c.registerReceiver(null, IntentFilter("android.net.wifi.WIFI_AP_STATE_CHANGED"))
            if (i != null && i.hasExtra("wifi_state")) {
                val s = i.getIntExtra("wifi_state", 0)
                return s == AP_ENABLED || s == AP_ENABLING
            }
        } catch (ignored: Throwable) {
        }
        return null
    }

    /** Name of an up soft-AP interface that has an IPv4 address, or null. */
    private fun apIface(): String? {
        try {
            for (ni in NetworkInterface.getNetworkInterfaces().toList()) {
                val n = ni.name
                if (!ni.isUp || !n.matches(AP_NAME)) continue
                for (a in ni.inetAddresses.toList()) if (a is Inet4Address) return n
            }
        } catch (ignored: Throwable) {
        }
        return null
    }

    /** Clients in the ARP table on that interface, -1 if unreadable (blocked for apps since Android 10). */
    private fun arpClients(iface: String): Int =
        try {
            BufferedReader(FileReader("/proc/net/arp")).use { r ->
                var n = 0
                r.readLine()
                while (true) {
                    val l = r.readLine() ?: break
                    val f = l.trim().split(Regex("\\s+"))
                    if (f.size >= 6 && f[5] == iface && f[2] != "0x0") n++
                }
                n
            }
        } catch (e: Throwable) {
            -1
        }

    // ---- helpers ----

    private fun sysInt(c: Context, k: String, def: Int): Int =
        try {
            Settings.System.getInt(c.contentResolver, k, def)
        } catch (e: Throwable) {
            def
        }

    private fun globalInt(c: Context, k: String, def: Int): Int =
        try {
            Settings.Global.getInt(c.contentResolver, k, def)
        } catch (e: Throwable) {
            def // hidden keys may throw for apps targeting Android 12+
        }

    private fun cmp(pkg: String, cls: String): Intent = Intent().setComponent(ComponentName(pkg, cls))

    private fun display(c: Context): Intent? = pick(c, Intent(Settings.ACTION_DISPLAY_SETTINGS), null)

    private fun scanning(c: Context): Intent? =
        pick(c, Intent("android.settings.LOCATION_SCANNING_SETTINGS"), Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))

    /** First intent that resolves; the last one is returned anyway (public action) if none can be checked. Null-safe. */
    private fun pick(c: Context, vararg intents: Intent?): Intent? {
        val pm = c.packageManager
        var last: Intent? = null
        for (i in intents) {
            if (i == null) continue
            last = i
            try {
                if (pm.resolveActivity(i, PackageManager.MATCH_DEFAULT_ONLY) != null)
                    return i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            } catch (ignored: Throwable) {
            }
        }
        return last?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
