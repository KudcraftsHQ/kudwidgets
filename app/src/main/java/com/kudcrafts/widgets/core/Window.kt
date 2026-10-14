package com.kudcrafts.widgets.core

import com.kudcrafts.widgets.store.Period
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/** Where a bucket sits in a widget's window, for the charts. Days are Asia/Jakarta, as the servers bucket them. */
object Window {
    /** How many day slots the chart's x axis has: the whole month, not just the days so far. */
    fun days(period: Period, from: String): Int = when (period) {
        Period.Today, Period.Yesterday -> 1
        Period.Week -> 7
        Period.Month -> runCatching { YearMonth.parse(from.take(7)).lengthOfMonth() }.getOrDefault(31)
    }

    /** Day bucket `t` ("2026-10-12") → 0-based slot from `from`; null when unparseable. */
    fun dayIndex(from: String, t: String): Int? = runCatching {
        ChronoUnit.DAYS.between(LocalDate.parse(from), LocalDate.parse(t.take(10))).toInt()
    }.getOrNull()

    /** Per-slot values from day buckets (`t`, value). */
    fun daily(from: String, slots: Int, buckets: List<Pair<String, Double>>): DoubleArray {
        val out = DoubleArray(slots)
        for ((t, v) in buckets) dayIndex(from, t)?.takeIf { it in 0 until slots }?.let { out[it] += v }
        return out
    }
}
