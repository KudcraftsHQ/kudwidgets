package com.kudcrafts.widgets.chart

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.compose.ui.graphics.toArgb
import com.kudcrafts.widgets.ui.Palette

/**
 * The background of a Trading widget: today's running revenue to "now" with a soft cream fill, a hairline
 * and ring at now, and yesterday's whole day dashed to the right edge. No axes, no grid.
 *
 * It is a bitmap under real text, so it keeps out of the text's way: the scale tops out at [topFraction]
 * of the height (header and the big number own the top-left), and [quiet] rects (the stats block) get a
 * feathered surface scrim so no curve runs through a number.
 */
object RunningChart {
    data class Spec(
        val widthPx: Int,
        val heightPx: Int,
        val density: Float,
        /** (hour 0..24, running IDR) up to now. */
        val today: List<Pair<Double, Double>>,
        /** (hour 0..24, running IDR) for the whole of yesterday; empty when unknown. */
        val yesterday: List<Pair<Double, Double>>,
        /** Where the highest value sits, as a fraction of the height from the top. */
        val topFraction: Float = 0.32f,
        val bottomInsetDp: Float = 6f,
        /** Areas (in px) that real text will cover; the chart is hushed under them. */
        val quiet: List<RectF> = emptyList(),
        /** Draw everything at reduced strength (stale numbers). */
        val dimmed: Boolean = false,
        val yesterdayLabel: Boolean = true,
    )

    fun draw(spec: Spec): Bitmap {
        val w = spec.widthPx.coerceAtLeast(1)
        val h = spec.heightPx.coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val d = spec.density
        val cream = Palette.cream.toArgb()
        val strength = if (spec.dimmed) 0.55f else 1f

        val max = maxOf(spec.today.maxOfOrNull { it.second } ?: 0.0, spec.yesterday.maxOfOrNull { it.second } ?: 0.0)
        val top = h * spec.topFraction
        val bottom = h - spec.bottomInsetDp * d
        fun x(hour: Double) = (hour / 24.0 * w).toFloat()
        fun y(v: Double) = if (max <= 0) bottom else (bottom - (v / max) * (bottom - top)).toFloat()

        // Yesterday, dashed, whole day to the right edge.
        if (spec.yesterday.size >= 2) {
            val p = smooth(spec.yesterday.map { x(it.first) to y(it.second) })
            c.drawPath(p, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 1.4f * d
                color = withAlpha(cream, 0.40f * strength)
                pathEffect = DashPathEffect(floatArrayOf(4f * d, 4f * d), 0f)
                strokeCap = Paint.Cap.ROUND
            })
            if (spec.yesterdayLabel) {
                val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    textSize = 10f * d
                    color = withAlpha(cream, 0.45f * strength)
                    textAlign = Paint.Align.RIGHT
                    typeface = Typeface.DEFAULT
                }
                // Under the line across the label's whole width, so the dashes never strike through it.
                val right = w - 8f * d
                val left = right - tp.measureText("yesterday")
                val lowest = spec.yesterday.filter { x(it.first) >= left - 4f * d }.maxOf { y(it.second) }
                c.drawText("yesterday", right, lowest + 13f * d, tp)
            }
        }

        // Today: fill, line, hairline and ring at now.
        if (spec.today.size >= 2) {
            val pts = spec.today.map { x(it.first) to y(it.second) }
            val line = smooth(pts)
            val fill = Path(line).apply {
                lineTo(pts.last().first, h.toFloat())
                lineTo(pts.first().first, h.toFloat())
                close()
            }
            val peak = pts.minOf { it.second }
            c.drawPath(fill, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(
                    0f, peak, 0f, h.toFloat(),
                    withAlpha(cream, 0.30f * strength), withAlpha(cream, 0f), Shader.TileMode.CLAMP,
                )
            })
            c.drawPath(line, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 2.2f * d
                color = withAlpha(cream, strength)
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            })
            val (nx, ny) = pts.last()
            c.drawLine(nx, ny, nx, h.toFloat(), Paint(Paint.ANTI_ALIAS_FLAG).apply {
                strokeWidth = 1f * d
                color = withAlpha(cream, 0.28f * strength)
            })
            c.drawCircle(nx, ny, 4.5f * d, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Palette.surface.toArgb() })
            c.drawCircle(nx, ny, 4.5f * d, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 2f * d
                color = withAlpha(cream, strength)
            })
        }

        // Hush the chart where text sits: erase most of the ink there (feathered), so no box shows where
        // there was nothing to hide.
        if (spec.quiet.isNotEmpty()) {
            val scrim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = withAlpha(0xFF000000.toInt(), 0.85f)
                xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
                maskFilter = BlurMaskFilter(10f * d, BlurMaskFilter.Blur.NORMAL)
            }
            for (r in spec.quiet) c.drawRoundRect(r, 12f * d, 12f * d, scrim)
        }
        return bmp
    }

    /** Monotone cubic through the points (Fritsch–Carlson), so a running total never appears to dip. */
    internal fun smooth(pts: List<Pair<Float, Float>>): Path {
        val path = Path()
        if (pts.isEmpty()) return path
        path.moveTo(pts[0].first, pts[0].second)
        if (pts.size == 2) { path.lineTo(pts[1].first, pts[1].second); return path }
        val n = pts.size
        val dx = FloatArray(n - 1) { pts[it + 1].first - pts[it].first }
        val m = FloatArray(n - 1) { if (dx[it] == 0f) 0f else (pts[it + 1].second - pts[it].second) / dx[it] }
        val t = FloatArray(n)
        t[0] = m[0]; t[n - 1] = m[n - 2]
        for (i in 1 until n - 1) t[i] = if (m[i - 1] * m[i] <= 0f) 0f else (m[i - 1] + m[i]) / 2f
        for (i in 0 until n - 1) {
            if (m[i] == 0f) { t[i] = 0f; t[i + 1] = 0f; continue }
            val a = t[i] / m[i]; val b = t[i + 1] / m[i]
            val s = a * a + b * b
            if (s > 9f) { val k = 3f / kotlin.math.sqrt(s); t[i] = k * a * m[i]; t[i + 1] = k * b * m[i] }
        }
        for (i in 0 until n - 1) {
            val (x0, y0) = pts[i]; val (x1, y1) = pts[i + 1]
            val h = dx[i] / 3f
            path.cubicTo(x0 + h, y0 + t[i] * h, x1 - h, y1 - t[i + 1] * h, x1, y1)
        }
        return path
    }

    private fun withAlpha(argb: Int, a: Float): Int =
        ((a.coerceIn(0f, 1f) * ((argb ushr 24) and 0xFF)).toInt() shl 24) or (argb and 0xFFFFFF)
}
