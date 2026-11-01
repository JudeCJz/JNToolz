package com.jntoolz.widget

import java.util.Objects
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.jntoolz.R
import com.jntoolz.data.CalendarEvent
import com.jntoolz.data.CalendarRepository
import com.jntoolz.data.ThemeMath
import com.jntoolz.data.ThemeSettings
import com.jntoolz.data.WidgetPreferences

class NothingWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        return NothingViewsFactory(applicationContext, intent.getIntExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID, 0))
    }
}

private sealed class TimelineListItem {
    data class Header(val title: String) : TimelineListItem()
    object Divider : TimelineListItem()
    data class EventItem(
        val event: CalendarEvent,
        val isFirstToday: Boolean,
        val isToday: Boolean,
        val eventIndex: Int
    ) : TimelineListItem()
}

private class Snapshot(
    val items: List<TimelineListItem>,
    val theme: ThemeSettings,
    val cardWidthDp: Int = 222
)

private class NothingViewsFactory(private val context: Context, private val appWidgetId: Int) : RemoteViewsService.RemoteViewsFactory {

    private val repository = CalendarRepository(context)
    private val prefs = WidgetPreferences(context)

    @Volatile
    private var snapshot = Snapshot(emptyList(), ThemeSettings.from(prefs))

    override fun onCreate() {}

    private fun computeCardWidthDp(): Int {
        val opts = android.appwidget.AppWidgetManager.getInstance(context).getAppWidgetOptions(appWidgetId)
        val w = opts.getInt(android.appwidget.AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 250).coerceAtLeast(60)
        return w - 28
    }

    @Synchronized
    override fun onDataSetChanged() {
        val theme = ThemeSettings.from(prefs)
        val cardWidth = computeCardWidthDp()
        val built = ArrayList<TimelineListItem>()

        if (repository.hasCalendarPermission() && prefs.isCalendarToolEnabled) {
            val allEvents = repository.getUpcomingEvents(daysAhead = 180)
                .distinctBy { it.eventId to it.startTime }
            val dayStart = java.util.Calendar.getInstance().apply {
                set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0)
                set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
            }
            val from = dayStart.timeInMillis
            dayStart.add(java.util.Calendar.DAY_OF_YEAR, 1)
            val to = dayStart.timeInMillis
            val (todayEvents, upcomingEvents) = allEvents.partition { it.startTime in from until to }

            var currentEventIdx = 0

            if (todayEvents.isNotEmpty()) {
                built.add(TimelineListItem.Header("T O D A Y"))
                todayEvents.forEachIndexed { idx, ev ->
                    built.add(
                        TimelineListItem.EventItem(
                            event = ev,
                            isFirstToday = (idx == 0),
                            isToday = true,
                            eventIndex = currentEventIdx++
                        )
                    )
                }
                built.add(TimelineListItem.Divider)
            }

            if (upcomingEvents.isNotEmpty()) {
                built.add(TimelineListItem.Header("U P C O M I N G"))
                upcomingEvents.forEach { ev ->
                    built.add(
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

        // Single atomic swap
        snapshot = Snapshot(built, theme, cardWidth)
    }

    override fun onDestroy() {
        snapshot = Snapshot(emptyList(), snapshot.theme, snapshot.cardWidthDp)
    }

    override fun getCount(): Int = snapshot.items.size

    override fun getViewAt(position: Int): RemoteViews? {
        val snap = snapshot
        if (position < 0 || position >= snap.items.size) return null
        val theme = snap.theme

        return when (val item = snap.items[position]) {
            is TimelineListItem.Header -> {
                val views = RemoteViews(context.packageName, R.layout.item_section_header)
                views.setTextViewText(R.id.tv_section_title, item.title)
                views.setTextColor(R.id.tv_section_title, ThemeMath.headerColor(theme.bgColor))
                views
            }
            is TimelineListItem.Divider -> {
                val views = RemoteViews(context.packageName, R.layout.item_divider)
                views.setInt(R.id.iv_divider, "setBackgroundColor", ThemeMath.dividerColor(theme.bgColor))
                views
            }
            is TimelineListItem.EventItem -> {
                val views = RemoteViews(context.packageName, R.layout.item_event_box)
                val event = item.event
                val look = ThemeMath.resolve(theme, event.color, item.eventIndex, item.isToday, item.isFirstToday)

                // 1. Card background
                if (look.gradient != null) {
                    val gradRes = when (look.gradient) {
                        "RUBY" -> R.drawable.box_gradient_ruby
                        "CYBER" -> R.drawable.box_gradient_cyber
                        "AMBER" -> R.drawable.box_gradient_amber
                        else -> R.drawable.box_gradient_charcoal
                    }
                    views.setImageViewResource(R.id.iv_box_bg, gradRes)
                    views.setInt(R.id.iv_box_bg, "setColorFilter", 0)
                } else if (look.lightGradient) {
                    views.setImageViewBitmap(
                        R.id.iv_box_bg,
                        WidgetBitmaps.card(context, look.fillTop, look.fill, look.fillAlpha, snap.cardWidthDp, 56)
                    )
                    views.setInt(R.id.iv_box_bg, "setColorFilter", 0)
                } else {
                    views.setImageViewResource(R.id.iv_box_bg, R.drawable.box_event_shape)
                    views.setInt(R.id.iv_box_bg, "setColorFilter", look.fill)
                }
                views.setInt(R.id.iv_box_bg, "setImageAlpha", if (look.lightGradient) 255 else look.fillAlpha)

                // 2. Marker
                if (look.markerVisible) {
                    val res = when (theme.markerStyle) {
                        "RING" -> R.drawable.marker_ring
                        "BAR" -> R.drawable.marker_bar
                        else -> R.drawable.marker_dot
                    }
                    views.setViewVisibility(R.id.iv_marker, View.VISIBLE)
                    views.setImageViewResource(R.id.iv_marker, res)
                    views.setInt(R.id.iv_marker, "setColorFilter", look.markerColor)
                } else {
                    views.setViewVisibility(R.id.iv_marker, View.GONE)
                }

                // 3. Text
                views.setTextColor(R.id.tv_title, look.title)
                views.setTextColor(R.id.tv_subtitle, look.sub)
                views.setTextViewText(R.id.tv_title, event.title)
                views.setTextViewText(
                    R.id.tv_subtitle,
                    if (item.isToday) event.getFormattedTime() else event.getFormattedUpcomingDateTime()
                )

                // Fill-in intent to open Google Calendar at event time
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

    override fun getItemId(position: Int): Long {
        val items = snapshot.items
        if (position < 0 || position >= items.size) return position.toLong()
        return when (val item = items[position]) {
            is TimelineListItem.Header -> Objects.hash("H", item.title).toLong()
            is TimelineListItem.Divider -> Objects.hash("D").toLong()
            is TimelineListItem.EventItem ->
                Objects.hash("E", item.event.eventId, item.event.startTime).toLong()
        }
    }

    override fun hasStableIds(): Boolean = true
}
