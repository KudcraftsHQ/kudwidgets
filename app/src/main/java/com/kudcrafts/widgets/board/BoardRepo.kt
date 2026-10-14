package com.kudcrafts.widgets.board

import android.content.Context
import com.kudcrafts.widgets.core.AuthStatus
import com.kudcrafts.widgets.core.Display
import com.kudcrafts.widgets.core.FetchResult
import com.kudcrafts.widgets.core.after
import com.kudcrafts.widgets.net.Http
import com.kudcrafts.widgets.store.Accounts
import com.kudcrafts.widgets.store.CacheStore
import com.kudcrafts.widgets.store.Period
import com.kudcrafts.widgets.store.Source
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The one place board data is fetched, cached and read back for drawing. All three board widgets share it. */
object BoardRepo {
    const val DASHBOARD = "https://board.kudcrafts.com/"

    private fun cache(context: Context, period: Period = Period.Today) =
        CacheStore(context, if (period == Period.Today) "board" else "board-${period.preset}", BoardOverview.serializer())

    suspend fun display(context: Context, period: Period = Period.Today, now: Long = System.currentTimeMillis()): Display<BoardOverview> {
        val auth = Accounts(context).account(Source.Board).status(now)
        return Display.resolve(auth, cache(context, period).read(), now)
    }

    suspend fun refresh(
        context: Context,
        periods: Set<Period> = setOf(Period.Today),
        now: Long = System.currentTimeMillis(),
    ): AuthStatus = withContext(Dispatchers.IO) {
        val accounts = Accounts(context)
        val status = accounts.account(Source.Board).status(now)
        if (status != AuthStatus.SignedIn) return@withContext status
        val token = accounts.token(Source.Board) ?: run {
            accounts.signOut(Source.Board)
            return@withContext AuthStatus.SignedOut
        }
        val api = BoardApi(Http.client)
        for (period in periods.sortedBy { it.ordinal }) {
            val store = cache(context, period)
            val result = api.overview(token, period.preset)
            store.write(store.read().after(result, System.currentTimeMillis()))
            if (result == FetchResult.Refused) {
                accounts.markRefused(Source.Board)
                return@withContext AuthStatus.Refused
            }
        }
        AuthStatus.SignedIn
    }

    fun clear(context: Context) = Period.entries.forEach { cache(context, it).clear() }
}
