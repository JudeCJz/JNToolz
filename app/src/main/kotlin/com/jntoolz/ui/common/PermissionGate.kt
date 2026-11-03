package com.jntoolz.ui.common

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.jntoolz.tools.Perm
import com.jntoolz.tools.PermKind
import com.jntoolz.tools.Tool
import com.jntoolz.tools.ToolPerms
import com.jntoolz.tools.Tools
import com.jntoolz.ui.sot.SotColors
import com.jntoolz.ui.sot.SotLabel
import com.jntoolz.ui.sot.SotText

/** What the tool still lacks, re-checked on every resume (back from a Settings screen or a dialog). Empty while the tool is off. */
@Composable
fun rememberMissing(tool: Tool): State<List<Perm>> {
    val context = LocalContext.current
    val state = remember { mutableStateOf(missingNow(context, tool)) }
    LifecycleResumeEffect(tool) {
        state.value = missingNow(context, tool)
        onPauseOrDispose { }
    }
    return state
}

private fun missingNow(c: Context, tool: Tool): List<Perm> =
    if (Tools.isEnabled(c, tool)) ToolPerms.missing(c, tool) else emptyList()

/**
 * Put once at the top of a tool screen. On entering (and on each resume) with the tool on and something missing:
 * asks every runtime permission together, once per entry, then lists what is still missing in a bottom sheet
 * (GRANT per row, LATER to close). The sheet closes by itself when nothing is missing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionGate(tool: Tool) {
    val context = LocalContext.current
    val missing by rememberMissing(tool)
    var asked by remember { mutableStateOf(false) }
    var asking by remember { mutableStateOf(false) }
    var showSheet by remember { mutableStateOf(false) }
    var later by remember { mutableStateOf(false) } // closed by the user: not shown again in this entry

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        asking = false
        val left = missingNow(context, tool)
        if (!later && left.any { it.kind == PermKind.SPECIAL }) showSheet = true
    }

    // The same check on entering and on each return; a denied runtime ask is not repeated in this entry.
    LifecycleResumeEffect(tool) {
        val left = missingNow(context, tool)
        if (left.isNotEmpty() && !asking) {
            val names = left.filter { it.kind == PermKind.RUNTIME }.mapNotNull { it.runtime }.toTypedArray()
            if (names.isNotEmpty() && !asked) {
                asked = true
                asking = true
                launcher.launch(names)
            } else if (!later && left.any { it.kind == PermKind.SPECIAL }) {
                showSheet = true
            }
        }
        onPauseOrDispose { }
    }
    LaunchedEffect(missing) { if (missing.isEmpty()) showSheet = false }

    if (showSheet && missing.isNotEmpty()) {
        ModalBottomSheet(
            onDismissRequest = { showSheet = false; later = true },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = SotColors.Bg,
            contentColor = SotColors.Txt
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                SotLabel("PERMISSIONS")
                for (p in missing) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        SotText(p.label, modifier = Modifier.weight(1f))
                        Spacer(Modifier.size(8.dp).clip(CircleShape).background(SotColors.Red))
                        Spacer(Modifier.width(12.dp))
                        GrantPill {
                            grant(context, p, asked) {
                                asking = true
                                launcher.launch(arrayOf(p.runtime!!))
                            }
                        }
                    }
                }
                SotText(
                    "LATER",
                    size = 12.sp,
                    color = SotColors.Dim,
                    modifier = Modifier
                        .align(Alignment.End)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { showSheet = false; later = true }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun GrantPill(onClick: () -> Unit) {
    SotText(
        "GRANT",
        size = 12.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(SotColors.Chip)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    )
}

/** A runtime perm is asked again unless the system no longer shows its dialog; everything else opens its Settings screen. */
private fun grant(c: Context, p: Perm, askedBefore: Boolean, ask: () -> Unit) {
    if (p.kind == PermKind.RUNTIME && p.runtime != null) {
        val blocked = askedBefore && c.findActivity()?.shouldShowRequestPermissionRationale(p.runtime) == false
        if (!blocked) {
            ask()
            return
        }
        start(c, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + c.packageName)))
        return
    }
    val i = p.settings(c)
    if (i != null && !start(c, i)) {
        // The package-specific screen is missing on some phones: fall back to the plain action.
        start(c, Intent(i.action))
    }
}

private fun start(c: Context, i: Intent): Boolean = try {
    c.startActivity(i)
    true
} catch (_: RuntimeException) {
    false
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
