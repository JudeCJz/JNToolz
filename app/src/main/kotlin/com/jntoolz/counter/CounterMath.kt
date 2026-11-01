package com.jntoolz.counter

import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

/** What a counter shows right now. Pure, so it is unit tested without Android. */
data class CounterDisplay(
    val phase: Phase,
    /** Calendar days between today and the target, always >= 0. */
    val days: Long,
    /** Big text: the day count, or "TODAY". */
    val number: String,
    /** Small label under the number ("DAYS LEFT", "DAY AGO", "" for today). */
    val label: String,
    /** Millis until the target moment, only when the counter has a time and it is still ahead. */
    val millisToTarget: Long?,
    /** 0..1 from the counter's creation day to the target day. */
    val progress: Float,
) {
    enum class Phase { FUTURE, TODAY, PAST }
}

object CounterMath {

    /** Days are calendar days in [zone]: a target tomorrow at 00:30 is "1 DAY LEFT" even at 23:50. */
    fun display(
        target: LocalDate,
        time: LocalTime?,
        created: LocalDate,
        now: ZonedDateTime,
    ): CounterDisplay {
        val today = now.toLocalDate()
        val diff = ChronoUnit.DAYS.between(today, target)
        val phase = when {
            diff > 0 -> CounterDisplay.Phase.FUTURE
            diff < 0 -> CounterDisplay.Phase.PAST
            else -> CounterDisplay.Phase.TODAY
        }
        val days = kotlin.math.abs(diff)
        val number = if (phase == CounterDisplay.Phase.TODAY) "TODAY" else days.toString()
        val label = when (phase) {
            CounterDisplay.Phase.FUTURE -> if (days == 1L) "DAY LEFT" else "DAYS LEFT"
            CounterDisplay.Phase.PAST -> if (days == 1L) "DAY AGO" else "DAYS AGO"
            CounterDisplay.Phase.TODAY -> ""
        }
        val millis = time?.let { t ->
            val at = targetMoment(target, t, now.zone)
            Duration.between(now, at).toMillis().takeIf { it > 0 }
        }
        return CounterDisplay(phase, days, number, label, millis, progress(created, target, today))
    }

    /** The target as a moment in [zone]; a time inside a DST gap moves forward like ZonedDateTime does. */
    fun targetMoment(target: LocalDate, time: LocalTime, zone: ZoneId): ZonedDateTime =
        ZonedDateTime.of(target, time, zone)

    fun progress(created: LocalDate, target: LocalDate, today: LocalDate): Float {
        val total = ChronoUnit.DAYS.between(created, target)
        if (total <= 0) return if (today >= target) 1f else 0f
        val done = ChronoUnit.DAYS.between(created, today)
        return (done.toFloat() / total).coerceIn(0f, 1f)
    }

    /** Start of the next local day, the moment every counter's day count changes. */
    fun nextMidnight(now: ZonedDateTime): ZonedDateTime =
        now.toLocalDate().plusDays(1).atStartOfDay(now.zone)
}
