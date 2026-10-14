package com.kudcrafts.widgets

import com.kudcrafts.widgets.board.Balance
import com.kudcrafts.widgets.board.BoardApi
import com.kudcrafts.widgets.board.BoardMath
import com.kudcrafts.widgets.board.BoardOverview
import com.kudcrafts.widgets.board.DeviceFlow
import com.kudcrafts.widgets.board.DeviceFlow.Poll
import com.kudcrafts.widgets.core.FetchResult
import com.kudcrafts.widgets.core.Format
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BoardTest {
    private val body = javaClass.getResource("/board-today.json")!!.readText()
    private val o = BoardOverview.parse(body)
    private val server = MockWebServer().apply { start() }
    private val api = BoardApi(OkHttpClient(), server.url("").toString().trimEnd('/'))

    @After fun stop() = server.shutdown()

    @Test fun parsesTheLiteOverviewIncludingAProductThatFailed() {
        assertEquals(695_000.0, o.combined.revenue!!.current.IDR, 0.0)
        assertEquals(240_000.0, o.combined.contribution!!.current, 0.0)
        assertEquals(3, o.products.size)
        assertNull(o.products[2].series)
        assertEquals("unreachable", o.products[2].errorCode)
        assertEquals(540.0, o.balances!![0].granted!!, 0.0)
    }

    @Test fun funnelSumsEveryProduct() {
        val f = BoardMath.funnel(o)
        assertEquals(310.0, f.leads, 0.0)
        assertEquals(88.0, f.selfies, 0.0)
        assertEquals(19.0, f.created, 0.0)
        assertEquals(7.0, f.paid, 0.0)
    }

    @Test fun hourlyRevenueIsIdrOnly() {
        val h = BoardMath.hourlyRevenue(o)
        assertEquals(200_000.0, h[9], 0.0)
        assertEquals(495_000.0, h[10], 0.0) // USD never added in
    }

    @Test fun productLineByFirstLetterBiggestFirstSkippingFailures() {
        assertEquals("A 620K · B 75K", BoardMath.productLine(o, Format::short))
    }

    @Test fun openAiCreditAndTank() {
        val b = BoardMath.openAi(o)!!
        assertTrue(b.needsAttention)
        assertEquals("$41", Format.usdShort(b.available))
        assertEquals(41.2 / 540, BoardMath.tankLevel(b), 1e-9)
        // Without granted: four times the threshold is "full".
        assertEquals(0.25, BoardMath.tankLevel(Balance("openai", "OpenAI", available = 5.0, warnBelow = 5.0)), 1e-9)
        assertEquals(1.0, BoardMath.tankLevel(Balance("openai", "OpenAI", available = 500.0)), 1e-9)
        assertTrue(Balance("x", "x", 90.0, stale = true).needsAttention)
        assertTrue(Balance("x", "x", 90.0, problem = "401").needsAttention)
        assertTrue(!Balance("x", "x", 90.0).needsAttention)
    }

    @Test fun usdShort() {
        assertEquals("$4.5", Format.usdShort(4.56))
        assertEquals("$5", Format.usdShort(5.0))
        assertEquals("$1.2K", Format.usdShort(1250.0))
    }

    @Test fun deviceFlowBodiesMatchBoardBar() {
        val start = Json.parseToJsonElement(DeviceFlow.requestBody("Kud Widgets · Xiaomi \"X\"")).jsonObject
        assertEquals("board-cli", start["client_id"]!!.jsonPrimitive.content)
        assertEquals("Kud Widgets · Xiaomi \"X\"", start["device_name"]!!.jsonPrimitive.content)
        val poll = Json.parseToJsonElement(DeviceFlow.pollBody("dc")).jsonObject
        assertEquals("urn:ietf:params:oauth:grant-type:device_code", poll["grant_type"]!!.jsonPrimitive.content)
        assertEquals("dc", poll["device_code"]!!.jsonPrimitive.content)
        assertEquals("board-cli", poll["client_id"]!!.jsonPrimitive.content)
    }

    @Test fun pollResultsAndSchedule() {
        assertEquals(Poll.Pending, DeviceFlow.parsePoll(400, """{"error":"authorization_pending"}"""))
        assertEquals(Poll.SlowDown, DeviceFlow.parsePoll(400, """{"error":"slow_down"}"""))
        assertTrue(DeviceFlow.parsePoll(400, """{"error":"access_denied"}""") is Poll.Failed)
        assertTrue(DeviceFlow.parsePoll(400, """{"error":"expired_token"}""") is Poll.Failed)
        val done = DeviceFlow.parsePoll(200, """{"access_token":"bct_x","token_type":"Bearer","expires_at":"2027-10-14T00:00:00Z",
            "token":{"id":"t1","name":"Kud Widgets · Pixel","scopes":{}},"user":{"email":"h@k.com","name":null}}""") as Poll.Done
        assertEquals("bct_x", done.token.accessToken)
        assertEquals("h@k.com", done.token.user!!.email)
        assertEquals(5, DeviceFlow.nextInterval(5, Poll.Pending))
        assertEquals(10, DeviceFlow.nextInterval(5, Poll.SlowDown))
        assertNull(DeviceFlow.nextInterval(5, Poll.Failed("x")))
    }

    @Test fun overviewRequestAndRefusal() {
        server.enqueue(MockResponse().setBody(body))
        assertTrue(api.overview("bct_t") is FetchResult.Ok)
        val req = server.takeRequest()
        assertEquals("/api/board/overview?lite=1&preset=today", req.path)
        assertEquals("Bearer bct_t", req.getHeader("Authorization"))
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":"unauthorized"}"""))
        assertEquals(FetchResult.Refused, api.overview("bct_t"))
    }

    @Test fun deviceStartPollAndLogout() {
        server.enqueue(MockResponse().setBody("""{"device_code":"dc","user_code":"ABCD-EFGH","verification_uri":"https://b/device",
            "verification_uri_complete":"https://b/device?code=ABCD-EFGH","expires_in":600,"interval":5}"""))
        val start = api.startDevice("Kud Widgets · Pixel") as BoardApi.Start.Ok
        assertEquals("ABCD-EFGH", start.code.userCode)
        assertEquals("/api/board/device/code", server.takeRequest().path)
        server.enqueue(MockResponse().setResponseCode(400).setBody("""{"error":"authorization_pending"}"""))
        assertEquals(Poll.Pending, api.poll("dc"))
        assertEquals("/api/board/device/token", server.takeRequest().path)
        server.enqueue(MockResponse().setBody("""{"ok":true}"""))
        assertTrue(api.logout("bct_t"))
        val logout = server.takeRequest()
        assertEquals("/api/board/cli/logout", logout.path)
        assertEquals("Bearer bct_t", logout.getHeader("Authorization"))
    }

    @Test fun liveShapedBalanceParses() {
        // Fields board-bar ignores (currency, used, nextExpiry, fetchedAt) must not break parsing.
        assertEquals("openai", o.balances!![0].provider)
    }
}
