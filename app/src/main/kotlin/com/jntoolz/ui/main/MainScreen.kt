package com.jntoolz.ui.main

import android.Manifest
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jntoolz.data.CalendarEvent
import com.jntoolz.data.CalendarInfo
import com.jntoolz.data.WidgetPreferences
import com.jntoolz.widget.NothingCalendarWidgetProvider
import com.jntoolz.widget.WidgetUpdater

private val BgScreen = Color(0xFF000000)
private val CardBg = Color(0xFF141414)
private val TextWhite = Color(0xFFFFFFFF)
private val MutedGrey = Color(0xFF8E8E8E)
private val DividerGrey = Color(0xFF222222)
private val AccentRed = Color(0xFFFF2E2E)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainScreenViewModel = viewModel(),
    onBack: (() -> Unit)? = null
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val prefs = remember { WidgetPreferences(context) }

    // Theme states
    var useGoogleColors by remember { mutableStateOf(prefs.useGoogleEventColors) }
    var googleColorMode by remember { mutableStateOf(prefs.googleColorDisplayMode) }
    var isFadeEnabled by remember { mutableStateOf(prefs.isFadeEnabled) }
    var widgetBgColor by remember { mutableStateOf(prefs.widgetBackgroundColor) }
    var customCardColor by remember { mutableStateOf(prefs.customCardColor) }
    var cardGradientStyle by remember { mutableStateOf(prefs.cardGradientStyle) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        viewModel.refresh()
        if (isGranted) {
            Toast.makeText(context, "Calendar access granted!", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Permission needed to display your events", Toast.LENGTH_LONG).show()
        }
    }

    val todayEvents = remember(uiState.events) { uiState.events.filter { it.isToday() } }
    val upcomingEvents = remember(uiState.events) { uiState.events.filter { !it.isToday() } }

    Scaffold(
        containerColor = BgScreen,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Text(
                                text = "←",
                                color = TextWhite,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                },
                title = {
                    Text(
                        text = "CALENDAR",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 2.sp,
                        color = TextWhite
                    )
                },
                actions = {
                    Text(
                        text = "REFRESH",
                        color = AccentRed,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp,
                        modifier = Modifier
                            .clickable { viewModel.refresh() }
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BgScreen)
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Permission Card (if not granted)
            if (!uiState.hasPermission) {
                item {
                    PermissionCard(onRequest = { permissionLauncher.launch(Manifest.permission.READ_CALENDAR) })
                }
            }

            // Widget Quick Pin Button
            item {
                Button(
                    onClick = {
                        requestPinWidget(context, NothingCalendarWidgetProvider::class.java)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = TextWhite,
                        contentColor = BgScreen
                    )
                ) {
                    Text(
                        text = "+ ADD SCROLLABLE WIDGET",
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            }

            // The Authentic Nothing OS Widget Preview Card
            item {
                NothingWidgetPreviewCard(
                    todayEvents = todayEvents,
                    upcomingEvents = upcomingEvents,
                    useGoogleColors = useGoogleColors,
                    googleColorMode = googleColorMode,
                    isFadeEnabled = isFadeEnabled,
                    widgetBgColor = Color(widgetBgColor),
                    customCardColor = Color(customCardColor),
                    cardGradientStyle = cardGradientStyle
                )
            }

            // ================= THEME & STYLING =================
            item {
                Text(
                    text = "THEME & STYLING",
                    color = MutedGrey,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.5.sp,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                )
            }

            // Google Calendar Colors Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = CardBg),
                    border = BorderStroke(1.dp, Color(0xFF222222))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Google Calendar Colors",
                                    color = TextWhite,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = "Port event colors from Google Calendar",
                                    color = MutedGrey,
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                            Switch(
                                checked = useGoogleColors,
                                onCheckedChange = { checked ->
                                    useGoogleColors = checked
                                    prefs.useGoogleEventColors = checked
                                    WidgetUpdater.updateAllWidgets(context)
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = TextWhite,
                                    checkedTrackColor = AccentRed,
                                    uncheckedThumbColor = MutedGrey,
                                    uncheckedTrackColor = Color(0xFF222222)
                                )
                            )
                        }

                        if (useGoogleColors) {
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = "COLOR DISPLAY TARGET:",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MutedGrey,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                val isDot = googleColorMode == "DOT"
                                OutlinedButton(
                                    onClick = {
                                        googleColorMode = "DOT"
                                        prefs.googleColorDisplayMode = "DOT"
                                        WidgetUpdater.updateAllWidgets(context)
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = if (isDot) Color(0xFF262626) else Color.Transparent,
                                        contentColor = if (isDot) TextWhite else MutedGrey
                                    ),
                                    border = BorderStroke(1.dp, if (isDot) AccentRed else Color(0xFF333333)),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = "DOTS ONLY",
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                val isBox = googleColorMode == "BOX"
                                OutlinedButton(
                                    onClick = {
                                        googleColorMode = "BOX"
                                        prefs.googleColorDisplayMode = "BOX"
                                        WidgetUpdater.updateAllWidgets(context)
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = if (isBox) Color(0xFF262626) else Color.Transparent,
                                        contentColor = if (isBox) TextWhite else MutedGrey
                                    ),
                                    border = BorderStroke(1.dp, if (isBox) AccentRed else Color(0xFF333333)),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = "ENTIRE CARD",
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Distance Fade & Background Color Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = CardBg),
                    border = BorderStroke(1.dp, Color(0xFF222222))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        // Distance Fade Toggle
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Event Distance Fade",
                                    color = TextWhite,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = "Gradually fade distant cards into background",
                                    color = MutedGrey,
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                            Switch(
                                checked = isFadeEnabled,
                                onCheckedChange = { checked ->
                                    isFadeEnabled = checked
                                    prefs.isFadeEnabled = checked
                                    WidgetUpdater.updateAllWidgets(context)
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = TextWhite,
                                    checkedTrackColor = AccentRed,
                                    uncheckedThumbColor = MutedGrey,
                                    uncheckedTrackColor = Color(0xFF222222)
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        HorizontalDivider(color = Color(0xFF222222), thickness = 1.dp)
                        Spacer(modifier = Modifier.height(14.dp))

                        // Background Color Choice
                        Text(
                            text = "WIDGET BACKGROUND:",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MutedGrey,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            val bgPresets = listOf(
                                "AMOLED Black" to 0xFF000000L,
                                "Dark Slate" to 0xFF121214L,
                                "Deep Navy" to 0xFF090E17L,
                                "Dark Ruby" to 0xFF180A0EL
                            )
                            bgPresets.forEach { (name, colorVal) ->
                                val selected = widgetBgColor == colorVal
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            widgetBgColor = colorVal
                                            prefs.widgetBackgroundColor = colorVal
                                            WidgetUpdater.updateAllWidgets(context)
                                        }
                                        .padding(4.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(Color(colorVal))
                                            .border(
                                                width = if (selected) 2.dp else 1.dp,
                                                color = if (selected) AccentRed else Color(0xFF444444),
                                                shape = CircleShape
                                            )
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = name,
                                        fontSize = 9.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = if (selected) TextWhite else MutedGrey
                                    )
                                }
                            }
                        }

                        // Card Color / Gradient (when Google Colors are OFF)
                        if (!useGoogleColors) {
                            Spacer(modifier = Modifier.height(14.dp))
                            HorizontalDivider(color = Color(0xFF222222), thickness = 1.dp)
                            Spacer(modifier = Modifier.height(14.dp))

                            Text(
                                text = "SOLID CARD COLOR:",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MutedGrey,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                val cardPresets = listOf(
                                    "Slate" to 0xFF202121L,
                                    "Crimson" to 0xFF2B1414L,
                                    "Cobalt" to 0xFF0F1E28L,
                                    "Forest" to 0xFF17241AL
                                )
                                cardPresets.forEach { (name, colorVal) ->
                                    val isSelected = cardGradientStyle == "NONE" && customCardColor == colorVal
                                    OutlinedButton(
                                        onClick = {
                                            cardGradientStyle = "NONE"
                                            prefs.cardGradientStyle = "NONE"
                                            customCardColor = colorVal
                                            prefs.customCardColor = colorVal
                                            WidgetUpdater.updateAllWidgets(context)
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            containerColor = if (isSelected) Color(0xFF282828) else Color(0xFF141414),
                                            contentColor = if (isSelected) TextWhite else MutedGrey
                                        ),
                                        border = BorderStroke(1.dp, if (isSelected) AccentRed else Color(0xFF333333)),
                                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(10.dp)
                                                .clip(CircleShape)
                                                .background(Color(colorVal))
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(text = name, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "OR GRADIENT PRESETS:",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MutedGrey,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                val gradients = listOf(
                                    "Charcoal" to "CHARCOAL",
                                    "Ruby" to "RUBY",
                                    "Cyber" to "CYBER",
                                    "Amber" to "AMBER"
                                )
                                gradients.forEach { (label, key) ->
                                    val isSelected = cardGradientStyle == key
                                    OutlinedButton(
                                        onClick = {
                                            cardGradientStyle = key
                                            prefs.cardGradientStyle = key
                                            WidgetUpdater.updateAllWidgets(context)
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            containerColor = if (isSelected) Color(0xFF282828) else Color(0xFF141414),
                                            contentColor = if (isSelected) TextWhite else MutedGrey
                                        ),
                                        border = BorderStroke(1.dp, if (isSelected) AccentRed else Color(0xFF333333)),
                                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Text(text = label, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ================= GOOGLE CALENDARS =================
            item {
                Text(
                    text = "GOOGLE CALENDARS",
                    color = MutedGrey,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.5.sp,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                )
            }

            if (uiState.calendars.isEmpty()) {
                item {
                    Text(
                        text = "(No Google calendars detected yet)",
                        color = MutedGrey,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            } else {
                val grouped = uiState.calendars.groupBy { it.accountName }
                grouped.forEach { (account, cals) ->
                    item {
                        CalendarAccountGroup(
                            accountName = account,
                            calendars = cals,
                            enabledIds = uiState.enabledCalendarIds,
                            onToggle = { cal, isChecked ->
                                viewModel.toggleCalendar(cal, isChecked)
                            }
                        )
                    }
                }
            }

            // Relocated: Sync Google Accounts Button directly UNDER Google Calendars section
            item {
                OutlinedButton(
                    onClick = {
                        viewModel.syncGoogleAccounts()
                        Toast.makeText(context, "Syncing Google Accounts...", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextWhite),
                    border = BorderStroke(1.dp, Color(0xFF333333))
                ) {
                    if (uiState.isSyncing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = AccentRed,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(
                        text = if (uiState.isSyncing) "SYNCING..." else "SYNC GOOGLE ACCOUNTS NOW",
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(30.dp))
            }
        }
    }
}

@Composable
fun CalendarAccountGroup(
    accountName: String,
    calendars: List<CalendarInfo>,
    enabledIds: Set<String>,
    onToggle: (CalendarInfo, Boolean) -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }
    val selectedCount = calendars.count { enabledIds.contains(it.id.toString()) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        border = BorderStroke(1.dp, Color(0xFF222222))
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = accountName,
                        color = AccentRed,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "$selectedCount of ${calendars.size} calendars active",
                        color = MutedGrey,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Text(
                    text = if (isExpanded) "▲" else "▼",
                    color = MutedGrey,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }

            if (isExpanded) {
                HorizontalDivider(color = Color(0xFF222222), thickness = 1.dp)
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                    calendars.forEach { cal ->
                        val isChecked = enabledIds.contains(cal.id.toString())
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onToggle(cal, !isChecked) }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = isChecked,
                                onCheckedChange = { onToggle(cal, it) },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = AccentRed,
                                    uncheckedColor = Color(0xFF444444),
                                    checkmarkColor = TextWhite
                                )
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(Color(cal.color))
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = cal.displayName,
                                    color = if (isChecked) TextWhite else MutedGrey,
                                    fontSize = 13.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = if (isChecked) FontWeight.SemiBold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun NothingWidgetPreviewCard(
    todayEvents: List<CalendarEvent>,
    upcomingEvents: List<CalendarEvent>,
    useGoogleColors: Boolean,
    googleColorMode: String,
    isFadeEnabled: Boolean,
    widgetBgColor: Color,
    customCardColor: Color,
    cardGradientStyle: String
) {
    val allPreviewEvents = remember(todayEvents, upcomingEvents) {
        (todayEvents + upcomingEvents).take(5)
    }
    val previewToday = allPreviewEvents.filter { it.isToday() }
    val previewUpcoming = allPreviewEvents.filter { !it.isToday() }

    var eventIdx = 0

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = widgetBgColor),
        border = null
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
        ) {
            // TODAY SECTION
            if (previewToday.isNotEmpty()) {
                Text(
                    text = "T O D A Y",
                    color = MutedGrey,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(bottom = 10.dp, start = 4.dp)
                )

                previewToday.forEachIndexed { index, event ->
                    EventBoxItem(
                        event = event,
                        isToday = true,
                        isFirstToday = (index == 0),
                        eventIndex = eventIdx++,
                        useGoogleColors = useGoogleColors,
                        googleColorMode = googleColorMode,
                        isFadeEnabled = isFadeEnabled,
                        widgetBgColor = widgetBgColor,
                        customCardColor = customCardColor,
                        cardGradientStyle = cardGradientStyle
                    )
                }

                HorizontalDivider(
                    modifier = Modifier.padding(top = 4.dp, bottom = 10.dp),
                    thickness = 1.dp,
                    color = DividerGrey
                )
            }

            // UPCOMING SECTION
            if (previewUpcoming.isNotEmpty() || previewToday.isEmpty()) {
                Text(
                    text = "U P C O M I N G",
                    color = MutedGrey,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(bottom = 10.dp, start = 4.dp)
                )

                if (previewUpcoming.isEmpty()) {
                    Text(
                        text = "No upcoming events",
                        color = MutedGrey,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(start = 12.dp, top = 2.dp, bottom = 8.dp)
                    )
                } else {
                    previewUpcoming.forEach { event ->
                        EventBoxItem(
                            event = event,
                            isToday = false,
                            isFirstToday = false,
                            eventIndex = eventIdx++,
                            useGoogleColors = useGoogleColors,
                            googleColorMode = googleColorMode,
                            isFadeEnabled = isFadeEnabled,
                            widgetBgColor = widgetBgColor,
                            customCardColor = customCardColor,
                            cardGradientStyle = cardGradientStyle
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun EventBoxItem(
    event: CalendarEvent,
    isToday: Boolean,
    isFirstToday: Boolean,
    eventIndex: Int,
    useGoogleColors: Boolean,
    googleColorMode: String,
    isFadeEnabled: Boolean,
    widgetBgColor: Color,
    customCardColor: Color,
    cardGradientStyle: String
) {
    // Card Background Color / Gradient Calculation
    val cardModifier = if (useGoogleColors && googleColorMode == "BOX") {
        val factor = if (!isFadeEnabled) 0.22f else maxOf(0.04f, 0.26f - (eventIndex * 0.05f))
        val eventCol = Color(event.color)
        val tinted = Color(
            red = (eventCol.red * factor).coerceIn(0f, 1f),
            green = (eventCol.green * factor).coerceIn(0f, 1f),
            blue = (eventCol.blue * factor).coerceIn(0f, 1f),
            alpha = 1f
        )
        Modifier.background(tinted)
    } else if (!useGoogleColors && cardGradientStyle != "NONE") {
        val (startCol, endCol) = when (cardGradientStyle) {
            "RUBY" -> Color(0xFF361717) to Color(0xFF180A0A)
            "CYBER" -> Color(0xFF142B33) to Color(0xFF0A161A)
            "AMBER" -> Color(0xFF302414) to Color(0xFF161008)
            else -> Color(0xFF2A2B2D) to Color(0xFF141517)
        }
        val alphaFactor = if (isFadeEnabled) (1f - (eventIndex.coerceIn(0, 4) * 0.18f)).coerceIn(0.15f, 1f) else 1f
        Modifier.background(
            brush = Brush.verticalGradient(
                colors = listOf(startCol.copy(alpha = alphaFactor), endCol.copy(alpha = alphaFactor))
            )
        )
    } else {
        val solidColor = if (isFadeEnabled) {
            val fraction = (eventIndex.coerceIn(0, 4) / 4f)
            Color(
                red = customCardColor.red + fraction * (widgetBgColor.red - customCardColor.red),
                green = customCardColor.green + fraction * (widgetBgColor.green - customCardColor.green),
                blue = customCardColor.blue + fraction * (widgetBgColor.blue - customCardColor.blue),
                alpha = 1f
            )
        } else {
            customCardColor
        }
        Modifier.background(solidColor)
    }

    // Typography fade
    val titleColor = if (isFadeEnabled) {
        when (eventIndex) {
            0 -> Color(0xFFFFFFFF)
            1 -> Color(0xFFE0E0E0)
            2 -> Color(0xFFBCBCBC)
            3 -> Color(0xFF8E8E8E)
            else -> Color(0xFF606060)
        }
    } else {
        Color(0xFFFFFFFF)
    }

    val subColor = if (isFadeEnabled) {
        when (eventIndex) {
            0 -> Color(0xFFA8A8A8)
            1 -> Color(0xFF909090)
            2 -> Color(0xFF787878)
            3 -> Color(0xFF5A5A5A)
            else -> Color(0xFF444444)
        }
    } else {
        Color(0xFF9E9E9E)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 5.dp)
            .clip(RoundedCornerShape(14.dp))
            .then(cardModifier)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Marker
        if (useGoogleColors && googleColorMode == "DOT") {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(Color(event.color))
            )
        } else if (isFirstToday) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(AccentRed)
            )
        } else if (isToday) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFCCCCCC))
            )
        } else {
            val markerAlpha = if (isFadeEnabled) (1f - (eventIndex * 0.18f)).coerceIn(0.2f, 1f) else 1f
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .border(1.5.dp, TextWhite.copy(alpha = markerAlpha), CircleShape)
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = event.title,
                color = titleColor,
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = if (isToday) event.getFormattedTime() else event.getFormattedUpcomingDateTime(),
                color = subColor,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 1.dp)
            )
        }
    }
}

@Composable
fun PermissionCard(onRequest: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(AccentRed))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(AccentRed)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "CALENDAR PERMISSION REQUIRED",
                    color = TextWhite,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Grant calendar access to load and show your Google Calendar timeline.",
                color = MutedGrey,
                fontSize = 11.sp,
                lineHeight = 16.sp
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onRequest,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccentRed,
                    contentColor = TextWhite
                )
            ) {
                Text(
                    text = "GRANT ACCESS",
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp
                )
            }
        }
    }
}

private fun requestPinWidget(context: Context, providerClass: Class<*>) {
    val appWidgetManager = AppWidgetManager.getInstance(context)
    val myProvider = ComponentName(context, providerClass)

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && appWidgetManager.isRequestPinAppWidgetSupported) {
        val successCallback = PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, providerClass),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        appWidgetManager.requestPinAppWidget(myProvider, null, successCallback)
        Toast.makeText(context, "Adding widget to home screen...", Toast.LENGTH_SHORT).show()
    } else {
        Toast.makeText(
            context,
            "Long-press your home screen and select JNToolz Events from widgets.",
            Toast.LENGTH_LONG
        ).show()
    }
}
