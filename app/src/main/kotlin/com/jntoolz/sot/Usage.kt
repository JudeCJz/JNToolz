package com.jntoolz.sot

import android.Manifest
import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process

/** Screen on/off history kept by Android itself (needs Usage access). Exact even when the app was not running. */
object Usage {
    private const val SCREEN_INTERACTIVE = 15
    private const val SCREEN_NON_INTERACTIVE = 16

    /** How far before the last query's end the next one starts, for events logged a little late. */
    const val OVERLAP = 60_000L

    /**
     * Where an incremental query should start so that no cached interval straddles it: [since], or earlier,
     * the start of the newest intervals that are still open or end at/after that point. Intervals are oldest first.
     */
    fun resumeAt(old: List<LongArray>, since: Long): Long {
        var q = since
        for (i in old.indices.reversed()) {
            val iv = old[i]
            if (iv[1] >= 0 && iv[1] < q) break
            q = minOf(q, iv[0])
        }
        return q
    }

    /** The cached intervals that ended before [from], then [fresh] (a query from [from], as [screen] returns it). */
    fun merge(old: List<LongArray>, fresh: List<LongArray>, from: Long): List<LongArray> {
        val out = ArrayList<LongArray>(old.size + fresh.size)
        for (iv in old) if (iv[1] in 0 until from) out.add(iv)
        out.addAll(fresh)
        return out
    }

    @Suppress("DEPRECATION")
    fun allowed(c: Context): Boolean {
        if (Build.VERSION.SDK_INT < 28) return false // screen events were added in Android 9
        val ao = c.getSystemService(AppOpsManager::class.java)
        val mode = if (Build.VERSION.SDK_INT >= 29)
            ao.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), c.packageName)
        else
            ao.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), c.packageName)
        if (mode == AppOpsManager.MODE_DEFAULT)
            return c.checkSelfPermission(Manifest.permission.PACKAGE_USAGE_STATS) == PackageManager.PERMISSION_GRANTED
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /** Intervals {on, off}, off = -1 while still on. Null if the system refused. */
    fun screen(c: Context, from: Long, to: Long): List<LongArray>? {
        try {
            val um = c.getSystemService(UsageStatsManager::class.java)
            val ev = um.queryEvents(from, to)
            val e = UsageEvents.Event()
            val out = ArrayList<LongArray>()
            var onAt = -1L
            var first = true
            while (ev.hasNextEvent()) {
                ev.getNextEvent(e)
                val type = e.eventType
                if (type == SCREEN_INTERACTIVE) {
                    if (onAt < 0) onAt = e.timeStamp
                } else if (type == SCREEN_NON_INTERACTIVE) {
                    if (onAt >= 0) out.add(longArrayOf(onAt, e.timeStamp))
                    else if (first) out.add(longArrayOf(from, e.timeStamp)) // was already on at `from`
                    onAt = -1
                } else {
                    continue
                }
                first = false
            }
            if (onAt >= 0) out.add(longArrayOf(onAt, -1))
            return out
        } catch (e: RuntimeException) {
            return null
        }
    }
}
