package com.kudcrafts.widgets.trading

/**
 * Running revenue through a WIB day, as points (hour 0..24, cumulative IDR), and the
 * "vs yesterday at the same time" comparison. Pure, so it can be tested without a clock.
 */
object Curves {
    /** Hourly series indexed 0..23 by the hour in `t` ("YYYY-MM-DDTHH:00"); missing hours are 0. */
    fun hourly(series: List<Bucket>): DoubleArray {
        val out = DoubleArray(24)
        for (b in series) {
            val h = b.t.substringAfter('T', "").take(2).toIntOrNull() ?: continue
            if (h in 0..23) out[h] += b.revenue
        }
        return out
    }

    /** Running total from midnight to `untilHour` (fractional), spreading the current hour evenly. */
    fun runningAt(hourly: DoubleArray, untilHour: Double): Double {
        val clamped = untilHour.coerceIn(0.0, 24.0)
        val whole = clamped.toInt()
        var sum = 0.0
        for (h in 0 until minOf(whole, 24)) sum += hourly[h]
        if (whole < 24) sum += hourly[whole] * (clamped - whole)
        return sum
    }

    /**
     * Today's running curve up to `nowHour`: one point per finished hour, then today's whole total at now
     * (the current hour's orders are all already in).
     */
    fun today(hourly: DoubleArray, nowHour: Double): List<Pair<Double, Double>> {
        val now = nowHour.coerceIn(0.0, 24.0)
        val pts = ArrayList<Pair<Double, Double>>()
        var sum = 0.0
        pts += 0.0 to 0.0
        for (h in 0 until now.toInt().coerceAtMost(24)) {
            sum += hourly[h]
            pts += (h + 1).toDouble() to sum
        }
        val total = hourly.sum()
        if (now > pts.last().first) pts += now to total else pts[pts.lastIndex] = now to total
        return pts
    }

    /** Yesterday's whole-day running curve, one point per hour boundary. */
    fun fullDay(hourly: DoubleArray): List<Pair<Double, Double>> {
        val pts = ArrayList<Pair<Double, Double>>(25)
        var sum = 0.0
        pts += 0.0 to 0.0
        for (h in 0 until 24) {
            sum += hourly[h]
            pts += (h + 1).toDouble() to sum
        }
        return pts
    }

    /**
     * Today's revenue against yesterday's running total at the same time of day, in percent.
     * Null when yesterday had nothing by then (a percentage of zero says nothing).
     */
    fun deltaVsYesterday(todayRevenue: Double, yesterday: List<Bucket>, nowHour: Double): Double? {
        val base = runningAt(hourly(yesterday), nowHour)
        if (base <= 0.0) return null
        return (todayRevenue / base - 1.0) * 100.0
    }

    /**
     * A multi-day window as a running curve on the same 0..24 axis the charts use: slot k of `slots` ends at
     * 24·(k+1)/slots. Runs through `lastSlot` (today, partly done); the rest of the window stays empty.
     */
    fun windowRunning(daily: DoubleArray, lastSlot: Int): List<Pair<Double, Double>> {
        val n = daily.size.coerceAtLeast(1)
        val pts = ArrayList<Pair<Double, Double>>()
        pts += 0.0 to 0.0
        var sum = 0.0
        for (k in 0..lastSlot.coerceIn(0, n - 1)) {
            sum += daily[k]
            pts += 24.0 * (k + 1) / n to sum
        }
        return pts
    }
}
