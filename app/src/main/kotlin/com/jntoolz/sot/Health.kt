package com.jntoolz.sot

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import java.io.BufferedReader
import java.io.FileReader

/** Battery capacity and health: what the system reports plus what we learn from the charge counter. */
object Health {
    private var design = -2.0

    /** Design capacity in mAh from the device's power profile (or the kernel), -1 if unknown. */
    @Synchronized
    fun designMah(c: Context): Double {
        if (design != -2.0) return design
        design = -1.0
        try {
            val pp = Class.forName("com.android.internal.os.PowerProfile")
            val p = pp.getConstructor(Context::class.java).newInstance(c)
            val v = pp.getMethod("getBatteryCapacity").invoke(p) as Double
            if (v > 500) design = v
        } catch (ignored: Throwable) {
        }
        if (design < 0) {
            val v = sys("charge_full_design")
            if (v > 0) design = if (v > 100_000) v / 1000.0 else v.toDouble()
        }
        return design
    }

    /** Full-charge capacity the kernel reports in mAh, -1 when the file is not readable (most phones). */
    fun kernelFullMah(): Double {
        val v = sys("charge_full")
        return if (v > 0) (if (v > 100_000) v / 1000.0 else v.toDouble()) else -1.0
    }

    fun kernelCycles(): Long = sys("cycle_count")

    private fun sys(name: String): Long =
        try {
            BufferedReader(FileReader("/sys/class/power_supply/battery/$name")).use { it.readLine()!!.trim().toLong() }
        } catch (e: Throwable) {
            -1
        }

    /** Charge counter in mAh, -1 if the phone does not report it. */
    fun chargeMah(bm: BatteryManager): Double =
        ccMah(bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER))

    fun ccMah(raw: Int): Double {
        if (raw <= 0 || raw == Int.MIN_VALUE) return -1.0
        return if (raw > 100_000) raw / 1000.0 else raw.toDouble() // most phones report uAh, a few mAh
    }

    /** Current in mA, positive = into the battery. NaN if not reported. */
    fun currentMa(bm: BatteryManager, charging: Boolean): Double {
        val raw = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        if (raw == Int.MIN_VALUE || raw == 0) return Double.NaN
        val ma = if (Math.abs(raw) > 20_000) raw / 1000.0 else raw.toDouble() // uA on most phones, mA on some
        // The sign convention differs between makers, so trust the charging state for direction.
        return if (charging) Math.abs(ma) else -Math.abs(ma)
    }

    /**
     * Full capacity learned from how many mAh the counter moved per 1% over long stretches
     * (20%+ of one charge or discharge). Median of the latest five. -1 if not enough data.
     */
    fun learnedMah(b: List<Store.B>): Double {
        val est = ArrayList<Double>()
        var i = 0
        val n = b.size
        while (i < n) {
            val plugged = b[i].plug != 0
            var j = i
            while (j + 1 < n && (b[j + 1].plug != 0) == plugged) j++
            var first: Store.B? = null
            var last: Store.B? = null
            for (k in i..j) {
                if (ccMah(b[k].cc) > 0) {
                    if (first == null) first = b[k]
                    last = b[k]
                }
            }
            if (first != null && last != null) {
                val dl = Math.abs(last.level - first.level)
                val dc = Math.abs(ccMah(last.cc) - ccMah(first.cc))
                if (dl >= 20 && dc > 0) est.add(dc / dl * 100)
            }
            i = j + 1
        }
        if (est.isEmpty()) return -1.0
        val recent = ArrayList(est.subList(maxOf(0, est.size - 5), est.size))
        recent.sort()
        return recent[recent.size / 2]
    }

    /**
     * Capacity compared to new as text ("92%"), "–" when unknown: the kernel's full charge, else what was
     * learned from the charge counter, else a rough guess from the counter now.
     */
    fun percentText(c: Context, bat: List<Store.B>, battery: Intent? = sticky(c)): String {
        val i: Intent = battery ?: return "–"
        val lv = level(i)
        val design = designMah(c)
        var cap = kernelFullMah()
        if (cap <= 0) cap = learnedMah(bat)
        if (cap <= 0) {
            val now = chargeMah(c.getSystemService(BatteryManager::class.java))
            if (now > 0 && lv >= 20) cap = now / lv * 100
        }
        return if (cap > 0 && design > 0) String.format(java.util.Locale.US, "%.0f%%", minOf(100.0, cap / design * 100)) else "–"
    }

    /** The last BATTERY_CHANGED broadcast (sticky), null if the system has none. */
    fun sticky(c: Context): Intent? = c.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))

    /** Level in percent from a BATTERY_CHANGED intent, -1 when it has none. */
    fun level(i: Intent): Int {
        val raw = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        if (raw < 0) return -1
        val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        return if (scale > 0) Math.round(raw * 100f / scale) else raw
    }
}
