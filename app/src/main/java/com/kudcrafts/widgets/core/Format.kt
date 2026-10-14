package com.kudcrafts.widgets.core

import kotlin.math.abs
import kotlin.math.roundToInt

/** Number formats shared with trading-bar and board-bar (Format.swift), so phone and Mac agree. */
object Format {
    /** 950 · 695K · 2.4M · 24M · 200M · 1.2B. One decimal below 10 of a unit, truncated, never rounded up. */
    fun short(value: Double): String {
        val v = abs(value)
        val sign = if (value < 0 && v >= 1) "-" else ""
        for ((unit, suffix) in listOf(1e9 to "B", 1e6 to "M", 1e3 to "K")) {
            if (v < unit) continue
            if (v >= unit * 10) return "$sign${(v / unit).toLong()}$suffix"
            val tenths = (v / (unit / 10)).toLong()
            return if (tenths % 10 == 0L) "$sign${tenths / 10}$suffix" else "$sign${tenths / 10}.${tenths % 10}$suffix"
        }
        return "$sign${v.toLong()}"
    }

    /** 64 · 1.204 — a count with Indonesian grouping. */
    fun count(value: Double): String {
        val n = value.roundToInt()
        return "%,d".format(n).replace(',', '.')
    }

    /** 5.6 — ROAS on a widget, one decimal; an en dash when nothing was spent. */
    fun roas(value: Double?): String =
        if (value == null || value.isNaN()) "–" else (Math.floor(value * 10) / 10).let {
            if (it >= 100) it.toLong().toString() else "%.1f".format(java.util.Locale.ROOT, it)
        }

    /**
     * "▲ 12% vs yesterday" · "▼ 4.5% vs yesterday" · "≈ yesterday". Below 10% one decimal, like
     * trading-bar's percent(). Null when there is nothing to compare with.
     */
    fun delta(pct: Double?): String? {
        if (pct == null || pct.isNaN() || pct.isInfinite()) return null
        val a = abs(pct)
        if (a < 0.5) return "≈ yesterday"
        val n = if (a >= 10) a.toLong().toString() else "%.1f".format(java.util.Locale.ROOT, Math.floor(a * 10) / 10)
            .removeSuffix(".0")
        return "${if (pct > 0) "▲" else "▼"} $n% vs yesterday"
    }

    /** $41 · $4.5 · $1.2K — a credit balance on a small widget. */
    fun usdShort(value: Double): String = when {
        value >= 1000 -> "$" + short(value)
        value >= 10 -> "$" + value.toLong()
        else -> "$" + "%.1f".format(java.util.Locale.ROOT, Math.floor(value * 10) / 10).removeSuffix(".0")
    }

    /** "now" · "38m ago" · "3h ago" · "2d ago". */
    fun age(minutes: Long): String = when {
        minutes < 1 -> "now"
        minutes < 60 -> "${minutes}m ago"
        minutes < 48 * 60 -> "${minutes / 60}h ago"
        else -> "${minutes / (24 * 60)}d ago"
    }
}
