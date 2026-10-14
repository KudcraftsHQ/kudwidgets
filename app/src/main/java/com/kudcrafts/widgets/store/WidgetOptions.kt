package com.kudcrafts.widgets.store

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidgetManager
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** The window a widget shows; the names are the presets both servers take. */
@Serializable
enum class Period(val preset: String, val label: String) {
    Today("today", "today"),
    Yesterday("yesterday", "yesterday"),
    Week("last7", "7 days"),
    Month("this_month", "month"),
}

/**
 * Per-widget options (docs/PLAN.md › Per-widget options), chosen in the configure screen when the widget is
 * added or reconfigured with a long-press.
 */
@Serializable
data class WidgetOptions(
    val period: Period = Period.Today,
    /** A business slug (Trading · total) or product slug (Board · today, Funnel); null = all of them. */
    val filter: String? = null,
    /** Open this instead of the widget's own dashboard link. */
    val tapUrl: String? = null,
)

private val Context.widgetOptions by preferencesDataStore(name = "widget-options")

class OptionsStore(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true }
    private fun key(appWidgetId: Int) = stringPreferencesKey("w.$appWidgetId")

    suspend fun get(appWidgetId: Int): WidgetOptions =
        context.widgetOptions.data.first()[key(appWidgetId)]
            ?.let { runCatching { json.decodeFromString(WidgetOptions.serializer(), it) }.getOrNull() }
            ?: WidgetOptions()

    /** Options for a placed widget; defaults for a preview (the gallery renders with a fake id). */
    suspend fun get(glanceId: GlanceId): WidgetOptions =
        runCatching { get(GlanceAppWidgetManager(context).getAppWidgetId(glanceId)) }.getOrDefault(WidgetOptions())

    suspend fun set(appWidgetId: Int, options: WidgetOptions) {
        context.widgetOptions.edit { it[key(appWidgetId)] = json.encodeToString(WidgetOptions.serializer(), options) }
    }

    suspend fun remove(appWidgetIds: IntArray) {
        context.widgetOptions.edit { p -> appWidgetIds.forEach { p.remove(key(it)) } }
    }
}

/** A tap URL the person typed: https only, or nothing. */
fun cleanTapUrl(raw: String?): String? {
    val s = raw?.trim().orEmpty()
    if (s.isEmpty()) return null
    val withScheme = if ("://" in s) s else "https://$s"
    return withScheme.takeIf { it.startsWith("https://") && it.length > "https://".length && ' ' !in it }
}
