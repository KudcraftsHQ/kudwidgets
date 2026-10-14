package com.kudcrafts.widgets.auth

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/** RFC 7636 PKCE, S256 only (the only method kudtrading accepts). */
object Pkce {
    private val b64 = Base64.getUrlEncoder().withoutPadding()

    /** 32 random bytes → a 43-char base64url verifier (RFC 7636 allows 43..128). */
    fun verifier(random: SecureRandom = SecureRandom()): String =
        ByteArray(32).also(random::nextBytes).let(b64::encodeToString)

    /** BASE64URL(SHA256(ascii(verifier))), 43 chars. */
    fun challenge(verifier: String): String =
        b64.encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)))

    /** An opaque CSRF `state`. */
    fun state(random: SecureRandom = SecureRandom()): String =
        ByteArray(16).also(random::nextBytes).let(b64::encodeToString)
}
