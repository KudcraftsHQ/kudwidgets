package com.kudcrafts.widgets

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import com.kudcrafts.widgets.widget.BoardTodayWidget
import com.kudcrafts.widgets.widget.FunnelWidget
import com.kudcrafts.widgets.widget.OpenAiWidget
import com.kudcrafts.widgets.widget.TradingBusinessWidget
import com.kudcrafts.widgets.widget.TradingTotalWidget
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every widget offers a ladder of heights (the system picks the rung by the real size). Each rung carries its
 * own chart bitmaps, and Android refuses an update whose bitmaps exceed screen pixels × 4 bytes × 1.5. Check the
 * worst case (a full-width widget, every rung, charts at the 2× cap) against a modest 1080×2400 phone.
 */
class SizingTest {
    private val budget = 1080L * 2400 * 4 * 3 / 2
    private val scale = 2.0
    private val fullWidthDp = 411.0

    private fun rungs(w: GlanceAppWidget) = (w.sizeMode as SizeMode.Responsive).sizes.map { it.height.value.toDouble() }.sorted()

    private fun bytes(widthDp: Double, heights: List<Double>, slack: Double, chrome: Double = 0.0) =
        heights.sumOf { h -> (widthDp * scale) * ((h + slack - chrome).coerceAtLeast(0.0) * scale) * 4 }.toLong()

    @Test fun laddersAreSmallEnoughAndFitTheBitmapBudget() {
        val cases = listOf(
            Triple("trading total", TradingTotalWidget(), 10.0),
            Triple("trading by business", TradingBusinessWidget(), 18.0),
            Triple("board today", BoardTodayWidget(), 8.0),
            Triple("funnel", FunnelWidget(), 8.0),
            Triple("openai", OpenAiWidget(), 8.0),
        )
        for ((name, widget, slack) in cases) {
            val h = rungs(widget)
            assertTrue("$name: ${h.size} rungs", h.size in 4..16)
            val used = bytes(fullWidthDp, h, slack)
            assertTrue("$name: ${used / 1_000_000} MB of ${budget / 1_000_000} MB", used < budget)
        }
    }

    @Test fun rungsCoverPhoneWidgetHeights() {
        // One cell row is ~60–100dp on phones; the ladders must reach the tallest of each widget's sizes.
        assertTrue(rungs(TradingTotalWidget()).last() >= 240.0)
        assertTrue(rungs(TradingBusinessWidget()).last() >= 360.0)
        assertTrue(rungs(BoardTodayWidget()).first() <= 60.0)
    }
}
