package com.jntoolz.ui.sot

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/** One card per day, newest first; tapping one drops down that day's graph (the picture only) with a copy icon. */
@Composable
internal fun HistorySection(vm: SotViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        for (day in vm.days) key(day.from) {
            DayCard(day, vm.openDay == day.from, { vm.toggleDay(day.from) }, vm::dayText)
        }
    }
}

@Composable
private fun DayCard(day: DayUi, open: Boolean, onTap: () -> Unit, text: suspend (DayUi) -> String) {
    val chevron by animateFloatAsState(if (open) 90f else 0f, tween(200), label = "chevron")
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    SotCard(0.dp, Modifier.animateContentSize(tween(200)), border = if (open) SotColors.Red else SotColors.Border) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .tap(onTap),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SotText(day.name.uppercase(), 13.sp, modifier = Modifier.weight(1f))
            if (open && day.graph != null) {
                CopyButton("Copy graph data") { scope.launch { copyGraph(ctx, text(day)) } }
                Spacer(Modifier.width(10.dp))
            }
            SotNumber(day.sot, 20.sp)
            SotText(
                "›",
                20.sp,
                SotColors.Dim,
                bold = false,
                modifier = Modifier
                    .padding(start = 14.dp, end = 2.dp)
                    .rotate(chevron)
            )
        }
        val graph = day.graph
        if (open && graph != null) {
            SotGraph(
                graph,
                Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 16.dp)
                    .height(150.dp),
                day.rangeFrom,
                day.rangeTo,
                axis = false
            )
        }
    }
}
