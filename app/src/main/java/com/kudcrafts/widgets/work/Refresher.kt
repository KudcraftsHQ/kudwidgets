package com.kudcrafts.widgets.work

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.updateAll
import com.kudcrafts.widgets.store.OptionsStore
import com.kudcrafts.widgets.store.Period
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.kudcrafts.widgets.store.RefreshLog
import com.kudcrafts.widgets.trading.TradingRepo
import com.kudcrafts.widgets.board.BoardRepo
import com.kudcrafts.widgets.widget.BoardTodayWidget
import com.kudcrafts.widgets.widget.FunnelWidget
import com.kudcrafts.widgets.widget.OpenAiWidget
import com.kudcrafts.widgets.widget.TradingBusinessWidget
import com.kudcrafts.widgets.widget.TradingTotalWidget
import java.util.concurrent.TimeUnit

/** Every 15 minutes (WorkManager's floor), plus on demand: a widget tap, opening the app, signing in. */
object Refresher {
    private const val PERIODIC = "refresh-periodic"
    private const val NOW = "refresh-now"

    private val online = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    fun schedule(context: Context) {
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<RefreshWorker>(15, TimeUnit.MINUTES).setConstraints(online).build(),
        )
    }

    fun now(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            NOW,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<RefreshWorker>()
                .setConstraints(online)
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build(),
        )
    }

    /** Redraw every widget from the cache, without fetching. */
    suspend fun redraw(context: Context) {
        TradingTotalWidget().updateAll(context)
        TradingBusinessWidget().updateAll(context)
        BoardTodayWidget().updateAll(context)
        FunnelWidget().updateAll(context)
        OpenAiWidget().updateAll(context)
    }

    /** The windows the placed widgets of these kinds show, plus today (the gallery and app always show it). */
    suspend fun periodsFor(context: Context, kinds: List<Class<out GlanceAppWidget>>): Set<Period> {
        val manager = GlanceAppWidgetManager(context)
        val options = OptionsStore(context)
        val out = mutableSetOf(Period.Today)
        for (kind in kinds) for (id in manager.getGlanceIds(kind)) out += options.get(id).period
        return out
    }

    suspend fun refreshAll(context: Context) {
        val tradingPeriods = periodsFor(context, listOf(TradingTotalWidget::class.java, TradingBusinessWidget::class.java))
        val trading = runCatching { TradingRepo.refresh(context, tradingPeriods) }
        RefreshLog(context).add("trading", trading.fold({ it.name }, { "error: ${it.message}" }))
        val boardPeriods = periodsFor(context, listOf(BoardTodayWidget::class.java, FunnelWidget::class.java))
        val board = runCatching { BoardRepo.refresh(context, boardPeriods) }
        RefreshLog(context).add("board", board.fold({ it.name }, { "error: ${it.message}" }))
        redraw(context)
    }
}

class RefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        Refresher.refreshAll(applicationContext)
        return Result.success()
    }
}
