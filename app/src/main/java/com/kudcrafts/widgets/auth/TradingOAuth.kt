package com.kudcrafts.widgets.auth

import java.net.URLEncoder
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * kudtrading sign-in: authorization code + PKCE S256 against the `kudtrading login` flow, as a separate
 * public client (kudtrading PR "Kud Widgets sign-in", src/lib/cli-auth.ts):
 *
 *  - authorize: GET {base}/cli/authorize?client_id=kud-widgets&redirect_uri=…&state=…
 *    &code_challenge=…&code_challenge_method=S256&device_name=…  (opened in a Custom Tab)
 *  - callback:  com.kudcrafts.widgets:/oauth/callback?code=…&state=…  or  ?error=access_denied&state=…
 *  - token:     POST {base}/api/cli/auth/token  JSON {client_id, code, code_verifier, redirect_uri}
 *               → {account:{email}, apiKey, keyId, keyName, readOnly, issuedAt, expiresAt}
 *  - sign-out:  POST {base}/api/cli/auth/revoke  with the key as bearer → {revoked}
 *
 * Contract: KudcraftsHQ/trading-backoffice#630.
 * The key is read-only and lives 365 days; it is used as `Authorization: Bearer <apiKey>`.
 */
object TradingOAuth {
    const val BASE = "https://trading.kudcrafts.com"
    const val CLIENT_ID = "kud-widgets"
    const val REDIRECT_URI = "com.kudcrafts.widgets:/oauth/callback"

    data class Pending(val state: String, val verifier: String)

    fun authorizeUrl(pending: Pending, deviceName: String, base: String = BASE): String =
        "$base/cli/authorize?" + listOf(
            "client_id" to CLIENT_ID,
            "redirect_uri" to REDIRECT_URI,
            "response_type" to "code",
            "state" to pending.state,
            "code_challenge" to Pkce.challenge(pending.verifier),
            "code_challenge_method" to "S256",
            "device_name" to deviceName,
        ).joinToString("&") { (k, v) -> "$k=${URLEncoder.encode(v, Charsets.UTF_8).replace("+", "%20")}" }

    sealed interface Callback {
        data class Code(val code: String) : Callback
        data class Denied(val error: String) : Callback
        /** The redirect doesn't belong to the sign-in we started (stale tab, replay, forged link). */
        data object Mismatch : Callback
    }

    /** Read the redirect's query parameters against the sign-in we started. */
    fun parseCallback(params: Map<String, String>, pending: Pending?): Callback {
        if (pending == null || params["state"] != pending.state) return Callback.Mismatch
        params["error"]?.let { return Callback.Denied(it) }
        val code = params["code"]
        return if (code.isNullOrBlank()) Callback.Denied("invalid_request") else Callback.Code(code)
    }

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Serializable
    data class TokenRequest(
        @SerialName("client_id") val clientId: String = CLIENT_ID,
        val code: String,
        @SerialName("code_verifier") val codeVerifier: String,
        @SerialName("redirect_uri") val redirectUri: String = REDIRECT_URI,
    )

    @Serializable
    data class Account(val email: String)

    @Serializable
    data class TokenResponse(
        val account: Account,
        val apiKey: String,
        val keyId: String? = null,
        val keyName: String? = null,
        val readOnly: Boolean? = null,
        val issuedAt: String? = null,
        val expiresAt: String? = null,
    )

    @Serializable
    data class ErrorResponse(val error: String? = null)

    fun tokenRequestBody(code: String, verifier: String): String =
        json.encodeToString(TokenRequest.serializer(), TokenRequest(code = code, codeVerifier = verifier))

    fun parseToken(body: String): TokenResponse = json.decodeFromString(TokenResponse.serializer(), body)

    fun parseError(body: String): String =
        runCatching { json.decodeFromString(ErrorResponse.serializer(), body).error }.getOrNull() ?: "server_error"

    /** Human words for the token endpoint's error codes. */
    fun describe(error: String): String = when (error) {
        "access_denied" -> "Sign-in was cancelled."
        "not_permitted" -> "This account may not sign in to Kud Widgets."
        "invalid_grant", "expired_code", "redirect_uri_mismatch" -> "The sign-in expired. Try again."
        "invalid_client", "invalid_redirect_uri" -> "kudtrading doesn't know Kud Widgets yet (server not updated)."
        "rate_limited" -> "Too many attempts. Wait a minute and try again."
        else -> "Sign-in failed ($error)."
    }
}
