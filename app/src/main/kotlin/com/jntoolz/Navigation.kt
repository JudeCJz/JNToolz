package com.jntoolz

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.jntoolz.ui.hub.HubScreen
import com.jntoolz.ui.main.MainScreen

enum class AppDestination {
    HUB,
    CALENDAR
}

@Composable
fun MainNavigation() {
    var destination by remember { mutableStateOf(AppDestination.HUB) }

    when (destination) {
        AppDestination.HUB -> {
            HubScreen(
                onOpenCalendar = { destination = AppDestination.CALENDAR }
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
    }
}
