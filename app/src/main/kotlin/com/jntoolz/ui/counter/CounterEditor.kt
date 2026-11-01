package com.jntoolz.ui.counter

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jntoolz.counter.Counter
import com.jntoolz.counter.CounterGradient
import com.jntoolz.counter.CounterStore
import com.jntoolz.counter.CounterTheme
import com.jntoolz.counter.CounterLayouts
import com.jntoolz.counter.CounterShape
import com.jntoolz.counter.CounterWidgets
import com.jntoolz.counter.SizeDp
import com.jntoolz.ui.theme.NothingFont
import com.jntoolz.ui.theme.NothingFonts
import androidx.compose.foundation.layout.size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private val DateFmt = DateTimeFormatter.ofPattern("d MMM yyyy")
private val TimeFmt = DateTimeFormatter.ofPattern("HH:mm")

/** Bottom sheet to add ([initial] null) or edit a counter. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CounterEditorSheet(
    initial: Counter?,
    onDismiss: () -> Unit,
    onSave: (Counter) -> Unit,
    onDelete: ((Counter) -> Unit)? = null,
) {
    val base = remember(initial) {
        initial ?: Counter(id = CounterStore.newId(), title = "", date = LocalDate.now().plusDays(7))
    }
    var title by remember(base) { mutableStateOf(base.title) }
    // Previews follow the title ~150 ms after the last keystroke, not on every key.
    var previewTitle by remember(base) { mutableStateOf(base.title) }
    LaunchedEffect(title) {
        delay(150)
        previewTitle = title
    }
    var date by remember(base) { mutableStateOf(base.date) }
    var time by remember(base) { mutableStateOf(base.time) }
    var theme by remember(base) { mutableStateOf(base.theme) }
    var gradient by remember(base) { mutableStateOf(base.gradient) }
    var live by remember(base) { mutableStateOf(base.liveClock) }
    val shape = remember(base) { if (base.shape == CounterShape.WIDE) CounterShape.WIDE else CounterShape.SQUARE }
    val allowedFonts = remember { setOf(NothingFont.NDOT_77, NothingFont.LETTERA, NothingFont.CLOCK, NothingFont.SYSTEM) }
    var font by remember(base) { mutableStateOf(base.font?.takeIf { it in allowedFonts }) }
    val context = LocalContext.current
    // A counter already on the home screen previews at its real widget size.
    val placed by produceState<SizeDp?>(null, base) {
        if (initial != null) {
            value = withContext(Dispatchers.IO) { runCatching { CounterWidgets.placedSizes(context)[base.id] }.getOrNull() }
        }
    }
    // null first: "Theme" (each theme's own face). Font files load on IO; the row appears once they have.
    val fonts by produceState(listOf<NothingFont?>(null), base) {
        value = withContext(Dispatchers.IO) {
            NothingFonts.warm()
            listOf<NothingFont?>(null) + NothingFont.entries.filter { it in allowedFonts && NothingFonts.available(it) }
        }
    }
    var pickDate by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }

    val draft = base.copy(
        title = title.trim(), date = date, time = time, theme = theme, gradient = gradient, liveClock = live && time != null,
        shape = shape, font = font,
    )
    val placedSize = placed
    val previewSize = placedSize ?: shape.size
    val previewDraft = draft.copy(title = previewTitle.trim())

    CounterDarkTheme {
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = CounterColors.Dialog,
            contentColor = CounterColors.Text,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .navigationBarsPadding()
                    .padding(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                CounterWidgetTile(previewDraft, previewSize, maxHeight = 240.dp)

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it.take(40) },
                    singleLine = true,
                    placeholder = { Text("TITLE", fontFamily = FontFamily.Monospace, letterSpacing = 1.5.sp, color = CounterColors.Muted) },
                    textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 15.sp, color = CounterColors.Text),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CounterColors.Text,
                        unfocusedBorderColor = Color(0xFF333333),
                        cursorColor = CounterColors.Red,
                        focusedContainerColor = CounterColors.Card,
                        unfocusedContainerColor = CounterColors.Card,
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FieldChip("DATE", date.format(DateFmt), Modifier.weight(1f)) { pickDate = true }
                    FieldChip(
                        "TIME", time?.format(TimeFmt) ?: "--:--", Modifier.weight(1f),
                        onClear = if (time != null) ({ time = null }) else null
                    ) { pickTime = true }
                }

                Label("THEME")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(CounterTheme.entries) { t ->
                        val selected = t == theme
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            // Same aspect as the big preview, faithfully scaled down.
                            CounterWidgetTile(
                                counter = previewDraft.copy(theme = t),
                                size = previewSize,
                                selected = selected,
                                maxHeight = 64.dp,
                                pad = 6.dp,
                                modifier = Modifier.width(thumbWidth(previewSize)),
                                onClick = { theme = t },
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                t.label,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                letterSpacing = 1.sp,
                                color = if (selected) CounterColors.Text else CounterColors.Muted
                            )
                        }
                    }
                }

                Label("GRADIENT")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(CounterGradient.entries) { g ->
                        val selected = g == gradient
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                modifier = Modifier
                                    .size(width = 54.dp, height = 36.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .then(
                                        if (g == CounterGradient.NONE) {
                                            Modifier.background(Color(0xFF222222))
                                        } else {
                                            Modifier.background(
                                                Brush.linearGradient(
                                                    colors = listOf(Color(g.startColor), Color(g.endColor))
                                                )
                                            )
                                        }
                                    )
                                    .border(
                                        BorderStroke(if (selected) 2.dp else 1.dp, if (selected) CounterColors.Red else Color(0xFF333333)),
                                        RoundedCornerShape(8.dp)
                                    )
                                    .clickable { gradient = g },
                                contentAlignment = Alignment.Center
                            ) {
                                if (g == CounterGradient.NONE) {
                                    Text("OFF", fontFamily = FontFamily.Monospace, fontSize = 9.sp, color = CounterColors.Muted)
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                g.label,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                letterSpacing = 1.sp,
                                color = if (selected) CounterColors.Text else CounterColors.Muted
                            )
                        }
                    }
                }

                if (fonts.size > 1) {
                    Label("FONT")
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(fonts) { f ->
                            val selected = f == font
                            Text(
                                f?.label ?: "Theme",
                                fontFamily = if (f != null) NothingFonts.family(f) else FontFamily.Monospace,
                                fontSize = 14.sp,
                                color = if (selected) CounterColors.Text else CounterColors.Muted,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .border(
                                        BorderStroke(1.dp, if (selected) CounterColors.Red else Color(0xFF333333)),
                                        RoundedCornerShape(50)
                                    )
                                    .clickable { font = f }
                                    .padding(horizontal = 14.dp, vertical = 8.dp)
                            )
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        Label("LIVE CLOCK")
                        if (time == null) {
                            Text("needs a time", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = Color(0xFF555555))
                        }
                    }
                    Switch(
                        checked = live && time != null,
                        enabled = time != null,
                        onCheckedChange = { live = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = CounterColors.Text,
                            checkedTrackColor = CounterColors.Red,
                            uncheckedThumbColor = CounterColors.Muted,
                            uncheckedTrackColor = CounterColors.Border,
                            disabledUncheckedThumbColor = Color(0xFF444444),
                            disabledUncheckedTrackColor = CounterColors.Border,
                        )
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    if (initial != null && onDelete != null) {
                        TextButton(onClick = { onDelete(initial) }) {
                            Text("DELETE", color = CounterColors.Red, fontFamily = FontFamily.Monospace, letterSpacing = 1.5.sp)
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        "SAVE",
                        color = CounterColors.Bg,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(CounterColors.Text)
                            .clickable { onSave(draft) }
                            .padding(horizontal = 26.dp, vertical = 12.dp)
                    )
                }
            }
        }

        if (pickDate) {
            val state = rememberDatePickerState(
                initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
            )
            DatePickerDialog(
                onDismissRequest = { pickDate = false },
                confirmButton = {
                    TextButton(onClick = {
                        state.selectedDateMillis?.let {
                            date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                        }
                        pickDate = false
                    }) { Text("OK", color = CounterColors.Text, fontFamily = FontFamily.Monospace) }
                },
                dismissButton = {
                    TextButton(onClick = { pickDate = false }) {
                        Text("CANCEL", color = CounterColors.Muted, fontFamily = FontFamily.Monospace)
                    }
                },
                colors = DatePickerDefaults.colors(containerColor = CounterColors.Dialog)
            ) {
                DatePicker(
                    state = state,
                    colors = DatePickerDefaults.colors(
                        containerColor = CounterColors.Dialog,
                        selectedDayContainerColor = CounterColors.Red,
                        selectedDayContentColor = CounterColors.Text,
                        todayDateBorderColor = CounterColors.Text,
                        todayContentColor = CounterColors.Text,
                    )
                )
            }
        }

        if (pickTime) {
            val t = time ?: LocalTime.of(9, 0)
            val state = rememberTimePickerState(initialHour = t.hour, initialMinute = t.minute, is24Hour = true)
            AlertDialog(
                onDismissRequest = { pickTime = false },
                containerColor = CounterColors.Dialog,
                confirmButton = {
                    TextButton(onClick = {
                        time = LocalTime.of(state.hour, state.minute)
                        pickTime = false
                    }) { Text("OK", color = CounterColors.Text, fontFamily = FontFamily.Monospace) }
                },
                dismissButton = {
                    TextButton(onClick = { pickTime = false }) {
                        Text("CANCEL", color = CounterColors.Muted, fontFamily = FontFamily.Monospace)
                    }
                },
                text = {
                    TimePicker(
                        state = state,
                        colors = TimePickerDefaults.colors(
                            clockDialColor = CounterColors.Card,
                            selectorColor = CounterColors.Red,
                            timeSelectorSelectedContainerColor = CounterColors.Red,
                            timeSelectorSelectedContentColor = CounterColors.Text,
                            timeSelectorUnselectedContainerColor = CounterColors.Card,
                            timeSelectorUnselectedContentColor = CounterColors.Text,
                        )
                    )
                }
            )
        }
    }
}

/** Thumbnail tile width: the widget at 64dp high plus the tile padding, at least 64dp. */
private fun thumbWidth(size: SizeDp): Dp {
    val k = CounterLayouts.fitScale(size, 160f, 64f)
    return (size.w * k + 12f).coerceAtLeast(64f).dp
}

@Composable
private fun Label(text: String) {
    Text(text, fontFamily = FontFamily.Monospace, fontSize = 11.sp, letterSpacing = 1.5.sp, color = CounterColors.Muted)
}

@Composable
private fun FieldChip(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    onClear: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .border(BorderStroke(1.dp, Color(0xFF333333)), RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Label(label)
            Spacer(Modifier.height(2.dp))
            Text(value, fontFamily = FontFamily.Monospace, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = CounterColors.Text)
        }
        if (onClear != null) {
            Text(
                "×",
                color = CounterColors.Muted,
                fontSize = 18.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable { onClear() }
                    .padding(horizontal = 6.dp)
            )
            Spacer(Modifier.width(2.dp))
        }
    }
}
