package com.jntoolz.ui.hub

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jntoolz.R
import com.jntoolz.data.ReleaseInfo
import com.jntoolz.data.UpdateChecker
import com.jntoolz.data.WidgetPreferences
import com.jntoolz.tools.Tool
import com.jntoolz.tools.Tools
import com.jntoolz.tools.ToolPerms
import com.jntoolz.ui.common.AttentionDot
import com.jntoolz.ui.counter.CounterHubCard
import com.jntoolz.ui.common.ToolOnOffToggle
import com.jntoolz.ui.unscroll.UnscrollHubCard
import com.jntoolz.widget.WidgetUpdater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val BgScreen = Color(0xFF000000)
private val CardBg = Color(0xFF141414)
private val DialogBg = Color(0xFF161616)
private val TextWhite = Color(0xFFFFFFFF)
private val MutedGrey = Color(0xFF8E8E8E)
private val AccentRed = Color(0xFFFF2E2E)
private val BorderGrey = Color(0xFF222222)

/** Logo and title of a switched-off tool. */
internal const val OFF_ALPHA = 0.4f

private fun openInBrowser(context: Context, url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
    runCatching { context.startActivity(intent) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HubScreen(
    onOpenCalendar: () -> Unit,
    onOpenSot: () -> Unit,
    onOpenCounter: () -> Unit,
    onOpenUnscroll: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val prefs = remember { WidgetPreferences(context) }
    var isCalendarEnabled by remember { mutableStateOf(Tools.isEnabled(context, Tool.CALENDAR)) }
    var isSotEnabled by remember { mutableStateOf(Tools.isEnabled(context, Tool.BATTERY)) }
    var calendarAttention by remember { mutableStateOf(ToolPerms.needsAttention(context, Tool.CALENDAR)) }
    var sotAttention by remember { mutableStateOf(ToolPerms.needsAttention(context, Tool.BATTERY)) }
    // Back from a tool or system Settings: the red markers follow the permissions.
    LifecycleResumeEffect(Unit) {
        calendarAttention = ToolPerms.needsAttention(context, Tool.CALENDAR)
        sotAttention = ToolPerms.needsAttention(context, Tool.BATTERY)
        onPauseOrDispose { }
    }

    val currentVersion = remember { UpdateChecker.getCurrentVersionName(context) }
    var releaseInfo by remember { mutableStateOf<ReleaseInfo?>(null) }
    var isCheckingUpdate by remember { mutableStateOf(false) }
    var showUpdateDialog by remember { mutableStateOf(false) }
    var showWhatsNewDialog by remember { mutableStateOf(false) }

    var isDownloading by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableStateOf(0f) }

    // Runs the update check (network work happens on IO inside UpdateChecker); never leaves the spinner stuck
    suspend fun checkUpdates(): ReleaseInfo? {
        isCheckingUpdate = true
        return try {
            UpdateChecker.checkForUpdates(context).also { releaseInfo = it }
        } finally {
            isCheckingUpdate = false
        }
    }

    // Check on every open whether an update or new version notes should appear
    LaunchedEffect(Unit) {
        val seen = withContext(Dispatchers.IO) { prefs.lastSeenVersion }
        if (seen.isNotEmpty() && seen != currentVersion) showWhatsNewDialog = true
        checkUpdates()
    }

    // Glowing animation when an update is available
    val infiniteTransition = rememberInfiniteTransition(label = "updateGlow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )

    Scaffold(
        containerColor = BgScreen,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_jntoolz_logo),
                            contentDescription = "JNToolz Logo",
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "JNTOOLZ",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 2.sp,
                            color = TextWhite,
                            modifier = Modifier.alignByBaseline()
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = currentVersion,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            color = MutedGrey,
                            modifier = Modifier.alignByBaseline()
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BgScreen)
            )
        }
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = isCheckingUpdate,
            onRefresh = {
                coroutineScope.launch {
                    val info = checkUpdates()
                    withContext(Dispatchers.IO) { WidgetUpdater.updateAllWidgets(context) }
                    if (info?.hasUpdate == true) showUpdateDialog = true
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            indicator = {
                PullToRefreshDefaults.Indicator(
                    state = rememberPullToRefreshState(),
                    isRefreshing = isCheckingUpdate,
                    modifier = Modifier.align(Alignment.TopCenter),
                    containerColor = CardBg,
                    color = AccentRed
                )
            }
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Calendar App Card
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp)),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = CardBg),
                        border = BorderStroke(1.dp, BorderGrey)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Calendar Logo
                            Image(
                                painter = painterResource(id = R.drawable.ic_calendar_logo),
                                contentDescription = "Calendar",
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .alpha(if (isCalendarEnabled) 1f else OFF_ALPHA)
                                    .clickable(enabled = isCalendarEnabled) { onOpenCalendar() }
                            )

                            Spacer(modifier = Modifier.width(16.dp))

                            // App Title & Status
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .alpha(if (isCalendarEnabled) 1f else OFF_ALPHA)
                                    .clickable(enabled = isCalendarEnabled) { onOpenCalendar() }
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "Calendar",
                                        color = TextWhite,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    AttentionDot(calendarAttention)
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            // Enable / Disable Toggle
                            ToolOnOffToggle(
                                enabled = isCalendarEnabled,
                                onToggle = { checked ->
                                    isCalendarEnabled = checked
                                    Tools.setEnabled(context, Tool.CALENDAR, checked)
                                    calendarAttention = ToolPerms.needsAttention(context, Tool.CALENDAR)
                                    if (checked && ToolPerms.missing(context, Tool.CALENDAR).isNotEmpty()) onOpenCalendar()
                                }
                            )
                        }
                    }
                }

                // Battery (screen-on time) Card
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp)),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = CardBg),
                        border = BorderStroke(1.dp, BorderGrey)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.ic_sot_logo),
                                contentDescription = "Battery",
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .alpha(if (isSotEnabled) 1f else OFF_ALPHA)
                                    .clickable(enabled = isSotEnabled) { onOpenSot() }
                            )

                            Spacer(modifier = Modifier.width(16.dp))

                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .alpha(if (isSotEnabled) 1f else OFF_ALPHA)
                                    .clickable(enabled = isSotEnabled) { onOpenSot() }
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "Battery",
                                        color = TextWhite,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    AttentionDot(sotAttention)
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            // Enable / Disable Toggle: starts or stops battery tracking
                            ToolOnOffToggle(
                                enabled = isSotEnabled,
                                onToggle = { checked ->
                                    isSotEnabled = checked
                                    Tools.setEnabled(context, Tool.BATTERY, checked)
                                    sotAttention = ToolPerms.needsAttention(context, Tool.BATTERY)
                                    if (checked && ToolPerms.missing(context, Tool.BATTERY).isNotEmpty()) onOpenSot()
                                }
                            )
                        }
                    }
                }

                // Counter App Card
                item { CounterHubCard(onOpen = onOpenCounter) }

                // Unscroll Card
                item { UnscrollHubCard(onOpen = onOpenUnscroll) }
            }

            // Bottom-Right Update Pill Button
            val hasUpdate = releaseInfo?.hasUpdate == true
            Card(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(20.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .clickable {
                        if (hasUpdate) {
                            showUpdateDialog = true
                        } else {
                            coroutineScope.launch {
                                val info = checkUpdates()
                                if (info?.hasUpdate == true) {
                                    showUpdateDialog = true
                                } else {
                                    Toast.makeText(context, "JNToolz v$currentVersion is up to date!", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    },
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (hasUpdate) Color(0xFF1F0808) else CardBg
                ),
                border = BorderStroke(
                    width = if (hasUpdate) 1.5.dp else 1.dp,
                    color = if (hasUpdate) AccentRed.copy(alpha = glowAlpha) else BorderGrey
                )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isCheckingUpdate) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(12.dp),
                            color = AccentRed,
                            strokeWidth = 1.5.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    } else if (hasUpdate) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(AccentRed.copy(alpha = glowAlpha))
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }

                    Text(
                        text = when {
                            isCheckingUpdate -> "CHECKING..."
                            hasUpdate -> "UPDATE: ${releaseInfo?.tagName ?: "NEW"}"
                            else -> "UPDATES"
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp,
                        color = if (hasUpdate) AccentRed else MutedGrey
                    )
                }
            }
        }
    }

    // In-App Update Dialog
    val info = releaseInfo
    if (showUpdateDialog && info != null) {
        AlertDialog(
            onDismissRequest = {
                if (!isDownloading) showUpdateDialog = false
            },
            containerColor = DialogBg,
            shape = RoundedCornerShape(20.dp),
            title = {
                Text(
                    text = "UPDATE AVAILABLE",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.5.sp,
                    color = TextWhite
                )
            },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        text = "${info.title} (${info.tagName})",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = AccentRed,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "PATCHES & FEATURES:",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MutedGrey,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = info.changelog,
                        fontSize = 12.sp,
                        color = TextWhite,
                        lineHeight = 18.sp,
                        fontFamily = FontFamily.Monospace
                    )

                    if (isDownloading) {
                        Spacer(modifier = Modifier.height(16.dp))
                        LinearProgressIndicator(
                            progress = { downloadProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            color = AccentRed,
                            trackColor = BorderGrey,
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (downloadProgress > 0f) "Downloading: ${(downloadProgress * 100).toInt()}%" else "Starting download...",
                            fontSize = 10.5.sp,
                            color = MutedGrey,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (isDownloading) return@Button
                        val targetApkUrl = info.apkUrl
                        if (targetApkUrl != null && targetApkUrl.endsWith(".apk", ignoreCase = true)) {
                            isDownloading = true
                            downloadProgress = 0f
                            coroutineScope.launch {
                                val result = UpdateChecker.downloadAndInstallApk(context, targetApkUrl) { progress ->
                                    downloadProgress = progress
                                }
                                isDownloading = false
                                if (!result.isSuccess) {
                                    Toast.makeText(context, "Direct download failed, opening browser...", Toast.LENGTH_SHORT).show()
                                    openInBrowser(context, info.htmlUrl)
                                }
                            }
                        } else {
                            openInBrowser(context, info.htmlUrl)
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AccentRed,
                        contentColor = TextWhite
                    )
                ) {
                    if (isDownloading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(13.dp),
                            color = TextWhite,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "DOWNLOADING...",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    } else {
                        Text(
                            text = "UPDATE IN-APP",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    }
                }
            },
            dismissButton = {
                if (!isDownloading) {
                    TextButton(onClick = { showUpdateDialog = false }) {
                        Text(
                            text = "SKIP",
                            color = MutedGrey,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        )
    }

    // What's New On Relaunch Dialog
    if (showWhatsNewDialog) {
        AlertDialog(
            onDismissRequest = {
                prefs.lastSeenVersion = currentVersion
                showWhatsNewDialog = false
            },
            containerColor = DialogBg,
            shape = RoundedCornerShape(20.dp),
            title = {
                Text(
                    text = "WHAT'S NEW IN v$currentVersion",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp,
                    color = TextWhite
                )
            },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        text = "LATEST PATCHES & IMPROVEMENTS:",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = AccentRed,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = UpdateChecker.CURRENT_VERSION_CHANGELOG,
                        fontSize = 12.sp,
                        color = TextWhite,
                        lineHeight = 18.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        prefs.lastSeenVersion = currentVersion
                        showWhatsNewDialog = false
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = TextWhite,
                        contentColor = BgScreen
                    )
                ) {
                    Text(
                        text = "GOT IT",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                }
            }
        )
    }
}
