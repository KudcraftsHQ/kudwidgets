package com.kudcrafts.widgets.widget

import android.content.Context
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.ImageProvider
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.kudcrafts.widgets.BuildConfigInfo
import com.kudcrafts.widgets.MainActivity
import com.kudcrafts.widgets.R
import com.kudcrafts.widgets.ui.Palette

/** Phase 1 placeholder: proves the release pipeline and the night look end to end. */
class PlaceholderWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val version = BuildConfigInfo.versionName(context)
        provideContent {
            Column(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(ImageProvider(R.drawable.widget_surface))
                    .cornerRadius(22.dp)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .clickable(actionStartActivity<MainActivity>()),
            ) {
                WidgetHeader(source = "Kud Widgets", qualifier = "v$version", time = null)
                Spacer(GlanceModifier.height(6.dp))
                Text(
                    "Trading and Board are on the way",
                    style = TextStyle(color = ColorProvider(Palette.creamDim), fontSize = 13.sp),
                    maxLines = 2,
                )
            }
        }
    }
}

class PlaceholderWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PlaceholderWidget()
}
