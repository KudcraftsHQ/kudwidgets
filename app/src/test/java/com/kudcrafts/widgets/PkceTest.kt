package com.kudcrafts.widgets

import com.kudcrafts.widgets.auth.Pkce
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PkceTest {
    @Test fun challengeIsBase64UrlSha256WithoutPadding() {
        // Reference value computed independently: base64url(sha256(v)) with Python's hashlib.
        assertEquals("Kv2If-Cxa-3Jo2RTYvH7ScyxHmU-VY_IJkGa6_F-G_M", Pkce.challenge("kud-widgets-test-verifier-0123456789abcdefghijk"))
    }

    @Test fun verifierFitsTheServersRules() {
        repeat(50) {
            val v = Pkce.verifier()
            // kudtrading: 43..128 chars; RFC 7636 unreserved characters.
            assertTrue(v.length in 43..128)
            assertTrue(v.matches(Regex("^[A-Za-z0-9._~-]+$")))
            // validateChallenge(): ^[A-Za-z0-9_-]{43}$
            assertTrue(Pkce.challenge(v).matches(Regex("^[A-Za-z0-9_-]{43}$")))
        }
    }

    @Test fun freshValuesEveryTime() {
        assertNotEquals(Pkce.verifier(), Pkce.verifier())
        assertNotEquals(Pkce.state(), Pkce.state())
    }
}
