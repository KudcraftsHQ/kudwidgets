package com.kudcrafts.widgets

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.graphics.Color
import android.os.Bundle
import android.util.SizeF
import android.widget.LinearLayout
import android.widget.ScrollView
import com.kudcrafts.widgets.widget.BoardTodayReceiver
import com.kudcrafts.widgets.widget.FunnelReceiver
import com.kudcrafts.widgets.widget.OpenAiReceiver
import com.kudcrafts.widgets.widget.TradingBusinessReceiver
import com.kudcrafts.widgets.widget.TradingTotalReceiver

/**
 * Debug builds only: a home screen in miniature. Hosts every widget in a real AppWidgetHost and, like Hammas's
 * launcher, reports each one shorter than it is (`--ef report 0.55`, the default) while laying it out at its
 * real size, so the real renderer shows what his phone shows. Needs
 * `adb shell appwidget grantbind --package com.kudcrafts.widgets.debug` once.
 */
class HostActivity : Activity() {
    private lateinit var host: AppWidgetHost

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val report = intent.getFloatExtra("report", 0.55f)
        val d = resources.displayMetrics.density
        host = AppWidgetHost(this, 4711)
        host.deleteHost()
        val manager = AppWidgetManager.getInstance(this)
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(0x2B, 0x24, 0x2E))
            setPadding((12 * d).toInt(), (12 * d).toInt(), (12 * d).toInt(), (12 * d).toInt())
        }
        val widthDp = (resources.displayMetrics.widthPixels / d) - 24
        val rows = listOf(
            TradingTotalReceiver::class.java to (widthDp to 190f),
            TradingBusinessReceiver::class.java to (widthDp to 318f),
            BoardTodayReceiver::class.java to (widthDp to 92f),
            FunnelReceiver::class.java to (widthDp * 0.6f to 92f),
            OpenAiReceiver::class.java to (widthDp * 0.38f to 92f),
        )
        for ((receiver, size) in rows) {
            val (w, h) = size
            val id = host.allocateAppWidgetId()
            val provider = ComponentName(this, receiver)
            check(manager.bindAppWidgetIdIfAllowed(id, provider)) { "grantbind first" }
            val reportedH = (h * report).toInt()
            manager.updateAppWidgetOptions(id, Bundle().apply {
                putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, w.toInt())
                putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, w.toInt())
                putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, reportedH)
                putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, reportedH)
                if (android.os.Build.VERSION.SDK_INT >= 31) {
                    putParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES, arrayListOf(SizeF(w, reportedH.toFloat())))
                }
            })
            val view = host.createView(this, id, manager.getAppWidgetInfo(id))
            column.addView(view, LinearLayout.LayoutParams((w * d).toInt(), (h * d).toInt()).apply { bottomMargin = (12 * d).toInt() })
        }
        setContentView(ScrollView(this).apply { addView(column) })
    }

    override fun onStart() { super.onStart(); host.startListening() }
    override fun onStop() { super.onStop(); host.stopListening() }
}
