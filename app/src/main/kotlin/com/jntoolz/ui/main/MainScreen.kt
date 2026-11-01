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
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.jntoolz.data.CalendarEvent
import com.jntoolz.data.GoogleCalendarApi
import com.jntoolz.data.ThemeMath
import com.jntoolz.widget.NothingCalendarWidgetProvider

private val TopBarTextStyle = TextStyle(
    platformStyle = PlatformTextStyle(includeFontPadding = false),
    lineHeightStyle = LineHeightStyle(
        alignment = LineHeightStyle.Alignment.Center,
        trim = LineHeightStyle.Trim.Both
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainScreenViewModel = viewModel(),
    onBack: (() -> Unit)? = null
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val theme by viewModel.theme.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var pickerTarget by remember { mutableStateOf<PickerTarget?>(null) }
    var eventPicker by remember { mutableStateOf<CalendarEvent?>(null) }
    var pendingLink by remember { mutableStateOf<String?>(null) }

    val authLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { res ->
        val acc = pendingLink
        pendingLink = null
        if (acc != null) {
            try {
                Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(res.data)
                viewModel.setLinked(acc, true)
            } catch (e: ApiException) {
                Toast.makeText(context, "Google link failed: ${e.statusCode}", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Google link cancelled", Toast.LENGTH_SHORT).show()
            }
        }
    }
    val onLinkToggle: (String, Boolean) -> Unit = remember(viewModel, context, authLauncher) {
        { account, linked ->
            if (linked) {
                viewModel.setLinked(account, false)
            } else {
                GoogleCalendarApi.authorize(context, account)
                    .addOnSuccessListener { r ->
                        val pi = r.pendingIntent
                        if (r.hasResolution() && pi != null) {
                            pendingLink = account
                            authLauncher.launch(IntentSenderRequest.Builder(pi.intentSender).build())
                        } else {
                            viewModel.setLinked(account, true)
                        }
                    }
                    .addOnFailureListener { e ->
                        val code = (e as? ApiException)?.statusCode
                        Toast.makeText(context, "Google link failed: ${code ?: e.message}", Toast.LENGTH_LONG).show()
                    }
            }
        }
    }

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
    val grouped = remember(uiState.calendars) { uiState.calendars.groupBy { it.accountName } }

    pickerTarget?.let { target ->
        ColorPickerDialog(
            initial = if (target == PickerTarget.MARKER) theme.markerColor else theme.cardColor,
            onDismiss = { pickerTarget = null },
            onConfirm = { argb ->
                if (target == PickerTarget.MARKER) {
                    viewModel.updateTheme { it.copy(markerColor = argb) }
                } else {
                    viewModel.updateTheme { it.copy(gradientStyle = "NONE", cardColor = argb) }
                }
                pickerTarget = null
            }
        )
    }

    eventPicker?.let { ev ->
        val hasOverride = remember(ev) { viewModel.hasColorOverride(ev) }
        ColorPickerDialog(
            initial = ev.color,
            presets = GOOGLE_PALETTE,
            onDismiss = { eventPicker = null },
            onConfirm = { argb ->
                viewModel.setEventColor(ev, argb or ThemeMath.OPAQUE)
                eventPicker = null
            },
            onReset = if (hasOverride) ({
                viewModel.resetEventColor(ev)
                eventPicker = null
            }) else null
        )
    }

    Scaffold(
        containerColor = BgScreen,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(BgScreen)
                    .statusBarsPadding()
                    .height(56.dp)
                    .padding(horizontal = if (onBack != null) 4.dp else 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (onBack != null) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .clickable { onBack() },
                        contentAlignment = Alignment.Center
                    ) {
                        // Drawn arrow so it centres geometrically (a text glyph sits on its baseline)
                        Canvas(modifier = Modifier.size(18.dp)) {
                            val w = size.width
                            val cy = size.height / 2f
                            val stroke = 2.dp.toPx()
                            val cap = StrokeCap.Round
                            drawLine(TextWhite, Offset(w * 0.08f, cy), Offset(w * 0.92f, cy), stroke, cap)
                            drawLine(TextWhite, Offset(w * 0.08f, cy), Offset(w * 0.42f, cy - w * 0.34f), stroke, cap)
                            drawLine(TextWhite, Offset(w * 0.08f, cy), Offset(w * 0.42f, cy + w * 0.34f), stroke, cap)
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    text = "CALENDAR",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 2.sp,
                    color = TextWhite,
                    style = TopBarTextStyle
                )
            }
        }
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = uiState.isSyncing,
            onRefresh = viewModel::syncGoogleAccounts,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            indicator = {
                PullToRefreshDefaults.Indicator(
                    state = rememberPullToRefreshState(),
                    isRefreshing = uiState.isSyncing,
                    modifier = Modifier.align(Alignment.TopCenter),
                    containerColor = CardBg,
                    color = AccentRed
                )
            }
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (!uiState.hasPermission) {
                    item(key = "permission") {
                        PermissionCard(onRequest = { permissionLauncher.launch(Manifest.permission.READ_CALENDAR) })
                    }
                }

                item(key = "pin") {
                    Button(
                        onClick = { requestPinWidget(context, NothingCalendarWidgetProvider::class.java) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = TextWhite, contentColor = BgScreen)
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

                item(key = "preview") {
                    NothingWidgetPreviewCard(
                        todayEvents = todayEvents,
                        upcomingEvents = upcomingEvents,
                        onEventClick = { eventPicker = it },
                        theme = theme
                    )
                }

                item(key = "themeHeader") { SectionHeader("THEME") }
                item(key = "theme") {
                    ThemeCard(
                        theme = theme,
                        onChange = viewModel::updateTheme,
                        onPick = { pickerTarget = it }
                    )
                }

                item(key = "googleHeader") { SectionHeader("GOOGLE CALENDARS") }
                if (uiState.calendars.isEmpty()) {
                    item(key = "noCalendars") {
                        Text(
                            text = "(No Google calendars detected yet)",
                            color = MutedGrey,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                } else {
                    item(key = "accounts") {
                        CalendarAccountsCard(
                            grouped = grouped,
                            enabledIds = uiState.enabledCalendarIds,
                            onToggle = viewModel::toggleCalendar,
                            linked = uiState.linkedAccounts,
                            onLinkToggle = onLinkToggle
                        )
                    }
                }

                item(key = "spacer") { Spacer(modifier = Modifier.height(30.dp)) }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        color = MutedGrey,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = FontFamily.Monospace,
        letterSpacing = 1.5.sp,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
    )
}

@Composable
private fun PermissionCard(onRequest: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(AccentRed))
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
