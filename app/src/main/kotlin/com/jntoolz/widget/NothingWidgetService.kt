package com.jntoolz.widget

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.jntoolz.R
import com.jntoolz.data.CalendarEvent
import com.jntoolz.data.CalendarRepository
import com.jntoolz.data.WidgetPreferences

class NothingWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        return NothingViewsFactory(applicationContext)
    }
}

private sealed class TimelineListItem {
    data class Header(val title: String) : TimelineListItem()
    object Divider : TimelineListItem()
    data class EventItem(
        val event: CalendarEvent,
        val isFirstToday: Boolean,
        val isToday: Boolean,
        val eventIndex: Int // 0 for the top-most event, 1 for second, etc.
    ) : TimelineListItem()
}

private class NothingViewsFactory(private val context: Context) : RemoteViewsService.RemoteViewsFactory {

    private val repository = CalendarRepository(context)
    private val prefs = WidgetPreferences(context)
    private val items = mutableListOf<TimelineListItem>()

    override fun onCreate() {}

    override fun onDataSetChanged() {
        items.clear()
        if (!repository.hasCalendarPermission() || !prefs.isCalendarToolEnabled) return

        val allEvents = repository.getUpcomingEvents(daysAhead = 180)
        val todayEvents = allEvents.filter { it.isToday() }
        val upcomingEvents = allEvents.filter { !it.isToday() }

        var currentEventIdx = 0

        // "if there is nothing scheduld for today dont show teh today secion at all"
        if (todayEvents.isNotEmpty()) {
            items.add(TimelineListItem.Header("T O D A Y"))
            todayEvents.forEachIndexed { idx, ev ->
                items.add(
                    TimelineListItem.EventItem(
                        event = ev,
                        isFirstToday = (idx == 0),
                        isToday = true,
                        eventIndex = currentEventIdx++
                    )
                )
            }
            items.add(TimelineListItem.Divider)
        }

        if (upcomingEvents.isNotEmpty()) {
            items.add(TimelineListItem.Header("U P C O M I N G"))
            upcomingEvents.forEach { ev ->
                items.add(
                    TimelineListItem.EventItem(
                        event = ev,
                        isFirstToday = false,
                        isToday = false,
                        eventIndex = currentEventIdx++
                    )
                )
            }
        }
    }

    override fun onDestroy() {
        items.clear()
    }

    override fun getCount(): Int = items.size

    override fun getViewAt(position: Int): RemoteViews? {
        if (position < 0 || position >= items.size) return null

        return when (val item = items[position]) {
            is TimelineListItem.Header -> {
                val views = RemoteViews(context.packageName, R.layout.item_section_header)
                views.setTextViewText(R.id.tv_section_title, item.title)
                views
            }
            is TimelineListItem.Divider -> {
                RemoteViews(context.packageName, R.layout.item_divider)
            }
            is TimelineListItem.EventItem -> {
                val views = RemoteViews(context.packageName, R.layout.item_event_box)
                val event = item.event

                // "make sure the color fading completely fades into the background color"
                val bgRes = when (item.eventIndex) {
                    0 -> R.drawable.box_event_tier_0 // #202121
                    1 -> R.drawable.box_event_tier_1 // #161717
                    2 -> R.drawable.box_event_tier_2 // #0D0E0E
                    3 -> R.drawable.box_event_tier_3 // #050505
                    else -> R.drawable.box_event_tier_4 // #000000 (completely faded into background)
                }
                views.setInt(R.id.box_container, "setBackgroundResource", bgRes)

                val titleColor = when (item.eventIndex) {
                    0 -> Color.parseColor("#FFFFFF")
                    1 -> Color.parseColor("#D8D8D8")
                    2 -> Color.parseColor("#AEAEAE")
                    3 -> Color.parseColor("#808080")
                    else -> Color.parseColor("#555555")
                }
                val subColor = when (item.eventIndex) {
                    0 -> Color.parseColor("#A0A0A0")
                    1 -> Color.parseColor("#888888")
                    2 -> Color.parseColor("#707070")
                    3 -> Color.parseColor("#505050")
                    else -> Color.parseColor("#383838")
                }

                views.setTextColor(R.id.tv_title, titleColor)
                views.setTextColor(R.id.tv_subtitle, subColor)

                views.setTextViewText(R.id.tv_title, event.title)
                views.setTextViewText(
                    R.id.tv_subtitle,
                    if (item.isToday) event.getFormattedTime() else event.getFormattedUpcomingDateTime()
                )

                // Timeline marker
                val markerRes = when {
                    item.isFirstToday -> R.drawable.marker_today_first
                    item.isToday -> R.drawable.marker_today_other
                    else -> R.drawable.marker_upcoming
                }
                views.setImageViewResource(R.id.iv_marker, markerRes)

                // Fill-in intent to open Google Calendar at that month
                val fillInIntent = Intent().apply {
                    putExtra(NothingCalendarWidgetProvider.EXTRA_EVENT_TIME, event.startTime)
                }
                views.setOnClickFillInIntent(R.id.box_container, fillInIntent)

                views
            }
        }
    }

    override fun getLoadingView(): RemoteViews? = null

    override fun getViewTypeCount(): Int = 3

    override fun getItemId(position: Int): Long = position.toLong()

    override fun hasStableIds(): Boolean = false
}
