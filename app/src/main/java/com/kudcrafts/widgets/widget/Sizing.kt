package com.kudcrafts.widgets.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.os.Build
import android.util.SizeF
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.LocalSize
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.SizeMode
import kotlin.math.min

/*
 * Sizing that doesn't trust the launcher.
 *
 * Some launchers (seen on Hammas's phone) report a widget much shorter than it really is. SizeMode.Exact draws
 * for the reported size and the bitmaps get stretched: the "now" ring turns into an oval, the stats vanish.
 * On Android 12+ the *system* picks, from the sizes a widget offers, the largest that fits the widget's real
 * measured size. So each widget offers a ladder of heights: the real height picks the rung. Every rung has a
 * narrow key width (so width never rules a rung out); the real width comes from the launcher, whose widths
 * have been right. Bitmaps are drawn half a rung taller than the key, so the stretch is at most ± half a rung.
 */

/** The narrow width every rung uses as its key, so only height decides. */
private val KEY_WIDTH = 48.dp

/** A ladder of heights `from..to` every `step`. Keep it to ≤ 16 rungs (RemoteViews' limit). */
fun heightLadder(from: Dp, to: Dp, step: Dp): SizeMode.Responsive {
    val sizes = generateSequence(from) { it + step }.takeWhile { it <= to }.map { DpSize(KEY_WIDTH, it) }.toSet()
    require(sizes.size in 1..16)
    return SizeMode.Responsive(sizes)
}

/** The launcher-reported width (its widths are right), or null for previews and when it reports nothing. */
fun reportedWidth(context: Context, id: GlanceId): Dp? = runCatching {
    val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
    val opts = AppWidgetManager.getInstance(context).getAppWidgetOptions(appWidgetId)
    val sizes: List<SizeF>? = if (Build.VERSION.SDK_INT >= 31) {
        @Suppress("DEPRECATION")
        opts.getParcelableArrayList<SizeF>(AppWidgetManager.OPTION_APPWIDGET_SIZES)
    } else null
    val w = sizes?.maxOfOrNull { it.width }?.takeIf { it > 0 }
        ?: opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH).toFloat().takeIf { it > 0 }
    w?.dp
}.getOrNull()

/** Provided by each widget from [reportedWidth]; null in previews, where LocalSize is the real size. */
val LocalReportedWidth = compositionLocalOf<Dp?> { null }

/** Provided by each widget: half its ladder's step, added to bitmap heights. Zero in previews. */
val LocalHeightSlack = compositionLocalOf { 0.dp }

/**
 * The best estimate of the widget's real size, to lay out and draw for: the launcher's width, and the rung's
 * height plus half a step (the real height lies somewhere in the rung).
 */
@Composable
fun widgetSize(): DpSize {
    val s = LocalSize.current
    val w = LocalReportedWidth.current
    return if (w != null && s.width <= KEY_WIDTH) DpSize(w, s.height + LocalHeightSlack.current) else s
}

/** Wraps a widget's content with its real width and its ladder's slack. */
@Composable
fun Sized(width: Dp?, ladderStep: Dp, content: @Composable () -> Unit) =
    androidx.compose.runtime.CompositionLocalProvider(
        LocalReportedWidth provides width,
        LocalHeightSlack provides ladderStep / 2,
        content = content,
    )

/**
 * Pixels per dp for chart bitmaps: the screen's density, capped at 2 so a ladder of rungs stays inside the
 * per-widget bitmap budget (screen pixels × 4 bytes × 1.5). The soft charts don't need more.
 */
fun chartScale(context: Context): Float = min(context.resources.displayMetrics.density, 2f)
