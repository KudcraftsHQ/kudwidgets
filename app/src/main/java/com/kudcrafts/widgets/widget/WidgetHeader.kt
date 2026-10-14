package com.kudcrafts.widgets.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.kudcrafts.widgets.R
import com.kudcrafts.widgets.ui.Palette

/** Header row on every widget: face · bold source · dim qualifier · right-aligned data time. */
@Composable
fun WidgetHeader(source: String, qualifier: String?, time: String?, timeColor: Color = Palette.creamDim) {
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            provider = ImageProvider(R.drawable.mascot_face),
            contentDescription = null,
            modifier = GlanceModifier.size(16.dp),
        )
        Spacer(GlanceModifier.width(8.dp))
        Text(
            source,
            style = TextStyle(color = ColorProvider(Palette.cream), fontSize = 14.sp, fontWeight = FontWeight.Bold),
            maxLines = 1,
        )
        if (qualifier != null) {
            Spacer(GlanceModifier.width(6.dp))
            Text(qualifier, style = TextStyle(color = ColorProvider(Palette.creamDim), fontSize = 13.sp), maxLines = 1, modifier = GlanceModifier.defaultWeight())
        }
        if (qualifier == null) Spacer(GlanceModifier.defaultWeight())
        if (time != null) {
            Spacer(GlanceModifier.width(8.dp))
            Text(time, style = TextStyle(color = ColorProvider(timeColor), fontSize = 13.sp), maxLines = 1)
        }
    }
}
