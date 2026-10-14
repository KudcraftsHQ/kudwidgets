package com.kudcrafts.widgets.board

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

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
    fun funnel(o: BoardOverview): Funnel {
        var l = 0.0; var s = 0.0; var c = 0.0; var p = 0.0
        for (row in o.products) for (pt in row.series.orEmpty()) {
            l += pt.leads ?: 0.0; s += pt.selfies ?: 0.0; c += pt.created ?: 0.0; p += pt.paid
        }
        return Funnel(l, s, c, p)
    }

    /** IDR revenue per hour 0..23 across products (board never sums currencies; USD stays out). */
    fun hourlyRevenue(o: BoardOverview): DoubleArray {
        val out = DoubleArray(24)
        for (row in o.products) for (pt in row.series.orEmpty()) {
            val h = pt.t.substringAfter('T', "").take(2).toIntOrNull() ?: continue
            if (h in 0..23) out[h] += pt.revenueIDR
        }
        return out
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
