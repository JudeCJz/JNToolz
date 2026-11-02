package com.jntoolz.data

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** On-disk cache of Google Calendar API data (colours + events), per linked account. */
class GoogleColorCache private constructor(context: Context) {
    private val file = File(context.filesDir, "google_colors.json")
    private val prefs = context.getSharedPreferences("google_link", Context.MODE_PRIVATE)

    private var colors: Map<String, Int> = emptyMap()
    private var events: List<CalendarEvent> = emptyList()
    private var labels: Map<String, List<String>> = emptyMap()
    private var fetchedAt = 0L
    private var fetchedCals: Set<Long> = emptySet()
    @Volatile private var loaded = false

    fun linkedAccounts(): Set<String> = prefs.getStringSet("linked", emptySet()) ?: emptySet()
    fun isLinked(account: String) = linkedAccounts().contains(account)
    fun setLinked(account: String, linked: Boolean) {
        val s = linkedAccounts().toMutableSet()
        if (linked) s.add(account) else s.remove(account)
        prefs.edit().putStringSet("linked", s).apply()
    }

    @Synchronized
    private fun ensureLoaded() {
        // Single process owns the file; cache is invalidated on every write below.
        if (loaded) return
        loaded = true
        val c = HashMap<String, Int>()
        val ev = ArrayList<CalendarEvent>()
        val lb = LinkedHashMap<String, List<String>>()
        var f = 0L
        val fc = HashSet<Long>()
        try {
            if (file.exists()) {
                val root = JSONObject(file.readText())
                f = root.optLong("fetchedAt")
                val accs = root.optJSONObject("accounts")
                if (accs != null) for (a in accs.keys()) {
                    val o = accs.getJSONObject(a)
                    val cs = o.optJSONObject("colors")
                    if (cs != null) for (k in cs.keys()) c[k] = cs.getInt(k)
                    o.optJSONArray("cals")?.let { a -> for (i in 0 until a.length()) fc.add(a.getLong(i)) }
                    o.optJSONObject("labels")?.let { lo ->
                        for (k in lo.keys()) {
                            val ta = lo.getJSONArray(k)
                            lb[k] = (0 until ta.length()).map { ta.getString(it) }
                        }
                    }
                    val arr = o.optJSONArray("events") ?: JSONArray()
                    for (i in 0 until arr.length()) {
                        val e = arr.getJSONObject(i)
                        ev.add(
                            CalendarEvent(
                                id = e.getLong("i"), eventId = e.getLong("i"),
                                calendarId = e.optLong("c"), title = e.getString("t"),
                                startTime = e.getLong("s"), endTime = e.getLong("e"),
                                isAllDay = e.optBoolean("a"),
                                location = if (e.has("l")) e.getString("l") else null,
                                color = e.getInt("k"),
                                calendarDisplayName = if (e.has("n")) e.getString("n") else null,
                                labelId = if (e.has("b")) e.getString("b") else null,
                                googleId = if (e.has("g")) e.getString("g") else null
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.w("GoogleColorCache", "cache read failed: $e")
        }
        colors = c; events = ev; labels = lb; fetchedAt = f; fetchedCals = fc
    }

    /** Colour for a local instance, or null. Key B (syncId|begin) first, then key A (syncId). */
    fun lookup(syncId: String, begin: Long): Int? {
        ensureLoaded()
        return colors["$syncId|$begin"] ?: colors[syncId]
    }

    /** labelId -> sample event titles, across all linked accounts. */
    fun labelSamples(): Map<String, List<String>> { ensureLoaded(); return labels }
    fun labelColor(id: String): Int? =
        if (prefs.contains("lbl_$id")) prefs.getInt("lbl_$id", 0) else null
    fun setLabelColor(id: String, argb: Int) { prefs.edit().putInt("lbl_$id", argb).apply() }

    fun removeLabelColor(id: String) { prefs.edit().remove("lbl_$id").apply() }

    private fun overrideKey(e: CalendarEvent): String =
        "ev_" + (e.googleId?.let { "g_$it" } ?: if (e.eventId != 0L) "l_${e.eventId}" else "t_${e.title}")
    fun eventOverride(e: CalendarEvent): Int? =
        overrideKey(e).let { if (prefs.contains(it)) prefs.getInt(it, 0) else null }
    fun setEventOverride(e: CalendarEvent, argb: Int) { prefs.edit().putInt(overrideKey(e), argb).commit() }
    fun clearEventOverride(e: CalendarEvent) { prefs.edit().remove(overrideKey(e)).commit() }
    fun hasColorOverride(e: CalendarEvent): Boolean =
        eventOverride(e) != null || (e.labelId != null && labelColor(e.labelId) != null)

    fun cachedEvents(): List<CalendarEvent> { ensureLoaded(); return events }
    /** Local calendar ids whose events come from the API cache. */
    fun fetchedCalendarIds(): Set<Long> { ensureLoaded(); return fetchedCals }
    fun fetchedAt(): Long { ensureLoaded(); return fetchedAt }

    @Synchronized
    fun saveAccount(account: String, colorMap: Map<String, Int>, evs: List<CalendarEvent>, calIds: List<Long>,
        labelMap: Map<String, List<String>> = emptyMap()) {
        val root = readRoot()
        val accs = root.optJSONObject("accounts") ?: JSONObject().also { root.put("accounts", it) }
        val o = JSONObject()
        val cs = JSONObject()
        colorMap.forEach { (k, v) -> cs.put(k, v) }
        o.put("colors", cs)
        val arr = JSONArray()
        for (e in evs) arr.put(JSONObject().apply {
            put("i", e.eventId); put("c", e.calendarId); put("t", e.title); put("s", e.startTime)
            put("e", e.endTime); put("a", e.isAllDay); e.location?.let { put("l", it) }
            put("k", e.color); e.calendarDisplayName?.let { put("n", it) }
            e.labelId?.let { put("b", it) }
            e.googleId?.let { put("g", it) }
        })
        o.put("events", arr)
        val lo = JSONObject()
        labelMap.forEach { (k, v) -> lo.put(k, JSONArray(v)) }
        o.put("labels", lo)
        o.put("cals", JSONArray(calIds))
        accs.put(account, o)
        root.put("fetchedAt", System.currentTimeMillis())
        writeRoot(root)
    }

    @Synchronized
    fun clearAccount(account: String) {
        val root = readRoot()
        root.optJSONObject("accounts")?.remove(account)
        writeRoot(root)
    }

    private fun writeRoot(root: JSONObject) {
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(root.toString())
        if (!tmp.renameTo(file)) { file.writeText(root.toString()); tmp.delete() }
        loaded = false
    }

    private fun readRoot(): JSONObject = try {
        if (file.exists()) JSONObject(file.readText()) else JSONObject()
    } catch (_: Exception) {
        JSONObject()
    }

    companion object {
        @Volatile private var inst: GoogleColorCache? = null
        fun get(context: Context): GoogleColorCache =
            inst ?: synchronized(this) {
                inst ?: GoogleColorCache(context.applicationContext).also { inst = it }
            }
    }
}
