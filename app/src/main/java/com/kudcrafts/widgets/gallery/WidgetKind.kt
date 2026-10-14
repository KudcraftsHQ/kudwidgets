package com.kudcrafts.widgets.gallery

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import com.kudcrafts.widgets.board.BoardRepo
import com.kudcrafts.widgets.store.Source
import com.kudcrafts.widgets.store.WidgetOptions
import com.kudcrafts.widgets.trading.TradingRepo
import com.kudcrafts.widgets.widget.BoardTodayContent
import com.kudcrafts.widgets.widget.BoardTodayReceiver
import com.kudcrafts.widgets.widget.FunnelContent
import com.kudcrafts.widgets.widget.FunnelReceiver
import com.kudcrafts.widgets.widget.OpenAiContent
import com.kudcrafts.widgets.widget.OpenAiReceiver
import com.kudcrafts.widgets.widget.TradingBusinessContent
import com.kudcrafts.widgets.widget.TradingBusinessReceiver
import com.kudcrafts.widgets.widget.TradingTotalContent
import com.kudcrafts.widgets.widget.TradingTotalReceiver

/** Every widget the app ships, with what its options screen offers. */
enum class WidgetKind(
    val title: String,
    val receiver: Class<*>,
    val source: Source,
    /** Its default size on his 5-column launcher, for previews. */
    val size: DpSize,
    val hasPeriod: Boolean,
    /** "business" / "product" when the widget can follow one of them, else null. */
    val filterNoun: String?,
) {
    TradingTotal("Trading · total", TradingTotalReceiver::class.java, Source.Trading, DpSize(390.dp, 158.dp), true, "business"),
    TradingBusiness("Trading · by business", TradingBusinessReceiver::class.java, Source.Trading, DpSize(390.dp, 240.dp), true, null),
    BoardToday("Board · today", BoardTodayReceiver::class.java, Source.Board, DpSize(390.dp, 82.dp), true, "product"),
    Funnel("Board · funnel", FunnelReceiver::class.java, Source.Board, DpSize(234.dp, 82.dp), true, "product"),
    OpenAi("OpenAI credit", OpenAiReceiver::class.java, Source.Board, DpSize(150.dp, 82.dp), false, null);

    /** The business or product choices from the cached read: (slug, name). */
    suspend fun choices(context: Context): List<Pair<String, String>> = when (this) {
        TradingTotal -> (TradingRepo.display(context) as? com.kudcrafts.widgets.core.Display.Data)
            ?.data?.today?.businesses?.map { it.slug to it.name }.orEmpty()
        BoardToday, Funnel -> (BoardRepo.display(context) as? com.kudcrafts.widgets.core.Display.Data)
            ?.data?.products?.map { it.slug to it.name }.orEmpty()
        else -> emptyList()
    }

    companion object {
        fun forProvider(className: String?): WidgetKind? = entries.firstOrNull { it.receiver.name == className }
    }
}

/** The real widget content with explicit options, for the gallery and the options screen. Never placed. */
class PreviewWidget(private val kind: WidgetKind, private val options: WidgetOptions) : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        when (kind) {
            WidgetKind.TradingTotal -> TradingRepo.display(context, options.period).let { d -> provideContent { TradingTotalContent(d, options) } }
            WidgetKind.TradingBusiness -> TradingRepo.display(context, options.period).let { d -> provideContent { TradingBusinessContent(d, options) } }
            WidgetKind.BoardToday -> BoardRepo.display(context, options.period).let { d -> provideContent { BoardTodayContent(d, options) } }
            WidgetKind.Funnel -> BoardRepo.display(context, options.period).let { d -> provideContent { FunnelContent(d, options) } }
            WidgetKind.OpenAi -> BoardRepo.display(context).let { d -> provideContent { OpenAiContent(d, options) } }
        }
    }
}
