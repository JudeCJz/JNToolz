package com.jntoolz.ui.main

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jntoolz.data.CalendarInfo

@Composable
internal fun CalendarAccountsCard(
    grouped: Map<String, List<CalendarInfo>>,
    enabledIds: Set<String>,
    onToggle: (CalendarInfo, Boolean) -> Unit,
    linked: Set<String> = emptySet(),
    onLinkToggle: (String, Boolean) -> Unit = { _, _ -> }
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        border = BorderStroke(1.dp, DividerGrey)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            grouped.entries.forEachIndexed { index, (account, cals) ->
                if (index > 0) HorizontalDivider(color = DividerGrey, thickness = 1.dp)
                CalendarAccountRow(account, cals, enabledIds, onToggle, account in linked, onLinkToggle)
            }
        }
    }
}

@Composable
private fun CalendarAccountRow(
    accountName: String,
    calendars: List<CalendarInfo>,
    enabledIds: Set<String>,
    onToggle: (CalendarInfo, Boolean) -> Unit,
    isLinked: Boolean,
    onLinkToggle: (String, Boolean) -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }
    val selectedCount = calendars.count { enabledIds.contains(it.id.toString()) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { isExpanded = !isExpanded }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = accountName,
                    color = Color(0xFFE0E0E0),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "$selectedCount of ${calendars.size} calendars active",
                    color = MutedGrey,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (isLinked) Color(0xFF222222) else AccentRed)
                    .clickable { onLinkToggle(accountName, isLinked) }
                    .padding(horizontal = 12.dp, vertical = 5.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isLinked) "LINKED" else "LINK",
                    color = if (isLinked) Color(0xFF8E8E8E) else TextWhite,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = if (isExpanded) "▲" else "▼",
                color = Color(0xFF8E8E8E),
                fontSize = 11.sp
            )
        }

        if (isExpanded) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)) {
                calendars.forEach { cal ->
                    val isChecked = enabledIds.contains(cal.id.toString())
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onToggle(cal, !isChecked) }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = isChecked,
                            onCheckedChange = { onToggle(cal, it) },
                            colors = CheckboxDefaults.colors(
                                checkedColor = AccentRed,
                                uncheckedColor = Color(0xFF444444),
                                checkmarkColor = TextWhite
                            )
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(Color(cal.color))
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = cal.displayName,
                                color = if (isChecked) TextWhite else MutedGrey,
                                fontSize = 13.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = if (isChecked) FontWeight.SemiBold else FontWeight.Normal
                            )
                        }
                    }
                }
            }
        }
    }
}
