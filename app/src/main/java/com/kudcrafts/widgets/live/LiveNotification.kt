package com.kudcrafts.widgets.live

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.kudcrafts.widgets.MainActivity
import com.kudcrafts.widgets.R
import com.kudcrafts.widgets.board.BoardMath
import com.kudcrafts.widgets.board.BoardOverview
import com.kudcrafts.widgets.core.Display
import com.kudcrafts.widgets.core.Format
import com.kudcrafts.widgets.core.Jakarta
import com.kudcrafts.widgets.trading.Curves
import com.kudcrafts.widgets.trading.TradingSnapshot

/**
 * The live service's ongoing notification: today's numbers at a glance, so the notification earns its place.
 *
 *     ⌁ Kud Widgets · Live · 10:42
 *     Trading 5.4M  ·  Board 2.1M
 *     Connected to 4 streams · last push 10:41
 *       ▾ expanded
 *     Today
 *     Trading  5.4M  ▲ 12% · 64 orders · ROAS 5.6
 *     Board    2.1M  31 paid · F 1.6M · T 520K
 */
object LiveNotification {
    const val ID = 1
    private const val CHANNEL = "live"

    /** One source's row: the label, the headline number, and the rest. */
    data class Row(val label: String, val headline: String, val detail: String, val delta: Double? = null)

    /** `state` is the one word in the header ("Live"); `status` the line under the numbers when collapsed. */
    data class Content(val title: String, val state: String, val status: String, val rows: List<Row>, val fetchedAt: Long?)

    fun content(
        trading: Display<TradingSnapshot>?,
        board: Display<BoardOverview>?,
        state: Live.State,
    ): Content {
        val rows = buildList {
            trading?.let { add(tradingRow(it)) }
            board?.let { add(boardRow(it)) }
        }
        val fetched = listOfNotNull(
            (trading as? Display.Data)?.fetchedAt,
            (board as? Display.Data)?.fetchedAt,
        ).maxOrNull()
        val title = when {
            rows.isEmpty() -> "Not signed in"
            else -> rows.joinToString("  ·  ") { "${it.label} ${it.headline}" }
        }
        return Content(title, word(state, rows.isEmpty()), status(state, rows.isEmpty()), rows, fetched)
    }

    private fun tradingRow(d: Display<TradingSnapshot>): Row = when (d) {
        is Display.Data -> {
            val k = d.data.today.combined.kpis
            val delta = d.data.yesterday?.let { Curves.deltaVsYesterday(k.revenue, it.combined.series, Jakarta.hourOfDay(d.fetchedAt)) }
            Row(
                "Trading", Format.short(k.revenue),
                listOfNotNull(shortDelta(delta), "${Format.count(k.orders)} orders", "ROAS ${Format.roas(k.roas)}").joinToString(" · "),
                delta,
            )
        }
        else -> Row("Trading", "–", notData(d))
    }

    private fun boardRow(d: Display<BoardOverview>): Row = when (d) {
        is Display.Data -> {
            val k = d.data.combined
            val products = BoardMath.productLine(d.data, Format::short)
            Row(
                "Board", Format.short(k.revenue?.current?.IDR ?: 0.0),
                listOfNotNull(k.paid?.current?.let { "${Format.count(it)} paid" }, products.ifEmpty { null }).joinToString(" · "),
            )
        }
        else -> Row("Board", "–", notData(d))
    }

    /** "▲ 12%": the widget's delta without "vs yesterday", which the notification has no room for. */
    fun shortDelta(pct: Double?): String? = Format.delta(pct)?.removeSuffix(" vs yesterday")

    private fun word(s: Live.State, nothing: Boolean): String = when {
        nothing -> "Off"
        !s.online -> "Offline"
        s.liveCount == 0 -> "Connecting"
        s.liveCount < s.links.values.count { it != Live.Link.Refused } -> "Reconnecting"
        else -> "Live"
    }

    private fun notData(d: Display<*>): String = when (d) {
        is Display.SignIn -> "sign in again"
        is Display.Failed -> "can't reach it"
        else -> "loading…"
    }

    private fun status(s: Live.State, nothing: Boolean): String {
        // A stream the key may not open (a product board didn't grant) isn't coming back: leave it out of the count.
        val active = s.links.values.count { it != Live.Link.Refused }
        val live = s.liveCount
        return when {
            nothing -> "Sign in to a source in the app"
            !s.online -> "Waiting for network"
            active == 0 && s.links.isNotEmpty() -> "Live updates refused · sign in again"
            live == 0 -> "Connecting…"
            live == active -> "Connected to ${plural(live, "stream")}" + (s.lastEventAt?.let { " · last push ${Jakarta.clock(it)}" } ?: "")
            else -> "$live of $active streams connected"
        }
    }

    private fun plural(n: Int, word: String) = if (n == 1) "1 $word" else "$n ${word}s"

    fun ensureChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) != null) return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Live updates", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Keeps the widgets connected so they update the moment the numbers move."
                setShowBadge(false)
            },
        )
    }

    fun build(context: Context, c: Content): Notification {
        ensureChannel(context)
        val accent = ContextCompat.getColor(context, R.color.kw_live)
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        fun service(action: String, code: Int) = PendingIntent.getService(
            context, code, Intent(context, LiveService::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        // Expanded, the rows carry the numbers, so the title steps back to say what they are.
        val style = NotificationCompat.InboxStyle().setBigContentTitle("Today")
        for (row in c.rows) style.addLine(line(context, row))

        // The lock screen gets the state, never the revenue.
        val public = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_live)
            .setColor(accent)
            .setContentTitle("Live updates")
            .setSubText(c.state)
            .build()

        return NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_live)
            .setColor(accent)
            .setContentTitle(c.title)
            .setContentText(c.status)
            .setSubText(c.state)
            .setStyle(style)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(c.fetchedAt != null)
            .setWhen(c.fetchedAt ?: System.currentTimeMillis())
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(public)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(0, "Refresh", service(LiveService.ACTION_REFRESH, 1))
            .addAction(0, "Pause live", service(LiveService.ACTION_STOP, 2))
            .build()
    }

    /** "Trading  5.4M  ▲ 12% vs yesterday · 64 orders": the label and headline bold, the delta in its colour. */
    private fun line(context: Context, row: Row): CharSequence {
        val b = SpannableStringBuilder()
        b.append(row.label, StyleSpan(Typeface.BOLD), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        b.append("  ")
        b.append(row.headline, StyleSpan(Typeface.BOLD), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        if (row.detail.isNotEmpty()) {
            b.append("  ")
            val start = b.length
            b.append(row.detail)
            val deltaText = shortDelta(row.delta)
            if (deltaText != null && row.delta != null && row.detail.startsWith(deltaText) && kotlin.math.abs(row.delta) >= 0.5) {
                val color = ContextCompat.getColor(context, if (row.delta > 0) R.color.kw_up_on_any else R.color.kw_down_fill)
                b.setSpan(ForegroundColorSpan(color), start, start + deltaText.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
        return b
    }
}
