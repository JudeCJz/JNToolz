package com.jntoolz.ui.main

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jntoolz.data.CalendarEvent
import com.jntoolz.data.CalendarInfo
import com.jntoolz.data.CalendarRepository
import com.jntoolz.data.WidgetPreferences
import com.jntoolz.widget.WidgetUpdater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class MainUiState(
    val hasPermission: Boolean = false,
    val calendars: List<CalendarInfo> = emptyList(),
    val enabledCalendarIds: Set<String> = emptySet(),
    val events: List<CalendarEvent> = emptyList(),
    val isLoading: Boolean = false,
    val isSyncing: Boolean = false
)

class MainScreenViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = CalendarRepository(application)
    private val prefs = WidgetPreferences(application)

    private val _uiState = MutableStateFlow(
        MainUiState(
            hasPermission = repository.hasCalendarPermission()
        )
    )
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            val hasPerm = repository.hasCalendarPermission()
            val calList = if (hasPerm) repository.getAvailableCalendars() else emptyList()
            val eventsList = if (hasPerm) repository.getUpcomingEvents(daysAhead = 180) else emptyList()

            val enabledIds = if (!prefs.hasCustomizedCalendars) {
                calList.filter { it.isVisibleInGoogle }.map { it.id.toString() }.toSet()
            } else {
                prefs.getEnabledCalendarIds()
            }

            _uiState.value = _uiState.value.copy(
                hasPermission = hasPerm,
                calendars = calList,
                enabledCalendarIds = enabledIds,
                events = eventsList,
                isLoading = false,
                isSyncing = false
            )
            WidgetUpdater.updateAllWidgets(getApplication())
        }
    }

    fun toggleCalendar(calendar: CalendarInfo, isChecked: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            prefs.setCalendarEnabled(calendar.id, isChecked, _uiState.value.calendars)
            refresh()
        }
    }

    fun syncGoogleAccounts() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(isSyncing = true)
            repository.requestGoogleCalendarSync()
            kotlinx.coroutines.delay(1000)
            refresh()
        }
    }
}
