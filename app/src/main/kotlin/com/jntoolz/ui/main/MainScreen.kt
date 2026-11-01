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
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jntoolz.R
import com.jntoolz.data.CalendarEvent
import com.jntoolz.data.CalendarInfo
import com.jntoolz.widget.NothingCalendarWidgetProvider

private val BgScreen = Color(0xFF000000)
private val WidgetBg = Color(0xFF000000) // Pure black darker color for widget
private val CardBg = Color(0xFF141414)
private val TextWhite = Color(0xFFFFFFFF)
private val MutedGrey = Color(0xFF8E8E8E)
private val DividerGrey = Color(0xFF222222)
private val AccentRed = Color(0xFFFF2E2E)

// Box background tiers matching the widget fade:
// "make sure the color fading completely fades into the background color"
private val BoxTier0 = Color(0xFF202121) // Top-most event (lightest card)
private val BoxTier1 = Color(0xFF161717) // Event 1
private val BoxTier2 = Color(0xFF0D0E0E) // Event 2
private val BoxTier3 = Color(0xFF050505) // Event 3
private val BoxTier4 = Color(0xFF000000) // Event 4+ (completely fades into background)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainScreenViewModel = viewModel(),
    onBack: (() -> Unit)? = null
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

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

            // Google Sync Action
            item {
                OutlinedButton(
                    onClick = {
                        viewModel.syncGoogleAccounts()
                        Toast.makeText(context, "Syncing Google Accounts...", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextWhite),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF333333))
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

            // The Authentic Nothing OS Widget Preview Card (Showing only 5 events, complete color fade)
            item {
                NothingWidgetPreviewCard(
                    todayEvents = todayEvents,
                    upcomingEvents = upcomingEvents
                )
            }

            // Checkbox selection of Google Accounts & Calendars (with collapsible drop downs)
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
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E1E1E))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Dropdown Header Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = accountName,
                        color = AccentRed,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "$selectedCount of ${calendars.size} calendars active",
                        color = Color(0xFF777777),
                        fontSize = 10.5.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }

                Text(
                    text = if (isExpanded) "▲" else "▼",
                    color = MutedGrey,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(horizontal = 6.dp)
                )
            }

            // Dropdown Content
            if (isExpanded) {
                HorizontalDivider(
                    modifier = Modifier.padding(top = 8.dp, bottom = 10.dp),
                    thickness = 1.dp,
                    color = Color(0xFF222222)
                )

                calendars.forEach { cal ->
                    val isChecked = enabledIds.contains(cal.id.toString())
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onToggle(cal, !isChecked) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = isChecked,
                            onCheckedChange = { onToggle(cal, it) },
                            colors = CheckboxDefaults.colors(
                                checkedColor = AccentRed,
                                uncheckedColor = MutedGrey,
                                checkmarkColor = TextWhite
                            )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = cal.displayName,
                                color = if (isChecked) TextWhite else MutedGrey,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                            if (cal.displayName != cal.accountName) {
                                Text(
                                    text = if (cal.isVisibleInGoogle) "Default in Google" else "Hidden in Google",
                                    color = Color(0xFF666666),
                                    fontSize = 10.sp
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
    upcomingEvents: List<CalendarEvent>
) {
    // "in the preview only show the 5 events in preview of that app"
    val allPreviewEvents = remember(todayEvents, upcomingEvents) {
        (todayEvents + upcomingEvents).take(5)
    }
    val previewToday = allPreviewEvents.filter { it.isToday() }
    val previewUpcoming = allPreviewEvents.filter { !it.isToday() }

    var eventIdx = 0

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = WidgetBg),
        border = null // No border
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
        ) {
            // ================= TODAY SECTION (Hidden if empty) =================
            if (previewToday.isNotEmpty()) {
                Text(
                    text = "T O D A Y",
                    color = MutedGrey,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 2.sp,
                    modifier = Modifier.padding(bottom = 12.dp, start = 4.dp)
                )

                previewToday.forEachIndexed { index, event ->
                    val isFirst = index == 0
                    EventBoxItem(
                        event = event,
                        isToday = true,
                        isFirstToday = isFirst,
                        eventIndex = eventIdx++
                    )
                }

                HorizontalDivider(
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
                    thickness = 1.dp,
                    color = DividerGrey
                )
            }

            // ================= UPCOMING SECTION =================
            if (previewUpcoming.isNotEmpty() || previewToday.isEmpty()) {
                Text(
                    text = "U P C O M I N G",
                    color = MutedGrey,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 2.sp,
                    modifier = Modifier.padding(bottom = 12.dp, start = 4.dp)
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
                            eventIndex = eventIdx++
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
    eventIndex: Int
) {
    // "make sure the color fading completely fades into the background color"
    val boxBg = when (eventIndex) {
        0 -> BoxTier0 // #202121
        1 -> BoxTier1 // #161717
        2 -> BoxTier2 // #0D0E0E
        3 -> BoxTier3 // #050505
        else -> BoxTier4 // #000000 (completely faded into background)
    }
    val titleColor = when (eventIndex) {
        0 -> Color(0xFFFFFFFF)
        1 -> Color(0xFFD8D8D8)
        2 -> Color(0xFFAEAEAE)
        3 -> Color(0xFF808080)
        else -> Color(0xFF555555)
    }
    val subColor = when (eventIndex) {
        0 -> Color(0xFFA0A0A0)
        1 -> Color(0xFF888888)
        2 -> Color(0xFF707070)
        3 -> Color(0xFF505050)
        else -> Color(0xFF383838)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(boxBg)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Marker
        if (isFirstToday) {
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
            val markerAlpha = when (eventIndex) {
                0 -> 1f
                1 -> 0.85f
                2 -> 0.65f
                3 -> 0.45f
                else -> 0.25f
            }
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
                fontSize = 13.5.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 1
            )
            Text(
                text = if (isToday) event.getFormattedTime() else event.getFormattedUpcomingDateTime(),
                color = subColor,
                fontSize = 11.5.sp,
                fontFamily = FontFamily.Monospace,
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
