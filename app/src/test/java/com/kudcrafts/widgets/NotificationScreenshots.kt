package com.kudcrafts.widgets

import android.app.Notification
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.View
import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import com.kudcrafts.widgets.board.BoardOverview
import com.kudcrafts.widgets.core.Display
import com.kudcrafts.widgets.live.Live
import com.kudcrafts.widgets.live.LiveNotification
import com.kudcrafts.widgets.live.Streams
import com.kudcrafts.widgets.trading.Overview
import com.kudcrafts.widgets.trading.TradingSnapshot
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** The live notification, collapsed and expanded, drawn with the platform's own templates. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h900dp-xxhdpi", application = android.app.Application::class)
class NotificationScreenshots {
    @Test fun liveNotification() {
        val ctx: Context = ApplicationProvider.getApplicationContext()
        val today = Overview.parse(javaClass.getResource("/overview-today.json")!!.readText())
        val yesterday = today.copy(preset = "yesterday", combined = today.combined.copy(series = today.combined.series.map { it.copy(revenue = it.revenue * 0.8) }))
        val board = BoardOverview.parse(javaClass.getResource("/board-today.json")!!.readText())
        val now = System.currentTimeMillis()
        val links = Streams.of(true, listOf("facemap", "typemap")).associateWith { Live.Link.Live }
        val c = LiveNotification.content(
            Display.Data(TradingSnapshot(today, yesterday), now, 0, false, null),
            Display.Data(board, now, 0, false, null),
            Live.State(running = true, links = links, lastEventAt = now - 60_000),
        )
        val n = LiveNotification.build(ctx, c)
        val b = Notification.Builder.recoverBuilder(ctx, n)
        val out = File(System.getProperty("user.dir"), "build/screenshots").apply { mkdirs() }
        for ((name, views) in listOf("collapsed" to b.createContentView(), "expanded" to b.createBigContentView())) {
            val parent = FrameLayout(ctx)
            val v = views.apply(ctx, parent)
            parent.addView(v)
            val w = (411 * 3)
            parent.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
            parent.layout(0, 0, w, parent.measuredHeight)
            val bmp = Bitmap.createBitmap(w, maxOf(parent.measuredHeight, 1), Bitmap.Config.ARGB_8888)
            Canvas(bmp).apply { drawColor(Color.rgb(0xF4, 0xF1, 0xF6)); parent.draw(this) }
            File(out, "notification-$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
