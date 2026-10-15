package com.kudcrafts.widgets

import com.kudcrafts.widgets.board.BoardOverview
import com.kudcrafts.widgets.core.AuthStatus
import com.kudcrafts.widgets.core.Display
import com.kudcrafts.widgets.live.Live
import com.kudcrafts.widgets.live.LiveNotification
import com.kudcrafts.widgets.live.Sse
import com.kudcrafts.widgets.live.SseEvent
import com.kudcrafts.widgets.live.SseParser
import com.kudcrafts.widgets.live.Stream
import com.kudcrafts.widgets.live.StreamEnd
import com.kudcrafts.widgets.live.Streams
import com.kudcrafts.widgets.store.Source
import com.kudcrafts.widgets.trading.Overview
import com.kudcrafts.widgets.trading.TradingSnapshot
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveTest {
    private fun parse(vararg lines: String): List<SseEvent> {
        val out = mutableListOf<SseEvent>()
        val p = SseParser { out += it }
        lines.forEach(p::line)
        return out
    }

    @Test fun parsesEventsAndDropsComments() {
        val events = parse(
            ": connected", "",
            "data: {\"type\":\"connected\"}", "",
            ": ping", "",
            "event: update", "data: a", "data: b", "id: 7", "",
            "data:no-space", "",
        )
        assertEquals(
            listOf(SseEvent(null, "{\"type\":\"connected\"}"), SseEvent("update", "a\nb"), SseEvent(null, "no-space")),
            events,
        )
    }

    @Test fun onlyEventsThatMoveNumbersRefresh() {
        fun e(json: String) = SseEvent(null, json)
        assertFalse(Streams.moves(Source.Trading, e("""{"type":"connected"}""")))
        assertTrue(Streams.moves(Source.Trading, e("""{"type":"webhook","event":{}}""")))
        assertTrue(Streams.moves(Source.Trading, e("""{"type":"ads_update","event":{}}""")))
        assertFalse(Streams.moves(Source.Board, e("""{"type":"hello"}""")))
        assertFalse(Streams.moves(Source.Board, e("""{"type":"page.rendered","orderId":"x"}""")))
        assertTrue(Streams.moves(Source.Board, e("""{"type":"order.paid","orderId":"x"}""")))
        assertTrue(Streams.moves(Source.Board, e("""{"type":"finance.synced","source":"schedule"}""")))
        // Not JSON: say it moved rather than miss a change.
        assertTrue(Streams.moves(Source.Trading, e("something")))
    }

    @Test fun streamsFollowTheSignedInSources() {
        assertEquals(emptyList<Stream>(), Streams.of(trading = false, boardProducts = null))
        assertEquals(listOf("orders", "ads"), Streams.of(trading = true, boardProducts = null).map { it.name })
        val board = Streams.of(trading = false, boardProducts = emptyList())
        assertEquals(listOf("https://board.kudcrafts.com/api/p/facemap/admin/events", "https://board.kudcrafts.com/api/p/typemap/admin/events"), board.map { it.url })
        assertEquals(listOf("facemap"), Streams.of(trading = false, boardProducts = listOf("facemap")).map { it.name })
    }

    @Test fun readsAStreamWithTheBearerKey() {
        val server = MockWebServer().apply { start() }
        server.enqueue(
            MockResponse().setHeader("Content-Type", "text/event-stream")
                .setBody(": connected\n\ndata: {\"type\":\"webhook\"}\n\ndata: {\"type\":\"webhook\"}\n\n"),
        )
        server.enqueue(MockResponse().setResponseCode(401))
        val events = mutableListOf<SseEvent>()
        var opened = false
        val end = Sse.read(server.url("/s").toString(), "kud_live_x", {}, { opened = true }, { events += it })
        assertTrue(opened)
        assertEquals(2, events.size)
        assertEquals(StreamEnd.Closed("ended"), end)
        assertEquals("Bearer kud_live_x", server.takeRequest().getHeader("Authorization"))
        assertEquals(StreamEnd.Refused, Sse.read(server.url("/s").toString(), "x", {}, {}, {}))
        server.shutdown()
    }

    private val trading = Overview.parse(javaClass.getResource("/overview-today.json")!!.readText())
    private val board = BoardOverview.parse(javaClass.getResource("/board-today.json")!!.readText())

    @Test fun notificationShowsTodaysNumbersAndTheLinks() {
        val t = Display.Data(TradingSnapshot(trading, null), 1_000L, 0, false, null)
        val b = Display.Data(board, 2_000L, 0, false, null)
        val links = Streams.of(true, listOf("facemap", "typemap")).associateWith { Live.Link.Live }
        val c = LiveNotification.content(t, b, Live.State(running = true, links = links))
        assertTrue(c.title, c.title.startsWith("Trading 5.4M  ·  Board "))
        assertEquals("Live", c.state)
        assertEquals("Connected to 4 streams", c.status)
        assertEquals(2_000L, c.fetchedAt)
        assertTrue(c.rows[0].detail, c.rows[0].detail.contains("orders · ROAS"))

        val oneDown = links + (links.keys.first() to Live.Link.Waiting)
        LiveNotification.content(t, b, Live.State(links = oneDown)).let {
            assertEquals("Reconnecting", it.state)
            assertEquals("3 of 4 streams connected", it.status)
        }
        assertEquals("Offline", LiveNotification.content(t, b, Live.State(online = false, links = links)).state)

        val refusedBoard = LiveNotification.content(t, Display.SignIn(AuthStatus.Refused), Live.State(links = links))
        assertEquals("sign in again", refusedBoard.rows[1].detail)
        assertEquals("Not signed in", LiveNotification.content(null, null, Live.State()).title)
    }
}
