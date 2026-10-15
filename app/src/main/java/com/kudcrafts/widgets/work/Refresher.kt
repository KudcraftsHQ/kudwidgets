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
import com.kudcrafts.widgets.live.Live
import com.kudcrafts.widgets.store.Source
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.TimeUnit

/**
 * Every 15 minutes (WorkManager's floor), plus on demand: a widget tap, opening the app, signing in, and the live
 * service when a source's event stream says its numbers moved.
 */
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

    private val _version = MutableStateFlow(0L)

    /**
     * Bumped on every redraw. A Glance session that is still running recomposes on update() instead of calling
     * provideGlance again, so the widgets watch this to re-read the cache (see `cached` in Common.kt).
     */
    val version: StateFlow<Long> = _version

    /** Redraw every widget from the cache, without fetching. */
    suspend fun redraw(context: Context) {
        _version.value++
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

    suspend fun refreshAll(context: Context) = refresh(context, Source.entries.toSet(), "")

    /** Fetch the given sources, log each with `why` (e.g. " · live"), redraw. */
    suspend fun refresh(context: Context, sources: Set<Source>, why: String) {
        // Debug demo data (DemoSeeder) stays put: no network while the flag file exists.
        if (java.io.File(context.filesDir, "demo").exists()) return redraw(context)
        if (Source.Trading in sources) {
            val periods = periodsFor(context, listOf(TradingTotalWidget::class.java, TradingBusinessWidget::class.java))
            val trading = runCatching { TradingRepo.refresh(context, periods) }
            RefreshLog(context).add("trading", trading.fold({ it.name }, { "error: ${it.message}" }) + why)
        }
        if (Source.Board in sources) {
            val periods = periodsFor(context, listOf(BoardTodayWidget::class.java, FunnelWidget::class.java))
            val board = runCatching { BoardRepo.refresh(context, periods) }
            RefreshLog(context).add("board", board.fold({ it.name }, { "error: ${it.message}" }) + why)
        }
        redraw(context)
    }
}

class RefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        Refresher.refreshAll(applicationContext)
        // Self-heal: if the system killed the live service, bring it back. Only allowed from the background when
        // the app is exempt from battery optimisation; otherwise this is a no-op until the app is next opened.
        Live.ensure(applicationContext)
        return Result.success()
    }
}
