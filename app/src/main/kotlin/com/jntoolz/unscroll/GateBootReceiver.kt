package com.jntoolz.unscroll

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Restarts the foreground watcher after a reboot or an app update, when it is wanted. */
class GateBootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        ForegroundWatcher.start(c)
    }
}
