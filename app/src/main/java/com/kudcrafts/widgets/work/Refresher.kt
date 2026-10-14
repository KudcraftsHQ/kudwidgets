package com.kudcrafts.widgets.work

import android.content.Context
import androidx.glance.appwidget.updateAll
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

    suspend fun refreshAll(context: Context) {
        val trading = runCatching { TradingRepo.refresh(context) }
        RefreshLog(context).add("trading", trading.fold({ it.name }, { "error: ${it.message}" }))
        val board = runCatching { BoardRepo.refresh(context) }
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
