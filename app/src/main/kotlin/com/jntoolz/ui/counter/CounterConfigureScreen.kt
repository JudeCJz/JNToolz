package com.jntoolz.ui.counter

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jntoolz.counter.Counter
import com.jntoolz.counter.CounterStore
import com.jntoolz.counter.SizeDp

/** Widget configure screen: tap a counter to show it, or "+" to make one (it is picked on save). */
@Composable
fun CounterConfigureScreen(
    currentId: String?,
    onPick: (Counter) -> Unit,
    onClose: () -> Unit,
    /** Size of the widget being configured, when the launcher already reported it. */
    widgetSize: SizeDp? = null,
    /** The counter this widget already shows: its editor opens straight away (CHANGE leads to the list). */
    editing: Counter? = null,
) {
    val context = LocalContext.current
    val store = remember { CounterStore(context) }
    var counters by remember { mutableStateOf(store.all()) }
    var adding by remember { mutableStateOf(editing == null && counters.isEmpty()) }
    var editingCounter by remember { mutableStateOf(editing) }

    Scaffold(
        containerColor = CounterColors.Bg,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CounterColors.Bg)
                    .statusBarsPadding()
                    .height(56.dp)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BackArrow(onClose)
                Spacer(Modifier.width(8.dp))
                Text(
                    "COUNTER",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 2.sp,
                    color = CounterColors.Text,
                    modifier = Modifier.weight(1f)
                )
                if (editingCounter != null) {
                    Text(
                        "CHANGE",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp,
                        color = CounterColors.Text,
                        modifier = Modifier.clickable { editingCounter = null }.padding(8.dp)
                    )
                } else {
                    AddButton { adding = true }
                }
                Spacer(Modifier.width(12.dp))
            }
        }
    ) { inner ->
        CounterList(
            counters = counters,
            onClick = onPick,
            contentPadding = inner,
            selectedId = currentId,
            forcedSize = widgetSize,
        )
    }

    editingCounter?.let { current ->
        CounterEditorSheet(
            initial = current,
            onDismiss = { editingCounter = null },
            // Reconfiguring a placed widget: APPLY saves and redraws it, the editor stays open.
            onSave = {
                store.save(it)
                counters = store.all()
                refreshWidgets(context)
            }
        )
    }

    if (adding) {
        CounterEditorSheet(
            initial = null,
            onDismiss = { adding = false },
            // A new counter is picked for this widget: APPLY saves it and finishes, so a new widget gets placed.
            onSave = {
                store.save(it)
                counters = store.all()
                adding = false
                refreshWidgets(context)
                onPick(it)
            }
        )
    }
}
