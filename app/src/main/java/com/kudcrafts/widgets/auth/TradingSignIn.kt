package com.kudcrafts.widgets.auth

import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.ui.graphics.toArgb
import com.kudcrafts.widgets.net.Http
import com.kudcrafts.widgets.store.Accounts
import com.kudcrafts.widgets.store.Source
import com.kudcrafts.widgets.trading.TradingApi
import com.kudcrafts.widgets.trading.TradingRepo
import com.kudcrafts.widgets.ui.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant

/** Drives the kudtrading OAuth round trip from the app. */
object TradingSignIn {
    /** "Xiaomi 23078PND5G" — what Admin → API Keys shows after "Kud Widgets · ". */
    fun deviceName(): String {
        val maker = Build.MANUFACTURER.orEmpty().replaceFirstChar { it.uppercase() }
        val model = Build.MODEL.orEmpty()
        return if (model.startsWith(maker, ignoreCase = true)) model else "$maker $model".trim()
    }

    suspend fun start(context: Context) {
        val pending = TradingOAuth.Pending(state = Pkce.state(), verifier = Pkce.verifier())
        Accounts(context).savePending(Source.Trading, pending.state, pending.verifier)
        openTab(context, TradingOAuth.authorizeUrl(pending, deviceName()))
    }

    /** True when `uri` is our redirect. */
    fun isCallback(uri: Uri?): Boolean =
        uri != null && uri.scheme == "com.kudcrafts.widgets" && uri.path == "/oauth/callback"

    /** Finish a sign-in from the redirect. Returns null on success, or words for the person. */
    suspend fun finish(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
        val accounts = Accounts(context)
        val pending = accounts.pending(Source.Trading)?.let { (s, v) -> TradingOAuth.Pending(s, v) }
        val params = uri.queryParameterNames.associateWith { uri.getQueryParameter(it).orEmpty() }
        when (val cb = TradingOAuth.parseCallback(params, pending)) {
            TradingOAuth.Callback.Mismatch -> "That sign-in link is out of date. Try again."
            is TradingOAuth.Callback.Denied -> {
                accounts.clearPending(Source.Trading)
                TradingOAuth.describe(cb.error)
            }
            is TradingOAuth.Callback.Code -> {
                accounts.clearPending(Source.Trading)
                when (val r = TradingApi(Http.client).exchange(cb.code, pending!!.verifier)) {
                    is TradingApi.TokenResult.Error -> TradingOAuth.describe(r.code)
                    is TradingApi.TokenResult.Ok -> {
                        val t = r.token
                        TradingRepo.clear(context)
                        accounts.signIn(
                            Source.Trading,
                            token = t.apiKey,
                            email = t.account.email,
                            keyName = t.keyName,
                            expiresAt = t.expiresAt?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() },
                        )
                        null
                    }
                }
            }
        }
    }

    /** Revoke the key on the server (best effort), then forget it here. True when the server revoked it. */
    suspend fun signOut(context: Context): Boolean = withContext(Dispatchers.IO) {
        val accounts = Accounts(context)
        val revoked = accounts.token(Source.Trading)?.let { TradingApi(Http.client).revoke(it) } ?: false
        accounts.signOut(Source.Trading)
        TradingRepo.clear(context)
        revoked
    }

    fun openTab(context: Context, url: String) {
        val colors = CustomTabColorSchemeParams.Builder()
            .setToolbarColor(Palette.surface.toArgb())
            .setNavigationBarColor(Palette.surface.toArgb())
            .build()
        CustomTabsIntent.Builder()
            .setDefaultColorSchemeParams(colors)
            .setColorScheme(CustomTabsIntent.COLOR_SCHEME_DARK)
            .setShowTitle(true)
            .build()
            .launchUrl(context, Uri.parse(url))
    }
}
