package com.kudcrafts.widgets.core

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Days and hours are Asia/Jakarta everywhere, like the servers. */
object Jakarta {
    val zone: ZoneId = ZoneId.of("Asia/Jakarta")
    private val hhmm = DateTimeFormatter.ofPattern("HH:mm").withZone(zone)

    /** "11:05" */
    fun clock(epochMillis: Long): String = hhmm.format(Instant.ofEpochMilli(epochMillis))

    /** Hours since local midnight, fractional: 10:30 → 10.5. */
    fun hourOfDay(epochMillis: Long): Double {
        val t = Instant.ofEpochMilli(epochMillis).atZone(zone)
        return t.hour + t.minute / 60.0 + t.second / 3600.0
    }

    /** "2026-10-14" */
    fun day(epochMillis: Long): String = Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate().toString()
}
