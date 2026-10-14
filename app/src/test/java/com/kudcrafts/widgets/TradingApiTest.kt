package com.kudcrafts.widgets

import com.kudcrafts.widgets.core.FetchResult
import com.kudcrafts.widgets.trading.Overview
import com.kudcrafts.widgets.trading.TradingApi
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TradingApiTest {
    private val server = MockWebServer().apply { start() }
    private val api = TradingApi(OkHttpClient(), server.url("").toString().trimEnd('/'))
    private val today = javaClass.getResource("/overview-today.json")!!.readText()
    private val yesterday = today.replace("\"preset\": \"today\"", "\"preset\": \"yesterday\"")

    @After fun stop() = server.shutdown()

    @Test fun parsesTheOverview() {
        val o = Overview.parse(today)
        assertEquals(5_400_000.0, o.combined.kpis.revenue, 0.0)
        assertEquals(2, o.businesses.size)
        assertNull(o.businesses[1].kpis.roas)
        assertEquals("2026-10-14T09:00", o.combined.series[1].t)
    }

    @Test fun snapshotReadsTodayThenYesterdayWithTheBearerKey() {
        server.enqueue(MockResponse().setBody(today))
        server.enqueue(MockResponse().setBody(yesterday))
        val r = api.snapshot("kud_read_x") as FetchResult.Ok
        assertEquals("yesterday", r.data.yesterday!!.preset)
        val first = server.takeRequest()
        assertEquals("/api/menubar/overview?preset=today", first.path)
        assertEquals("Bearer kud_read_x", first.getHeader("Authorization"))
        assertEquals("GET", first.method)
        assertEquals("/api/menubar/overview?preset=yesterday", server.takeRequest().path)
    }

    @Test fun refusedKeyFlipsToSignIn() {
        server.enqueue(MockResponse().setResponseCode(401))
        assertEquals(FetchResult.Refused, api.snapshot("k"))
        server.enqueue(MockResponse().setBody(today))
        server.enqueue(MockResponse().setResponseCode(403))
        assertEquals(FetchResult.Refused, api.snapshot("k"))
    }

    @Test fun aFailedYesterdayOnlyDropsTheComparison() {
        server.enqueue(MockResponse().setBody(today))
        server.enqueue(MockResponse().setResponseCode(500))
        val r = api.snapshot("k") as FetchResult.Ok
        assertNull(r.data.yesterday)
    }

    @Test fun serverErrorsAndGarbageAreErrors() {
        server.enqueue(MockResponse().setResponseCode(502))
        assertEquals(FetchResult.Error("HTTP 502"), api.snapshot("k"))
        server.enqueue(MockResponse().setBody("<html>"))
        assertTrue(api.snapshot("k") is FetchResult.Error)
    }

    @Test fun signOutRevokesTheKey() {
        server.enqueue(MockResponse().setBody("""{"revoked":true}"""))
        assertTrue(api.revoke("kud_read_x"))
        val req = server.takeRequest()
        assertEquals("/api/cli/auth/revoke", req.path)
        assertEquals("POST", req.method)
        assertEquals("Bearer kud_read_x", req.getHeader("Authorization"))
        server.enqueue(MockResponse().setBody("""{"revoked":false}"""))
        assertTrue(!api.revoke("k"))
        server.enqueue(MockResponse().setResponseCode(401))
        assertTrue(!api.revoke("k"))
    }

    @Test fun tokenExchangePostsTheContract() {
        server.enqueue(MockResponse().setBody("""{"account":{"email":"h@k.com"},"apiKey":"kud_read_1","expiresAt":"2027-10-14T00:00:00Z"}"""))
        val ok = api.exchange("c0de", "v".repeat(43)) as TradingApi.TokenResult.Ok
        assertEquals("kud_read_1", ok.token.apiKey)
        val req = server.takeRequest()
        assertEquals("/api/cli/auth/token", req.path)
        assertEquals("POST", req.method)
        assertTrue(req.body.readUtf8().contains("\"client_id\":\"kud-widgets\""))

        server.enqueue(MockResponse().setResponseCode(400).setBody("""{"error":"invalid_client"}"""))
        assertEquals(TradingApi.TokenResult.Error("invalid_client"), api.exchange("c", "v".repeat(43)))
    }
}
