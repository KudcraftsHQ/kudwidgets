package com.kudcrafts.widgets.board

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import com.kudcrafts.widgets.core.Jakarta
import com.kudcrafts.widgets.core.Window
import com.kudcrafts.widgets.store.Period

// board's GET /api/board/overview?lite=1 (server/lib/overview.ts), as board-bar's Models.swift reads it.
// A product on an older contract sends nulls, so nearly everything is optional.

@Serializable
data class Money(val IDR: Double = 0.0, val USD: Double = 0.0)

@Serializable
data class MoneyPair(val current: Money, val previous: Money? = null)

@Serializable
data class NumPair(val current: Double, val previous: Double? = null)

@Serializable
data class NullablePair(val current: Double? = null, val previous: Double? = null)

@Serializable
data class BoardKpis(
    val revenue: MoneyPair? = null,
    /** IDR, PPN included. */
    val adSpend: NumPair? = null,
    /** IDR, PPN included. */
    val aiSpend: NumPair? = null,
    val roas: NullablePair? = null,
    val orders: NumPair? = null,
    val paid: NumPair? = null,
    /** IDR revenue minus ad and AI spend. */
    val contribution: NumPair? = null,
    val contributionPct: Double? = null,
)

/** One bucket: an hour ("2026-10-12T09:00") for a single day, a day otherwise. */
@Serializable
data class BoardPoint(
    val t: String,
    val revenueIDR: Double = 0.0,
    val paid: Double = 0.0,
    val leads: Double? = null,
    val selfies: Double? = null,
    val created: Double? = null,
)

@Serializable
data class Product(
    val slug: String,
    val name: String,
    val color: String? = null,
    val kpis: BoardKpis? = null,
    val series: List<BoardPoint>? = null,
    val bucket: String? = null,
    val errorCode: String? = null,
    val error: String? = null,
)

/** A prepaid balance a collector reports to board (OpenAI credit; server/lib/balances.ts). */
@Serializable
data class Balance(
    val provider: String,
    val name: String,
    val available: Double,
    /** Credit granted (bought) in total, when the collector reports it: the tank's real "full". */
    val granted: Double? = null,
    /** Warn under this much; null when the collector sets no threshold. */
    val warnBelow: Double? = null,
    val topUpUrl: String? = null,
    /** Why the collector can't read it any more; `available` is then the last known value. */
    val problem: String? = null,
    val low: Boolean = false,
    /** No report for three hours. */
    val stale: Boolean = false,
) {
    /** Running low, or no longer updating: either way it wants a look. */
    val needsAttention: Boolean get() = low || stale || problem != null
}

@Serializable
data class BoardDates(val from: String, val to: String)

@Serializable
data class BoardOverview(
    val preset: String,
    val dates: BoardDates,
    val products: List<Product> = emptyList(),
    val combined: BoardKpis = BoardKpis(),
    /** Absent on a board older than balances. */
    val balances: List<Balance>? = null,
) {
    companion object {
        val json = Json { ignoreUnknownKeys = true; explicitNulls = false; coerceInputValues = true }
        fun parse(body: String): BoardOverview = json.decodeFromString(serializer(), body)
    }
}

/** The four funnel steps, summed over every product's series. */
data class Funnel(val leads: Double, val selfies: Double, val created: Double, val paid: Double)

object BoardMath {
    /** The products a widget covers: one when filtered (and present), else all. */
    fun products(o: BoardOverview, filter: String? = null): List<Product> =
        filter?.let { f -> o.products.filter { it.slug == f } }?.takeIf { it.isNotEmpty() } ?: o.products

    /** The KPIs a widget shows: one product's, or the combined. */
    fun kpis(o: BoardOverview, filter: String? = null): BoardKpis =
        filter?.let { f -> o.products.firstOrNull { it.slug == f }?.kpis } ?: o.combined

    fun funnel(o: BoardOverview, filter: String? = null): Funnel {
        var l = 0.0; var s = 0.0; var c = 0.0; var p = 0.0
        for (row in products(o, filter)) for (pt in row.series.orEmpty()) {
            l += pt.leads ?: 0.0; s += pt.selfies ?: 0.0; c += pt.created ?: 0.0; p += pt.paid
        }
        return Funnel(l, s, c, p)
    }

    /** IDR revenue per hour 0..23 across products (board never sums currencies; USD stays out). */
    fun hourlyRevenue(o: BoardOverview, filter: String? = null): DoubleArray {
        val out = DoubleArray(24)
        for (row in products(o, filter)) for (pt in row.series.orEmpty()) {
            val h = pt.t.substringAfter('T', "").take(2).toIntOrNull() ?: continue
            if (h in 0..23) out[h] += pt.revenueIDR
        }
        return out
    }

    /**
     * Revenue per chart slot and how many slots are finished (the slot in progress is partial and would read
     * as a dip): hours for a single day, days for 7 days or the month.
     */
    fun revenueSlots(o: BoardOverview, period: Period, filter: String?, fetchedAt: Long): Pair<DoubleArray, Int> = when (period) {
        Period.Today -> hourlyRevenue(o, filter) to Jakarta.hourOfDay(fetchedAt).toInt()
        Period.Yesterday -> hourlyRevenue(o, filter) to 24
        else -> {
            val slots = Window.days(period, o.dates.from)
            val daily = Window.daily(o.dates.from, slots, products(o, filter).flatMap { p -> p.series.orEmpty().map { it.t to it.revenueIDR } })
            daily to (Window.dayIndex(o.dates.from, Jakarta.day(fetchedAt)) ?: slots).coerceIn(0, slots)
        }
    }

    /** The OpenAI credit, if the collector reports one. */
    fun openAi(o: BoardOverview): Balance? =
        o.balances.orEmpty().firstOrNull { it.provider.equals("openai", ignoreCase = true) }
            ?: o.balances.orEmpty().firstOrNull()

    /**
     * How full the credit "tank" looks, 0..1: available ÷ granted. Without `granted` the full mark is four
     * times the warning threshold (or $100 without one), so the threshold reads a quarter full.
     */
    fun tankLevel(b: Balance): Double {
        val full = b.granted?.takeIf { it > 0 } ?: b.warnBelow?.takeIf { it > 0 }?.times(4) ?: 100.0
        return (b.available / full).coerceIn(0.0, 1.0)
    }

    /** "F 620K · T 75K": products with revenue today, by first letter, biggest first. */
    fun productLine(o: BoardOverview, short: (Double) -> String): String =
        o.products
            .filter { it.errorCode == null }
            .map { it to (it.kpis?.revenue?.current?.IDR ?: 0.0) }
            .sortedByDescending { it.second }
            .joinToString(" · ") { (p, v) -> "${p.name.firstOrNull { c -> c.isLetterOrDigit() }?.uppercaseChar() ?: '?'} ${short(v)}" }
}
