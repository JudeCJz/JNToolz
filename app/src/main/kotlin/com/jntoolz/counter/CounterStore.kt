package com.jntoolz.counter

import android.content.Context
import android.content.SharedPreferences
import com.jntoolz.data.CardMath
import com.jntoolz.data.CardStyle
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
        get() = prefs.getBoolean(KEY_ENABLED, false)
        set(value) {
            prefs.edit().putBoolean(KEY_ENABLED, value).apply()
        }

    /** Whether the tool switch was ever written (an install from before the default turned off has it unset). */
    val hasEnabledFlag: Boolean
        get() = prefs.contains(KEY_ENABLED)

    fun counterIdForWidget(appWidgetId: Int): String? = prefs.getString(KEY_WIDGET + appWidgetId, null)

    fun setWidgetCounter(appWidgetId: Int, counterId: String) {
        prefs.edit().putString(KEY_WIDGET + appWidgetId, counterId).commit()
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
        prefs.edit().putString(KEY_LIST, arr.toString()).commit()
    }

    companion object {
        const val FILE = "counters"
        private const val KEY_LIST = "list"
        private const val KEY_WIDGET = "widget_"
        private const val KEY_ENABLED = "enabled"
        /**
         * JSON format: 2 = the 7-theme set (older counters may need [CounterTheme.legacyFont]);
         * 3 = no themes, the look is the card style + font only (theme is written as BLACK for older versions).
         */
        private const val FORMAT = 3
        /** Gradient angle of saves from before the shared card style: the old top-left to bottom-right diagonal. */
        private const val LEGACY_ANGLE = 45
        /** Card colour of the removed WHITE / YOU SOFT themes. */
        const val LIGHT_CARD = 0xFFF4F4F4.toInt()
        /** See-through amount given to counters of the removed GLASS theme (its card was 50% black). */
        const val LEGACY_GLASS_LEVEL = 50

        fun newId(): String = UUID.randomUUID().toString()

        fun toJson(c: Counter): JSONObject = JSONObject().apply {
            put("id", c.id)
            put("title", c.title)
            put("date", c.date.toString())
            c.time?.let { put("time", it.toString()) }
            put("theme", CounterTheme.BLACK.name)
            if (c.cardStyle != CardStyle()) put("card", styleToJson(c.cardStyle))
            put("live", c.liveClock)
            put("shape", c.shape.name)
            c.font?.let { put("font", it.name) }
            put("v", FORMAT)
        }

        /**
         * A removed theme folded into the card style: WHITE / YOU SOFT become the light card (when no colour was
         * picked), GLASS becomes glass; the others (BLACK, RED, PROGRESS, YOU) were dark cards and change nothing.
         */
        fun migrateTheme(theme: CounterTheme, s: CardStyle): CardStyle = when (theme) {
            CounterTheme.WHITE, CounterTheme.YOU_SOFT -> if (s.color == 0) s.copy(color = LIGHT_CARD) else s
            CounterTheme.GLASS -> if (s.glass) s else s.copy(glass = true, glassLevel = LEGACY_GLASS_LEVEL)
            else -> s
        }

        /** Saves from before the shared card style: a gradient preset name + angle becomes a [CardStyle]. */
        fun legacyStyle(preset: String?, angle: Int): CardStyle =
            CounterGradient.of(preset).toStyle(angle) ?: CardStyle()

        private fun styleToJson(s: CardStyle) = JSONObject().apply {
            put("color", s.color.toLong() and 0xFFFFFFFFL)
            put("gradient", s.gradient)
            put("top", s.gradientTop.toLong() and 0xFFFFFFFFL)
            put("intensity", s.intensity)
            put("angle", s.angle)
            put("glass", s.glass)
            put("glassLevel", s.glassLevel)
        }

        private fun styleFromJson(o: JSONObject): CardStyle {
            val d = CardStyle()
            return CardStyle(
                color = o.optLong("color", d.color.toLong()).toInt(),
                gradient = o.optBoolean("gradient", d.gradient),
                gradientTop = o.optLong("top", d.gradientTop.toLong()).toInt(),
                intensity = o.optInt("intensity", d.intensity).coerceIn(0, 100),
                angle = CardMath.normalize(o.optInt("angle", d.angle)),
                glass = o.optBoolean("glass", d.glass),
                glassLevel = o.optInt("glassLevel", d.glassLevel).coerceIn(0, 100),
            )
        }

        fun fromJson(o: JSONObject?): Counter? {
            o ?: return null
            return try {
                val themeName = o.optString("theme")
                val style = o.optJSONObject("card")?.let { styleFromJson(it) } ?: legacyStyle(
                    o.optString("gradient", "").takeIf { it.isNotEmpty() },
                    o.optInt("gradientAngle", LEGACY_ANGLE)
                )
                Counter(
                    id = o.getString("id"),
                    title = o.optString("title", ""),
                    date = LocalDate.parse(o.getString("date")),
                    time = o.optString("time", "").takeIf { it.isNotEmpty() }?.let { LocalTime.parse(it) },
                    cardStyle = migrateTheme(CounterTheme.of(themeName), style),
                    liveClock = o.optBoolean("live", false),
                    shape = CounterShape.of(o.optString("shape").takeIf { it.isNotEmpty() }),
                    font = Counter.allowedFont(
                        o.optString("font", "").takeIf { it.isNotEmpty() }?.let { NothingFont.of(it) }
                            ?: CounterTheme.legacyFont(themeName, o.optInt("v", 1))
                    ),
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}
