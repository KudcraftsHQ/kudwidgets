package com.kudcrafts.widgets.trading

import com.kudcrafts.widgets.auth.TradingOAuth
import com.kudcrafts.widgets.core.FetchResult
import kotlinx.serialization.Serializable
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/** Today and yesterday together: the widgets compare today against yesterday's running total. */
@Serializable
data class TradingSnapshot(val today: Overview, val yesterday: Overview?)

class TradingApi(private val http: OkHttpClient, private val base: String = TradingOAuth.BASE) {

    fun overview(token: String, preset: String): FetchResult<Overview> = try {
        val req = Request.Builder()
            .url("$base/api/menubar/overview?preset=$preset")
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/json")
            .build()
        http.newCall(req).execute().use { res ->
            FetchResult.forStatus(res.code) ?: FetchResult.Ok(Overview.parse(res.body!!.string()))
        }
    } catch (e: IOException) {
        FetchResult.Error(e.message ?: "network error")
    } catch (e: kotlinx.serialization.SerializationException) {
        FetchResult.Error("unexpected response")
    }

    /**
     * One window. For today also yesterday, for the comparison: a refused or failed `today` wins; a failed
     * `yesterday` just drops the comparison.
     */
    fun snapshot(token: String, preset: String = "today"): FetchResult<TradingSnapshot> =
        if (preset != "today") when (val r = overview(token, preset)) {
            is FetchResult.Ok -> FetchResult.Ok(TradingSnapshot(r.data, null))
            FetchResult.Refused -> FetchResult.Refused
            is FetchResult.Error -> r
        } else when (val today = overview(token, "today")) {
            is FetchResult.Ok -> {
                val y = overview(token, "yesterday")
                if (y == FetchResult.Refused) FetchResult.Refused
                else FetchResult.Ok(TradingSnapshot(today.data, (y as? FetchResult.Ok)?.data))
            }
            FetchResult.Refused -> FetchResult.Refused
            is FetchResult.Error -> today
        }

    /** Revoke our own key (allowed for read-only keys). True when the server confirmed it. */
    fun revoke(token: String): Boolean = try {
        val req = Request.Builder()
            .url("$base/api/cli/auth/revoke")
            .header("Authorization", "Bearer $token")
            .post(ByteArray(0).toRequestBody(null))
            .build()
        http.newCall(req).execute().use { res -> res.isSuccessful && res.body?.string().orEmpty().contains("\"revoked\":true") }
    } catch (e: IOException) {
        false
    }

    sealed interface TokenResult {
        data class Ok(val token: TradingOAuth.TokenResponse) : TokenResult
        data class Error(val code: String) : TokenResult
    }

    fun exchange(code: String, verifier: String): TokenResult = try {
        val req = Request.Builder()
            .url("$base/api/cli/auth/token")
            .post(TradingOAuth.tokenRequestBody(code, verifier).toRequestBody("application/json".toMediaType()))
            .build()
        http.newCall(req).execute().use { res ->
            val body = res.body?.string().orEmpty()
            if (res.isSuccessful) TokenResult.Ok(TradingOAuth.parseToken(body))
            else TokenResult.Error(TradingOAuth.parseError(body))
        }
    } catch (e: IOException) {
        TokenResult.Error("network")
    } catch (e: kotlinx.serialization.SerializationException) {
        TokenResult.Error("unexpected_response")
    }
}
