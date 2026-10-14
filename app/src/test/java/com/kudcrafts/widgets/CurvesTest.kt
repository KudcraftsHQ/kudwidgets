package com.kudcrafts.widgets

import com.kudcrafts.widgets.core.Jakarta
import com.kudcrafts.widgets.trading.Bucket
import com.kudcrafts.widgets.trading.Curves
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.ZonedDateTime

class CurvesTest {
    private fun b(hour: Int, revenue: Double, day: String = "2026-10-13") =
        Bucket("${day}T%02d:00".format(hour), orders = 1.0, revenue = revenue)

    private val yesterday = listOf(b(8, 100.0), b(9, 200.0), b(10, 400.0), b(20, 1000.0))

    @Test fun hourlyPlacesBucketsByHour() {
        val h = Curves.hourly(yesterday + Bucket("2026-10-13", 1.0, 999.0)) // a day bucket is ignored
        assertEquals(100.0, h[8], 0.0)
        assertEquals(400.0, h[10], 0.0)
        assertEquals(0.0, h[11], 0.0)
        assertEquals(1700.0, h.sum(), 0.0)
    }

    @Test fun runningAtSpreadsTheCurrentHour() {
        val h = Curves.hourly(yesterday)
        assertEquals(0.0, Curves.runningAt(h, 8.0), 1e-9)
        assertEquals(300.0, Curves.runningAt(h, 10.0), 1e-9)
        assertEquals(300.0 + 400.0 * 0.25, Curves.runningAt(h, 10.25), 1e-9)
        assertEquals(1700.0, Curves.runningAt(h, 24.0), 1e-9)
        assertEquals(1700.0, Curves.runningAt(h, 30.0), 1e-9)
    }

    @Test fun deltaComparesWithYesterdayAtTheSameTime() {
        // 10:30 → yesterday had 100 + 200 + half of 400 = 500 by then.
        assertEquals(20.0, Curves.deltaVsYesterday(600.0, yesterday, 10.5)!!, 1e-9)
        assertEquals(-50.0, Curves.deltaVsYesterday(250.0, yesterday, 10.5)!!, 1e-9)
        // Not against yesterday's whole day: that would read -65%.
        assertEquals(-20.0, Curves.deltaVsYesterday(400.0, yesterday, 10.5)!!, 1e-9)
    }

    @Test fun deltaIsNullWithNothingToCompare() {
        assertNull(Curves.deltaVsYesterday(500.0, yesterday, 7.9))
        assertNull(Curves.deltaVsYesterday(500.0, emptyList(), 12.0))
    }

    @Test fun deltaUsesJakartaClock() {
        // 03:30 UTC is 10:30 WIB.
        val at = ZonedDateTime.parse("2026-10-14T03:30:00Z").toInstant().toEpochMilli()
        assertEquals(10.5, Jakarta.hourOfDay(at), 1e-9)
        assertEquals("2026-10-14", Jakarta.day(at))
        assertEquals("10:30", Jakarta.clock(at))
    }

    @Test fun todayCurveEndsAtNowWithTheWholeTotal() {
        val today = Curves.hourly(listOf(b(8, 100.0, "2026-10-14"), b(10, 50.0, "2026-10-14")))
        val pts = Curves.today(today, 10.5)
        assertEquals(0.0 to 0.0, pts.first())
        assertEquals(10.5 to 150.0, pts.last())
        assertEquals(9.0 to 100.0, pts[9])
        // Running totals never dip.
        pts.zipWithNext().forEach { (a, c) -> assert(c.second >= a.second && c.first > a.first) }
    }

    @Test fun fullDayHas25Points() {
        val pts = Curves.fullDay(Curves.hourly(yesterday))
        assertEquals(25, pts.size)
        assertEquals(24.0 to 1700.0, pts.last())
    }
}
