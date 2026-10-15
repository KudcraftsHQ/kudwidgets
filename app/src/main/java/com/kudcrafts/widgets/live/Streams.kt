package com.kudcrafts.widgets.live

import com.kudcrafts.widgets.auth.TradingOAuth
import com.kudcrafts.widgets.board.BoardApi
import com.kudcrafts.widgets.store.Source
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

/** One event stream the live service holds open, and which source an event on it refreshes. */
data class Stream(val source: Source, val name: String, val url: String)

object Streams {
    /** board proxies each product's `/api/admin/events`; these are the products when no overview is cached yet. */
    val DEFAULT_BOARD_PRODUCTS = listOf("facemap", "typemap")

    /**
     * kudtrading: Desty order webhooks (a new or changed order moves revenue) and Databro ad syncs (ad spend).
     * board: each product's admin feed.
     */
    fun of(trading: Boolean, boardProducts: List<String>?): List<Stream> = buildList {
        if (trading) {
            add(Stream(Source.Trading, "orders", "${TradingOAuth.BASE}/api/webhooks/stream"))
            add(Stream(Source.Trading, "ads", "${TradingOAuth.BASE}/api/ads/stream"))
        }
        if (boardProducts != null) for (slug in boardProducts.ifEmpty { DEFAULT_BOARD_PRODUCTS }) {
            add(Stream(Source.Board, slug, "${BoardApi.BASE}/api/p/$slug/admin/events"))
        }
    }

    private val json = Json { ignoreUnknownKeys = true }

    /** Handshakes and keep-alives say nothing changed. */
    private val QUIET = setOf("connected", "hello", "ping")

    /**
     * board events that move a number its widgets show: a payment, a refund or removal, and the ad/AI spend sync.
     * Rendering progress (page.*, report_stage.*) is chatter.
     */
    private val BOARD_NUMBERS = setOf("order.paid", "admin.refunded", "admin.cancelled", "admin.deleted", "finance.synced")

    /** Should this event refresh the stream's source? */
    fun moves(source: Source, event: SseEvent): Boolean {
        val type = runCatching { json.parseToJsonElement(event.data) as? JsonObject }.getOrNull()
            ?.get("type")?.jsonPrimitive?.content
            ?: event.event
        if (type in QUIET) return false
        return when (source) {
            // Every Desty webhook is an order changing state; every ads_update is fresh spend.
            Source.Trading -> true
            Source.Board -> type in BOARD_NUMBERS
        }
    }
}
