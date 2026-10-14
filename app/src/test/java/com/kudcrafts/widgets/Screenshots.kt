package com.kudcrafts.widgets

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.View
import android.widget.FrameLayout
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.appwidget.ExperimentalGlanceRemoteViewsApi
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.compose
import androidx.glance.appwidget.provideContent
import androidx.test.core.app.ApplicationProvider
import com.kudcrafts.widgets.core.AuthStatus
import com.kudcrafts.widgets.core.Display
import com.kudcrafts.widgets.trading.Bucket
import com.kudcrafts.widgets.trading.Business
import com.kudcrafts.widgets.trading.Dates
import com.kudcrafts.widgets.trading.Kpis
import com.kudcrafts.widgets.trading.Overview
import com.kudcrafts.widgets.trading.Summary
import com.kudcrafts.widgets.trading.TradingSnapshot
import com.kudcrafts.widgets.widget.TradingBusinessContent
import com.kudcrafts.widgets.widget.TradingTotalContent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.ZonedDateTime

/**
 * Renders each widget state to PNG (app/build/screenshots) through the real RemoteViews path, so PRs can
 * show what the widgets look like without an emulator. Not an assertion: a picture to look at.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi", application = android.app.Application::class)
class Screenshots {
    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private val out = File(System.getProperty("user.dir"), "build/screenshots").apply { mkdirs() }

    /** 11:05 WIB on 2026-10-14. */
    private val at = ZonedDateTime.parse("2026-10-14T04:05:00Z").toInstant().toEpochMilli()
    private val min = 60_000L

    // A plausible day: quiet night, a morning rise, an evening bump. Shares are per hour.
    private val shape = doubleArrayOf(0.4, 0.2, 0.1, 0.1, 0.2, 0.6, 1.5, 3.0, 5.5, 7.5, 8.0, 7.0, 6.0, 6.5, 6.0, 5.5, 5.0, 5.5, 6.5, 7.0, 6.5, 4.5, 2.5, 1.2)

    private fun series(day: String, total: Double, untilHour: Int, jitter: Double = 0.0): List<Bucket> {
        val sum = shape.sum()
        return (0 until untilHour).map { h ->
            // The current hour (11:05 → hour 11) has only its first minutes in.
            val part = if (untilHour < 24 && h == untilHour - 1) 5.0 / 60 else 1.0
            val r = total * shape[h] / sum * (1 + jitter * kotlin.math.sin(h * 1.7)) * part
            Bucket("${day}T%02d:00".format(h), orders = Math.round(r / 85_000.0).toDouble(), revenue = r)
        }
    }

    private fun kpis(revenue: Double, orders: Double, roas: Double?, contrib: Double) =
        Kpis(revenue = revenue, orders = orders, roas = roas, contribution = contrib)

    private fun overview(preset: String, day: String, hours: Int, scale: Double): Overview {
        val h = series(day, 9_600_000.0 * scale, hours, 0.25)
        val e = series(day, 4_100_000.0 * scale, hours, -0.3)
        fun s(l: List<Bucket>) = l.sumOf { it.revenue }
        val biz = listOf(
            Business("atlas", "Atlas Parts", kpis(s(h), 41.0, 5.9, s(h) * 0.36), h),
            Business("birch", "Birch Flowers", kpis(s(e), 23.0, 4.8, s(e) * 0.31), e),
        )
        val combined = (0 until hours).map { i ->
            Bucket(h[i].t, h[i].orders + e[i].orders, h[i].revenue + e[i].revenue)
        }
        return Overview(preset, Dates(day, day), "hour", biz, Summary(kpis(s(h) + s(e), 64.0, 5.6, (s(h) + s(e)) * 0.35), combined))
    }

    private val snapshot = TradingSnapshot(
        today = overview("today", "2026-10-14", 12, 1.12),
        yesterday = overview("yesterday", "2026-10-13", 24, 1.0),
    )

    private fun data(ageMin: Long = 0, stale: Boolean = false, error: String? = null) =
        Display.Data(snapshot, at - ageMin * min, ageMin, stale, error)

    private class Fixed(val body: @androidx.compose.runtime.Composable () -> Unit) : GlanceAppWidget() {
        override val sizeMode = SizeMode.Exact
        override suspend fun provideGlance(context: Context, id: GlanceId) = provideContent { body() }
    }

    @OptIn(ExperimentalGlanceRemoteViewsApi::class)
    private suspend fun shoot(name: String, size: DpSize, body: @androidx.compose.runtime.Composable () -> Unit) {
        val rv = Fixed(body).compose(ctx, size = size)
        val d = ctx.resources.displayMetrics.density
        val pad = (14 * d).toInt()
        val w = (size.width.value * d).toInt()
        val h = (size.height.value * d).toInt()
        val host = FrameLayout(ctx)
        val view: View = rv.apply(ctx, host)
        host.addView(view, FrameLayout.LayoutParams(w, h))
        host.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
        host.layout(0, 0, w, h)
        val bmp = Bitmap.createBitmap(w + 2 * pad, h + 2 * pad, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.rgb(0x2B, 0x24, 0x2E)) // his wallpaper's night purple
        c.translate(pad.toFloat(), pad.toFloat())
        host.draw(c)
        File(out, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    // His launcher: 5 columns on a ~411dp-wide screen. 5×2 ≈ 390×158dp, 5×3 ≈ 390×240dp.
    private val total = DpSize(390.dp, 158.dp)
    private val byBusiness = DpSize(390.dp, 240.dp)

    @Test fun tradingTotal() = runTest {
        shoot("trading-total-fresh", total) { TradingTotalContent(data()) }
        shoot("trading-total-stale", total) { TradingTotalContent(data(ageMin = 38, stale = true)) }
        shoot("trading-total-error", total) { TradingTotalContent(data(ageMin = 22, error = "HTTP 502")) }
        shoot("trading-total-signin", total) { TradingTotalContent(Display.SignIn(AuthStatus.SignedOut)) }
        shoot("trading-total-refused", total) { TradingTotalContent(Display.SignIn(AuthStatus.Refused)) }
        shoot("trading-total-loading", total) { TradingTotalContent(Display.Loading) }
        shoot("trading-total-failed", total) { TradingTotalContent(Display.Failed("HTTP 502")) }
        shoot("trading-total-5x1", DpSize(390.dp, 82.dp)) { TradingTotalContent(data()) }
        shoot("trading-total-3x2", DpSize(234.dp, 158.dp)) { TradingTotalContent(data()) }
    }

    @Test fun tradingByBusiness() = runTest {
        shoot("trading-business-fresh", byBusiness) { TradingBusinessContent(data()) }
        shoot("trading-business-stale", byBusiness) { TradingBusinessContent(data(ageMin = 38, stale = true)) }
        shoot("trading-business-signin", byBusiness) { TradingBusinessContent(Display.SignIn(AuthStatus.Expired)) }
        shoot("trading-business-5x2", total) { TradingBusinessContent(data()) }
    }
}
