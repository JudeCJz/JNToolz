package com.jntoolz.data

import android.accounts.Account
import android.content.Context
import android.graphics.Color
import android.util.Log
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.concurrent.TimeUnit

object GoogleCalendarApi {
    private const val TAG = "GoogleCalendarApi"
    private const val SCOPE = "https://www.googleapis.com/auth/calendar.readonly"
    private const val BASE = "https://www.googleapis.com/calendar/v3"
    const val STALE_MS = 30 * 60 * 1000L

    fun authorize(context: Context, email: String): Task<AuthorizationResult> {
        val req = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(SCOPE)))
            .setAccount(Account(email, "com.google"))
            .build()
        return Identity.getAuthorizationClient(context.applicationContext).authorize(req)
    }

    private fun silentToken(context: Context, email: String): String? = try {
        val r = Tasks.await(authorize(context, email), 20, TimeUnit.SECONDS)
        if (r.hasResolution()) null else r.accessToken
    } catch (e: Exception) {
        Log.w(TAG, "silent auth failed for $email: $e")
        null
    }

    private fun get(token: String, url: String): JSONObject {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 15000
            c.readTimeout = 15000
            c.setRequestProperty("Authorization", "Bearer $token")
            if (c.responseCode !in 200..299) throw RuntimeException("HTTP ${c.responseCode} $url")
            return JSONObject(c.inputStream.bufferedReader().readText())
        } finally {
            c.disconnect()
        }
    }

    private fun hex(s: String?): Int? = try {
        if (s.isNullOrEmpty()) null else toModern(Color.parseColor(s))
    } catch (_: Exception) {
        null
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    fun isStale(context: Context): Boolean =
        System.currentTimeMillis() - GoogleColorCache.get(context).fetchedAt() > STALE_MS

    /** Blocking. Fetches every linked account; returns true if anything was saved. */
    fun refreshAll(context: Context): Boolean {
        val app = context.applicationContext
        val cache = GoogleColorCache.get(app)
        val linked = cache.linkedAccounts()
        if (linked.isEmpty()) return false
        val prefs = WidgetPreferences(app)
        val cals = CalendarRepository(app).getAvailableCalendars()
        var saved = false
        for (email in linked) {
            try {
                val token = silentToken(app, email) ?: continue
                val mine = cals.filter {
                    it.accountName == email && prefs.isCalendarEnabled(it) && it.ownerAccount.isNotEmpty()
                }
                fetchAccount(cache, token, email, mine)
                saved = true
            } catch (e: Exception) {
                Log.w(TAG, "fetch failed for $email: $e")
            }
        }
        return saved
    }

    private fun fetchAccount(
        cache: GoogleColorCache, token: String, email: String, mine: List<CalendarInfo>
    ) {
        val palette = HashMap<String, Int>()
        val evPal = get(token, "$BASE/colors").optJSONObject("event")
        if (evPal != null) for (k in evPal.keys()) {
            hex(evPal.getJSONObject(k).optString("background"))?.let { palette[k] = it }
        }

        val calColor = HashMap<String, Int>()
        val list = get(token, "$BASE/users/me/calendarList?maxResults=250").optJSONArray("items")
        if (list != null) for (i in 0 until list.length()) {
            val o = list.getJSONObject(i)
            hex(o.optString("backgroundColor"))?.let { calColor[o.getString("id")] = it }
        }

        val now = System.currentTimeMillis()
        val min = Instant.ofEpochMilli(now - 86_400_000L).toString()
        val max = Instant.ofEpochMilli(now + 180L * 86_400_000L).toString()
        val colorMap = HashMap<String, Int>()
        val events = ArrayList<CalendarEvent>()
        val labels = LinkedHashMap<String, MutableList<String>>()
        val fields = enc("items(id,recurringEventId,colorId,start,end,summary,location,status,eventLabelId),nextPageToken")

        for (cal in mine) {
            val gid = cal.ownerAccount
            val base = calColor[gid] ?: cal.color
            colorMap["cal:$gid"] = base
            var page: String? = null
            do {
                var url = "$BASE/calendars/${enc(gid)}/events?singleEvents=true&timeMin=${enc(min)}" +
                    "&timeMax=${enc(max)}&maxResults=2500&fields=$fields"
                if (page != null) url += "&pageToken=${enc(page)}"
                val resp = try {
                    get(token, url)
                } catch (e: Exception) {
                    Log.w(TAG, "events $gid: $e")
                    break
                }
                val items = resp.optJSONArray("items")
                if (items != null) for (i in 0 until items.length()) {
                    val o = items.getJSONObject(i)
                    if (o.optString("status") == "cancelled") continue
                    val id = o.getString("id")
                    val cid = o.optString("colorId", "")
                    val color = if (cid.isNotEmpty()) {
                        palette[cid] ?: run { Log.w(TAG, "unknown colorId $cid"); base }
                    } else base
                    val (start, allDay) = parseTime(o.optJSONObject("start")) ?: continue
                    val end = parseTime(o.optJSONObject("end"))?.first ?: start
                    colorMap[id] = color
                    colorMap["${o.optString("recurringEventId", id)}|$start"] = color
                    val title = o.optString("summary", "").trim()
                    if (title.isEmpty()) continue
                    val labelId = o.optString("eventLabelId", "").ifEmpty { null }
                    if (labelId != null) {
                        val l = labels.getOrPut(labelId) { ArrayList() }
                        if (l.size < 2 && title !in l) l.add(title)
                    }
                    val loc = o.optString("location", "").ifEmpty { null }
                    val numId = id.hashCode().toLong() - 0x1_0000_0000L
                    events.add(
                        CalendarEvent(
                            id = numId, eventId = numId, calendarId = cal.id, title = title,
                            startTime = start, endTime = if (allDay) end - 1 else end,
                            isAllDay = allDay, location = loc, color = color,
                            calendarDisplayName = cal.displayName, labelId = labelId, googleId = id
                        )
                    )
                }
                page = resp.optString("nextPageToken", "").ifEmpty { null }
            } while (page != null)
        }
        cache.saveAccount(email, colorMap, events, mine.map { it.id }, labels)
    }

    private fun parseTime(o: JSONObject?): Pair<Long, Boolean>? {
        if (o == null) return null
        return try {
            val dt = o.optString("dateTime", "")
            if (dt.isNotEmpty()) OffsetDateTime.parse(dt).toInstant().toEpochMilli() to false
            else LocalDate.parse(o.getString("date")).atStartOfDay(ZoneOffset.UTC).toInstant()
                .toEpochMilli() to true
        } catch (_: Exception) {
            null
        }
    }
}
