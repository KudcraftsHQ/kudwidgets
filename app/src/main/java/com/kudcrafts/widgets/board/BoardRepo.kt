package com.kudcrafts.widgets.board

import android.content.Context
import com.kudcrafts.widgets.core.AuthStatus
import com.kudcrafts.widgets.core.Display
import com.kudcrafts.widgets.core.FetchResult
import com.kudcrafts.widgets.core.after
import com.kudcrafts.widgets.net.Http
import com.kudcrafts.widgets.store.Accounts
import com.kudcrafts.widgets.store.CacheStore
import com.kudcrafts.widgets.store.Source
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The one place board data is fetched, cached and read back for drawing. All three board widgets share it. */
object BoardRepo {
    const val DASHBOARD = "https://board.kudcrafts.com/"

    private fun cache(context: Context) = CacheStore(context, "board", BoardOverview.serializer())

    suspend fun display(context: Context, now: Long = System.currentTimeMillis()): Display<BoardOverview> {
        val auth = Accounts(context).account(Source.Board).status(now)
        return Display.resolve(auth, cache(context).read(), now)
    }

    suspend fun refresh(context: Context, now: Long = System.currentTimeMillis()): AuthStatus = withContext(Dispatchers.IO) {
        val accounts = Accounts(context)
        val status = accounts.account(Source.Board).status(now)
        if (status != AuthStatus.SignedIn) return@withContext status
        val token = accounts.token(Source.Board) ?: run {
            accounts.signOut(Source.Board)
            return@withContext AuthStatus.SignedOut
        }
        val store = cache(context)
        val result = BoardApi(Http.client).overview(token)
        store.write(store.read().after(result, System.currentTimeMillis()))
        if (result == FetchResult.Refused) {
            accounts.markRefused(Source.Board)
            AuthStatus.Refused
        } else AuthStatus.SignedIn
    }

    fun clear(context: Context) = cache(context).clear()
}
