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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import com.jntoolz.counter.CounterRenderer
import com.jntoolz.ui.common.ApplyButton
import com.jntoolz.ui.common.ApplyButtonClearance
import com.jntoolz.ui.common.CardStyleEditor
import com.jntoolz.ui.common.SegmentedControl
import com.jntoolz.ui.common.ThemeRow
import com.jntoolz.ui.common.pinnedPreviewMaxHeight
import com.jntoolz.ui.common.applyButtonPadding
import androidx.compose.ui.graphics.Brush
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jntoolz.counter.Counter
import com.jntoolz.counter.CounterStore
import com.jntoolz.counter.CounterShape
import com.jntoolz.counter.CounterWidgets
import com.jntoolz.counter.SizeDp
import com.jntoolz.ui.theme.NothingFont
import com.jntoolz.ui.theme.NothingFonts
import androidx.compose.ui.platform.LocalContext
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

/**
 * Bottom sheet to add ([initial] null) or edit a counter. The floating APPLY calls [onSave] with the edits;
 * the sheet stays open unless the caller closes it, and APPLY dims until something changes again.
 */
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
    var cardStyle by remember(base) { mutableStateOf(base.cardStyle) }
    var live by remember(base) { mutableStateOf(base.liveClock) }
    val shape = remember(base) { if (base.shape == CounterShape.WIDE) CounterShape.WIDE else CounterShape.SQUARE }
    // Two faces to toggle between: "Theme" (null) and Ndot 77.
    var font by remember(base) { mutableStateOf(base.font?.takeIf { it == NothingFont.NDOT_77 }) }
    val context = LocalContext.current
    // A counter already on the home screen previews at its real widget size.
    val placed by produceState<SizeDp?>(null, base) {
        if (initial != null) {
            value = withContext(Dispatchers.IO) { runCatching { CounterWidgets.placedSizes(context)[base.id] }.getOrNull() }
        }
    }
    // null first: "Theme" (the default face). Font files load on IO; the row appears once they have.
    val fonts by produceState(listOf<NothingFont?>(null), base) {
        value = withContext(Dispatchers.IO) {
            NothingFonts.warm()
            listOf<NothingFont?>(null) + listOf(NothingFont.NDOT_77).filter { NothingFonts.available(it) }
        }
    }
    var pickDate by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }

    val draft = base.copy(
        title = title.trim(), date = date, time = time, cardStyle = cardStyle, liveClock = live && time != null,
        shape = shape, font = font,
    )
    // What was last applied (the counter as opened, or null while a new one is unsaved): APPLY is lit when they differ.
    val opened = remember(base) { draft }
    var applied by remember(base) { mutableStateOf(if (initial != null) opened else null) }
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
            Box(Modifier.fillMaxWidth().fillMaxHeight()) {
                // Like the Battery widget editor: the live preview stays pinned at the top, only the options scroll.
                Column(Modifier.fillMaxWidth().fillMaxHeight()) {
                    CounterWidgetTile(
                        previewDraft, previewSize,
                        modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 10.dp),
                        maxHeight = minOf(240.dp, pinnedPreviewMaxHeight() - 36.dp),
                    )
                    HorizontalDivider(thickness = 1.dp, color = Color(0xFF222222))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            // The options (and their scrim) never draw up over the pinned preview box.
                            .clipToBounds()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 20.dp)
                                .navigationBarsPadding()
                                .padding(top = 10.dp, bottom = ApplyButtonClearance),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
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

                            // Same controls as the Calendar cards and the Battery widget background.
                            Column {
                                CardStyleEditor(
                                    style = cardStyle,
                                    onChange = { cardStyle = it },
                                    autoColor = CounterRenderer.autoCardColor(),
                                )
                                if (fonts.size > 1) {
                                    ThemeRow("Font") {
                                        SegmentedControl(
                                            items = fonts.map { it to (it?.label ?: "Theme") },
                                            selected = font,
                                            onSelect = { font = it },
                                            modifier = Modifier.weight(1f).padding(vertical = 4.dp),
                                            fontFamily = { f -> if (f != null) NothingFonts.family(f) else FontFamily.Monospace }
                                        )
                                    }
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                Column(Modifier.weight(1f)) {
                                    Label("LIVE CLOCK")
                                    if (time == null) {
                                        Text("set a time to enable", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = Color(0xFF8E8E8E))
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
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(12.dp)
                                .align(Alignment.TopCenter)
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(CounterColors.Dialog, Color.Transparent)
                                    )
                                )
                        )
                    }
                }
                val deletable = applied
                if (deletable != null && onDelete != null) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .navigationBarsPadding()
                            .padding(start = 20.dp, bottom = 16.dp)
                            .height(44.dp)
                            .clip(RoundedCornerShape(50))
                            .border(1.dp, CounterColors.Red, RoundedCornerShape(50))
                            .background(Color.Transparent)
                            .clickable { onDelete(deletable) }
                            .padding(horizontal = 20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "DELETE",
                            color = CounterColors.Red,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.5.sp,
                            maxLines = 1
                        )
                    }
                }
                ApplyButton(
                    dirty = applied != draft,
                    onApply = {
                        onSave(draft)
                        applied = draft
                    },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .applyButtonPadding()
                )
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
