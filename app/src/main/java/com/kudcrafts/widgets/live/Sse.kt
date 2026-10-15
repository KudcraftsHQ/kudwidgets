package com.kudcrafts.widgets.live

import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/** One server-sent event: the `event:` name (null for the default "message") and its joined `data:` lines. */
data class SseEvent(val event: String?, val data: String)

/** The text/event-stream wire format, line by line. Comments (`: ping`) and `id:`/`retry:` are dropped. */
class SseParser(private val onEvent: (SseEvent) -> Unit) {
    private var event: String? = null
    private val data = StringBuilder()

    fun line(line: String) {
        if (line.isEmpty()) {
            if (data.isNotEmpty()) onEvent(SseEvent(event, data.removeSuffix("\n").toString()))
            event = null
            data.clear()
            return
        }
        if (line.startsWith(":")) return
        val colon = line.indexOf(':')
        val field = if (colon < 0) line else line.substring(0, colon)
        val value = if (colon < 0) "" else line.substring(colon + 1).removePrefix(" ")
        when (field) {
            "event" -> event = value
            "data" -> data.append(value).append('\n')
        }
    }
}

/** How a stream ended. */
sealed interface StreamEnd {
    /** 401/403: the key is no good for this stream; don't hammer it. */
    data object Refused : StreamEnd
    data class Closed(val reason: String) : StreamEnd
}

object Sse {
    /**
     * Its own client: no overall read deadline, but a quiet stream (no event, no `: ping`) for 150s counts as dead,
     * and HTTP/2 pings notice a dead connection behind Cloudflare long before that.
     */
    val client: OkHttpClient by lazy {
        com.kudcrafts.widgets.net.Http.client.newBuilder()
            .readTimeout(150, TimeUnit.SECONDS)
            .pingInterval(30, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Read one stream until it ends. Blocking; `onCall` hands back the call so a cancelled coroutine can close the
     * socket, which is the only way to unblock the read.
     */
    fun read(
        url: String,
        token: String,
        onCall: (okhttp3.Call) -> Unit,
        onOpen: () -> Unit,
        onEvent: (SseEvent) -> Unit,
    ): StreamEnd {
        val call = client.newCall(
            Request.Builder().url(url)
                .header("Authorization", "Bearer $token")
                .header("Accept", "text/event-stream")
                .header("Cache-Control", "no-cache")
                .build(),
        )
        onCall(call)
        return try {
            call.execute().use { res ->
                if (res.code == 401 || res.code == 403) return StreamEnd.Refused
                if (!res.isSuccessful) return StreamEnd.Closed("HTTP ${res.code}")
                onOpen()
                val parser = SseParser(onEvent)
                val source = res.body!!.source()
                while (true) parser.line(source.readUtf8Line() ?: break)
                StreamEnd.Closed("ended")
            }
        } catch (e: IOException) {
            StreamEnd.Closed(e.message ?: "network error")
        }
    }
}
