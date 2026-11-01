package com.jntoolz.ui.counter

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jntoolz.counter.Counter
import com.jntoolz.counter.CounterStore
import com.jntoolz.counter.CounterWidgets
import com.jntoolz.counter.SizeDp
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The Counter tool: list of counters with live widget previews, add / edit / delete. */
@Composable
fun CounterScreen(onBack: () -> Unit) {
    BackHandler { onBack() }
    val context = LocalContext.current
    val store = remember { CounterStore(context) }
    var counters by remember { mutableStateOf(store.all()) }
    var editing by remember { mutableStateOf<Counter?>(null) }
    var adding by remember { mutableStateOf(false) }

    fun changed() {
        counters = store.all()
        refreshWidgets(context)
    }

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
                BackArrow(onBack)
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
                AddButton { adding = true }
                Spacer(Modifier.width(12.dp))
            }
        }
    ) { inner ->
        CounterList(
            counters = counters,
            onClick = { editing = it },
            contentPadding = inner,
        )
    }

    if (adding || editing != null) {
        CounterEditorSheet(
            initial = editing,
            onDismiss = { adding = false; editing = null },
            onSave = {
                store.save(it)
                adding = false; editing = null
                changed()
            },
            onDelete = {
                store.delete(it.id)
                adding = false; editing = null
                changed()
            }
        )
    }
}

/** Redraws counter widgets on the counter worker thread; outlives the screen (the configure activity finishes right after). */
internal fun refreshWidgets(context: Context) = CounterWidgets.onCountersChanged(context)

/**
 * Counters, each shown as exactly its home-screen widget (shared with the widget configure screen).
 * Size: [forcedSize] when given (the widget being configured), else the real size of a placed widget
 * showing that counter, else the counter's shape default.
 */
@Composable
internal fun CounterList(
    counters: List<Counter>,
    onClick: (Counter) -> Unit,
    contentPadding: PaddingValues,
    selectedId: String? = null,
    forcedSize: SizeDp? = null,
) {
    val context = LocalContext.current
    // Re-read placed widget sizes whenever the screen comes back (the user may have resized one).
    var resumes by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        resumes++
        onPauseOrDispose { }
    }
    // Binder calls + a JSON parse: on IO, never in composition.
    val placed by produceState(emptyMap<String, SizeDp>(), counters, resumes) {
        value = withContext(Dispatchers.IO) { runCatching { CounterWidgets.placedSizes(context) }.getOrDefault(emptyMap()) }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        if (counters.isEmpty()) {
            item {
                Text(
                    "NO COUNTERS",
                    color = Color(0xFF3A3A3A),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    letterSpacing = 1.5.sp,
                    modifier = Modifier.fillMaxWidth().padding(top = 40.dp),
                    textAlign = TextAlign.Center
                )
            }
        }
        items(counters, key = { it.id }) { c ->
            CounterWidgetTile(
                counter = c,
                size = forcedSize ?: placed[c.id] ?: c.shape.size,
                selected = c.id == selectedId,
                onClick = { onClick(c) },
            )
        }
    }
}

@Composable
internal fun AddButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(CounterColors.Card)
            .clickable { onClick() }
            .semantics { contentDescription = "Add counter" },
        contentAlignment = Alignment.Center
    ) {
        PlusMark(CounterColors.Text)
    }
}
