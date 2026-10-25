package com.kudcrafts.widgets.live

import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.ServiceCompat
import com.kudcrafts.widgets.board.BoardRepo
import com.kudcrafts.widgets.core.AuthStatus
import com.kudcrafts.widgets.core.Display
import com.kudcrafts.widgets.store.Accounts
import com.kudcrafts.widgets.store.Source
import com.kudcrafts.widgets.trading.TradingRepo
import com.kudcrafts.widgets.work.Refresher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

/**
 * Holds every signed-in source's event stream open and refreshes that source the moment an event says its numbers
 * moved, so the widgets follow the dashboards within seconds instead of every 15 minutes. A foreground service,
 * because nothing else may keep a connection open; its notification shows today's numbers.
 *
 * Events only ever say "something changed": the numbers still come from the same overview reads the widgets use,
 * so nothing is counted twice or differently. Bursts are folded into one refresh per source, at most one per
 * [MIN_GAP_MS].
 */
class LiveService : Service() {
    companion object {
        const val ACTION_REFRESH = "com.kudcrafts.widgets.live.REFRESH"
        const val ACTION_STOP = "com.kudcrafts.widgets.live.STOP"

        /** Wait this long after an event for the rest of its burst (a webhook rarely comes alone). */
        private const val SETTLE_MS = 2_000L
        /** At most one refresh per source this often. */
        private const val MIN_GAP_MS = 20_000L
        /** Down at least this long and the stream may have missed events: refresh once it's back. */
        private const val MISSED_AFTER_MS = 30_000L
        private const val BACKOFF_MIN_MS = 2_000L
        private const val BACKOFF_MAX_MS = 60_000L
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** The streams being held, with the token each was opened with; reconciled against the accounts. */
    private var held: Map<Stream, String> = emptyMap()
    private var holding: Job? = null
    /** Open calls, so a stop or a network change can close their sockets (a blocked read ignores cancellation). */
    private val calls = ConcurrentHashMap<Stream, okhttp3.Call>()
    /** Bumped when the network comes back: streams waiting out a backoff retry at once. */
    private val wake = MutableStateFlow(0)

    private val lastRefresh = ConcurrentHashMap<Source, Long>()
    private val refreshing = mutableMapOf<Source, Job>()
    private val again = mutableSetOf<Source>()

    private var network: Network? = null
    private val netCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(n: Network) {
            val changed = network != null && network != n
            network = n
            Live.update { it.copy(online = true) }
            // A new network strands sockets on the old one: reconnect now rather than at the next read timeout.
            StreamLog.add(null, if (changed) "network changed · reconnecting all" else "network available")
            if (changed) calls.values.forEach { it.cancel() }
            wake.value++
        }

        override fun onLost(n: Network) {
            if (n != network) return
            network = null
            StreamLog.add(null, "network lost")
            Live.update { it.copy(online = false) }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        StreamLog.init(this)
        StreamLog.add(null, "service started")
        val cm = getSystemService(ConnectivityManager::class.java)
        network = cm.activeNetwork
        Live.update { Live.State(running = true, online = network != null) }
        cm.registerDefaultNetworkCallback(netCallback)
        goForeground(placeholder())

        // Redraw the notification whenever the links or the numbers change, and follow sign-ins and sign-outs.
        scope.launch {
            combine(Live.state, Refresher.version) { s, _ -> s }.collectLatest { s ->
                delay(300)
                if (!reconcile()) return@collectLatest
                notifyNow(s)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Every startForegroundService must be answered with startForeground, even one that is about to stop.
        goForeground(placeholder())
        when (intent?.action) {
            ACTION_STOP -> {
                Live.setEnabled(this, false)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_REFRESH -> Source.entries.forEach { trigger(it, "refresh", force = true) }
        }
        scope.launch { reconcile() }
        return START_STICKY
    }

    override fun onDestroy() {
        StreamLog.add(null, "service stopped")
        scope.cancel()
        calls.values.forEach { it.cancel() }
        runCatching { getSystemService(ConnectivityManager::class.java).unregisterNetworkCallback(netCallback) }
        Live.update { Live.State() }
        super.onDestroy()
    }

    // ── streams ─────────────────────────────────────────────────────────────────────────────────────────────

    /** Hold exactly the streams the signed-in accounts allow. False when there are none and the service stops. */
    private suspend fun reconcile(): Boolean {
        val accounts = Accounts(this)
        val want = buildMap {
            val now = System.currentTimeMillis()
            val tradingToken = accounts.token(Source.Trading)
                ?.takeIf { accounts.account(Source.Trading).status(now) == AuthStatus.SignedIn }
            val boardToken = accounts.token(Source.Board)
                ?.takeIf { accounts.account(Source.Board).status(now) == AuthStatus.SignedIn }
            val products = (BoardRepo.display(this@LiveService) as? Display.Data)?.data?.products?.map { it.slug }
            for (s in Streams.of(tradingToken != null, if (boardToken != null) products.orEmpty() else null)) {
                put(s, if (s.source == Source.Trading) tradingToken!! else boardToken!!)
            }
        }
        synchronized(this) {
            if (want == held) return want.isNotEmpty()
            holding?.cancel()
            calls.values.forEach { it.cancel() }
            calls.clear()
            held = want
            Live.update { it.copy(links = want.keys.associateWith { Live.Link.Connecting }) }
            if (want.isEmpty()) {
                stopSelf()
                return false
            }
            val job = SupervisorJob(scope.coroutineContext[Job])
            holding = job
            val streamScope = CoroutineScope(scope.coroutineContext + job)
            want.forEach { (stream, token) -> streamScope.hold(stream, token) }
        }
        return true
    }

    private fun secs(ms: Long) = if (ms < 10_000) "%.1fs".format(ms / 1000.0) else "${ms / 1000}s"

    private fun describe(end: StreamEnd) = when (end) {
        StreamEnd.Refused -> "refused (401/403)"
        is StreamEnd.Closed -> end.reason
    }

    private fun link(stream: Stream, l: Live.Link) = Live.update {
        if (stream in it.links) it.copy(links = it.links + (stream to l)) else it
    }

    private fun CoroutineScope.hold(stream: Stream, token: String) = launch {
        var backoff = BACKOFF_MIN_MS
        var downSince: Long? = null
        while (isActive) {
            Live.state.first { it.online }
            link(stream, Live.Link.Connecting)
            StreamLog.add(stream, "connecting")
            val startedAt = System.currentTimeMillis()
            var openedAt = 0L
            var pings = 0
            var events = 0
            val end = Sse.read(
                stream.url, token,
                onCall = { calls[stream] = it },
                onOpen = { how ->
                    openedAt = System.currentTimeMillis()
                    StreamLog.add(stream, "open · $how · headers after ${secs(openedAt - startedAt)}")
                    link(stream, Live.Link.Live)
                    if (downSince != null && openedAt - downSince!! >= MISSED_AFTER_MS) trigger(stream.source, "back online")
                    downSince = null
                },
                onEvent = { e ->
                    val name = e.event ?: Regex("\"type\"\\s*:\\s*\"([^\"]+)\"").find(e.data)?.groupValues?.get(1) ?: "message"
                    if (name == "ping") {
                        if (pings++ == 0) StreamLog.add(stream, "first ping after ${secs(System.currentTimeMillis() - startedAt)}")
                    } else {
                        events++
                        StreamLog.add(stream, "event $name")
                    }
                    if (Streams.moves(stream.source, e)) {
                        Live.update { it.copy(lastEventAt = System.currentTimeMillis()) }
                        trigger(stream.source, "live")
                    }
                },
            )
            calls.remove(stream)
            val up = if (openedAt > 0) "up ${secs(System.currentTimeMillis() - openedAt)}" else "never opened, ${secs(System.currentTimeMillis() - startedAt)}"
            StreamLog.add(stream, "ended · ${describe(end)} · $up · $events events, $pings pings" + if (!isActive) " (stopped)" else "")
            if (!isActive) break
            if (end == StreamEnd.Refused) {
                link(stream, Live.Link.Refused)
                break
            }
            val now = System.currentTimeMillis()
            link(stream, Live.Link.Waiting)
            if (downSince == null) downSince = now
            // A stream that stayed up a while and then dropped (a deploy, a read timeout) retries right away.
            if (openedAt > 0 && now - openedAt > BACKOFF_MAX_MS) backoff = BACKOFF_MIN_MS
            StreamLog.add(stream, "retry in ~${secs(backoff)}")
            val seen = wake.value
            withTimeoutOrNull(backoff + Random.nextLong(backoff / 4 + 1)) { wake.first { it != seen } }
            backoff = (backoff * 2).coerceAtMost(BACKOFF_MAX_MS)
        }
    }

    // ── refreshes ───────────────────────────────────────────────────────────────────────────────────────────

    /** Refresh `source` soon: one run per burst, at most one per MIN_GAP_MS; events during a run queue one more. */
    private fun trigger(source: Source, why: String, force: Boolean = false) {
        synchronized(refreshing) {
            if (refreshing[source] != null) {
                again += source
                return
            }
            refreshing[source] = scope.launch {
                // Held from the event to the redraw: a delay doesn't advance while the CPU sleeps.
                val lock = getSystemService(PowerManager::class.java)
                    .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "kudwidgets:live-refresh")
                lock.acquire(90_000L)
                try {
                    var first = true
                    do {
                        val sinceLast = System.currentTimeMillis() - (lastRefresh[source] ?: 0L)
                        val wait = if (force && first) 0L else maxOf(SETTLE_MS, MIN_GAP_MS - sinceLast)
                        first = false
                        delay(wait)
                        synchronized(refreshing) { again -= source }
                        runCatching { Refresher.refresh(this@LiveService, setOf(source), " · $why") }
                        lastRefresh[source] = System.currentTimeMillis()
                    } while (synchronized(refreshing) {
                            (source in again).also { if (!it) refreshing.remove(source) }
                        })
                } finally {
                    // Only our own entry: a trigger after the loop's last check may already have started the next run.
                    val self = coroutineContext[Job]
                    synchronized(refreshing) { if (refreshing[source] === self) refreshing.remove(source) }
                    if (lock.isHeld) lock.release()
                }
            }
        }
    }

    // ── notification ────────────────────────────────────────────────────────────────────────────────────────

    private fun placeholder() = LiveNotification.build(this, lastContent ?: LiveNotification.Content("Kud Widgets", "Connecting", "Connecting…", emptyList(), null))

    @Volatile private var lastContent: LiveNotification.Content? = null

    private suspend fun notifyNow(s: Live.State) {
        val accounts = Accounts(this)
        val now = System.currentTimeMillis()
        // A signed-out source has no row; a refused or expired one keeps its row, asking to sign in again.
        val trading = if (accounts.account(Source.Trading).status(now) == AuthStatus.SignedOut) null else TradingRepo.display(this)
        val board = if (accounts.account(Source.Board).status(now) == AuthStatus.SignedOut) null else BoardRepo.display(this)
        val c = LiveNotification.content(trading, board, s)
        lastContent = c
        getSystemService(NotificationManager::class.java).notify(LiveNotification.ID, LiveNotification.build(this, c))
    }

    private fun goForeground(n: android.app.Notification) {
        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        ServiceCompat.startForeground(this, LiveNotification.ID, n, type)
    }
}
