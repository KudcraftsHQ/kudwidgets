package com.kudcrafts.widgets

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.kudcrafts.widgets.board.Balance
import com.kudcrafts.widgets.board.BoardDates
import com.kudcrafts.widgets.board.BoardKpis
import com.kudcrafts.widgets.board.BoardOverview
import com.kudcrafts.widgets.board.BoardPoint
import com.kudcrafts.widgets.board.Money
import com.kudcrafts.widgets.board.MoneyPair
import com.kudcrafts.widgets.board.NumPair
import com.kudcrafts.widgets.board.Product
import com.kudcrafts.widgets.core.Cached
import com.kudcrafts.widgets.core.Jakarta
import com.kudcrafts.widgets.store.Accounts
import com.kudcrafts.widgets.store.CacheStore
import com.kudcrafts.widgets.store.Source
import com.kudcrafts.widgets.trading.Bucket
import com.kudcrafts.widgets.trading.Business
import com.kudcrafts.widgets.trading.Dates
import com.kudcrafts.widgets.trading.Kpis
import com.kudcrafts.widgets.trading.Overview
import com.kudcrafts.widgets.trading.Summary
import com.kudcrafts.widgets.trading.TradingSnapshot
import com.kudcrafts.widgets.work.Refresher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.sin

/**
 * Debug builds only. `adb shell am broadcast -n com.kudcrafts.widgets.debug/com.kudcrafts.widgets.DemoSeeder`
 * signs both sources in with a dummy key, fills the caches with made-up numbers for "now", and pauses network
 * refresh (the `demo` flag file), so every widget can be placed and photographed on a device. `--ez off true`
 * undoes it.
 */
class DemoSeeder : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val flag = File(context.filesDir, "demo")
                val accounts = Accounts(context)
                if (intent.getBooleanExtra("off", false)) {
                    flag.delete()
                    Source.entries.forEach { accounts.signOut(it) }
                } else {
                    flag.writeText("1")
                    val now = System.currentTimeMillis()
                    Source.entries.forEach { accounts.signIn(it, "demo", "demo@kudcrafts.com", "Kud Widgets · demo", now + 300L * 86_400_000) }
                    CacheStore(context, "trading", TradingSnapshot.serializer()).write(Cached(trading(now), now))
                    CacheStore(context, "board", BoardOverview.serializer()).write(Cached(board(now), now))
                }
                Refresher.redraw(context)
            } finally {
                pending.finish()
            }
        }
    }

    private val shape = doubleArrayOf(0.4, 0.2, 0.1, 0.1, 0.2, 0.6, 1.5, 3.0, 5.5, 7.5, 8.0, 7.0, 6.0, 6.5, 6.0, 5.5, 5.0, 5.5, 6.5, 7.0, 6.5, 4.5, 2.5, 1.2)

    private fun series(day: String, total: Double, untilHour: Double, jitter: Double): List<Bucket> {
        val sum = shape.sum()
        val whole = untilHour.toInt()
        return (0..minOf(whole, 23)).map { h ->
            val part = if (h == whole) untilHour - whole else 1.0
            val r = total * shape[h] / sum * (1 + jitter * sin(h * 1.7)) * part
            Bucket("${day}T%02d:00".format(h), orders = Math.round(r / 85_000.0).toDouble(), revenue = r)
        }
    }

    private fun overview(preset: String, day: String, until: Double, scale: Double): Overview {
        val h = series(day, 9_600_000.0 * scale, until, 0.25)
        val e = series(day, 4_100_000.0 * scale, until, -0.3)
        fun s(l: List<Bucket>) = l.sumOf { it.revenue }
        val biz = listOf(
            Business("atlas", "Atlas Parts", Kpis(s(h), 51.0, roas = 5.9, contribution = s(h) * 0.36), h),
            Business("birch", "Birch Flowers", Kpis(s(e), 25.0, roas = null, contribution = s(e) * 0.31), e),
        )
        val combined = h.indices.map { i -> Bucket(h[i].t, h[i].orders + e[i].orders, h[i].revenue + e[i].revenue) }
        return Overview(preset, Dates(day, day), "hour", biz, Summary(Kpis(s(h) + s(e), 76.0, roas = 5.6, contribution = (s(h) + s(e)) * 0.35), combined))
    }

    private fun trading(now: Long): TradingSnapshot {
        val today = Jakarta.day(now)
        val yesterday = java.time.LocalDate.parse(today).minusDays(1).toString()
        return TradingSnapshot(overview("today", today, Jakarta.hourOfDay(now), 1.12), overview("yesterday", yesterday, 23.999, 1.0))
    }

    private fun board(now: Long): BoardOverview {
        val day = Jakarta.day(now)
        val until = Jakarta.hourOfDay(now)
        val sum = shape.sum()
        val pts = (0..minOf(until.toInt(), 23)).map { h ->
            val w = shape[h] / sum * 24
            BoardPoint("${day}T%02d:00".format(h), revenueIDR = 95_000.0 * w * (1 + 0.4 * sin(h * 2.1)), paid = 0.6 * w, leads = 5.1 * w, selfies = 2.2 * w, created = 1.3 * w)
        }
        val rev = pts.sumOf { it.revenueIDR }
        return BoardOverview(
            preset = "today", dates = BoardDates(day, day),
            products = listOf(
                Product("facemap", "FaceMap", kpis = BoardKpis(revenue = MoneyPair(Money(rev * 0.74)), contribution = NumPair(rev * 0.15)), series = pts.map { it.copy(revenueIDR = it.revenueIDR * 0.74) }),
                Product("typemap", "TypeMap", kpis = BoardKpis(revenue = MoneyPair(Money(rev * 0.26)), contribution = NumPair(rev * 0.05)), series = pts.map { it.copy(revenueIDR = it.revenueIDR * 0.26, leads = 0.0, selfies = 0.0, created = 0.0, paid = 0.0) }),
            ),
            combined = BoardKpis(revenue = MoneyPair(Money(rev)), contribution = NumPair(rev * 0.2)),
            balances = listOf(Balance("openai", "OpenAI", available = 88.0, granted = 540.0, warnBelow = 5.0, topUpUrl = "https://platform.openai.com/settings/organization/billing/overview")),
        )
    }
}
