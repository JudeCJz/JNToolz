package com.jntoolz.tools

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.jntoolz.counter.CounterStore
import com.jntoolz.counter.CounterWidgets
import com.jntoolz.data.WidgetPreferences
import com.jntoolz.sot.SotTool
import com.jntoolz.sot.Store
import com.jntoolz.sot.TrackerService
import com.jntoolz.sot.widget.BatteryWidgets
import com.jntoolz.unscroll.UnscrollStore
import com.jntoolz.widget.WidgetUpdater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * The hub's tools. Each owns a fixed set of manifest components ([components], full class names) that are
 * disabled with PackageManager while the tool is off, so Android never starts the app for it.
 * Shared components (MainActivity, the update FileProvider) belong to no tool and are never touched.
 */
enum class Tool(val id: String, val components: List<String>) {
    CALENDAR(
        "calendar",
        listOf(
            "com.jntoolz.widget.NothingCalendarWidgetProvider",
            "com.jntoolz.widget.NothingWidgetService",
            "com.jntoolz.widget.CalendarWidgetConfigureActivity",
        )
    ),
    BATTERY(
        "sot",
        listOf(
            "com.jntoolz.sot.TrackerService",
            "com.jntoolz.sot.BootReceiver",
            "com.jntoolz.sot.widget.BatteryWidgetProvider",
            "com.jntoolz.sot.widget.BatteryWidgetConfigureActivity",
        )
    ),
    COUNTER(
        "counter",
        listOf(
            "com.jntoolz.counter.CounterWidgetProvider",
            "com.jntoolz.counter.CounterConfigureActivity",
        )
    ),
    UNSCROLL(
        "unscroll",
        listOf("com.jntoolz.unscroll.UnscrollService")
    );

    companion object {
        /** The tool named by MainActivity's "open_tool" extra, or null. */
        fun of(id: String?): Tool? = entries.firstOrNull { it.id == id }
    }
}

/** One on/off switch per tool. Off disables the tool's components and stops its work; on reverses that. */
object Tools {
    private const val TAG = "Tools"

    /** Every switch change and the start-up reconcile run here, one at a time and in order. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val serial = Dispatchers.IO.limitedParallelism(1)

    /** Not tied to a screen, so leaving the hub right after a tap cannot cancel the change half-way. */
    private val scope = CoroutineScope(SupervisorJob() + serial)

    /** The stored flag. Existing keys are kept: calendar and counter default on, battery default off. */
    fun isEnabled(c: Context, tool: Tool): Boolean = when (tool) {
        Tool.CALENDAR -> WidgetPreferences(c).isCalendarToolEnabled
        Tool.BATTERY -> SotTool.isEnabled(c)
        Tool.COUNTER -> CounterStore(c).enabled
        Tool.UNSCROLL -> UnscrollStore(c).enabled
    }

    /**
     * The hub switch. The flag is saved right away (so a screen reading it next sees the new value); the
     * components and the tool's work change in the background. Safe to call on the main thread.
     */
    fun setEnabled(c: Context, tool: Tool, on: Boolean) {
        val app = c.applicationContext
        // Hard mode is only left through the typed unlock on the Unscroll hub card.
        if (!on && tool == Tool.UNSCROLL && UnscrollStore(app).hard) return
        saveFlag(app, tool, on)
        scope.launch {
            if (on) {
                applyComponents(app, tool, true)
                safe(tool) { startWork(app, tool) }
            } else {
                safe(tool) { stopWork(app, tool) }
                applyComponents(app, tool, false)
            }
        }
    }

    /** App start: makes every tool's component state match its flag, and makes sure an off tool is idle. */
    fun reconcile(c: Context) {
        val app = c.applicationContext
        scope.launch {
            for (tool in Tool.entries) {
                val on = isEnabled(app, tool)
                if (!on) safe(tool) { stopWork(app, tool) }
                applyComponents(app, tool, on)
            }
        }
    }

    private fun saveFlag(c: Context, tool: Tool, on: Boolean) {
        when (tool) {
            Tool.CALENDAR -> WidgetPreferences(c).isCalendarToolEnabled = on
            Tool.BATTERY -> SotTool.saveEnabled(c, on)
            Tool.COUNTER -> CounterStore(c).enabled = on
            Tool.UNSCROLL -> UnscrollStore(c).enabled = on
        }
    }

    private fun startWork(c: Context, tool: Tool) {
        when (tool) {
            Tool.CALENDAR -> WidgetUpdater.updateAllWidgets(c)
            Tool.BATTERY -> TrackerService.start(c)
            Tool.COUNTER -> CounterWidgets.updateAll(c) // redraws and reschedules the midnight alarm
            Tool.UNSCROLL -> Unit // the accessibility service reads the flag itself
        }
    }

    /**
     * Stops what runs for the tool and forgets the configs of its placed widgets (disabling a widget provider
     * makes Android drop them, without an onDeleted call).
     */
    private fun stopWork(c: Context, tool: Tool) {
        when (tool) {
            Tool.CALENDAR -> Unit // no alarms or jobs: it only reacts to broadcasts and widget binds
            Tool.BATTERY -> {
                TrackerService.stop(c)
                c.getSystemService(NotificationManager::class.java)?.cancel(TrackerService.ID)
                val ids = BatteryWidgets.ids(c)
                if (ids.isNotEmpty()) BatteryWidgets.remove(c, ids)
                Store.release()
            }
            Tool.COUNTER -> {
                CounterWidgets.cancel(c)
                val ids = CounterWidgets.ids(c)
                if (ids.isNotEmpty()) CounterStore(c).removeWidgets(ids)
            }
            Tool.UNSCROLL -> Unit // the service ignores events while the flag is off
        }
    }

    /** Sets each of the tool's components that is not already in the wanted state. */
    private fun applyComponents(c: Context, tool: Tool, on: Boolean) {
        val pm = c.packageManager
        val want = if (on) PackageManager.COMPONENT_ENABLED_STATE_DEFAULT else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        val changes = tool.components.map { ComponentName(c.packageName, it) }.filter { cn ->
            val now = try {
                pm.getComponentEnabledSetting(cn)
            } catch (e: IllegalArgumentException) {
                return@filter false // not in this build
            }
            val isOn = now != PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            isOn != on
        }
        if (changes.isEmpty()) return
        try {
            if (Build.VERSION.SDK_INT >= 33) {
                // One package-changed broadcast for the whole tool.
                pm.setComponentEnabledSettings(
                    changes.map { PackageManager.ComponentEnabledSetting(it, want, PackageManager.DONT_KILL_APP) }
                )
            } else {
                for (cn in changes) pm.setComponentEnabledSetting(cn, want, PackageManager.DONT_KILL_APP)
            }
        } catch (e: RuntimeException) {
            Log.w(TAG, "components of $tool not changed: $e")
        }
    }

    private inline fun safe(tool: Tool, block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            Log.w(TAG, "$tool work change failed: $e")
        }
    }
}
