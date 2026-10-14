package com.kudcrafts.widgets.widget

import android.content.Context
import android.graphics.RectF
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
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
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.Text
import androidx.glance.unit.ColorProvider
import com.kudcrafts.widgets.chart.RunningChart
import com.kudcrafts.widgets.core.Display
import com.kudcrafts.widgets.core.Format
import com.kudcrafts.widgets.core.Jakarta
import com.kudcrafts.widgets.store.Source
import com.kudcrafts.widgets.trading.Bucket
import com.kudcrafts.widgets.trading.Curves
import com.kudcrafts.widgets.trading.Kpis
import com.kudcrafts.widgets.trading.TradingRepo
import com.kudcrafts.widgets.trading.TradingSnapshot
import com.kudcrafts.widgets.ui.Palette
import com.kudcrafts.widgets.work.Refresher

/** What one Trading figure needs on screen: the headline, the comparison and the curves behind it. */
data class TradingFigure(
    val kpis: Kpis,
    val delta: Double?,
    val today: List<Pair<Double, Double>>,
    val yesterday: List<Pair<Double, Double>>,
) {
    companion object {
        fun of(kpis: Kpis, today: List<Bucket>, yesterday: List<Bucket>?, fetchedAt: Long): TradingFigure {
            val now = Jakarta.hourOfDay(fetchedAt)
            return TradingFigure(
                kpis = kpis,
                delta = yesterday?.let { Curves.deltaVsYesterday(kpis.revenue, it, now) },
                today = Curves.today(Curves.hourly(today), now),
                yesterday = yesterday?.let { Curves.fullDay(Curves.hourly(it)) }.orEmpty(),
            )
        }
    }
}

private fun deltaColor(pct: Double) = when {
    pct >= 0.5 -> Palette.up
    pct <= -0.5 -> Palette.downText
    else -> Palette.creamDim
}

@Composable
private fun DeltaLine(delta: Double?, dim: Boolean, size: androidx.compose.ui.unit.TextUnit = 13.sp) {
    val label = Format.delta(delta) ?: return
    Text(label, style = text(size, if (dim) Palette.creamDim else deltaColor(delta!!)), maxLines = 1)
}

@Composable
private fun StatsRow(kpis: Kpis, dim: Boolean, valueSize: androidx.compose.ui.unit.TextUnit, gap: androidx.compose.ui.unit.Dp) {
    Row(verticalAlignment = Alignment.Bottom) {
        Stat("Orders", Format.count(kpis.orders), dim, valueSize)
        Spacer(GlanceModifier.width(gap))
        Stat("ROAS", Format.roas(kpis.roas), dim, valueSize)
        Spacer(GlanceModifier.width(gap))
        Stat("Contrib", Format.short(kpis.contribution), dim, valueSize)
    }
}

private fun chartImage(
    context: Context,
    size: DpSize,
    figure: TradingFigure,
    topFraction: Float,
    quietDp: List<RectF>,
    dimmed: Boolean,
    yesterdayLabel: Boolean = true,
): ImageProvider {
    val d = context.resources.displayMetrics.density
    val bmp = RunningChart.draw(
        RunningChart.Spec(
            widthPx = (size.width.value * d).toInt(),
            heightPx = (size.height.value * d).toInt(),
            density = d,
            today = figure.today,
            yesterday = figure.yesterday,
            topFraction = topFraction,
            quiet = quietDp.map { RectF(it.left * d, it.top * d, it.right * d, it.bottom * d) },
            dimmed = dimmed,
            yesterdayLabel = yesterdayLabel,
        ),
    )
    return ImageProvider(bmp)
}

// ── Trading · total (5×2) ─────────────────────────────────────────────────────────────────────────

class TradingTotalWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val display = TradingRepo.display(context)
        provideContent { TradingTotalContent(display) }
    }
}

@Composable
fun TradingTotalContent(display: Display<TradingSnapshot>) {
    val context = LocalContext.current
    val size = LocalSize.current
    val (time, timeColor) = headerTime(display)

    if (display !is Display.Data) {
        Column(
            GlanceModifier.fillMaxSize().surface().padding(horizontal = WidgetPadH, vertical = WidgetPadV)
                .clickable(nonDataAction(context, display, Source.Trading, TradingRepo.DASHBOARD)),
        ) {
            WidgetHeader("Trading", "today", time)
            NonDataBody(context, display, Source.Trading, compact = size.height < 100.dp)
        }
        return
    }

    val snap = display.data
    val figure = TradingFigure.of(snap.today.combined.kpis, snap.today.combined.series, snap.yesterday?.combined?.series, display.fetchedAt)
    val dim = display.stale
    val showStats = size.height >= 112.dp && size.width >= 250.dp
    val showDelta = size.height >= 84.dp
    val bigSize = if (size.height >= 112.dp) 38.sp else 30.sp
    val headline = Format.short(figure.kpis.revenue)

    // Where real text sits, so the chart hushes there (dp, widget coordinates).
    val w = size.width.value; val h = size.height.value
    val quiet = buildList {
        add(RectF(0f, 0f, 24f + headline.length * bigSize.value * 0.62f + 8f, 22f + bigSize.value * 1.25f + (if (showDelta) 22f else 0f) + 12f))
        if (showStats) add(RectF(w - 16f - 186f, h - 12f - 44f, w - 6f, h - 4f))
    }

    Box(GlanceModifier.fillMaxSize().surface().clickable(openAction(context, TradingRepo.DASHBOARD))) {
        Image(
            provider = chartImage(context, size, figure, if (showStats) 0.34f else 0.42f, quiet, dim),
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = GlanceModifier.fillMaxSize(),
        )
        Column(GlanceModifier.fillMaxSize().padding(horizontal = WidgetPadH, vertical = WidgetPadV)) {
            WidgetHeader("Trading", "today", time, timeColor)
            Text(headline, style = text(bigSize, if (dim) Palette.creamDim else Palette.cream), maxLines = 1)
            if (showDelta) DeltaLine(figure.delta, dim)
        }
        if (showStats) {
            Box(GlanceModifier.fillMaxSize().padding(horizontal = WidgetPadH, vertical = WidgetPadV), contentAlignment = Alignment.BottomEnd) {
                StatsRow(figure.kpis, dim, 18.sp, 20.dp)
            }
        }
    }
}

// ── Trading · by business (5×3) ───────────────────────────────────────────────────────────────────

class TradingBusinessWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val display = TradingRepo.display(context)
        provideContent { TradingBusinessContent(display) }
    }
}

@Composable
fun TradingBusinessContent(display: Display<TradingSnapshot>) {
    val context = LocalContext.current
    val size = LocalSize.current
    val (time, timeColor) = headerTime(display)

    if (display !is Display.Data) {
        Column(
            GlanceModifier.fillMaxSize().surface().padding(horizontal = WidgetPadH, vertical = WidgetPadV)
                .clickable(nonDataAction(context, display, Source.Trading, TradingRepo.DASHBOARD)),
        ) {
            WidgetHeader("Trading", "today", time)
            NonDataBody(context, display, Source.Trading, compact = size.height < 100.dp)
        }
        return
    }

    val snap = display.data
    val dim = display.stale
    val businesses = snap.today.businesses.sortedByDescending { it.kpis.revenue }
    val shown = businesses.take(2)
    val more = businesses.size - shown.size
    val yesterdayBySlug = snap.yesterday?.businesses?.associateBy { it.slug }.orEmpty()

    val headerH = 34.dp
    val bandH = if (shown.isEmpty()) 0.dp else (size.height - headerH - WidgetPadV) / shown.size
    val qualifier = "today · total ${Format.short(snap.today.combined.kpis.revenue)}" + if (more > 0) " · +$more" else ""

    Column(GlanceModifier.fillMaxSize().surface()) {
        Box(
            GlanceModifier.fillMaxWidth().height(headerH).padding(start = WidgetPadH, end = WidgetPadH, top = WidgetPadV)
                .clickable(openAction(context, TradingRepo.DASHBOARD)),
        ) {
            WidgetHeader("Trading", qualifier, time, timeColor)
        }
        if (shown.isEmpty()) {
            Text("No businesses on this key", style = text(13.sp, Palette.creamDim), modifier = GlanceModifier.padding(horizontal = WidgetPadH))
        }
        shown.forEachIndexed { i, b ->
            if (i > 0) {
                Box(GlanceModifier.fillMaxWidth().padding(horizontal = WidgetPadH)) {
                    Box(GlanceModifier.fillMaxWidth().height(1.dp).background(ColorProvider(Palette.cream.copy(alpha = 0.10f)))) {}
                }
            }
            val figure = TradingFigure.of(b.kpis, b.series, yesterdayBySlug[b.slug]?.series, display.fetchedAt)
            BusinessBand(context, DpSize(size.width, bandH - if (i > 0) 1.dp else 0.dp), b.name, b.slug, figure, dim)
        }
    }
}

@Composable
private fun BusinessBand(context: Context, band: DpSize, name: String, slug: String, figure: TradingFigure, dim: Boolean) {
    val showStats = band.height >= 84.dp && band.width >= 250.dp
    val showDelta = band.height >= 64.dp
    val headline = Format.short(figure.kpis.revenue)
    val w = band.width.value; val h = band.height.value
    val quiet = buildList {
        add(RectF(0f, 0f, 16f + 26f + headline.length * 26f * 0.62f + 10f, 10f + 32f + (if (showDelta) 18f else 0f) + 8f))
        if (showStats) add(RectF(w - 16f - 168f, h - 10f - 38f, w - 6f, h - 2f))
    }
    Box(
        GlanceModifier.fillMaxWidth().height(band.height).clickable(openAction(context, TradingRepo.businessUrl(slug))),
    ) {
        Image(
            // No "yesterday" label in a band: it would crowd the stats, and the total widget names the dashes.
            provider = chartImage(context, band, figure, 0.36f, quiet, dim, yesterdayLabel = false),
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = GlanceModifier.fillMaxSize(),
        )
        Column(GlanceModifier.fillMaxSize().padding(start = WidgetPadH, end = WidgetPadH, top = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LetterChip(name)
                Spacer(GlanceModifier.width(8.dp))
                Text(headline, style = text(26.sp, if (dim) Palette.creamDim else Palette.cream), maxLines = 1)
            }
            if (showDelta) DeltaLine(figure.delta, dim, 12.sp)
        }
        if (showStats) {
            Box(GlanceModifier.fillMaxSize().padding(start = WidgetPadH, end = WidgetPadH, bottom = 10.dp), contentAlignment = Alignment.BottomEnd) {
                StatsRow(figure.kpis, dim, 15.sp, 16.dp)
            }
        }
    }
}

/** The business's first letter, like trading-bar's menu bar label. */
@Composable
private fun LetterChip(name: String) {
    val letter = name.firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString() ?: "?"
    Box(
        GlanceModifier.size(20.dp).background(ImageProvider(com.kudcrafts.widgets.R.drawable.letter_chip)),
        contentAlignment = Alignment.Center,
    ) {
        Text(letter, style = text(11.sp, Palette.cream, bold = true), maxLines = 1)
    }
}

class TradingTotalReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TradingTotalWidget()
    override fun onEnabled(context: Context) { super.onEnabled(context); Refresher.schedule(context); Refresher.now(context) }
}

class TradingBusinessReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TradingBusinessWidget()
    override fun onEnabled(context: Context) { super.onEnabled(context); Refresher.schedule(context); Refresher.now(context) }
}
