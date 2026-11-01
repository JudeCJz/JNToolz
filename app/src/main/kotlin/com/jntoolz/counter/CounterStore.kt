package com.jntoolz.counter

import android.content.Context
import android.content.SharedPreferences
import com.jntoolz.ui.theme.NothingFont
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

/** Counters as JSON plus the appWidgetId -> counterId map, in the "counters" prefs file. */
class CounterStore(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun all(): List<Counter> {
        val raw = prefs.getString(KEY_LIST, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { fromJson(arr.optJSONObject(it)) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun get(id: String?): Counter? = id?.let { i -> all().firstOrNull { it.id == i } }

    /** Adds or replaces by id, keeping list order. */
    @Synchronized
    fun save(counter: Counter) {
        val list = all().toMutableList()
        val i = list.indexOfFirst { it.id == counter.id }
        if (i >= 0) list[i] = counter else list.add(counter)
        write(list)
    }

    @Synchronized
    fun delete(id: String) {
        write(all().filterNot { it.id == id })
        // Widgets showing it fall back to the first counter; drop the stale mapping.
        val e = prefs.edit()
        for ((k, v) in prefs.all) if (k.startsWith(KEY_WIDGET) && v == id) e.remove(k)
        e.apply()
    }

    /** Tool switch on the hub card; off widgets show a dimmed logo and no alarm runs. */
    var enabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, true)
        set(value) {
            prefs.edit().putBoolean(KEY_ENABLED, value).apply()
        }

    fun counterIdForWidget(appWidgetId: Int): String? = prefs.getString(KEY_WIDGET + appWidgetId, null)

    fun setWidgetCounter(appWidgetId: Int, counterId: String) {
        prefs.edit().putString(KEY_WIDGET + appWidgetId, counterId).apply()
    }

    fun removeWidgets(ids: IntArray) {
        val e = prefs.edit()
        for (id in ids) e.remove(KEY_WIDGET + id)
        e.apply()
    }

    /** The counter a widget shows: its picked one, else the first, else null (empty "+" state). Pass [list] to reuse one parse. */
    fun counterForWidget(appWidgetId: Int, list: List<Counter> = all()): Counter? {
        val id = counterIdForWidget(appWidgetId)
        return list.firstOrNull { it.id == id } ?: list.firstOrNull()
    }

    private fun write(list: List<Counter>) {
        val arr = JSONArray()
        list.forEach { arr.put(toJson(it)) }
        prefs.edit().putString(KEY_LIST, arr.toString()).apply()
    }

    companion object {
        const val FILE = "counters"
        private const val KEY_LIST = "list"
        private const val KEY_WIDGET = "widget_"
        private const val KEY_ENABLED = "enabled"
        /** JSON format: 2 = the 7-theme set (older counters may need [CounterTheme.legacyFont]). */
        private const val FORMAT = 2

        fun newId(): String = UUID.randomUUID().toString()

        fun toJson(c: Counter): JSONObject = JSONObject().apply {
            put("id", c.id)
            put("title", c.title)
            put("date", c.date.toString())
            c.time?.let { put("time", it.toString()) }
            put("theme", c.theme.name)
            if (c.gradient != CounterGradient.NONE) {
                put("gradient", c.gradient.name)
            }
            put("live", c.liveClock)
            put("created", c.created.toString())
            put("shape", c.shape.name)
            c.font?.let { put("font", it.name) }
            put("v", FORMAT)
        }

        fun fromJson(o: JSONObject?): Counter? {
            o ?: return null
            return try {
                val themeName = o.optString("theme")
                val date = LocalDate.parse(o.getString("date"))
                Counter(
                    id = o.getString("id"),
                    title = o.optString("title", ""),
                    date = date,
                    time = o.optString("time", "").takeIf { it.isNotEmpty() }?.let { LocalTime.parse(it) },
                    theme = CounterTheme.of(themeName),
                    gradient = o.optString("gradient", "").takeIf { it.isNotEmpty() }?.let { CounterGradient.of(it) } ?: CounterGradient.NONE,
                    liveClock = o.optBoolean("live", false),
                    created = o.optString("created", "").takeIf { it.isNotEmpty() }
                        ?.let { LocalDate.parse(it) } ?: minOf(date, LocalDate.now()),
                    shape = CounterShape.of(o.optString("shape").takeIf { it.isNotEmpty() }),
                    font = o.optString("font", "").takeIf { it.isNotEmpty() }?.let { NothingFont.of(it) }
                        ?: CounterTheme.legacyFont(themeName, o.optInt("v", 1)),
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}
