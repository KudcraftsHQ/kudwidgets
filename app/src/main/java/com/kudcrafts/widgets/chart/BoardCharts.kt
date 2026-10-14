package com.kudcrafts.widgets.chart

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Shader
import androidx.compose.ui.graphics.toArgb
import com.kudcrafts.widgets.chart.RunningChart.withAlpha
import com.kudcrafts.widgets.ui.Palette
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt

/** Background bitmaps for the board widgets. Same rules as [RunningChart]: no axes, ink kept off the text. */
object BoardCharts {
    private val cream get() = Palette.cream.toArgb()

    private fun canvas(w: Int, h: Int) = Bitmap.createBitmap(w.coerceAtLeast(1), h.coerceAtLeast(1), Bitmap.Config.ARGB_8888)

    /** Clip to the widget's own corners: not every launcher clips a widget's children to its outline. */
    internal fun clipCorners(c: Canvas, w: Int, h: Int, d: Float) {
        c.clipPath(Path().apply { addRoundRect(RectF(0f, 0f, w.toFloat(), h.toFloat()), 22f * d, 22f * d, Path.Direction.CW) })
    }

    private fun hush(c: Canvas, quiet: List<RectF>, d: Float) {
        if (quiet.isEmpty()) return
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = withAlpha(0xFF000000.toInt(), 0.85f)
            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
            maskFilter = BlurMaskFilter(10f * d, BlurMaskFilter.Blur.NORMAL)
        }
        for (r in quiet) c.drawRoundRect(r, 12f * d, 12f * d, p)
    }

    /**
     * Board · today: a faint area of IDR revenue per slot (hours of a day, or days of a window), the window's
     * start at the left edge, through the last finished slot. Faint on purpose: the number is the point.
     */
    fun revenueArea(w: Int, h: Int, d: Float, slots: DoubleArray, finished: Int, topPx: Float, quiet: List<RectF>, dimmed: Boolean): Bitmap {
        val bmp = canvas(w, h)
        val c = Canvas(bmp)
        clipCorners(c, w, h, d)
        val n = slots.size.coerceAtLeast(1)
        val max = slots.take(finished.coerceIn(0, n)).maxOrNull() ?: 0.0
        if (finished < 1 || max <= 0) return bmp
        val top = topPx
        val bottom = h - 4f * d
        val strength = if (dimmed) 0.6f else 1f
        // A point mid-hour per finished hour, anchored at 0 on the left.
        val pts = buildList {
            add(0f to bottom)
            for (i in 0 until finished.coerceAtMost(n)) {
                add(((i + 0.5) / n * w).toFloat() to (bottom - (slots[i] / max) * (bottom - top)).toFloat())
            }
        }
        val line = smoothNoMonotone(pts)
        val fill = Path(line).apply {
            lineTo(pts.last().first, h.toFloat()); lineTo(0f, h.toFloat()); close()
        }
        c.drawPath(fill, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, top, 0f, h.toFloat(), withAlpha(cream, 0.20f * strength), withAlpha(cream, 0f), Shader.TileMode.CLAMP)
        })
        c.drawPath(line, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 1.6f * d; color = withAlpha(cream, 0.38f * strength)
            strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
        })
        hush(c, quiet, d)
        return bmp
    }

    /**
     * Funnel: a faint band that narrows left to right, one step per column. Height ∝ √(step ÷ leads), so a
     * 2% conversion still reads as a band rather than a hairline.
     */
    fun funnelBand(w: Int, h: Int, d: Float, steps: List<Double>, columnCenters: List<Float>, dimmed: Boolean): Bitmap {
        val bmp = canvas(w, h)
        val c = Canvas(bmp)
        clipCorners(c, w, h, d)
        val first = steps.firstOrNull()?.takeIf { it > 0 } ?: return bmp
        val maxH = h * 0.62f
        val minH = h * 0.10f
        val heights = steps.map { (minH + (maxH - minH) * sqrt((it / first).coerceIn(0.0, 1.0))).toFloat() }
        val pts = buildList {
            add(0f to h - heights.first())
            columnCenters.zip(heights).forEach { (x, hh) -> add(x to h - hh) }
            add(w.toFloat() to h - heights.last())
        }
        val edge = smoothNoMonotone(pts)
        val band = Path(edge).apply { lineTo(w.toFloat(), h.toFloat()); lineTo(0f, h.toFloat()); close() }
        val strength = if (dimmed) 0.6f else 1f
        c.drawPath(band, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, h - maxH, 0f, h.toFloat(), withAlpha(cream, 0.13f * strength), withAlpha(cream, 0.04f * strength), Shader.TileMode.CLAMP)
        })
        return bmp
    }

    /**
     * OpenAI credit: liquid at the bottom, as high as the tank is full, with a soft wave on top. Warning: the
     * coral fill. The surface colour itself comes from the widget's background.
     */
    fun liquid(w: Int, h: Int, d: Float, level: Double, warning: Boolean, dimmed: Boolean): Bitmap {
        val bmp = canvas(w, h)
        val c = Canvas(bmp)
        clipCorners(c, w, h, d)
        val lvl = level.coerceIn(0.0, 1.0).toFloat()
        // Never quite empty or brim-full: a sliver stays visible, and the text above stays on the surface.
        val surfaceY = h - (h * (0.08f + 0.47f * lvl))
        val amp = 3.2f * d
        val path = Path().apply {
            moveTo(0f, h.toFloat())
            lineTo(0f, surfaceY)
            val steps = 48
            for (i in 0..steps) {
                val x = w * i / steps.toFloat()
                val y = surfaceY + amp * sin((i / steps.toFloat()) * 2 * PI * 1.25 + 0.6).toFloat()
                lineTo(x, y)
            }
            lineTo(w.toFloat(), h.toFloat())
            close()
        }
        val strength = if (dimmed) 0.6f else 1f
        val base = if (warning) Palette.downFill.toArgb() else cream
        val a = if (warning) 0.78f else 0.16f
        c.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, surfaceY, 0f, h.toFloat(), withAlpha(base, a * strength), withAlpha(base, a * 0.75f * strength), Shader.TileMode.CLAMP)
        })
        return bmp
    }

    /** Catmull-Rom through the points (fine where values may dip, unlike a running total). */
    internal fun smoothNoMonotone(pts: List<Pair<Float, Float>>): Path {
        val p = Path()
        if (pts.isEmpty()) return p
        p.moveTo(pts[0].first, pts[0].second)
        for (i in 0 until pts.size - 1) {
            val p0 = pts[maxOf(i - 1, 0)]; val p1 = pts[i]; val p2 = pts[i + 1]; val p3 = pts[minOf(i + 2, pts.lastIndex)]
            p.cubicTo(
                p1.first + (p2.first - p0.first) / 6f, p1.second + (p2.second - p0.second) / 6f,
                p2.first - (p3.first - p1.first) / 6f, p2.second - (p3.second - p1.second) / 6f,
                p2.first, p2.second,
            )
        }
        return p
    }
}
