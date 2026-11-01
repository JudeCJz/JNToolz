package com.jntoolz.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Velocity
import com.jntoolz.data.CalendarEvent
import com.jntoolz.data.ThemeMath
import com.jntoolz.data.ThemeSettings

@Composable
internal fun NothingWidgetPreviewCard(
    todayEvents: List<CalendarEvent>,
    upcomingEvents: List<CalendarEvent>,
    theme: ThemeSettings,
    onEventClick: (CalendarEvent) -> Unit
) {
    val headerColor = Color(ThemeMath.headerColor(theme.bgColor))

    Card(
        modifier = Modifier.fillMaxWidth().height(360.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color(theme.bgColor)),
        border = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                // Swallow leftover scroll/fling so reaching the end doesn't scroll the page
                .nestedScroll(ConsumeRemainingScroll)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // TODAY SECTION
            if (todayEvents.isNotEmpty()) {
                Text(
                    text = "T O D A Y",
                    color = headerColor,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(bottom = 10.dp, start = 4.dp)
                )

                todayEvents.forEachIndexed { index, event ->
                    EventBoxItem(
                        event = event,
                        isToday = true,
                        isFirstToday = (index == 0),
                        eventIndex = index,
                        theme = theme,
                        onClick = { onEventClick(event) }
                    )
                }

                HorizontalDivider(
                    modifier = Modifier.padding(top = 4.dp, bottom = 10.dp),
                    thickness = 1.dp,
                    color = Color(ThemeMath.dividerColor(theme.bgColor))
                )
            }

            // UPCOMING SECTION
            if (upcomingEvents.isNotEmpty() || todayEvents.isEmpty()) {
                Text(
                    text = "U P C O M I N G",
                    color = headerColor,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(bottom = 10.dp, start = 4.dp)
                )

                if (upcomingEvents.isEmpty()) {
                    Text(
                        text = "No upcoming events",
                        color = headerColor,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(start = 12.dp, top = 2.dp, bottom = 8.dp)
                    )
                } else {
                    upcomingEvents.forEachIndexed { index, event ->
                        EventBoxItem(
                            event = event,
                            isToday = false,
                            isFirstToday = false,
                            eventIndex = todayEvents.size + index,
                            theme = theme,
                            onClick = { onEventClick(event) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun EventBoxItem(
    event: CalendarEvent,
    isToday: Boolean,
    isFirstToday: Boolean,
    eventIndex: Int,
    theme: ThemeSettings,
    onClick: () -> Unit
) {
    val look = ThemeMath.resolve(theme, event.color, eventIndex, isToday, isFirstToday)
    val a = look.fillAlpha / 255f
    val cardModifier = if (look.gradient != null) {
        Modifier.background(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color(ThemeMath.gradientStart(look.gradient)).copy(alpha = a),
                    Color(ThemeMath.gradientEnd(look.gradient)).copy(alpha = a)
                )
            )
        )
    } else if (look.lightGradient) {
        Modifier.background(
            brush = Brush.verticalGradient(
                listOf(Color(look.fillTop).copy(alpha = a), Color(look.fill).copy(alpha = a))
            )
        )
    } else {
        Modifier.background(Color(look.fill).copy(alpha = a))
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 5.dp)
            .height(56.dp)
            .clip(RoundedCornerShape(14.dp))
            .then(cardModifier)
            .clickable { onClick() }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (look.markerVisible) {
            val mc = Color(look.markerColor)
            when (theme.markerStyle) {
                "RING" -> Box(modifier = Modifier.size(8.dp).border(1.5.dp, mc, CircleShape))
                "BAR" -> Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(28.dp)
                        .clip(RoundedCornerShape(1.5.dp))
                        .background(mc)
                )
                else -> Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(mc))
            }
            Spacer(modifier = Modifier.width(10.dp))
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = event.title,
                color = Color(look.title),
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
                lineHeight = 13.sp,
                style = LocalTextStyle.current.copy(platformStyle = PlatformTextStyle(includeFontPadding = false)),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = if (isToday) event.getFormattedTime() else event.getFormattedUpcomingDateTime(),
                color = Color(look.sub),
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                lineHeight = 11.sp,
                style = LocalTextStyle.current.copy(platformStyle = PlatformTextStyle(includeFontPadding = false)),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

private val ConsumeRemainingScroll = object : NestedScrollConnection {
    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource) = available
    override suspend fun onPostFling(consumed: Velocity, available: Velocity) = available
}
