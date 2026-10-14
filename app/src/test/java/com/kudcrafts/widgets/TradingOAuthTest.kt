package com.kudcrafts.widgets

import com.kudcrafts.widgets.auth.Pkce
import com.kudcrafts.widgets.auth.TradingOAuth
import com.kudcrafts.widgets.auth.TradingOAuth.Callback
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URI
import java.net.URLDecoder

/** The contract in KudcraftsHQ/trading-backoffice#630 ("Kud Widgets sign-in") (src/lib/cli-auth.ts and the /api/cli/auth routes). */
class TradingOAuthTest {
    private val pending = TradingOAuth.Pending(state = "st4te", verifier = "kud-widgets-test-verifier-0123456789abcdefghijk")

    private fun query(url: String): Map<String, String> =
        URI(url).rawQuery.split('&').associate {
            val (k, v) = it.split('=', limit = 2)
            k to URLDecoder.decode(v, "UTF-8")
        }

    @Test fun authorizeUrlCarriesTheClientAndExactRedirect() {
        val url = TradingOAuth.authorizeUrl(pending, "Xiaomi 23078PND5G")
        assertTrue(url.startsWith("https://trading.kudcrafts.com/cli/authorize?"))
        val q = query(url)
        assertEquals("kud-widgets", q["client_id"])
        // Exact string match on the server: no normalisation, no prefix.
        assertEquals("com.kudcrafts.widgets:/oauth/callback", q["redirect_uri"])
        assertEquals("st4te", q["state"])
        assertEquals("S256", q["code_challenge_method"])
        assertEquals("code", q["response_type"])
        assertEquals(Pkce.challenge(pending.verifier), q["code_challenge"])
        assertEquals("Xiaomi 23078PND5G", q["device_name"])
        // Spaces are %20, never '+', which some decoders keep literally.
        assertTrue(url.contains("device_name=Xiaomi%2023078PND5G"))
        assertTrue(url.contains("redirect_uri=com.kudcrafts.widgets%3A%2Foauth%2Fcallback"))
    }

    @Test fun callbackWithMatchingStateGivesTheCode() {
        assertEquals(Callback.Code("abc"), TradingOAuth.parseCallback(mapOf("code" to "abc", "state" to "st4te"), pending))
    }

    @Test fun callbackWithWrongOrMissingStateIsRejected() {
        assertEquals(Callback.Mismatch, TradingOAuth.parseCallback(mapOf("code" to "abc", "state" to "other"), pending))
        assertEquals(Callback.Mismatch, TradingOAuth.parseCallback(mapOf("code" to "abc"), pending))
        assertEquals(Callback.Mismatch, TradingOAuth.parseCallback(mapOf("code" to "abc", "state" to "st4te"), null))
    }

    @Test fun deniedCallback() {
        assertEquals(
            Callback.Denied("access_denied"),
            TradingOAuth.parseCallback(mapOf("error" to "access_denied", "state" to "st4te"), pending),
        )
        assertEquals(Callback.Denied("invalid_request"), TradingOAuth.parseCallback(mapOf("state" to "st4te"), pending))
    }

    @Test fun tokenRequestBodyNamesTheClient() {
        val body = Json.parseToJsonElement(TradingOAuth.tokenRequestBody("the-code", pending.verifier)).jsonObject
        assertEquals("kud-widgets", body["client_id"]!!.jsonPrimitive.content)
        assertEquals("the-code", body["code"]!!.jsonPrimitive.content)
        assertEquals(pending.verifier, body["code_verifier"]!!.jsonPrimitive.content)
        assertEquals("com.kudcrafts.widgets:/oauth/callback", body["redirect_uri"]!!.jsonPrimitive.content)
    }

    @Test fun tokenResponseParses() {
        val t = TradingOAuth.parseToken(
            """{"account":{"email":"hammas@kudcrafts.com"},"apiKey":"kud_read_abc","keyId":"k1",
               "keyName":"Kud Widgets · Xiaomi 23078PND5G","readOnly":true,
               "issuedAt":"2026-10-14T04:00:00.000Z","expiresAt":"2027-10-14T04:00:00.000Z","extra":1}""",
        )
        assertEquals("kud_read_abc", t.apiKey)
        assertEquals("hammas@kudcrafts.com", t.account.email)
        assertEquals(true, t.readOnly)
        assertEquals("2027-10-14T04:00:00.000Z", t.expiresAt)
    }

    @Test fun errorsBecomeWords() {
        assertEquals("not_permitted", TradingOAuth.parseError("""{"error":"not_permitted"}"""))
        assertEquals("server_error", TradingOAuth.parseError("<html>"))
        assertTrue(TradingOAuth.describe("invalid_client").contains("server not updated"))
    }
}
