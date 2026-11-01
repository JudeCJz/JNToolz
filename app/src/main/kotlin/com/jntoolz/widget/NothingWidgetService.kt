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
        val eventIndex: Int
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
                val useGoogleColors = prefs.useGoogleEventColors
                val googleColorMode = prefs.googleColorDisplayMode
                val isFadeEnabled = prefs.isFadeEnabled
                val gradientStyle = prefs.cardGradientStyle
                val baseCardColor = prefs.customCardColor.toInt()
                val widgetBgColor = prefs.widgetBackgroundColor.toInt()

                // 1. Box background & color
                if (useGoogleColors && googleColorMode == "BOX") {
                    views.setImageViewResource(R.id.iv_box_bg, R.drawable.box_event_shape)
                    val cardTint = getGoogleBoxTintColor(event.color, item.eventIndex, isFadeEnabled)
                    views.setInt(R.id.iv_box_bg, "setColorFilter", cardTint)
                } else if (!useGoogleColors && gradientStyle != "NONE") {
                    val gradRes = when (gradientStyle) {
                        "RUBY" -> R.drawable.box_gradient_ruby
                        "CYBER" -> R.drawable.box_gradient_cyber
                        "AMBER" -> R.drawable.box_gradient_amber
                        else -> R.drawable.box_gradient_charcoal
                    }
                    views.setImageViewResource(R.id.iv_box_bg, gradRes)
                    if (isFadeEnabled) {
                        val alpha = (255 * (1f - (item.eventIndex.coerceIn(0, 4) * 0.18f))).toInt().coerceIn(40, 255)
                        views.setInt(R.id.iv_box_bg, "setImageAlpha", alpha)
                    } else {
                        views.setInt(R.id.iv_box_bg, "setImageAlpha", 255)
                    }
                    views.setInt(R.id.iv_box_bg, "setColorFilter", 0)
                } else {
                    views.setImageViewResource(R.id.iv_box_bg, R.drawable.box_event_shape)
                    views.setInt(R.id.iv_box_bg, "setImageAlpha", 255)
                    val color = if (isFadeEnabled) {
                        getFadeColor(baseCardColor, widgetBgColor, item.eventIndex)
                    } else {
                        baseCardColor
                    }
                    views.setInt(R.id.iv_box_bg, "setColorFilter", color)
                }

                // 2. Timeline Marker
                val defaultMarkerRes = when {
                    item.isFirstToday -> R.drawable.marker_today_first
                    item.isToday -> R.drawable.marker_today_other
                    else -> R.drawable.marker_upcoming
                }
                views.setImageViewResource(R.id.iv_marker, defaultMarkerRes)

                if (useGoogleColors && googleColorMode == "DOT") {
                    views.setInt(R.id.iv_marker, "setColorFilter", event.color)
                } else {
                    views.setInt(R.id.iv_marker, "setColorFilter", 0)
                }

                // 3. Text Colors
                val titleColor = if (isFadeEnabled) {
                    when (item.eventIndex) {
                        0 -> Color.parseColor("#FFFFFF")
                        1 -> Color.parseColor("#E0E0E0")
                        2 -> Color.parseColor("#BCBCBC")
                        3 -> Color.parseColor("#8E8E8E")
                        else -> Color.parseColor("#606060")
                    }
                } else {
                    Color.parseColor("#FFFFFF")
                }

                val subColor = if (isFadeEnabled) {
                    when (item.eventIndex) {
                        0 -> Color.parseColor("#A8A8A8")
                        1 -> Color.parseColor("#909090")
                        2 -> Color.parseColor("#787878")
                        3 -> Color.parseColor("#5A5A5A")
                        else -> Color.parseColor("#444444")
                    }
                } else {
                    Color.parseColor("#9E9E9E")
                }

                views.setTextColor(R.id.tv_title, titleColor)
                views.setTextColor(R.id.tv_subtitle, subColor)

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

    private fun getFadeColor(baseColor: Int, targetColor: Int, index: Int): Int {
        val fraction = (index.coerceIn(0, 4) / 4f)
        val r = (Color.red(baseColor) + fraction * (Color.red(targetColor) - Color.red(baseColor))).toInt().coerceIn(0, 255)
        val g = (Color.green(baseColor) + fraction * (Color.green(targetColor) - Color.green(baseColor))).toInt().coerceIn(0, 255)
        val b = (Color.blue(baseColor) + fraction * (Color.blue(targetColor) - Color.blue(baseColor))).toInt().coerceIn(0, 255)
        return Color.rgb(r, g, b)
    }

    private fun getGoogleBoxTintColor(color: Int, index: Int, fade: Boolean): Int {
        val factor = if (!fade) 0.22f else maxOf(0.04f, 0.26f - (index * 0.05f))
        val r = (Color.red(color) * factor).toInt().coerceIn(0, 255)
        val g = (Color.green(color) * factor).toInt().coerceIn(0, 255)
        val b = (Color.blue(color) * factor).toInt().coerceIn(0, 255)
        return Color.rgb(r, g, b)
    }

    override fun getLoadingView(): RemoteViews? = null
    override fun getViewTypeCount(): Int = 3
    override fun getItemId(position: Int): Long = position.toLong()
    override fun hasStableIds(): Boolean = false
}
