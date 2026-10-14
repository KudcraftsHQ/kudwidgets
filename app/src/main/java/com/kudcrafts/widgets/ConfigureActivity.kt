package com.kudcrafts.widgets

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.kudcrafts.widgets.gallery.WidgetKind
import com.kudcrafts.widgets.gallery.WidgetPreview
import com.kudcrafts.widgets.store.OptionsStore
import com.kudcrafts.widgets.store.Period
import com.kudcrafts.widgets.store.WidgetOptions
import com.kudcrafts.widgets.store.cleanTapUrl
import com.kudcrafts.widgets.ui.AppUi
import com.kudcrafts.widgets.ui.Palette
import com.kudcrafts.widgets.work.Refresher
import kotlinx.coroutines.launch

/**
 * A widget's options: shown when it's added (below Android 12; from 12 on it's optional) and on long-press
 * › reconfigure. Period, which business or product, and where a tap goes. The preview is the real widget.
 */
class ConfigureActivity : ComponentActivity() {
    private var options by mutableStateOf<WidgetOptions?>(null)
    private var tapText by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = intent?.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (id == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return }
        // Backing out keeps the defaults rather than dropping a widget the person just placed.
        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
        val provider = AppWidgetManager.getInstance(this).getAppWidgetInfo(id)?.provider?.className
        val kind = WidgetKind.forProvider(provider) ?: run { finish(); return }
        lifecycleScope.launch {
            val saved = OptionsStore(this@ConfigureActivity).get(id)
            tapText = saved.tapUrl.orEmpty()
            options = saved
        }
        setContent { Screen(kind, id) }
    }

    @OptIn(ExperimentalLayoutApi::class)
    @Composable
    private fun Screen(kind: WidgetKind, id: Int) {
        val o = options ?: return
        val choices by produceState(emptyList<Pair<String, String>>(), kind) { value = kind.choices(this@ConfigureActivity) }
        val preview = o.copy(tapUrl = cleanTapUrl(tapText))
        Column(
            Modifier.fillMaxSize().background(Palette.appBackground).safeDrawingPadding()
                .verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AppUi.Title(kind.title)
            WidgetPreview(kind, preview, version = 0)

            if (kind.hasPeriod) {
                AppUi.Section("Period")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Period.entries.forEach { p ->
                        AppUi.Choice(p.label.replaceFirstChar { it.uppercase() }, o.period == p) { options = o.copy(period = p) }
                    }
                }
                if (o.period != Period.Today) AppUi.Dim("Only today compares with yesterday.")
            }

            kind.filterNoun?.let { noun ->
                AppUi.Section(noun.replaceFirstChar { it.uppercase() })
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppUi.Choice("All", o.filter == null) { options = o.copy(filter = null) }
                    choices.forEach { (slug, name) -> AppUi.Choice(name, o.filter == slug) { options = o.copy(filter = slug) } }
                }
                if (choices.isEmpty()) AppUi.Dim("Sign in to ${kind.source.label} in the app to pick a $noun.")
            }

            AppUi.Section("Tap opens")
            BasicTextField(
                value = tapText,
                onValueChange = { tapText = it },
                singleLine = true,
                textStyle = TextStyle(color = Palette.cream, fontSize = 15.sp),
                cursorBrush = SolidColor(Palette.cream),
                modifier = Modifier.fillMaxWidth()
                    .background(AppUi.card, RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                decorationBox = { inner ->
                    if (tapText.isEmpty()) AppUi.Dim("The widget's own dashboard (default)")
                    inner()
                },
            )
            if (tapText.isNotBlank() && cleanTapUrl(tapText) == null) AppUi.Body("That isn't an https link.", Palette.downText)

            AppUi.Button("Save") { save(id, o) }
        }
    }

    private fun save(id: Int, o: WidgetOptions) {
        if (tapText.isNotBlank() && cleanTapUrl(tapText) == null) return
        lifecycleScope.launch {
            OptionsStore(this@ConfigureActivity).set(id, o.copy(tapUrl = cleanTapUrl(tapText)))
            Refresher.redraw(this@ConfigureActivity)
            // A new period may need a read the cache doesn't have yet.
            Refresher.now(this@ConfigureActivity)
            finish()
        }
    }
}
