package com.kudcrafts.widgets.trading

import android.content.Context
import com.kudcrafts.widgets.core.Display
import com.kudcrafts.widgets.core.FetchResult
import com.kudcrafts.widgets.core.after
import com.kudcrafts.widgets.net.Http
import com.kudcrafts.widgets.store.Accounts
import com.kudcrafts.widgets.store.CacheStore
import com.kudcrafts.widgets.store.Source
import com.kudcrafts.widgets.core.AuthStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The one place Trading data is fetched, cached and read back for drawing. */
object TradingRepo {
    const val DASHBOARD = "https://trading.kudcrafts.com/"
    fun businessUrl(slug: String) = "https://trading.kudcrafts.com/$slug/overview"

    private fun cache(context: Context) = CacheStore(context, "trading", TradingSnapshot.serializer())

    suspend fun display(context: Context, now: Long = System.currentTimeMillis()): Display<TradingSnapshot> {
        val auth = Accounts(context).account(Source.Trading).status(now)
        return Display.resolve(auth, cache(context).read(), now)
    }

    /** Fetch and store. Returns the auth status after the attempt. */
    suspend fun refresh(context: Context, now: Long = System.currentTimeMillis()): AuthStatus = withContext(Dispatchers.IO) {
        val accounts = Accounts(context)
        val status = accounts.account(Source.Trading).status(now)
        if (status != AuthStatus.SignedIn) return@withContext status
        val token = accounts.token(Source.Trading)
        if (token == null) {
            accounts.signOut(Source.Trading)
            return@withContext AuthStatus.SignedOut
        }
        val store = cache(context)
        val result = TradingApi(Http.client).snapshot(token)
        store.write(store.read().after(result, System.currentTimeMillis()))
        if (result == FetchResult.Refused) {
            accounts.markRefused(Source.Trading)
            AuthStatus.Refused
        } else AuthStatus.SignedIn
    }

    fun clear(context: Context) = cache(context).clear()
}
