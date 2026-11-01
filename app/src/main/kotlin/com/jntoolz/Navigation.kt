package com.jntoolz

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.jntoolz.tools.Tool
import com.jntoolz.tools.Tools
import com.jntoolz.ui.counter.CounterScreen
import com.jntoolz.ui.hub.HubScreen
import com.jntoolz.ui.main.MainScreen
import com.jntoolz.ui.sot.SotScreen

enum class AppDestination {
    HUB,
    CALENDAR,
    SOT,
    COUNTER
}

/** [openTool] is a tool asked for by the launch intent ("sot", "counter"); [onToolOpened] clears it once shown. */
@Composable
fun MainNavigation(openTool: String? = null, onToolOpened: () -> Unit = {}) {
    val context = LocalContext.current
    // A switched-off tool never opens: the request is dropped and the hub stays.
    fun open(tool: Tool?): AppDestination? = tool?.takeIf { Tools.isEnabled(context, it) }?.destination
    var destination by remember { mutableStateOf(open(Tool.of(openTool)) ?: AppDestination.HUB) }

    LaunchedEffect(openTool) {
        open(Tool.of(openTool))?.let { destination = it }
        if (openTool != null) onToolOpened()
    }

    when (destination) {
        AppDestination.HUB -> {
            HubScreen(
                onOpenCalendar = { open(Tool.CALENDAR)?.let { destination = it } },
                onOpenSot = { open(Tool.BATTERY)?.let { destination = it } },
                onOpenCounter = { open(Tool.COUNTER)?.let { destination = it } }
            )
        }
        AppDestination.CALENDAR -> {
            BackHandler {
                destination = AppDestination.HUB
            }
            MainScreen(
                onBack = { destination = AppDestination.HUB }
            )
        }
        AppDestination.SOT -> {
            // SotScreen handles back itself: other tabs go to Now first.
            SotScreen(onBack = { destination = AppDestination.HUB })
        }
        AppDestination.COUNTER -> {
            // CounterScreen handles back itself.
            CounterScreen(onBack = { destination = AppDestination.HUB })
        }
    }
}

private val Tool.destination: AppDestination
    get() = when (this) {
        Tool.CALENDAR -> AppDestination.CALENDAR
        Tool.BATTERY -> AppDestination.SOT
        Tool.COUNTER -> AppDestination.COUNTER
    }
