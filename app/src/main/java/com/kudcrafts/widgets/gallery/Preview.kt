package com.kudcrafts.widgets.gallery

import android.annotation.SuppressLint
import android.content.Context
import android.view.MotionEvent
import android.widget.FrameLayout
import android.widget.RemoteViews
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.min
import androidx.compose.ui.viewinterop.AndroidView
import androidx.glance.appwidget.ExperimentalGlanceRemoteViewsApi
import androidx.glance.appwidget.compose
import com.kudcrafts.widgets.store.WidgetOptions

/** A host that shows a widget but swallows taps: the preview is to look at, not to open dashboards from. */
@SuppressLint("ViewConstructor")
private class InertHost(context: Context) : FrameLayout(context) {
    override fun onInterceptTouchEvent(ev: MotionEvent?) = true
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent?) = true
}

/**
 * The widget exactly as the launcher draws it: the same Glance content composed to RemoteViews and inflated
 * here, at its home-screen size (narrowed to fit the screen). `version` re-renders after a refresh.
 */
@OptIn(ExperimentalGlanceRemoteViewsApi::class)
@Composable
fun WidgetPreview(kind: WidgetKind, options: WidgetOptions, version: Int, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val w = min(kind.size.width, maxWidth)
        val size = DpSize(w, kind.size.height)
        val views by produceState<RemoteViews?>(null, kind, options, version, w) {
            value = runCatching { PreviewWidget(kind, options).compose(context, size = size) }.getOrNull()
        }
        AndroidView(
            factory = { InertHost(it) },
            update = { host ->
                host.removeAllViews()
                views?.let { rv -> runCatching { host.addView(rv.apply(host.context, host)) } }
            },
            modifier = Modifier.width(size.width).height(size.height),
        )
    }
}
