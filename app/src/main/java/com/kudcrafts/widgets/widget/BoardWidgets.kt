package com.kudcrafts.widgets.widget

import android.content.Context
import android.graphics.RectF
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.Text
import com.kudcrafts.widgets.R
import com.kudcrafts.widgets.board.BoardMath
import com.kudcrafts.widgets.board.BoardOverview
import com.kudcrafts.widgets.board.BoardRepo
import com.kudcrafts.widgets.chart.BoardCharts
import com.kudcrafts.widgets.core.Display
import com.kudcrafts.widgets.core.Format
import com.kudcrafts.widgets.core.Jakarta
import com.kudcrafts.widgets.store.Source
import com.kudcrafts.widgets.ui.Palette
import com.kudcrafts.widgets.work.Refresher

private fun density(context: Context) = context.resources.displayMetrics.density

@Composable
private fun NonData(display: Display<*>, title: String, qualifier: String?, url: String) {
    val context = LocalContext.current
    val size = LocalSize.current
    val (time, _) = headerTime(display)
    Column(
        GlanceModifier.fillMaxSize().surface().padding(horizontal = WidgetPadH, vertical = WidgetPadV)
            .clickable(nonDataAction(context, display, Source.Board, url)),
    ) {
        WidgetHeader(title, if (size.width >= 200.dp) qualifier else null, time)
        NonDataBody(context, display, Source.Board, compact = size.height < 100.dp)
    }
}

// ── Board · today (5×1) ───────────────────────────────────────────────────────────────────────────

class BoardTodayWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val display = BoardRepo.display(context)
        provideContent { BoardTodayContent(display) }
    }
}

@Composable
fun BoardTodayContent(display: Display<BoardOverview>) {
    if (display !is Display.Data) return NonData(display, "Board", "today", BoardRepo.DASHBOARD)
    val context = LocalContext.current
    val size = LocalSize.current
    val o = display.data
    val dim = display.stale
    val (time, timeColor) = headerTime(display)
    val revenue = o.combined.revenue?.current?.IDR ?: 0.0
    val contrib = o.combined.contribution?.current
    val showContrib = contrib != null && size.width >= 220.dp
    val products = BoardMath.productLine(o, Format::short)
    val qualifier = if (size.width >= 300.dp && products.isNotEmpty()) "today · $products" else "today"
    val headline = Format.short(revenue)
    val big = if (size.height >= 76.dp) 30.sp else 24.sp

    val w = size.width.value; val h = size.height.value
    val quiet = buildList {
        add(RectF(0f, 0f, 16f + headline.length * big.value * 0.62f + 10f, h))
        if (showContrib) add(RectF(w - 16f - 70f, 26f, w, h))
    }
    val d = density(context)
    val chart = BoardCharts.hourlyArea(
        (w * d).toInt(), (h * d).toInt(), d, BoardMath.hourlyRevenue(o), Jakarta.hourOfDay(display.fetchedAt),
        // Below the header row: the qualifier's product numbers must stay on plain surface.
        topPx = 36f * d,
        quiet = quiet.map { RectF(it.left * d, it.top * d, it.right * d, it.bottom * d) }, dimmed = dim,
    )

    Box(GlanceModifier.fillMaxSize().surface().clickable(openAction(context, BoardRepo.DASHBOARD))) {
        Image(ImageProvider(chart), null, GlanceModifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
        Column(GlanceModifier.fillMaxSize().padding(horizontal = WidgetPadH, vertical = WidgetPadV)) {
            WidgetHeader("Board", qualifier, time, timeColor)
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                Text(headline, style = text(big, if (dim) Palette.creamDim else Palette.cream), maxLines = 1)
                Spacer(GlanceModifier.defaultWeight())
                if (showContrib) Stat("Contrib", Format.short(contrib!!), dim, 18.sp)
            }
        }
    }
}

// ── Board · funnel (3×1) ──────────────────────────────────────────────────────────────────────────

class FunnelWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val display = BoardRepo.display(context)
        provideContent { FunnelContent(display) }
    }
}

@Composable
fun FunnelContent(display: Display<BoardOverview>) {
    if (display !is Display.Data) return NonData(display, "Funnel", null, BoardRepo.DASHBOARD)
    val context = LocalContext.current
    val size = LocalSize.current
    val f = BoardMath.funnel(display.data)
    val dim = display.stale
    val (time, timeColor) = headerTime(display)
    val steps = listOf("Leads" to f.leads, "Selfie" to f.selfies, "Created" to f.created, "Paid" to f.paid)
    val d = density(context)
    val inner = size.width.value - 2 * WidgetPadH.value
    val centers = steps.indices.map { (WidgetPadH.value + (it + 0.5f) * inner / steps.size) * d }
    val band = BoardCharts.funnelBand((size.width.value * d).toInt(), (size.height.value * d).toInt(), d, steps.map { it.second }, centers, dim)

    Box(GlanceModifier.fillMaxSize().surface().clickable(openAction(context, BoardRepo.DASHBOARD))) {
        Image(ImageProvider(band), null, GlanceModifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
        Column(GlanceModifier.fillMaxSize().padding(horizontal = WidgetPadH, vertical = WidgetPadV)) {
            WidgetHeader("Funnel", null, time, timeColor)
            Spacer(GlanceModifier.defaultWeight())
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                steps.forEach { (label, v) ->
                    Column(GlanceModifier.defaultWeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(label, style = text(11.sp, Palette.creamDim), maxLines = 1)
                        Text(Format.count(v), style = text(18.sp, if (dim) Palette.creamDim else Palette.cream), maxLines = 1)
                    }
                }
            }
        }
    }
}

// ── OpenAI credit (2×1) ───────────────────────────────────────────────────────────────────────────

class OpenAiWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val display = BoardRepo.display(context)
        provideContent { OpenAiContent(display) }
    }
}

@Composable
fun OpenAiContent(display: Display<BoardOverview>) {
    if (display !is Display.Data) return NonData(display, "OpenAI", null, BoardRepo.DASHBOARD)
    val context = LocalContext.current
    val size = LocalSize.current
    val b = BoardMath.openAi(display.data)
    val (time, timeColor) = headerTime(display)
    val warn = b == null || b.needsAttention || display.stale || display.error != null
    val note = when {
        b == null -> "no collector"
        b.low -> "low · tap to top up"
        b.problem != null || b.stale -> "not updating"
        display.stale -> "stale"
        else -> null
    }
    val d = density(context)
    val liquid = BoardCharts.liquid(
        (size.width.value * d).toInt(), (size.height.value * d).toInt(), d,
        b?.let(BoardMath::tankLevel) ?: 0.0, warning = warn, dimmed = false,
    )
    val url = b?.topUpUrl ?: BoardRepo.DASHBOARD
    Box(
        GlanceModifier.fillMaxSize()
            .background(ImageProvider(if (warn) R.drawable.widget_surface_warn else R.drawable.widget_surface))
            .cornerRadius(22.dp)
            .clickable(openAction(context, url)),
    ) {
        Image(ImageProvider(liquid), null, GlanceModifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
        Column(GlanceModifier.fillMaxSize().padding(start = 14.dp, end = 14.dp, top = WidgetPadV, bottom = 6.dp)) {
            WidgetHeader("OpenAI", null, time, timeColor)
            Text(
                b?.let { Format.usdShort(it.available) } ?: "–",
                style = text(if (note != null && size.height < 100.dp) 22.sp else 26.sp, if (warn) Palette.downText else Palette.cream),
                maxLines = 1,
            )
            if (note != null) Text(note, style = text(11.sp, Palette.cream.copy(alpha = 0.78f)), maxLines = 1)
        }
    }
}

class BoardTodayReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = BoardTodayWidget()
    override fun onEnabled(context: Context) { super.onEnabled(context); Refresher.schedule(context); Refresher.now(context) }
}

class FunnelReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = FunnelWidget()
    override fun onEnabled(context: Context) { super.onEnabled(context); Refresher.schedule(context); Refresher.now(context) }
}

class OpenAiReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = OpenAiWidget()
    override fun onEnabled(context: Context) { super.onEnabled(context); Refresher.schedule(context); Refresher.now(context) }
}
