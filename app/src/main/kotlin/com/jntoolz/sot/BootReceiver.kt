package com.jntoolz.sot

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Restarts the tracker after a reboot or an app update, only when the tool is on. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        if (SotTool.isEnabled(c)) TrackerService.start(c)
    }
}
