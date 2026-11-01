package com.jntoolz.ui.sot

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** One card per day, newest first; tapping one drops down that whole day's graph and its sessions. */
@Composable
internal fun HistoryTab(vm: SotViewModel, scroll: ScrollState) {
    SotPage(scroll) {
        for (day in vm.days) key(day.from) {
            DayCard(day, vm.openDay == day.from) { vm.toggleDay(day.from) }
        }
    }
}

@Composable
private fun DayCard(day: DayUi, open: Boolean, onTap: () -> Unit) {
    val chevron by animateFloatAsState(if (open) 90f else 0f, tween(200), label = "chevron")
    SotCard(0.dp, Modifier.animateContentSize(tween(200)), border = if (open) SotColors.Red else SotColors.Border) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .tap(onTap),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SotText(day.name.uppercase(), 13.sp, modifier = Modifier.weight(1f))
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
            Column(Modifier.padding(bottom = 16.dp)) {
                SotGraph(
                    graph,
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                        .height(190.dp),
                    day.rangeFrom,
                    day.rangeTo
                )
                Column(Modifier.padding(top = 12.dp)) {
                    for ((span, sot) in day.sessions) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 40.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SotText(span, 12.sp, SotColors.Dim, bold = false, modifier = Modifier.weight(1f))
                            SotText(sot, 12.sp)
                        }
                    }
                }
            }
        }
    }
}
