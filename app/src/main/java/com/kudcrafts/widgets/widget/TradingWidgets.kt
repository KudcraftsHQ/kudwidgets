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
import com.kudcrafts.widgets.core.Window
import com.kudcrafts.widgets.store.OptionsStore
import com.kudcrafts.widgets.store.Period
import com.kudcrafts.widgets.store.WidgetOptions
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
        /** Today: running to now, against yesterday. */
        fun of(kpis: Kpis, today: List<Bucket>, yesterday: List<Bucket>?, fetchedAt: Long): TradingFigure {
            val now = Jakarta.hourOfDay(fetchedAt)
            return TradingFigure(
                kpis = kpis,
                delta = yesterday?.let { Curves.deltaVsYesterday(kpis.revenue, it, now) },
                today = Curves.today(Curves.hourly(today), now),
                yesterday = yesterday?.let { Curves.fullDay(Curves.hourly(it)) }.orEmpty(),
            )
        }

        /** Any window: today compares with yesterday; yesterday is a whole day; 7 days / month run per day. */
        fun of(kpis: Kpis, series: List<Bucket>, yesterday: List<Bucket>?, fetchedAt: Long, period: Period, from: String): TradingFigure =
            when (period) {
                Period.Today -> of(kpis, series, yesterday, fetchedAt)
                Period.Yesterday -> TradingFigure(kpis, null, Curves.today(Curves.hourly(series), 24.0), emptyList())
                else -> {
                    val slots = Window.days(period, from)
                    val daily = Window.daily(from, slots, series.map { it.t to it.revenue })
                    val last = Window.dayIndex(from, Jakarta.day(fetchedAt)) ?: (slots - 1)
                    TradingFigure(kpis, null, Curves.windowRunning(daily, last), emptyList())
                }
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

/**
 * The chart band's bitmap, `widthDp × bandDp`, pinned to the widget's bottom. `statsLeftDp` is where the
 * stats column starts (the time axis stops short of it, so no curve or ring ever sits on a stat); `quietDp`
 * is text that can reach into the band, in band coordinates.
 */
private fun chartBand(
    context: Context,
    widthDp: Float,
    bandDp: Float,
    figure: TradingFigure,
    statsLeftDp: Float?,
    quietDp: List<RectF>,
    dimmed: Boolean,
    yesterdayLabel: Boolean,
    bottomOfWidget: Boolean,
): android.graphics.Bitmap {
    val d = context.resources.displayMetrics.density
    return RunningChart.draw(
        RunningChart.Spec(
            widthPx = (widthDp * d).toInt(),
            heightPx = (bandDp * d).toInt(),
            density = d,
            today = figure.today,
            yesterday = figure.yesterday,
            topFraction = 0.10f,
            bottomInsetDp = 5f,
            quiet = quietDp.map { RectF(it.left * d, it.top * d, it.right * d, it.bottom * d) },
            dimmed = dimmed,
            yesterdayLabel = yesterdayLabel,
            clipBottomCorners = bottomOfWidget,
            plotRightPx = statsLeftDp?.let { (it - 10f) * d },
        ),
    )
}

/** Text that sits in widget coordinates `rect`, moved into a bottom band `bandDp` tall of a widget `hDp` tall. */
private fun intoBand(rect: RectF, hDp: Float, bandDp: Float): RectF? {
    val top = hDp - bandDp
    if (rect.bottom <= top) return null
    return RectF(rect.left, rect.top - top, rect.right, rect.bottom - top)
}

// ── Trading · total (5×2) ─────────────────────────────────────────────────────────────────────────

class TradingTotalWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val options = OptionsStore(context).get(id)
        val display = TradingRepo.display(context, options.period)
        provideContent { TradingTotalContent(display, options) }
    }
}

@Composable
fun TradingTotalContent(display: Display<TradingSnapshot>, options: WidgetOptions = WidgetOptions()) {
    val context = LocalContext.current
    val size = LocalSize.current
    val (time, timeColor) = headerTime(display)
    val defaultUrl = options.filter?.let(TradingRepo::businessUrl) ?: TradingRepo.DASHBOARD
    val tapUrl = options.tapUrl ?: defaultUrl

    if (display !is Display.Data) {
        Column(
            GlanceModifier.fillMaxSize().surface().padding(horizontal = WidgetPadH, vertical = WidgetPadV)
                .clickable(nonDataAction(context, display, Source.Trading, tapUrl)),
        ) {
            WidgetHeader("Trading", options.period.label, time)
            NonDataBody(context, display, Source.Trading, compact = size.height < 100.dp)
        }
        return
    }

    val snap = display.data
    val business = options.filter?.let { slug -> snap.today.businesses.firstOrNull { it.slug == slug } }
    val kpis = business?.kpis ?: snap.today.combined.kpis
    val series = business?.series ?: snap.today.combined.series
    val ySeries = if (business != null) snap.yesterday?.businesses?.firstOrNull { it.slug == business.slug }?.series
    else snap.yesterday?.combined?.series
    val figure = TradingFigure.of(kpis, series, ySeries, display.fetchedAt, options.period, snap.today.dates.from)
    val qualifier = listOfNotNull(business?.name, options.period.label).joinToString(" · ")
    val dim = display.stale
    // Launchers can under-report height (Hammas's does, by ~40%), so only width decides what fits beside the
    // headline; the stats sit bottom-right whatever the real height is.
    val showStats = size.width >= 260.dp
    val showDelta = size.height >= 70.dp
    val bigSize = if (size.height >= 112.dp) 38.sp else 30.sp
    val headline = Format.short(figure.kpis.revenue)
    val statValues = listOf(Format.count(figure.kpis.orders), Format.roas(figure.kpis.roas), Format.short(figure.kpis.contribution))

    val w = size.width.value; val h = size.height.value
    // A fixed-height band at the bottom, sized from the width (launchers report widths right, heights not
    // always); only a genuinely short widget (5×1) trims it.
    val band = minOf(w * 0.26f, h * 0.9f).coerceAtLeast(36f)
    val statsLeft = if (showStats) w - WidgetPadH.value - statsWidthDp(statValues, 18f, 20f) else null
    val headlineBlock = RectF(0f, 0f, WidgetPadH.value + textWidthDp(headline, bigSize.value) + 12f,
        WidgetPadV.value + 22f + bigSize.value * 1.25f + (if (showDelta) 20f else 0f) + 6f)
    val chart = chartBand(context, w, band, figure, statsLeft, listOfNotNull(intoBand(headlineBlock, h, band)), dim,
        yesterdayLabel = true, bottomOfWidget = true)

    Box(GlanceModifier.fillMaxSize().surface().clickable(openAction(context, tapUrl))) {
        BottomBand(chart, band.dp)
        Column(GlanceModifier.fillMaxSize().padding(horizontal = WidgetPadH, vertical = WidgetPadV)) {
            WidgetHeader("Trading", qualifier, time, timeColor)
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
        val options = OptionsStore(context).get(id)
        val display = TradingRepo.display(context, options.period)
        provideContent { TradingBusinessContent(display, options) }
    }
}

@Composable
fun TradingBusinessContent(display: Display<TradingSnapshot>, options: WidgetOptions = WidgetOptions()) {
    val context = LocalContext.current
    val size = LocalSize.current
    val (time, timeColor) = headerTime(display)
    val headerUrl = options.tapUrl ?: TradingRepo.DASHBOARD

    if (display !is Display.Data) {
        Column(
            GlanceModifier.fillMaxSize().surface().padding(horizontal = WidgetPadH, vertical = WidgetPadV)
                .clickable(nonDataAction(context, display, Source.Trading, headerUrl)),
        ) {
            WidgetHeader("Trading", options.period.label, time)
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
    val qualifier = "${options.period.label} · total ${Format.short(snap.today.combined.kpis.revenue)}" + if (more > 0) " · +$more" else ""

    Column(GlanceModifier.fillMaxSize().surface()) {
        Box(
            GlanceModifier.fillMaxWidth().height(headerH).padding(start = WidgetPadH, end = WidgetPadH, top = WidgetPadV)
                .clickable(openAction(context, headerUrl)),
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
            val figure = TradingFigure.of(b.kpis, b.series, yesterdayBySlug[b.slug]?.series, display.fetchedAt, options.period, snap.today.dates.from)
            // Each band takes its share of the real height by weight; bandH (from the reported size) only sizes its chart.
            BusinessBand(context, DpSize(size.width, bandH - if (i > 0) 1.dp else 0.dp), b.name, b.slug, figure, dim, GlanceModifier.defaultWeight(), last = i == shown.lastIndex)
        }
    }
}

@Composable
private fun BusinessBand(context: Context, bandSize: DpSize, name: String, slug: String, figure: TradingFigure, dim: Boolean, modifier: GlanceModifier, last: Boolean) {
    val showStats = bandSize.width >= 260.dp
    val showDelta = bandSize.height >= 56.dp
    val headline = Format.short(figure.kpis.revenue)
    val statValues = listOf(Format.count(figure.kpis.orders), Format.roas(figure.kpis.roas), Format.short(figure.kpis.contribution))
    val w = bandSize.width.value; val h = bandSize.height.value
    val band = minOf(w * 0.15f, h * 0.9f).coerceAtLeast(28f)
    val statsLeft = if (showStats) w - WidgetPadH.value - statsWidthDp(statValues, 15f, 16f) else null
    val headlineBlock = RectF(0f, 0f, WidgetPadH.value + 28f + textWidthDp(headline, 26f) + 10f, 8f + 34f + (if (showDelta) 18f else 0f) + 6f)
    // No "yesterday" label in a band: it would crowd the stats, and the total widget names the dashes.
    val chart = chartBand(context, w, band, figure, statsLeft, listOfNotNull(intoBand(headlineBlock, h, band)), dim,
        yesterdayLabel = false, bottomOfWidget = last)
    Box(
        modifier.fillMaxWidth().clickable(openAction(context, TradingRepo.businessUrl(slug))),
    ) {
        BottomBand(chart, band.dp)
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

class TradingTotalReceiver : KudReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TradingTotalWidget()
}

class TradingBusinessReceiver : KudReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TradingBusinessWidget()
}
