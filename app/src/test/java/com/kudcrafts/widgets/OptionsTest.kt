package com.kudcrafts.widgets

import com.kudcrafts.widgets.board.BoardMath
import com.kudcrafts.widgets.board.BoardOverview
import com.kudcrafts.widgets.board.BoardPoint
import com.kudcrafts.widgets.board.BoardDates
import com.kudcrafts.widgets.core.FetchResult
import com.kudcrafts.widgets.core.Window
import com.kudcrafts.widgets.store.Period
import com.kudcrafts.widgets.store.WidgetOptions
import com.kudcrafts.widgets.store.cleanTapUrl
import com.kudcrafts.widgets.trading.Curves
import com.kudcrafts.widgets.trading.TradingApi
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.ZonedDateTime

class OptionsTest {
    private val at = ZonedDateTime.parse("2026-10-14T04:05:00Z").toInstant().toEpochMilli() // 11:05 WIB

    @Test fun tapUrlIsHttpsOrNothing() {
        assertNull(cleanTapUrl(null))
        assertNull(cleanTapUrl("   "))
        assertEquals("https://board.kudcrafts.com/x", cleanTapUrl(" board.kudcrafts.com/x "))
        assertEquals("https://a.b/c", cleanTapUrl("https://a.b/c"))
        assertNull(cleanTapUrl("http://a.b"))
        assertNull(cleanTapUrl("javascript://alert(1)"))
        assertNull(cleanTapUrl("https://a b"))
    }

    @Test fun optionsDefaultAndSurviveUnknownFields() {
        val json = Json { ignoreUnknownKeys = true }
        assertEquals(WidgetOptions(), json.decodeFromString(WidgetOptions.serializer(), "{}"))
        val o = json.decodeFromString(WidgetOptions.serializer(), """{"period":"Week","filter":"atlas","later":1}""")
        assertEquals(Period.Week, o.period)
        assertEquals("last7", o.period.preset)
        assertEquals("atlas", o.filter)
    }

    @Test fun windowSlots() {
        assertEquals(7, Window.days(Period.Week, "2026-10-08"))
        assertEquals(31, Window.days(Period.Month, "2026-10-01"))
        assertEquals(29, Window.days(Period.Month, "2028-02-01"))
        assertEquals(1, Window.days(Period.Today, "2026-10-14"))
        assertEquals(6, Window.dayIndex("2026-10-08", "2026-10-14"))
        assertEquals(0, Window.dayIndex("2026-10-08", "2026-10-08T09:00"))
        val d = Window.daily("2026-10-08", 7, listOf("2026-10-08" to 1.0, "2026-10-10" to 2.0, "2026-10-10" to 3.0, "2026-11-01" to 9.0))
        assertEquals(listOf(1.0, 0.0, 5.0, 0.0, 0.0, 0.0, 0.0), d.toList())
    }

    @Test fun windowRunningStopsAtToday() {
        val pts = Curves.windowRunning(doubleArrayOf(1.0, 2.0, 3.0, 0.0, 0.0, 0.0, 0.0), lastSlot = 2)
        assertEquals(listOf(0.0 to 0.0, 24.0 / 7 to 1.0, 48.0 / 7 to 3.0, 72.0 / 7 to 6.0), pts)
    }

    private fun boardWeek() = BoardOverview(
        preset = "last7", dates = BoardDates("2026-10-08", "2026-10-14"),
        products = listOf(
            com.kudcrafts.widgets.board.Product("atlas", "Atlas", series = listOf(
                BoardPoint("2026-10-08", revenueIDR = 100.0, leads = 10.0), BoardPoint("2026-10-14", revenueIDR = 50.0, leads = 5.0))),
            com.kudcrafts.widgets.board.Product("birch", "Birch", series = listOf(BoardPoint("2026-10-09", revenueIDR = 30.0, leads = 3.0))),
        ),
    )

    @Test fun boardSlotsPerPeriodAndFilter() {
        val (slots, finished) = BoardMath.revenueSlots(boardWeek(), Period.Week, null, at)
        assertEquals(listOf(100.0, 30.0, 0.0, 0.0, 0.0, 0.0, 50.0), slots.toList())
        assertEquals(6, finished) // today (slot 6) is still running
        val (atlas, _) = BoardMath.revenueSlots(boardWeek(), Period.Week, "atlas", at)
        assertEquals(0.0, atlas[1], 0.0)
        assertEquals(15.0, BoardMath.funnel(boardWeek(), "atlas").leads, 0.0)
        assertEquals(18.0, BoardMath.funnel(boardWeek(), null).leads, 0.0)
        // An unknown filter (product removed) falls back to all of them rather than to nothing.
        assertEquals(18.0, BoardMath.funnel(boardWeek(), "gone").leads, 0.0)
    }

    @Test fun boardTodayFinishedHours() {
        val o = boardWeek().copy(preset = "today")
        assertEquals(11, BoardMath.revenueSlots(o, Period.Today, null, at).second)
        assertEquals(24, BoardMath.revenueSlots(o, Period.Yesterday, null, at).second)
    }

    @Test fun otherWindowsReadOnlyThatPreset() {
        val server = MockWebServer().apply { start() }
        val body = javaClass.getResource("/overview-today.json")!!.readText().replace("\"preset\": \"today\"", "\"preset\": \"last7\"")
        server.enqueue(MockResponse().setBody(body))
        val r = TradingApi(OkHttpClient(), server.url("").toString().trimEnd('/')).snapshot("k", "last7") as FetchResult.Ok
        assertNull(r.data.yesterday)
        assertEquals("/api/menubar/overview?preset=last7", server.takeRequest().path)
        assertEquals(1, server.requestCount)
        server.shutdown()
    }
}
