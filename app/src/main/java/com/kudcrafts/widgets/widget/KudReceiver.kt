package com.kudcrafts.widgets.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import com.kudcrafts.widgets.store.OptionsStore
import com.kudcrafts.widgets.work.Refresher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Every Kud widget: make sure the refresh is scheduled, fetch at once, forget options of removed widgets. */
abstract class KudReceiver : GlanceAppWidgetReceiver() {
    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        Refresher.schedule(context)
        Refresher.now(context)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try { OptionsStore(context).remove(appWidgetIds) } finally { pending.finish() }
        }
    }
}
