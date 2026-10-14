package com.kudcrafts.widgets.trading

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// kudtrading's GET /api/menubar/overview (src/lib/menubar/core/overview.ts), as trading-bar's Models.swift
// reads it. All counting rules stay on the server; nothing here re-derives a KPI.

@Serializable
data class Kpis(
    val revenue: Double,
    val orders: Double,
    val excludedOrders: Double = 0.0,
    /** IDR, PPN included. */
    val adSpend: Double = 0.0,
    /** Revenue ÷ ad spend (PPN included); null when nothing was spent. */
    val roas: Double? = null,
    val cogs: Double = 0.0,
    val cogsMissingItems: Double = 0.0,
    /** revenue − COGS − ad spend. */
    val contribution: Double = 0.0,
    val contributionPct: Double? = null,
)

/** One bucket: an hour ("2026-10-12T09:00") for a single day, a day ("2026-10-12") otherwise. */
@Serializable
data class Bucket(val t: String, val orders: Double, val revenue: Double, val cogs: Double = 0.0)

@Serializable
data class Business(val slug: String, val name: String, val kpis: Kpis, val series: List<Bucket>)

@Serializable
data class Summary(val kpis: Kpis, val series: List<Bucket>)

@Serializable
data class Dates(val from: String, val to: String)

@Serializable
data class Overview(
    val preset: String,
    val dates: Dates,
    /** "hour" or "day" */
    val bucket: String,
    val businesses: List<Business>,
    val combined: Summary,
) {
    companion object {
        val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
        fun parse(body: String): Overview = json.decodeFromString(serializer(), body)
    }
}
