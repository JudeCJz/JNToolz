package com.jntoolz.unscroll

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings

/**
 * Switches the Unscroll accessibility service on and off in system settings, which needs the
 * WRITE_SECURE_SETTINGS permission (granted once over adb). Only our own entry in the list is touched.
 */
object A11yToggle {
    fun canWrite(c: Context): Boolean =
        c.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED

    /** Puts the service in the wanted state; does nothing when it already is, or without the permission. */
    fun set(c: Context, on: Boolean) {
        if (!canWrite(c)) return
        try {
            val cr = c.contentResolver
            val flat = ComponentName(c.packageName, UnscrollStore.SERVICE_CLASS).flattenToString()
            val old = Settings.Secure.getString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            val new = edit(old, flat, on)
            if (new == old.orEmpty()) return
            Settings.Secure.putString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, new)
            if (on) Settings.Secure.putInt(cr, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
        } catch (e: SecurityException) {
            // Permission revoked meanwhile.
        }
    }

    /** The colon separated [list] with [flat] present or absent, every other entry kept in order. */
    fun edit(list: String?, flat: String, on: Boolean): String {
        val items = list.orEmpty().split(':').filter { it.isNotEmpty() }
        val kept = items.filterNot { same(it, flat) }
        return if (on) (if (kept.size < items.size) items else kept + flat).joinToString(":")
        else kept.joinToString(":")
    }

    /** Whether two flattened component names are the same one ("pkg/.Cls" equals "pkg/pkg.Cls"), ignoring case. */
    private fun same(a: String, b: String): Boolean = full(a).equals(full(b), ignoreCase = true)

    private fun full(s: String): String {
        val pkg = s.substringBefore('/')
        val cls = s.substringAfter('/', "")
        return if (cls.startsWith('.')) "$pkg/$pkg$cls" else s
    }
}
