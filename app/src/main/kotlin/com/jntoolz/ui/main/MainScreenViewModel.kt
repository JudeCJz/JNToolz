package com.jntoolz.ui.main

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jntoolz.data.CalendarEvent
import com.jntoolz.data.CalendarInfo
import com.jntoolz.data.CalendarRepository
import com.jntoolz.data.GoogleCalendarApi
import com.jntoolz.data.GoogleColorCache
import com.jntoolz.data.ThemeSettings
import com.jntoolz.data.WidgetPreferences
import com.jntoolz.widget.WidgetUpdater
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MainUiState(
    val hasPermission: Boolean = false,
    val calendars: List<CalendarInfo> = emptyList(),
    val enabledCalendarIds: Set<String> = emptySet(),
    val events: List<CalendarEvent> = emptyList(),
    val isSyncing: Boolean = false,
    val linkedAccounts: Set<String> = emptySet()
)

class MainScreenViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = CalendarRepository(application)
    private val prefs = WidgetPreferences(application)
    private val gCache = GoogleColorCache.get(application)

    private val _uiState = MutableStateFlow(MainUiState(hasPermission = repository.hasCalendarPermission()))
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    /** Single source of truth for the theme; prefs are written through [updateTheme]. */
    private val _theme = MutableStateFlow(ThemeSettings.from(prefs))
    val theme: StateFlow<ThemeSettings> = _theme.asStateFlow()

    private var refreshJob: Job? = null

    init {
        refresh()
        viewModelScope.launch(Dispatchers.IO) {
            if (gCache.linkedAccounts().isNotEmpty() && GoogleCalendarApi.isStale(getApplication())) {
                refreshGoogleNow()
                load()
            }
        }
    }

    /** Applies [change] to the theme, persists it and pushes it to the widgets. */
    fun updateTheme(change: (ThemeSettings) -> ThemeSettings) {
        val old = _theme.value
        val new = change(old)
        if (new == old) return
        _theme.value = new
        viewModelScope.launch(Dispatchers.IO) {
            if (new.useGoogleColors != old.useGoogleColors) prefs.useGoogleEventColors = new.useGoogleColors
            if (new.markerStyle != old.markerStyle) prefs.markerStyle = new.markerStyle
            if (new.dim != old.dim) prefs.isDimEnabled = new.dim
            if (new.fade != old.fade) prefs.isFadeEnabled = new.fade
            if (new.cardColor != old.cardColor) prefs.customCardColor = new.cardColor.toLong() and 0xFFFFFFFFL
            if (new.gradientStyle != old.gradientStyle) prefs.cardGradientStyle = new.gradientStyle
            if (new.markerColor != old.markerColor) prefs.markerColor = new.markerColor.toLong() and 0xFFFFFFFFL
            if (new.gradient != old.gradient) prefs.isGradientEnabled = new.gradient
            WidgetUpdater.updateAllWidgets(getApplication())
        }
    }

    private suspend fun refreshGoogleNow() {
        try {
            GoogleCalendarApi.refreshAll(getApplication())
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        }
    }

    fun setLinked(account: String, linked: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            gCache.setLinked(account, linked)
            if (linked) refreshGoogleNow() else gCache.clearAccount(account)
            load()
        }
    }

    fun hasColorOverride(event: CalendarEvent) = gCache.hasColorOverride(event)

    fun setEventColor(event: CalendarEvent, argb: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            if (event.labelId != null) gCache.setLabelColor(event.labelId, argb)
            else gCache.setEventOverride(event, argb)
            load()
        }
    }

    fun resetEventColor(event: CalendarEvent) {
        viewModelScope.launch(Dispatchers.IO) {
            gCache.clearEventOverride(event)
            if (event.labelId != null) gCache.removeLabelColor(event.labelId)
            load()
        }
    }

    /** Reloads calendars and events (latest call wins). */
    fun refresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch(Dispatchers.IO) { load() }
    }

    private suspend fun load() {
        val hasPerm = repository.hasCalendarPermission()
        val calList = if (hasPerm) repository.getAvailableCalendars() else emptyList()
        val eventsList = if (hasPerm) repository.getUpcomingEvents(daysAhead = 180) else emptyList()
        val enabledIds = if (!prefs.hasCustomizedCalendars) {
            calList.filter { it.isVisibleInGoogle }.map { it.id.toString() }.toSet()
        } else {
            prefs.getEnabledCalendarIds()
        }
        val linked = gCache.linkedAccounts()
        _uiState.update {
            it.copy(
                hasPermission = hasPerm,
                calendars = calList,
                enabledCalendarIds = enabledIds,
                events = eventsList,
                linkedAccounts = linked
            )
        }
        WidgetUpdater.updateAllWidgets(getApplication())
    }

    fun toggleCalendar(calendar: CalendarInfo, isChecked: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            prefs.setCalendarEnabled(calendar.id, isChecked, _uiState.value.calendars)
            load()
        }
    }

    fun syncGoogleAccounts() {
        if (_uiState.value.isSyncing) return
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isSyncing = true) }
            try {
                repository.requestGoogleCalendarSync()
                delay(1000)
                refreshGoogleNow()
                load()
            } finally {
                _uiState.update { it.copy(isSyncing = false) }
            }
        }
    }
}
