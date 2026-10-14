package com.kudcrafts.widgets.board

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * board's RFC 8628 device flow, exactly as `board login` and board-bar run it (server/routes/device.ts):
 * POST /api/board/device/code {client_id: board-cli, device_name}, open verification_uri_complete, then poll
 * POST /api/board/device/token {grant_type, device_code, client_id} until a `bct_…` token or a final error.
 */
object DeviceFlow {
    const val CLIENT_ID = "board-cli"
    const val GRANT = "urn:ietf:params:oauth:grant-type:device_code"

    @Serializable
    data class Code(
        @SerialName("device_code") val deviceCode: String,
        @SerialName("user_code") val userCode: String,
        @SerialName("verification_uri") val verificationUri: String,
        @SerialName("verification_uri_complete") val verificationUriComplete: String,
        @SerialName("expires_in") val expiresIn: Int,
        val interval: Int = 5,
    )

    @Serializable
    data class TokenInfo(val id: String? = null, val name: String? = null)

    @Serializable
    data class User(val email: String? = null)

    @Serializable
    data class Token(
        @SerialName("access_token") val accessToken: String,
        @SerialName("expires_at") val expiresAt: String? = null,
        val token: TokenInfo? = null,
        val user: User? = null,
    )

    @Serializable
    private data class OAuthError(val error: String? = null, @SerialName("error_description") val description: String? = null)

    val json = Json { ignoreUnknownKeys = true }

    sealed interface Poll {
        data class Done(val token: Token) : Poll
        data object Pending : Poll
        /** RFC 8628 §3.5: add 5 seconds to the interval. */
        data object SlowDown : Poll
        data class Failed(val message: String) : Poll
    }

    fun requestBody(deviceName: String): String =
        """{"client_id":"$CLIENT_ID","device_name":${Json.encodeToString(String.serializer(), deviceName)}}"""

    fun pollBody(deviceCode: String): String =
        """{"grant_type":"$GRANT","device_code":${Json.encodeToString(String.serializer(), deviceCode)},"client_id":"$CLIENT_ID"}"""

    fun parsePoll(status: Int, body: String): Poll {
        if (status == 200) {
            runCatching { json.decodeFromString(Token.serializer(), body) }.getOrNull()?.let { return Poll.Done(it) }
        }
        val err = runCatching { json.decodeFromString(OAuthError.serializer(), body) }.getOrNull()
        return when (err?.error) {
            "authorization_pending" -> Poll.Pending
            "slow_down" -> Poll.SlowDown
            "access_denied" -> Poll.Failed("The sign-in was denied.")
            "expired_token" -> Poll.Failed("The code expired. Try again.")
            else -> Poll.Failed(err?.description ?: "Sign-in failed (HTTP $status).")
        }
    }

    /** The polling schedule: next wait in seconds after a result, or null to stop. */
    fun nextInterval(current: Int, poll: Poll): Int? = when (poll) {
        Poll.Pending -> current
        Poll.SlowDown -> current + 5
        else -> null
    }
}
